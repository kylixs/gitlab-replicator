package com.gitlab.mirror.server.service.monitor;

import com.gitlab.mirror.common.model.GitLabProject;
import com.gitlab.mirror.common.model.RepositoryBranch;
import com.gitlab.mirror.server.entity.ProjectBranchSnapshot;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.mapper.TargetProjectInfoMapper;
import com.gitlab.mirror.server.service.PullSyncConfigService;
import com.gitlab.mirror.server.service.SyncTaskService;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import com.gitlab.mirror.server.service.monitor.model.ScanResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Unified Project Monitor Service
 * <p>
 * Orchestrates the project scanning and monitoring workflow.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class UnifiedProjectMonitor {

    private final BatchQueryExecutor batchQueryExecutor;
    private final UpdateProjectDataService updateProjectDataService;
    private final DiffCalculator diffCalculator;
    private final LocalCacheManager cacheManager;
    private final SyncProjectMapper syncProjectMapper;
    private final MetricsExporter metricsExporter;
    private final com.gitlab.mirror.server.service.ProjectDiscoveryService projectDiscoveryService;
    private final SourceProjectInfoMapper sourceProjectInfoMapper;
    private final TargetProjectInfoMapper targetProjectInfoMapper;
    private final PullSyncConfigService pullSyncConfigService;
    private final SyncTaskService syncTaskService;
    private final com.gitlab.mirror.server.client.GitLabApiClient sourceGitLabApiClient;
    private final com.gitlab.mirror.server.client.GitLabApiClient targetGitLabApiClient;
    private final com.gitlab.mirror.server.service.BranchSnapshotService branchSnapshotService;

    public UnifiedProjectMonitor(
            BatchQueryExecutor batchQueryExecutor,
            UpdateProjectDataService updateProjectDataService,
            DiffCalculator diffCalculator,
            LocalCacheManager cacheManager,
            SyncProjectMapper syncProjectMapper,
            MetricsExporter metricsExporter,
            com.gitlab.mirror.server.service.ProjectDiscoveryService projectDiscoveryService,
            SourceProjectInfoMapper sourceProjectInfoMapper,
            TargetProjectInfoMapper targetProjectInfoMapper,
            PullSyncConfigService pullSyncConfigService,
            SyncTaskService syncTaskService,
            @org.springframework.beans.factory.annotation.Qualifier("sourceGitLabApiClient") com.gitlab.mirror.server.client.GitLabApiClient sourceGitLabApiClient,
            @org.springframework.beans.factory.annotation.Qualifier("targetGitLabApiClient") com.gitlab.mirror.server.client.GitLabApiClient targetGitLabApiClient,
            com.gitlab.mirror.server.service.BranchSnapshotService branchSnapshotService) {
        this.batchQueryExecutor = batchQueryExecutor;
        this.updateProjectDataService = updateProjectDataService;
        this.diffCalculator = diffCalculator;
        this.cacheManager = cacheManager;
        this.syncProjectMapper = syncProjectMapper;
        this.metricsExporter = metricsExporter;
        this.projectDiscoveryService = projectDiscoveryService;
        this.sourceProjectInfoMapper = sourceProjectInfoMapper;
        this.targetProjectInfoMapper = targetProjectInfoMapper;
        this.pullSyncConfigService = pullSyncConfigService;
        this.syncTaskService = syncTaskService;
        this.sourceGitLabApiClient = sourceGitLabApiClient;
        this.targetGitLabApiClient = targetGitLabApiClient;
        this.branchSnapshotService = branchSnapshotService;
    }

    /**
     * Scan projects and calculate differences
     *
     * @param scanType Scan type (incremental or full)
     * @return Scan result statistics
     */
    @Transactional(rollbackFor = Exception.class)
    public ScanResult scan(String scanType) {
        log.info("Starting {} scan", scanType);

        if ("full".equals(scanType)) {
            return fullScan();
        } else {
            return incrementalScan();
        }
    }

    /**
     * Full scan: Compare source and target GitLab projects, calculate differences, and update database
     */
    private ScanResult fullScan() {
        log.info("=== Starting FULL SCAN ===");
        LocalDateTime startTime = LocalDateTime.now();

        ScanResult.ScanResultBuilder resultBuilder = ScanResult.builder()
                .scanType("full")
                .startTime(startTime)
                .status("success");

        try {
            // Step 1: Fetch all projects from source and target GitLab in parallel
            long step1Start = System.currentTimeMillis();
            log.info("[FULL-SCAN] Step 1: Fetching projects from source and target GitLab...");

            List<GitLabProject> sourceProjects = batchQueryExecutor.querySourceProjects(null, 100);
            List<GitLabProject> targetProjects = batchQueryExecutor.queryTargetProjects(null, 100);

            long step1Duration = System.currentTimeMillis() - step1Start;
            log.info("[FULL-SCAN] Step 1 completed: {} source projects, {} target projects - {}ms",
                    sourceProjects.size(), targetProjects.size(), step1Duration);

            if (sourceProjects.isEmpty()) {
                log.info("No source projects found");
                return buildEmptyResult(resultBuilder, startTime);
            }

            // Step 2: Fetch branch lists for all projects
            long step2Start = System.currentTimeMillis();
            log.info("[FULL-SCAN] Step 2: Fetching branch lists...");

            // Build maps: projectId -> branches
            Map<Long, List<RepositoryBranch>> sourceBranchesMap = new HashMap<>();
            Map<Long, List<RepositoryBranch>> targetBranchesMap = new HashMap<>();

            // Fetch source branches
            for (GitLabProject project : sourceProjects) {
                try {
                    List<RepositoryBranch> branches = sourceGitLabApiClient.getAllBranches(project.getId());
                    sourceBranchesMap.put(project.getId(), branches);
                    log.debug("[FULL-SCAN] Fetched {} branches for source project {}",
                            branches.size(), project.getPathWithNamespace());
                } catch (Exception e) {
                    log.warn("[FULL-SCAN] Failed to fetch branches for source project {}: {}",
                            project.getPathWithNamespace(), e.getMessage());
                    sourceBranchesMap.put(project.getId(), new ArrayList<>());
                }
            }

            // Fetch target branches
            for (GitLabProject project : targetProjects) {
                try {
                    List<RepositoryBranch> branches = targetGitLabApiClient.getAllBranches(project.getId());
                    targetBranchesMap.put(project.getId(), branches);
                    log.debug("[FULL-SCAN] Fetched {} branches for target project {}",
                            branches.size(), project.getPathWithNamespace());
                } catch (Exception e) {
                    log.warn("[FULL-SCAN] Failed to fetch branches for target project {}: {}",
                            project.getPathWithNamespace(), e.getMessage());
                    targetBranchesMap.put(project.getId(), new ArrayList<>());
                }
            }

            long step2Duration = System.currentTimeMillis() - step2Start;
            log.info("[FULL-SCAN] Step 2 completed: fetched branches for {} source and {} target projects - {}ms",
                    sourceBranchesMap.size(), targetBranchesMap.size(), step2Duration);

            // Step 3: Query database, compare differences, and batch update
            long step3Start = System.currentTimeMillis();
            log.info("[FULL-SCAN] Step 3: Comparing with database and updating...");

            ComparisonResult comparisonResult = compareAndUpdate(
                    sourceProjects, targetProjects,
                    sourceBranchesMap, targetBranchesMap
            );

            long step3Duration = System.currentTimeMillis() - step3Start;
            log.info("[FULL-SCAN] Step 3 completed: {} new, {} updated, {} unchanged - {}ms",
                    comparisonResult.getNewProjectsCount(),
                    comparisonResult.getUpdatedProjectsCount(),
                    comparisonResult.getUnchangedProjectsCount(),
                    step3Duration);

            // Step 4: Calculate diffs only for affected projects
            long step4Start = System.currentTimeMillis();
            log.info("[FULL-SCAN] Step 4: Calculating diffs for affected projects...");

            List<Long> affectedSyncProjectIds = comparisonResult.getAffectedSyncProjectIds();
            List<ProjectDiff> diffs = new ArrayList<>();

            if (!affectedSyncProjectIds.isEmpty()) {
                diffs = diffCalculator.calculateDiffBatch(affectedSyncProjectIds);
            }

            long step4Duration = System.currentTimeMillis() - step4Start;
            log.info("[FULL-SCAN] Step 4 completed: {} diffs calculated - {}ms", diffs.size(), step4Duration);

            // Count changes
            int changesDetected = (int) diffs.stream()
                    .filter(d -> d.getStatus() != ProjectDiff.SyncStatus.SYNCED)
                    .count();

            // Update last scan time
            updateLastScanTime(LocalDateTime.now());

            // Build result
            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = Duration.between(startTime, endTime).toMillis();

            // Update metrics
            long step6Start = System.currentTimeMillis();
            metricsExporter.recordScanDuration(durationMs);
            metricsExporter.refreshSystemMetrics();
            metricsExporter.refreshProjectMetrics(diffs);
            long step6Duration = System.currentTimeMillis() - step6Start;
            log.info("[FULL-SCAN] Step 6: Updated metrics - {}ms", step6Duration);

            // Performance summary
            log.info("[FULL-SCAN] === PERFORMANCE SUMMARY ===");
            log.info("[FULL-SCAN] Total Duration: {}ms", durationMs);
            log.info("[FULL-SCAN] Step 1 (Fetch Projects):     {}ms ({}%)", step1Duration, String.format("%.1f", step1Duration * 100.0 / durationMs));
            log.info("[FULL-SCAN] Step 2 (Fetch Branches):     {}ms ({}%)", step2Duration, String.format("%.1f", step2Duration * 100.0 / durationMs));
            log.info("[FULL-SCAN] Step 3 (Compare & Update):   {}ms ({}%)", step3Duration, String.format("%.1f", step3Duration * 100.0 / durationMs));
            log.info("[FULL-SCAN] Step 4 (Calculate Diffs):    {}ms ({}%)", step4Duration, String.format("%.1f", step4Duration * 100.0 / durationMs));
            log.info("[FULL-SCAN] Step 5 (Update Metrics):     {}ms ({}%)", step6Duration, String.format("%.1f", step6Duration * 100.0 / durationMs));
            log.info("[FULL-SCAN] ================================");

            return resultBuilder
                    .durationMs(durationMs)
                    .projectsScanned(sourceProjects.size())
                    .projectsUpdated(comparisonResult.getUpdatedProjectsCount())
                    .newProjects(comparisonResult.getNewProjectsCount())
                    .changesDetected(changesDetected)
                    .projectChanges(comparisonResult.getAllChanges())
                    .endTime(endTime)
                    .build();

        } catch (Exception e) {
            log.error("Full scan failed", e);
            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = Duration.between(startTime, endTime).toMillis();

            return resultBuilder
                    .status("failed")
                    .errorMessage(e.getMessage())
                    .durationMs(durationMs)
                    .endTime(endTime)
                    .build();
        }
    }

    /**
     * Incremental scan: Only scan projects updated since last scan
     */
    private ScanResult incrementalScan() {
        log.info("=== Starting INCREMENTAL SCAN ===");
        LocalDateTime startTime = LocalDateTime.now();

        ScanResult.ScanResultBuilder resultBuilder = ScanResult.builder()
                .scanType("incremental")
                .startTime(startTime)
                .status("success");

        try {
            // Step 1: Query source projects updated since last scan
            long step1Start = System.currentTimeMillis();
            LocalDateTime updatedAfter = getLastScanTime();
            List<GitLabProject> sourceProjects = batchQueryExecutor.querySourceProjects(updatedAfter, 100);
            long step1Duration = System.currentTimeMillis() - step1Start;
            log.info("[INCR-SCAN] Step 1: Query {} source projects (updatedAfter: {}) - {}ms",
                    sourceProjects.size(), updatedAfter, step1Duration);

            if (sourceProjects.isEmpty()) {
                log.info("No projects to scan");
                return buildEmptyResult(resultBuilder, startTime);
            }

            // Step 2: Get project details using GraphQL batch query
            long step2Start = System.currentTimeMillis();
            List<com.gitlab.mirror.server.client.graphql.GraphQLProjectInfo> graphQLInfos =
                    batchQueryExecutor.getProjectDetailsBatchGraphQL(sourceProjects);
            long step2Duration = System.currentTimeMillis() - step2Start;
            log.info("[INCR-SCAN] Step 2: GraphQL batch query {} source projects - {}ms", sourceProjects.size(), step2Duration);

            // Convert to map
            Map<Long, com.gitlab.mirror.server.client.graphql.GraphQLProjectInfo> graphQLMap = graphQLInfos.stream()
                    .collect(Collectors.toMap(
                            com.gitlab.mirror.server.client.graphql.GraphQLProjectInfo::getProjectId,
                            info -> info
                    ));

            // Step 3: Update source projects
            long step3Start = System.currentTimeMillis();
            UpdateProjectDataService.UpdateResult updateResult =
                    updateProjectDataService.updateSourceProjectsFromGraphQL(sourceProjects, graphQLMap, false);
            long step3Duration = System.currentTimeMillis() - step3Start;
            log.info("[INCR-SCAN] Step 3: Update {} source projects to DB - {}ms", updateResult.getSuccessCount(), step3Duration);

            // Step 4: Query target projects
            long step4Start = System.currentTimeMillis();
            List<GitLabProject> targetProjects = batchQueryExecutor.queryTargetProjects(updatedAfter, 100);
            long step4Duration = System.currentTimeMillis() - step4Start;
            log.info("[INCR-SCAN] Step 4: Query {} target projects - {}ms", targetProjects.size(), step4Duration);

            // Step 5: Get target project details using GraphQL
            long step5Start = System.currentTimeMillis();
            List<com.gitlab.mirror.server.client.graphql.GraphQLProjectInfo> targetGraphQLInfos =
                    batchQueryExecutor.getTargetProjectDetailsBatchGraphQL(targetProjects);
            long step5Duration = System.currentTimeMillis() - step5Start;
            log.info("[INCR-SCAN] Step 5: GraphQL batch query {} target projects - {}ms", targetProjects.size(), step5Duration);

            // Convert to map
            Map<Long, com.gitlab.mirror.server.client.graphql.GraphQLProjectInfo> targetGraphQLMap = targetGraphQLInfos.stream()
                    .collect(Collectors.toMap(
                            com.gitlab.mirror.server.client.graphql.GraphQLProjectInfo::getProjectId,
                            info -> info
                    ));

            // Step 6: Update target projects
            long step6Start = System.currentTimeMillis();
            UpdateProjectDataService.UpdateResult targetUpdateResult =
                    updateProjectDataService.updateTargetProjectsFromGraphQL(targetProjects, targetGraphQLMap, false);
            long step6Duration = System.currentTimeMillis() - step6Start;
            log.info("[INCR-SCAN] Step 6: Update {} target projects to DB - {}ms", targetUpdateResult.getSuccessCount(), step6Duration);

            // Step 7: Calculate diffs only for updated projects
            long step7Start = System.currentTimeMillis();
            java.util.Set<String> scannedProjectKeys = java.util.stream.Stream.concat(
                    sourceProjects.stream().map(GitLabProject::getPathWithNamespace),
                    targetProjects.stream().map(GitLabProject::getPathWithNamespace)
            ).collect(Collectors.toSet());

            List<SyncProject> syncProjects = syncProjectMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SyncProject>()
                            .in(SyncProject::getProjectKey, scannedProjectKeys)
            );
            List<Long> syncProjectIds = syncProjects.stream()
                    .map(SyncProject::getId)
                    .collect(Collectors.toList());

            List<ProjectDiff> diffs = syncProjectIds.isEmpty() ? new ArrayList<>() : diffCalculator.calculateDiffBatch(syncProjectIds);
            long step7Duration = System.currentTimeMillis() - step7Start;
            log.info("[INCR-SCAN] Step 7: Calculate {} project diffs - {}ms", diffs.size(), step7Duration);

            // Count changes
            int changesDetected = (int) diffs.stream()
                    .filter(d -> d.getStatus() != ProjectDiff.SyncStatus.SYNCED)
                    .count();

            // Update last scan time
            updateLastScanTime(LocalDateTime.now());

            // Collect all project changes
            List<com.gitlab.mirror.server.service.monitor.model.ProjectChange> allChanges = new ArrayList<>();
            allChanges.addAll(updateResult.getProjectChanges());
            allChanges.addAll(targetUpdateResult.getProjectChanges());

            // Build result
            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = Duration.between(startTime, endTime).toMillis();

            // Update metrics
            long step9Start = System.currentTimeMillis();
            metricsExporter.recordScanDuration(durationMs);
            metricsExporter.refreshSystemMetrics();
            metricsExporter.refreshProjectMetrics(diffs);
            long step9Duration = System.currentTimeMillis() - step9Start;
            log.info("[INCR-SCAN] Step 9: Update metrics - {}ms", step9Duration);

            // Performance summary
            log.info("[INCR-SCAN] === PERFORMANCE SUMMARY ===");
            log.info("[INCR-SCAN] Total Duration: {}ms", durationMs);
            log.info("[INCR-SCAN] Step 1 (Query Source):      {}ms ({}%)", step1Duration, String.format("%.1f", step1Duration * 100.0 / durationMs));
            log.info("[INCR-SCAN] Step 2 (GraphQL Source):    {}ms ({}%)", step2Duration, String.format("%.1f", step2Duration * 100.0 / durationMs));
            log.info("[INCR-SCAN] Step 3 (Update Source):     {}ms ({}%)", step3Duration, String.format("%.1f", step3Duration * 100.0 / durationMs));
            log.info("[INCR-SCAN] Step 4 (Query Target):      {}ms ({}%)", step4Duration, String.format("%.1f", step4Duration * 100.0 / durationMs));
            log.info("[INCR-SCAN] Step 5 (GraphQL Target):    {}ms ({}%)", step5Duration, String.format("%.1f", step5Duration * 100.0 / durationMs));
            log.info("[INCR-SCAN] Step 6 (Update Target):     {}ms ({}%)", step6Duration, String.format("%.1f", step6Duration * 100.0 / durationMs));
            log.info("[INCR-SCAN] Step 7 (Calculate Diff):    {}ms ({}%)", step7Duration, String.format("%.1f", step7Duration * 100.0 / durationMs));
            log.info("[INCR-SCAN] Step 8 (Metrics):           {}ms ({}%)", step9Duration, String.format("%.1f", step9Duration * 100.0 / durationMs));
            log.info("[INCR-SCAN] ================================");

            return resultBuilder
                    .durationMs(durationMs)
                    .projectsScanned(sourceProjects.size())
                    .projectsUpdated(updateResult.getSuccessCount() + targetUpdateResult.getSuccessCount())
                    .newProjects(0)
                    .changesDetected(changesDetected)
                    .projectChanges(allChanges)
                    .endTime(endTime)
                    .build();

        } catch (Exception e) {
            log.error("Incremental scan failed", e);
            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = Duration.between(startTime, endTime).toMillis();

            return resultBuilder
                    .status("failed")
                    .errorMessage(e.getMessage())
                    .durationMs(durationMs)
                    .endTime(endTime)
                    .build();
        }
    }

    /**
     * Compare GitLab data with database and batch update
     * <p>
     * This method:
     * 1. Queries all existing records from database
     * 2. Compares with GitLab data to identify new/updated/unchanged projects
     * 3. Batch inserts new projects
     * 4. Batch updates changed projects
     * 5. Updates branch snapshots for affected projects
     *
     * @param sourceProjects Source GitLab projects
     * @param targetProjects Target GitLab projects
     * @param sourceBranchesMap Source project branches map (projectId -> branches)
     * @param targetBranchesMap Target project branches map (projectId -> branches)
     * @return Comparison result with statistics
     */
    private ComparisonResult compareAndUpdate(
            List<GitLabProject> sourceProjects,
            List<GitLabProject> targetProjects,
            Map<Long, List<RepositoryBranch>> sourceBranchesMap,
            Map<Long, List<RepositoryBranch>> targetBranchesMap) {

        log.info("[COMPARE] Starting comparison and update process...");

        ComparisonResult result = new ComparisonResult();
        List<com.gitlab.mirror.server.service.monitor.model.ProjectChange> allChanges = new ArrayList<>();
        List<Long> affectedSyncProjectIds = new ArrayList<>();

        // Step 3.1: Query all existing records from database
        log.info("[COMPARE] Querying existing database records...");
        List<SyncProject> allSyncProjects = syncProjectMapper.selectList(null);
        List<SourceProjectInfo> allSourceInfos = sourceProjectInfoMapper.selectList(null);
        List<TargetProjectInfo> allTargetInfos = targetProjectInfoMapper.selectList(null);

        // Build maps for quick lookup
        Map<String, SyncProject> syncProjectMap = allSyncProjects.stream()
                .collect(Collectors.toMap(SyncProject::getProjectKey, p -> p));
        Map<Long, SourceProjectInfo> sourceInfoBySyncId = allSourceInfos.stream()
                .collect(Collectors.toMap(SourceProjectInfo::getSyncProjectId, p -> p));
        Map<Long, TargetProjectInfo> targetInfoBySyncId = allTargetInfos.stream()
                .collect(Collectors.toMap(TargetProjectInfo::getSyncProjectId, p -> p));

        log.info("[COMPARE] Found {} existing sync projects in database", allSyncProjects.size());

        // Step 3.2: Process source projects
        log.info("[COMPARE] Processing {} source projects...", sourceProjects.size());
        for (GitLabProject project : sourceProjects) {
            String projectKey = project.getPathWithNamespace();
            SyncProject syncProject = syncProjectMap.get(projectKey);
            List<RepositoryBranch> branches = sourceBranchesMap.getOrDefault(project.getId(), new ArrayList<>());

            if (syncProject == null) {
                // New project - create all records
                log.info("[COMPARE] New project discovered: {}", projectKey);
                try {
                    Long syncProjectId = createNewProject(project, branches);
                    affectedSyncProjectIds.add(syncProjectId);
                    result.incrementNewProjects();
                } catch (Exception e) {
                    log.error("[COMPARE] Failed to create new project {}: {}", projectKey, e.getMessage(), e);
                }
            } else {
                // Existing project - check for changes
                SourceProjectInfo sourceInfo = sourceInfoBySyncId.get(syncProject.getId());
                if (sourceInfo != null) {
                    com.gitlab.mirror.server.service.monitor.model.ProjectChange change =
                            updateSourceProjectIfChanged(sourceInfo, project, branches);

                    if (change != null) {
                        allChanges.add(change);
                        affectedSyncProjectIds.add(syncProject.getId());
                        result.incrementUpdatedProjects();

                        // Update branch snapshot for changed project
                        try {
                            branchSnapshotService.updateBranchSnapshot(
                                    syncProject.getId(),
                                    ProjectBranchSnapshot.ProjectType.SOURCE,
                                    branches,
                                    sourceInfo.getDefaultBranch()
                            );
                        } catch (Exception e) {
                            log.warn("[COMPARE] Failed to update source branch snapshot for {}: {}",
                                    projectKey, e.getMessage());
                        }
                    } else {
                        result.incrementUnchangedProjects();
                    }
                }
            }
        }

        // Step 3.3: Process target projects
        log.info("[COMPARE] Processing {} target projects...", targetProjects.size());
        for (GitLabProject project : targetProjects) {
            String projectKey = project.getPathWithNamespace();
            SyncProject syncProject = syncProjectMap.get(projectKey);
            List<RepositoryBranch> branches = targetBranchesMap.getOrDefault(project.getId(), new ArrayList<>());

            if (syncProject != null) {
                TargetProjectInfo targetInfo = targetInfoBySyncId.get(syncProject.getId());
                if (targetInfo != null) {
                    com.gitlab.mirror.server.service.monitor.model.ProjectChange change =
                            updateTargetProjectIfChanged(targetInfo, project, branches);

                    if (change != null) {
                        allChanges.add(change);
                        if (!affectedSyncProjectIds.contains(syncProject.getId())) {
                            affectedSyncProjectIds.add(syncProject.getId());
                        }

                        // Update branch snapshot for changed project
                        try {
                            branchSnapshotService.updateBranchSnapshot(
                                    syncProject.getId(),
                                    ProjectBranchSnapshot.ProjectType.TARGET,
                                    branches,
                                    targetInfo.getDefaultBranch()
                            );
                        } catch (Exception e) {
                            log.warn("[COMPARE] Failed to update target branch snapshot for {}: {}",
                                    projectKey, e.getMessage());
                        }
                    }
                }
            }
        }

        result.setAllChanges(allChanges);
        result.setAffectedSyncProjectIds(affectedSyncProjectIds);

        log.info("[COMPARE] Comparison completed - new: {}, updated: {}, unchanged: {}",
                result.getNewProjectsCount(), result.getUpdatedProjectsCount(), result.getUnchangedProjectsCount());

        return result;
    }

    /**
     * Create new project with all related records
     */
    private Long createNewProject(GitLabProject project, List<RepositoryBranch> branches) {
        // Step 1: Create sync_project record
        SyncProject syncProject = new SyncProject();
        syncProject.setProjectKey(project.getPathWithNamespace());
        syncProject.setSyncMethod("pull_sync");
        syncProject.setSyncStatus(SyncProject.SyncStatus.PENDING);
        syncProject.setEnabled(true);
        syncProject.setCreatedAt(LocalDateTime.now());
        syncProject.setUpdatedAt(LocalDateTime.now());

        syncProjectMapper.insert(syncProject);
        log.info("[COMPARE] Created sync_project: {} (id={})", project.getPathWithNamespace(), syncProject.getId());

        // Step 2: Create source_project_info record
        SourceProjectInfo sourceInfo = new SourceProjectInfo();
        sourceInfo.setSyncProjectId(syncProject.getId());
        sourceInfo.setGitlabProjectId(project.getId());
        sourceInfo.setPathWithNamespace(project.getPathWithNamespace());
        sourceInfo.setName(project.getName());
        sourceInfo.setDefaultBranch(project.getDefaultBranch());
        sourceInfo.setVisibility(project.getVisibility());
        sourceInfo.setArchived(project.getArchived());
        sourceInfo.setEmptyRepo(project.getEmptyRepo());
        sourceInfo.setStarCount(project.getStarCount());
        sourceInfo.setForkCount(project.getForksCount());

        if (project.getNamespace() != null) {
            sourceInfo.setGroupPath(project.getNamespace().getFullPath());
        }

        if (project.getLastActivityAt() != null) {
            sourceInfo.setLastActivityAt(convertToLocalDateTime(project.getLastActivityAt()));
        }

        if (project.getStatistics() != null) {
            sourceInfo.setRepositorySize(project.getStatistics().getRepositorySize());
            sourceInfo.setCommitCount(project.getStatistics().getCommitCount());
        }

        // Update from branches data
        sourceInfo.setBranchCount(branches.size());

        // Get latest commit SHA from default branch
        if (project.getDefaultBranch() != null && !branches.isEmpty()) {
            branches.stream()
                    .filter(b -> project.getDefaultBranch().equals(b.getName()))
                    .findFirst()
                    .ifPresent(branch -> {
                        if (branch.getCommit() != null) {
                            sourceInfo.setLatestCommitSha(branch.getCommit().getId());
                        }
                    });
        }

        sourceInfo.setSyncedAt(LocalDateTime.now());

        sourceProjectInfoMapper.insert(sourceInfo);
        log.info("[COMPARE] Created source_project_info for {} (id={})", project.getPathWithNamespace(), sourceInfo.getId());

        // Step 3: Create pull_sync_config record
        pullSyncConfigService.initializeConfig(syncProject.getId(), project.getPathWithNamespace());

        // Step 4: Create sync_task record
        syncTaskService.initializeTask(syncProject.getId(), "pull");

        // Step 5: Create branch snapshot
        try {
            branchSnapshotService.updateBranchSnapshot(
                    syncProject.getId(),
                    ProjectBranchSnapshot.ProjectType.SOURCE,
                    branches,
                    project.getDefaultBranch()
            );
        } catch (Exception e) {
            log.warn("[COMPARE] Failed to create source branch snapshot for {}: {}",
                    project.getPathWithNamespace(), e.getMessage());
        }

        return syncProject.getId();
    }

    /**
     * Update source project if changed
     * Returns ProjectChange if there are changes, null otherwise
     */
    private com.gitlab.mirror.server.service.monitor.model.ProjectChange updateSourceProjectIfChanged(
            SourceProjectInfo info,
            GitLabProject project,
            List<RepositoryBranch> branches) {

        com.gitlab.mirror.server.service.monitor.model.ProjectChange change =
                com.gitlab.mirror.server.service.monitor.model.ProjectChange.builder()
                        .projectKey(info.getPathWithNamespace())
                        .projectType("source")
                        .build();

        boolean hasChanges = false;

        // Update branch count
        int newBranchCount = branches.size();
        if (!Objects.equals(info.getBranchCount(), newBranchCount)) {
            change.addChange("branchCount", info.getBranchCount(), newBranchCount);
            info.setBranchCount(newBranchCount);
            hasChanges = true;
        }

        // Update latest commit SHA from default branch
        if (project.getDefaultBranch() != null && !branches.isEmpty()) {
            String newCommitSha = branches.stream()
                    .filter(b -> project.getDefaultBranch().equals(b.getName()))
                    .findFirst()
                    .map(branch -> branch.getCommit() != null ? branch.getCommit().getId() : null)
                    .orElse(null);

            if (newCommitSha != null && !Objects.equals(info.getLatestCommitSha(), newCommitSha)) {
                change.addChange("latestCommitSha", info.getLatestCommitSha(), newCommitSha);
                info.setLatestCommitSha(newCommitSha);
                hasChanges = true;
            }
        }

        // Update from project statistics
        if (project.getStatistics() != null) {
            if (project.getStatistics().getCommitCount() != null &&
                    !Objects.equals(info.getCommitCount(), project.getStatistics().getCommitCount())) {
                change.addChange("commitCount", info.getCommitCount(), project.getStatistics().getCommitCount());
                info.setCommitCount(project.getStatistics().getCommitCount());
                hasChanges = true;
            }

            if (project.getStatistics().getRepositorySize() != null &&
                    !Objects.equals(info.getRepositorySize(), project.getStatistics().getRepositorySize())) {
                change.addChange("repositorySize", info.getRepositorySize(), project.getStatistics().getRepositorySize());
                info.setRepositorySize(project.getStatistics().getRepositorySize());
                hasChanges = true;
            }
        }

        // Update last activity time
        if (project.getLastActivityAt() != null) {
            LocalDateTime newActivityTime = convertToLocalDateTime(project.getLastActivityAt());
            if (!Objects.equals(info.getLastActivityAt(), newActivityTime)) {
                change.addChange("lastActivityAt", info.getLastActivityAt(), newActivityTime);
                info.setLastActivityAt(newActivityTime);
                hasChanges = true;
            }
        }

        // Update default branch
        if (project.getDefaultBranch() != null &&
                !Objects.equals(info.getDefaultBranch(), project.getDefaultBranch())) {
            change.addChange("defaultBranch", info.getDefaultBranch(), project.getDefaultBranch());
            info.setDefaultBranch(project.getDefaultBranch());
            hasChanges = true;
        }

        // Save changes if any
        if (hasChanges) {
            sourceProjectInfoMapper.updateById(info);
            log.debug("[COMPARE] Updated source project {} with {} changes",
                    info.getPathWithNamespace(), change.getFieldChanges().size());
            return change;
        }

        return null;
    }

    /**
     * Update target project if changed
     * Returns ProjectChange if there are changes, null otherwise
     */
    private com.gitlab.mirror.server.service.monitor.model.ProjectChange updateTargetProjectIfChanged(
            TargetProjectInfo info,
            GitLabProject project,
            List<RepositoryBranch> branches) {

        com.gitlab.mirror.server.service.monitor.model.ProjectChange change =
                com.gitlab.mirror.server.service.monitor.model.ProjectChange.builder()
                        .projectKey(info.getPathWithNamespace())
                        .projectType("target")
                        .build();

        boolean hasChanges = false;

        // Update branch count
        int newBranchCount = branches.size();
        if (!Objects.equals(info.getBranchCount(), newBranchCount)) {
            change.addChange("branchCount", info.getBranchCount(), newBranchCount);
            info.setBranchCount(newBranchCount);
            hasChanges = true;
        }

        // Update latest commit SHA from default branch
        if (project.getDefaultBranch() != null && !branches.isEmpty()) {
            String newCommitSha = branches.stream()
                    .filter(b -> project.getDefaultBranch().equals(b.getName()))
                    .findFirst()
                    .map(branch -> branch.getCommit() != null ? branch.getCommit().getId() : null)
                    .orElse(null);

            if (newCommitSha != null && !Objects.equals(info.getLatestCommitSha(), newCommitSha)) {
                change.addChange("latestCommitSha", info.getLatestCommitSha(), newCommitSha);
                info.setLatestCommitSha(newCommitSha);
                hasChanges = true;
            }
        }

        // Update from project statistics
        if (project.getStatistics() != null) {
            if (project.getStatistics().getCommitCount() != null &&
                    !Objects.equals(info.getCommitCount(), project.getStatistics().getCommitCount())) {
                change.addChange("commitCount", info.getCommitCount(), project.getStatistics().getCommitCount());
                info.setCommitCount(project.getStatistics().getCommitCount());
                hasChanges = true;
            }

            if (project.getStatistics().getRepositorySize() != null &&
                    !Objects.equals(info.getRepositorySize(), project.getStatistics().getRepositorySize())) {
                change.addChange("repositorySize", info.getRepositorySize(), project.getStatistics().getRepositorySize());
                info.setRepositorySize(project.getStatistics().getRepositorySize());
                hasChanges = true;
            }
        }

        // Update last activity time
        if (project.getLastActivityAt() != null) {
            LocalDateTime newActivityTime = convertToLocalDateTime(project.getLastActivityAt());
            if (!Objects.equals(info.getLastActivityAt(), newActivityTime)) {
                change.addChange("lastActivityAt", info.getLastActivityAt(), newActivityTime);
                info.setLastActivityAt(newActivityTime);
                hasChanges = true;
            }
        }

        // Update default branch
        if (project.getDefaultBranch() != null &&
                !Objects.equals(info.getDefaultBranch(), project.getDefaultBranch())) {
            change.addChange("defaultBranch", info.getDefaultBranch(), project.getDefaultBranch());
            info.setDefaultBranch(project.getDefaultBranch());
            hasChanges = true;
        }

        // Save changes if any
        if (hasChanges) {
            targetProjectInfoMapper.updateById(info);
            log.debug("[COMPARE] Updated target project {} with {} changes",
                    info.getPathWithNamespace(), change.getFieldChanges().size());
            return change;
        }

        return null;
    }

    /**
     * Get last scan time from cache
     */
    private LocalDateTime getLastScanTime() {
        LocalDateTime lastScan = cacheManager.get("last_scan_time");
        return lastScan != null ? lastScan : LocalDateTime.now().minusHours(1);
    }

    /**
     * Update last scan time in cache
     */
    private void updateLastScanTime(LocalDateTime time) {
        cacheManager.put("last_scan_time", time, 60 * 24); // 24 hours TTL
    }

    /**
     * Convert OffsetDateTime to LocalDateTime
     */
    private LocalDateTime convertToLocalDateTime(OffsetDateTime offsetDateTime) {
        return offsetDateTime != null ? offsetDateTime.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() : null;
    }

    /**
     * Build empty result when no projects to scan
     */
    private ScanResult buildEmptyResult(ScanResult.ScanResultBuilder builder, LocalDateTime startTime) {
        LocalDateTime endTime = LocalDateTime.now();
        long durationMs = Duration.between(startTime, endTime).toMillis();

        return builder
                .durationMs(durationMs)
                .projectsScanned(0)
                .projectsUpdated(0)
                .newProjects(0)
                .changesDetected(0)
                .endTime(endTime)
                .build();
    }

    /**
     * Comparison result for full scan
     */
    @lombok.Data
    private static class ComparisonResult {
        private int newProjectsCount = 0;
        private int updatedProjectsCount = 0;
        private int unchangedProjectsCount = 0;
        private List<com.gitlab.mirror.server.service.monitor.model.ProjectChange> allChanges = new ArrayList<>();
        private List<Long> affectedSyncProjectIds = new ArrayList<>();

        public void incrementNewProjects() {
            this.newProjectsCount++;
        }

        public void incrementUpdatedProjects() {
            this.updatedProjectsCount++;
        }

        public void incrementUnchangedProjects() {
            this.unchangedProjectsCount++;
        }
    }
}
