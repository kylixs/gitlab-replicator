package com.gitlab.mirror.server.controller;

import com.gitlab.mirror.server.controller.dto.ProjectListDTO;
import com.gitlab.mirror.server.controller.dto.ProjectOverviewDTO;
import com.gitlab.mirror.server.controller.dto.SyncResultListDTO;
import com.gitlab.mirror.server.controller.dto.SyncResultDetailDTO;
import com.gitlab.mirror.server.entity.ProjectBranchSnapshot;
import com.gitlab.mirror.server.entity.SyncResult;
import com.gitlab.mirror.server.entity.PullSyncConfig;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.mapper.PullSyncConfigMapper;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.service.BranchSnapshotService;
import com.gitlab.mirror.server.service.ProjectListService;
import com.gitlab.mirror.server.service.PullSyncExecutorService;
import com.gitlab.mirror.server.service.SyncTaskService;
import com.gitlab.mirror.server.service.monitor.DiffCalculator;
import com.gitlab.mirror.server.service.monitor.LocalCacheManager;
import com.gitlab.mirror.server.service.monitor.UnifiedProjectMonitor;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import com.gitlab.mirror.server.service.monitor.model.ScanResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Sync Controller
 * <p>
 * REST API for project synchronization and monitoring.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final UnifiedProjectMonitor unifiedProjectMonitor;
    private final SyncProjectMapper syncProjectMapper;
    private final DiffCalculator diffCalculator;
    private final LocalCacheManager cacheManager;
    private final BranchSnapshotService branchSnapshotService;
    private final ProjectListService projectListService;
    private final SourceProjectInfoMapper sourceProjectInfoMapper;
    private final PullSyncExecutorService pullSyncExecutorService;
    private final PullSyncConfigMapper pullSyncConfigMapper;
    private final SyncTaskService syncTaskService;
    private final com.gitlab.mirror.server.mapper.SyncResultMapper syncResultMapper;

    public SyncController(
            UnifiedProjectMonitor unifiedProjectMonitor,
            SyncProjectMapper syncProjectMapper,
            DiffCalculator diffCalculator,
            LocalCacheManager cacheManager,
            BranchSnapshotService branchSnapshotService,
            ProjectListService projectListService,
            SourceProjectInfoMapper sourceProjectInfoMapper,
            PullSyncExecutorService pullSyncExecutorService,
            PullSyncConfigMapper pullSyncConfigMapper,
            SyncTaskService syncTaskService,
            com.gitlab.mirror.server.mapper.SyncResultMapper syncResultMapper) {
        this.unifiedProjectMonitor = unifiedProjectMonitor;
        this.syncProjectMapper = syncProjectMapper;
        this.diffCalculator = diffCalculator;
        this.cacheManager = cacheManager;
        this.branchSnapshotService = branchSnapshotService;
        this.projectListService = projectListService;
        this.sourceProjectInfoMapper = sourceProjectInfoMapper;
        this.pullSyncExecutorService = pullSyncExecutorService;
        this.pullSyncConfigMapper = pullSyncConfigMapper;
        this.syncTaskService = syncTaskService;
        this.syncResultMapper = syncResultMapper;
    }

    /**
     * Trigger manual scan
     *
     * POST /api/sync/scan?type=incremental
     */
    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<ScanResult>> triggerScan(
            @RequestParam(defaultValue = "incremental") String type) {
        log.info("Manual scan triggered - type: {}", type);

        try {
            ScanResult result = unifiedProjectMonitor.scan(type);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Scan failed", e);
            return ResponseEntity.ok(ApiResponse.error("Scan failed: " + e.getMessage()));
        }
    }

    /**
     * Get project list with enhanced filtering and sorting
     *
     * GET /api/sync/projects?status=synced&syncMethod=pull_sync&group=ai&search=test&sortBy=delay&sortOrder=desc&page=1&size=20
     */
    @GetMapping("/projects")
    public ResponseEntity<ApiResponse<PageResult<ProjectListDTO>>> getProjects(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String syncMethod,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String delayRange,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortOrder,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        log.info("Query projects - status: {}, syncMethod: {}, group: {}, search: {}, sortBy: {}, page: {}, size: {}",
                status, syncMethod, group, search, sortBy, page, size);

        try {
            QueryWrapper<SyncProject> queryWrapper = new QueryWrapper<>();

            // Filter by status
            if (status != null && !status.isEmpty()) {
                queryWrapper.eq("sync_status", status);
            }

            // Filter by sync method
            if (syncMethod != null && !syncMethod.isEmpty()) {
                queryWrapper.eq("sync_method", syncMethod);
            }

            // Filter by group (need to join with source_project_info)
            if (group != null && !group.isEmpty()) {
                List<SourceProjectInfo> sourceInfos = sourceProjectInfoMapper.selectList(
                        new QueryWrapper<SourceProjectInfo>().eq("group_path", group));
                if (!sourceInfos.isEmpty()) {
                    List<Long> projectIds = sourceInfos.stream()
                            .map(SourceProjectInfo::getSyncProjectId)
                            .collect(Collectors.toList());
                    queryWrapper.in("id", projectIds);
                } else {
                    // No projects in this group
                    queryWrapper.eq("id", -1); // Force empty result
                }
            }

            // Filter by search (project_key like search)
            if (search != null && !search.isEmpty()) {
                queryWrapper.like("project_key", search);
            }

            Page<SyncProject> pageQuery = new Page<>(page, size);
            Page<SyncProject> result = syncProjectMapper.selectPage(pageQuery, queryWrapper);

            // Build DTOs with diff and delay
            List<ProjectListDTO> dtos = result.getRecords().stream()
                    .map(projectListService::buildProjectListDTO)
                    .collect(Collectors.toList());

            // Apply delay range filter (post-processing since it's calculated)
            if (delayRange != null && !delayRange.isEmpty()) {
                dtos = filterByDelayRange(dtos, delayRange);
            }

            // Apply sorting (post-processing for calculated fields)
            if (sortBy != null && !sortBy.isEmpty()) {
                dtos = sortProjects(dtos, sortBy, sortOrder);
            }

            PageResult<ProjectListDTO> pageResult = new PageResult<>();
            pageResult.setItems(dtos);
            pageResult.setTotal(result.getTotal());
            pageResult.setPage(page);
            pageResult.setSize(size);

            return ResponseEntity.ok(ApiResponse.success(pageResult));
        } catch (Exception e) {
            log.error("Query projects failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Get all groups
     *
     * GET /api/sync/projects/groups
     */
    @GetMapping("/projects/groups")
    public ResponseEntity<ApiResponse<List<String>>> getGroups() {
        log.info("Query all groups");

        try {
            List<SourceProjectInfo> sourceInfos = sourceProjectInfoMapper.selectList(null);
            List<String> groups = sourceInfos.stream()
                    .map(SourceProjectInfo::getGroupPath)
                    .filter(g -> g != null && !g.isEmpty())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(groups));
        } catch (Exception e) {
            log.error("Query groups failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Filter projects by delay range
     */
    private List<ProjectListDTO> filterByDelayRange(List<ProjectListDTO> projects, String delayRange) {
        // delayRange format: "0-3600" (seconds) or "3600-86400" or "86400+"
        if (delayRange.endsWith("+")) {
            long minSeconds = Long.parseLong(delayRange.substring(0, delayRange.length() - 1));
            return projects.stream()
                    .filter(p -> p.getDelaySeconds() != null && p.getDelaySeconds() >= minSeconds)
                    .collect(Collectors.toList());
        } else {
            String[] parts = delayRange.split("-");
            if (parts.length == 2) {
                long minSeconds = Long.parseLong(parts[0]);
                long maxSeconds = Long.parseLong(parts[1]);
                return projects.stream()
                        .filter(p -> p.getDelaySeconds() != null &&
                                     p.getDelaySeconds() >= minSeconds &&
                                     p.getDelaySeconds() < maxSeconds)
                        .collect(Collectors.toList());
            }
        }
        return projects;
    }

    /**
     * Sort projects by specified field
     */
    private List<ProjectListDTO> sortProjects(List<ProjectListDTO> projects, String sortBy, String sortOrder) {
        boolean ascending = "asc".equalsIgnoreCase(sortOrder);

        Comparator<ProjectListDTO> comparator = switch (sortBy) {
            case "delay" -> Comparator.comparing(ProjectListDTO::getDelaySeconds,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "lastCommitTime" -> Comparator.comparing(ProjectListDTO::getLastCommitTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "lastSyncAt" -> Comparator.comparing(ProjectListDTO::getLastSyncAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "projectKey" -> Comparator.comparing(ProjectListDTO::getProjectKey);
            default -> Comparator.comparing(ProjectListDTO::getId);
        };

        if (!ascending) {
            comparator = comparator.reversed();
        }

        return projects.stream().sorted(comparator).collect(Collectors.toList());
    }

    /**
     * Get project details
     *
     * GET /api/sync/projects/{projectKey}
     */
    @GetMapping("/projects/{projectKey}")
    public ResponseEntity<ApiResponse<SyncProject>> getProjectDetails(
            @PathVariable String projectKey) {
        log.info("Query project details - projectKey: {}", projectKey);

        try {
            QueryWrapper<SyncProject> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("project_key", projectKey);
            SyncProject project = syncProjectMapper.selectOne(queryWrapper);

            if (project == null) {
                return ResponseEntity.ok(ApiResponse.error("Project not found"));
            }

            return ResponseEntity.ok(ApiResponse.success(project));
        } catch (Exception e) {
            log.error("Query project details failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Get project overview
     *
     * GET /api/sync/projects/{id}/overview
     */
    @GetMapping("/projects/{id}/overview")
    public ResponseEntity<ApiResponse<ProjectOverviewDTO>> getProjectOverview(
            @PathVariable Long id) {
        log.info("Query project overview - id: {}", id);

        try {
            SyncProject project = syncProjectMapper.selectById(id);
            if (project == null) {
                return ResponseEntity.ok(ApiResponse.error("Project not found"));
            }

            ProjectOverviewDTO overview = projectListService.buildProjectOverview(project);
            return ResponseEntity.ok(ApiResponse.success(overview));
        } catch (Exception e) {
            log.error("Query project overview failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Get project diff list
     *
     * GET /api/sync/diffs?status=OUTDATED&page=1&size=20
     */
    @GetMapping("/diffs")
    public ResponseEntity<ApiResponse<PageResult<ProjectDiff>>> getProjectDiffs(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        log.info("Query project diffs - status: {}, page: {}, size: {}", status, page, size);

        try {
            QueryWrapper<SyncProject> queryWrapper = new QueryWrapper<>();
            if (status != null && !status.isEmpty()) {
                queryWrapper.eq("sync_status", status);
            }

            Page<SyncProject> pageQuery = new Page<>(page, size);
            Page<SyncProject> result = syncProjectMapper.selectPage(pageQuery, queryWrapper);

            // Calculate diffs for each project
            List<ProjectDiff> diffs = result.getRecords().stream()
                    .map(project -> diffCalculator.calculateDiff(project.getId()))
                    .filter(diff -> diff != null)
                    .toList();

            PageResult<ProjectDiff> pageResult = new PageResult<>();
            pageResult.setItems(diffs);
            pageResult.setTotal(result.getTotal());
            pageResult.setPage(page);
            pageResult.setSize(size);

            return ResponseEntity.ok(ApiResponse.success(pageResult));
        } catch (Exception e) {
            log.error("Query project diffs failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Get project diff
     *
     * GET /api/sync/diff?projectKey=devops/gitlab-mirror
     * GET /api/sync/diff?syncProjectId=984
     */
    @GetMapping("/diff")
    public ResponseEntity<ApiResponse<ProjectDiff>> getProjectDiff(
            @RequestParam(required = false) String projectKey,
            @RequestParam(required = false) Long syncProjectId) {
        log.info("Query project diff - projectKey: {}, syncProjectId: {}", projectKey, syncProjectId);

        try {
            SyncProject project = null;

            // Query by syncProjectId first (if provided)
            if (syncProjectId != null) {
                project = syncProjectMapper.selectById(syncProjectId);
                if (project != null) {
                    projectKey = project.getProjectKey();
                }
            }
            // Query by projectKey (if provided and not found by ID)
            else if (projectKey != null && !projectKey.isEmpty()) {
                QueryWrapper<SyncProject> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("project_key", projectKey);
                project = syncProjectMapper.selectOne(queryWrapper);
            } else {
                return ResponseEntity.ok(ApiResponse.error("Either projectKey or syncProjectId must be provided"));
            }

            if (project == null) {
                return ResponseEntity.ok(ApiResponse.error("Project not found"));
            }

            ProjectDiff diff = diffCalculator.calculateDiff(project.getId());

            return ResponseEntity.ok(ApiResponse.success(diff));
        } catch (Exception e) {
            log.error("Query project diff failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Get project branches comparison
     *
     * GET /api/sync/branches?projectKey=ai/test-android-app-3
     * GET /api/sync/branches?syncProjectId=986
     */
    @GetMapping("/branches")
    public ResponseEntity<ApiResponse<BranchComparisonResult>> getProjectBranches(
            @RequestParam(required = false) String projectKey,
            @RequestParam(required = false) Long syncProjectId) {
        log.info("Query project branches - projectKey: {}, syncProjectId: {}", projectKey, syncProjectId);

        try {
            SyncProject project = null;

            // Query by syncProjectId first (if provided)
            if (syncProjectId != null) {
                project = syncProjectMapper.selectById(syncProjectId);
                if (project != null) {
                    projectKey = project.getProjectKey();
                }
            }
            // Query by projectKey (if provided and not found by ID)
            else if (projectKey != null && !projectKey.isEmpty()) {
                QueryWrapper<SyncProject> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("project_key", projectKey);
                project = syncProjectMapper.selectOne(queryWrapper);
            } else {
                return ResponseEntity.ok(ApiResponse.error("Either projectKey or syncProjectId must be provided"));
            }

            if (project == null) {
                return ResponseEntity.ok(ApiResponse.error("Project not found"));
            }

            // Get source and target branch snapshots
            List<ProjectBranchSnapshot> sourceBranches = branchSnapshotService.getBranchSnapshots(project.getId(), "source");
            List<ProjectBranchSnapshot> targetBranches = branchSnapshotService.getBranchSnapshots(project.getId(), "target");

            // Build comparison result
            BranchComparisonResult result = compareBranches(project, sourceBranches, targetBranches);

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Query project branches failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Compare source and target branches
     */
    private BranchComparisonResult compareBranches(
            SyncProject project,
            List<ProjectBranchSnapshot> sourceBranches,
            List<ProjectBranchSnapshot> targetBranches) {

        BranchComparisonResult result = new BranchComparisonResult();
        result.setProjectKey(project.getProjectKey());
        result.setSyncProjectId(project.getId());
        result.setSourceBranchCount(sourceBranches.size());
        result.setTargetBranchCount(targetBranches.size());

        // Build maps for quick lookup
        Map<String, ProjectBranchSnapshot> sourceMap = sourceBranches.stream()
                .collect(Collectors.toMap(ProjectBranchSnapshot::getBranchName, b -> b));
        Map<String, ProjectBranchSnapshot> targetMap = targetBranches.stream()
                .collect(Collectors.toMap(ProjectBranchSnapshot::getBranchName, b -> b));

        List<BranchInfo> branches = new ArrayList<>();

        // Process source branches
        for (ProjectBranchSnapshot source : sourceBranches) {
            ProjectBranchSnapshot target = targetMap.get(source.getBranchName());
            BranchInfo info = new BranchInfo();
            info.setBranchName(source.getBranchName());
            info.setSourceCommitSha(source.getCommitSha());
            info.setSourceCommitMessage(source.getCommitMessage());
            info.setSourceCommitAuthor(source.getCommitAuthor());
            info.setSourceCommittedAt(source.getCommittedAt());
            info.setIsDefault(source.getIsDefault());
            info.setIsProtected(source.getIsProtected());

            if (target != null) {
                info.setTargetCommitSha(target.getCommitSha());
                info.setTargetCommitMessage(target.getCommitMessage());
                info.setTargetCommitAuthor(target.getCommitAuthor());
                info.setTargetCommittedAt(target.getCommittedAt());

                // Determine status
                if (source.getCommitSha() != null && source.getCommitSha().equals(target.getCommitSha())) {
                    info.setStatus("synced");
                } else {
                    info.setStatus("outdated");
                }
            } else {
                info.setStatus("missing_in_target");
            }

            branches.add(info);
        }

        // Find branches only in target
        for (ProjectBranchSnapshot target : targetBranches) {
            if (!sourceMap.containsKey(target.getBranchName())) {
                BranchInfo info = new BranchInfo();
                info.setBranchName(target.getBranchName());
                info.setTargetCommitSha(target.getCommitSha());
                info.setTargetCommitMessage(target.getCommitMessage());
                info.setTargetCommitAuthor(target.getCommitAuthor());
                info.setTargetCommittedAt(target.getCommittedAt());
                info.setStatus("extra_in_target");
                branches.add(info);
            }
        }

        // Sort branches: default first, then alphabetically
        branches.sort((a, b) -> {
            if (Boolean.TRUE.equals(a.getIsDefault()) && !Boolean.TRUE.equals(b.getIsDefault())) {
                return -1;
            } else if (!Boolean.TRUE.equals(a.getIsDefault()) && Boolean.TRUE.equals(b.getIsDefault())) {
                return 1;
            }
            return a.getBranchName().compareTo(b.getBranchName());
        });

        result.setBranches(branches);

        // Count differences
        long syncedCount = branches.stream().filter(b -> "synced".equals(b.getStatus())).count();
        long outdatedCount = branches.stream().filter(b -> "outdated".equals(b.getStatus())).count();
        long missingCount = branches.stream().filter(b -> "missing_in_target".equals(b.getStatus())).count();
        long extraCount = branches.stream().filter(b -> "extra_in_target".equals(b.getStatus())).count();

        result.setSyncedCount((int) syncedCount);
        result.setOutdatedCount((int) outdatedCount);
        result.setMissingInTargetCount((int) missingCount);
        result.setExtraInTargetCount((int) extraCount);

        return result;
    }

    /**
     * Branch comparison result
     */
    @Data
    public static class BranchComparisonResult {
        private String projectKey;
        private Long syncProjectId;
        private Integer sourceBranchCount;
        private Integer targetBranchCount;
        private Integer syncedCount;
        private Integer outdatedCount;
        private Integer missingInTargetCount;
        private Integer extraInTargetCount;
        private List<BranchInfo> branches;
    }

    /**
     * Branch info
     */
    @Data
    public static class BranchInfo {
        private String branchName;
        private String status; // synced, outdated, missing_in_target, extra_in_target
        private String syncStatus; // Alias for status for frontend compatibility
        private Boolean isDefault;
        private Boolean isProtected;

        // Source branch info
        private String sourceCommitSha;
        private String sourceCommitId; // Alias for sourceCommitSha
        private String sourceCommitShort; // Short version of commit SHA (first 8 chars)
        private String sourceCommitMessage;
        private String sourceCommitAuthor;
        private LocalDateTime sourceCommittedAt;
        private String sourceLastCommitAt; // Alias for sourceCommittedAt

        // Target branch info
        private String targetCommitSha;
        private String targetCommitId; // Alias for targetCommitSha
        private String targetCommitShort; // Short version of commit SHA (first 8 chars)
        private String targetCommitMessage;
        private String targetCommitAuthor;
        private LocalDateTime targetCommittedAt;
        private String targetLastCommitAt; // Alias for targetCommittedAt

        public void setSourceCommitSha(String sourceCommitSha) {
            this.sourceCommitSha = sourceCommitSha;
            this.sourceCommitId = sourceCommitSha;
            if (sourceCommitSha != null && sourceCommitSha.length() >= 8) {
                this.sourceCommitShort = sourceCommitSha.substring(0, 8);
            }
        }

        public void setTargetCommitSha(String targetCommitSha) {
            this.targetCommitSha = targetCommitSha;
            this.targetCommitId = targetCommitSha;
            if (targetCommitSha != null && targetCommitSha.length() >= 8) {
                this.targetCommitShort = targetCommitSha.substring(0, 8);
            }
        }

        public void setStatus(String status) {
            this.status = status;
            this.syncStatus = status;
        }

        public void setSourceCommittedAt(LocalDateTime sourceCommittedAt) {
            this.sourceCommittedAt = sourceCommittedAt;
            this.sourceLastCommitAt = sourceCommittedAt != null ? sourceCommittedAt.toString() : null;
        }

        public void setTargetCommittedAt(LocalDateTime targetCommittedAt) {
            this.targetCommittedAt = targetCommittedAt;
            this.targetLastCommitAt = targetCommittedAt != null ? targetCommittedAt.toString() : null;
        }
    }

    /**
     * API Response wrapper
     */
    @Data
    public static class ApiResponse<T> {
        private boolean success;
        private T data;
        private String message;

        public static <T> ApiResponse<T> success(T data) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setData(data);
            return response;
        }

        public static <T> ApiResponse<T> error(String message) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage(message);
            return response;
        }
    }

    /**
     * Page result wrapper
     */
    @Data
    public static class PageResult<T> {
        private List<T> items;
        private Long total;
        private Integer page;
        private Integer size;
    }

    /**
     * Get sync results list with filtering
     *
     * GET /api/sync/results?syncStatus=success&search=ai&page=1&size=20
     */
    @GetMapping("/results")
    public ResponseEntity<ApiResponse<PageResult<SyncResultListDTO>>> getSyncResults(
            @RequestParam(required = false) String syncStatus,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        log.info("Query sync results - syncStatus: {}, search: {}, page: {}, size: {}",
                syncStatus, search, page, size);

        try {
            // Build query wrapper
            QueryWrapper<SyncResult> queryWrapper = new QueryWrapper<>();

            // Filter by sync status
            if (syncStatus != null && !syncStatus.isEmpty()) {
                queryWrapper.eq("sync_status", syncStatus);
            }

            // Order by last_sync_at DESC (most recent first)
            queryWrapper.orderByDesc("last_sync_at");

            // Execute pagination query
            Page<SyncResult> pageQuery = new Page<>(page, size);
            Page<SyncResult> result = syncResultMapper.selectPage(pageQuery, queryWrapper);

            // Convert to DTOs
            List<SyncResultListDTO> dtos = result.getRecords().stream()
                    .map(this::convertToSyncResultListDTO)
                    .collect(Collectors.toList());

            // Apply search filter (post-processing for projectKey)
            if (search != null && !search.isEmpty()) {
                String searchLower = search.toLowerCase();
                dtos = dtos.stream()
                        .filter(dto -> dto.getProjectKey() != null &&
                                      dto.getProjectKey().toLowerCase().contains(searchLower))
                        .collect(Collectors.toList());
            }

            PageResult<SyncResultListDTO> pageResult = new PageResult<>();
            pageResult.setItems(dtos);
            pageResult.setTotal(result.getTotal());
            pageResult.setPage(page);
            pageResult.setSize(size);

            return ResponseEntity.ok(ApiResponse.success(pageResult));
        } catch (Exception e) {
            log.error("Query sync results failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Convert SyncResult to SyncResultListDTO
     */
    private SyncResultListDTO convertToSyncResultListDTO(SyncResult syncResult) {
        SyncResultListDTO dto = new SyncResultListDTO();
        dto.setId(syncResult.getId());
        dto.setSyncProjectId(syncResult.getSyncProjectId());
        dto.setLastSyncAt(syncResult.getLastSyncAt());
        dto.setStartedAt(syncResult.getStartedAt());
        dto.setCompletedAt(syncResult.getCompletedAt());
        dto.setSyncStatus(syncResult.getSyncStatus());
        dto.setHasChanges(syncResult.getHasChanges());
        dto.setChangesCount(syncResult.getChangesCount());
        dto.setSourceCommitSha(syncResult.getSourceCommitSha());
        dto.setTargetCommitSha(syncResult.getTargetCommitSha());
        dto.setDurationSeconds(syncResult.getDurationSeconds());
        dto.setErrorMessage(syncResult.getErrorMessage());
        dto.setSummary(syncResult.getSummary());
        dto.setStatistics(syncResult.getStatistics());

        // Get project key and sync method from sync_project
        SyncProject project = syncProjectMapper.selectById(syncResult.getSyncProjectId());
        if (project != null) {
            dto.setProjectKey(project.getProjectKey());
            dto.setSyncMethod(project.getSyncMethod());
        }

        return dto;
    }

    /**
     * Get sync result detail with branch information
     *
     * GET /api/sync/results/{id}
     */
    @GetMapping("/results/{id}")
    public ResponseEntity<ApiResponse<SyncResultDetailDTO>> getSyncResultDetail(@PathVariable Long id) {
        log.info("Query sync result detail - id: {}", id);

        try {
            SyncResult syncResult = syncResultMapper.selectById(id);
            if (syncResult == null) {
                return ResponseEntity.ok(ApiResponse.error("Sync result not found"));
            }

            SyncResultDetailDTO dto = convertToSyncResultDetailDTO(syncResult);
            return ResponseEntity.ok(ApiResponse.success(dto));
        } catch (Exception e) {
            log.error("Query sync result detail failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Get sync result by project ID
     *
     * GET /api/sync/projects/{projectId}/result
     */
    @GetMapping("/projects/{projectId}/result")
    public ResponseEntity<ApiResponse<SyncResultDetailDTO>> getSyncResultByProjectId(@PathVariable Long projectId) {
        log.info("Query sync result by project ID - projectId: {}", projectId);

        try {
            SyncResult syncResult = syncResultMapper.selectBySyncProjectId(projectId);
            if (syncResult == null) {
                return ResponseEntity.ok(ApiResponse.error("Sync result not found for project"));
            }

            SyncResultDetailDTO dto = convertToSyncResultDetailDTO(syncResult);
            return ResponseEntity.ok(ApiResponse.success(dto));
        } catch (Exception e) {
            log.error("Query sync result by project ID failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Convert SyncResult to SyncResultDetailDTO with branch information
     */
    private SyncResultDetailDTO convertToSyncResultDetailDTO(SyncResult syncResult) {
        SyncResultDetailDTO dto = new SyncResultDetailDTO();
        dto.setId(syncResult.getId());
        dto.setSyncProjectId(syncResult.getSyncProjectId());
        dto.setLastSyncAt(syncResult.getLastSyncAt());
        dto.setStartedAt(syncResult.getStartedAt());
        dto.setCompletedAt(syncResult.getCompletedAt());
        dto.setSyncStatus(syncResult.getSyncStatus());
        dto.setHasChanges(syncResult.getHasChanges());
        dto.setChangesCount(syncResult.getChangesCount());
        dto.setSourceCommitSha(syncResult.getSourceCommitSha());
        dto.setTargetCommitSha(syncResult.getTargetCommitSha());
        dto.setDurationSeconds(syncResult.getDurationSeconds());
        dto.setErrorMessage(syncResult.getErrorMessage());
        dto.setSummary(syncResult.getSummary());
        dto.setStatistics(syncResult.getStatistics());

        // Get project key and sync method
        SyncProject project = syncProjectMapper.selectById(syncResult.getSyncProjectId());
        if (project != null) {
            dto.setProjectKey(project.getProjectKey());
            dto.setSyncMethod(project.getSyncMethod());
        }

        // Get branch snapshot information
        List<ProjectBranchSnapshot> allBranches = branchSnapshotService.getBranchSnapshots(
                syncResult.getSyncProjectId(),
                ProjectBranchSnapshot.ProjectType.SOURCE
        );

        dto.setTotalBranches(allBranches.size());

        // Sort by committed_at DESC and get last 10 branches
        List<SyncResultDetailDTO.BranchInfo> recentBranches = allBranches.stream()
                .sorted((b1, b2) -> {
                    if (b1.getCommittedAt() == null && b2.getCommittedAt() == null) return 0;
                    if (b1.getCommittedAt() == null) return 1;
                    if (b2.getCommittedAt() == null) return -1;
                    return b2.getCommittedAt().compareTo(b1.getCommittedAt());
                })
                .limit(10)
                .map(branch -> {
                    SyncResultDetailDTO.BranchInfo branchInfo = new SyncResultDetailDTO.BranchInfo();
                    branchInfo.setBranchName(branch.getBranchName());
                    branchInfo.setCommitSha(branch.getCommitSha());
                    branchInfo.setCommitMessage(branch.getCommitMessage());
                    branchInfo.setCommitAuthor(branch.getCommitAuthor());
                    branchInfo.setCommittedAt(branch.getCommittedAt());
                    branchInfo.setIsDefault(branch.getIsDefault());
                    branchInfo.setIsProtected(branch.getIsProtected());

                    // 标记24小时内更新的分支
                    if (branch.getCommittedAt() != null) {
                        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
                        branchInfo.setIsRecentlyUpdated(branch.getCommittedAt().isAfter(twentyFourHoursAgo));
                    } else {
                        branchInfo.setIsRecentlyUpdated(false);
                    }

                    return branchInfo;
                })
                .collect(Collectors.toList());
        dto.setRecentBranches(recentBranches);

        return dto;
    }

    /**
     * Batch trigger sync for multiple projects
     *
     * POST /api/sync/projects/batch-sync
     */
    @PostMapping("/projects/batch-sync")
    public ResponseEntity<ApiResponse<BatchOperationResult>> batchTriggerSync(
            @RequestBody BatchOperationRequest request) {
        log.info("Batch trigger sync - projectIds: {}", request.getProjectIds());

        try {
            BatchOperationResult result = new BatchOperationResult();
            List<String> successList = new ArrayList<>();
            List<String> failedList = new ArrayList<>();

            for (Long projectId : request.getProjectIds()) {
                String projectKey = null;
                try {
                    SyncProject project = syncProjectMapper.selectById(projectId);
                    if (project == null) {
                        failedList.add("Project ID " + projectId + " not found");
                        continue;
                    }
                    projectKey = project.getProjectKey();

                    // Clear error state and re-enable sync if needed
                    clearErrorStateAndReEnable(project, projectId);

                    // Get or create sync task
                    SyncTask task = syncTaskService.getTaskBySyncProjectId(projectId);
                    if (task == null) {
                        // Initialize task if it doesn't exist
                        String taskType = "pull_sync".equals(project.getSyncMethod()) ? "pull" : "push";
                        task = syncTaskService.initializeTask(projectId, taskType);
                    }

                    // Trigger immediate sync by setting next_run_at to now and status to waiting
                    task.setNextRunAt(java.time.Instant.now());
                    task.setConsecutiveFailures(0); // Reset failure count to allow immediate sync
                    task.setForceSync(true); // Force sync to bypass change detection
                    task.setErrorType(""); // Clear error type
                    task.setErrorMessage(""); // Clear error message

                    // Force reset stuck tasks
                    if ("running".equals(task.getTaskStatus())) {
                        // Check if task has been running for too long (> 30 minutes)
                        if (task.getStartedAt() != null) {
                            long minutesRunning = java.time.Duration.between(
                                task.getStartedAt(), java.time.Instant.now()).toMinutes();

                            if (minutesRunning > 30) {
                                log.warn("Force resetting stuck task for project {}, running for {} minutes",
                                    projectKey, minutesRunning);
                                task.setTaskStatus("waiting");
                                task.setStartedAt(null);
                                task.setCompletedAt(null);
                            } else {
                                // Task is actively running, will run again after completion
                                log.info("Task for project {} is actively running ({} min), will queue for next run",
                                    projectKey, minutesRunning);
                            }
                        } else {
                            // Running without start time is abnormal, reset it
                            log.warn("Task for project {} is in running state without start time, resetting",
                                projectKey);
                            task.setTaskStatus("waiting");
                        }
                    } else {
                        task.setTaskStatus("waiting");
                    }
                    syncTaskService.updateTask(task);

                    successList.add(project.getProjectKey());
                    log.info("Triggered immediate sync for project: {}", projectKey);
                } catch (Exception e) {
                    String key = projectKey != null ? projectKey : "ID " + projectId;
                    failedList.add(key + ": " + e.getMessage());
                    log.error("Failed to trigger sync for project {}", key, e);
                }
            }

            result.setTotal(request.getProjectIds().size());
            result.setSuccess(successList.size());
            result.setFailed(failedList.size());
            result.setSuccessList(successList);
            result.setFailedList(failedList);

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Batch trigger sync failed", e);
            return ResponseEntity.ok(ApiResponse.error("Batch operation failed: " + e.getMessage()));
        }
    }

    /**
     * Clear error state and re-enable sync
     * <p>
     * This method is called when manually triggering sync to recover from error states:
     * 1. Re-enable pull_sync_config if it was auto-disabled
     * 2. Clear local_repo_path if there was a Git-related error (will trigger first sync)
     * 3. Clear sync_project error message and set status to pending
     *
     * @param project   Sync project
     * @param projectId Project ID
     */
    private void clearErrorStateAndReEnable(SyncProject project, Long projectId) {
        try {
            // For pull sync projects
            if ("pull_sync".equals(project.getSyncMethod())) {
                PullSyncConfig pullConfig = pullSyncConfigMapper.selectOne(
                        new QueryWrapper<PullSyncConfig>().eq("sync_project_id", projectId)
                );

                if (pullConfig != null) {
                    boolean needsUpdate = false;

                    // Re-enable if disabled
                    if (!pullConfig.getEnabled()) {
                        pullConfig.setEnabled(true);
                        needsUpdate = true;
                        log.info("Re-enabling pull sync config for project: {}", project.getProjectKey());
                    }

                    // Clear local repo path if there was a Git command error
                    // This forces a fresh first sync instead of incremental sync
                    if (pullConfig.getLocalRepoPath() != null &&
                        project.getErrorMessage() != null &&
                        (project.getErrorMessage().contains("fatal:") ||
                         project.getErrorMessage().contains("git ") ||
                         project.getErrorMessage().contains("--mirror"))) {
                        log.info("Clearing local repo path for project {} due to Git error, will trigger first sync",
                                project.getProjectKey());
                        pullConfig.setLocalRepoPath(null);
                        needsUpdate = true;
                    }

                    if (needsUpdate) {
                        pullSyncConfigMapper.updateById(pullConfig);
                    }
                }
            }

            // Clear project error state
            if (project.getErrorMessage() != null || !"active".equals(project.getSyncStatus())) {
                project.setErrorMessage(null);
                project.setSyncStatus(SyncProject.SyncStatus.PENDING);
                syncProjectMapper.updateById(project);
                log.info("Cleared error state for project: {}", project.getProjectKey());
            }

        } catch (Exception e) {
            log.warn("Failed to clear error state for project {}: {}",
                    project.getProjectKey(), e.getMessage());
            // Don't throw exception - continue with sync trigger
        }
    }

    /**
     * Batch pause projects
     *
     * POST /api/sync/projects/batch-pause
     */
    @PostMapping("/projects/batch-pause")
    public ResponseEntity<ApiResponse<BatchOperationResult>> batchPause(
            @RequestBody BatchOperationRequest request) {
        log.info("Batch pause projects - projectIds: {}", request.getProjectIds());

        try {
            BatchOperationResult result = new BatchOperationResult();
            List<String> successList = new ArrayList<>();
            List<String> failedList = new ArrayList<>();

            for (Long projectId : request.getProjectIds()) {
                try {
                    SyncProject project = syncProjectMapper.selectById(projectId);
                    if (project == null) {
                        failedList.add("Project ID " + projectId + " not found");
                        continue;
                    }

                    // Disable project
                    project.setEnabled(false);
                    project.setSyncStatus("paused");
                    syncProjectMapper.updateById(project);

                    // Disable sync task
                    SyncTask task = syncTaskService.getTaskBySyncProjectId(projectId);
                    if (task != null) {
                        task.setTaskStatus("disabled");
                        task.setErrorMessage("Task disabled - project paused");
                        task.setErrorType("PROJECT_PAUSED");
                        task.setNextRunAt(null);
                        syncTaskService.updateTask(task);
                        log.info("Disabled sync task for paused project: {}", project.getProjectKey());
                    }

                    successList.add(project.getProjectKey());
                } catch (Exception e) {
                    failedList.add("Project ID " + projectId + ": " + e.getMessage());
                }
            }

            result.setTotal(request.getProjectIds().size());
            result.setSuccess(successList.size());
            result.setFailed(failedList.size());
            result.setSuccessList(successList);
            result.setFailedList(failedList);

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Batch pause failed", e);
            return ResponseEntity.ok(ApiResponse.error("Batch operation failed: " + e.getMessage()));
        }
    }

    /**
     * Batch resume projects
     *
     * POST /api/sync/projects/batch-resume
     */
    @PostMapping("/projects/batch-resume")
    public ResponseEntity<ApiResponse<BatchOperationResult>> batchResume(
            @RequestBody BatchOperationRequest request) {
        log.info("Batch resume projects - projectIds: {}", request.getProjectIds());

        try {
            BatchOperationResult result = new BatchOperationResult();
            List<String> successList = new ArrayList<>();
            List<String> failedList = new ArrayList<>();

            for (Long projectId : request.getProjectIds()) {
                try {
                    SyncProject project = syncProjectMapper.selectById(projectId);
                    if (project == null) {
                        failedList.add("Project ID " + projectId + " not found");
                        continue;
                    }

                    // Enable project
                    project.setEnabled(true);
                    project.setSyncStatus("active");
                    syncProjectMapper.updateById(project);

                    // Re-enable sync task
                    SyncTask task = syncTaskService.getTaskBySyncProjectId(projectId);
                    if (task != null) {
                        task.setTaskStatus("waiting");
                        task.setErrorMessage(null);
                        task.setErrorType(null);
                        task.setNextRunAt(java.time.Instant.now());
                        syncTaskService.updateTask(task);
                        log.info("Re-enabled sync task for resumed project: {}", project.getProjectKey());
                    } else {
                        // Create task if it doesn't exist
                        String taskType = "pull_sync".equals(project.getSyncMethod()) ? "pull" : "push";
                        task = syncTaskService.initializeTask(projectId, taskType);
                        log.info("Created sync task for resumed project: {}", project.getProjectKey());
                    }

                    successList.add(project.getProjectKey());
                } catch (Exception e) {
                    failedList.add("Project ID " + projectId + ": " + e.getMessage());
                }
            }

            result.setTotal(request.getProjectIds().size());
            result.setSuccess(successList.size());
            result.setFailed(failedList.size());
            result.setSuccessList(successList);
            result.setFailedList(failedList);

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Batch resume failed", e);
            return ResponseEntity.ok(ApiResponse.error("Batch operation failed: " + e.getMessage()));
        }
    }

    /**
     * Batch delete projects
     *
     * POST /api/sync/projects/batch-delete
     */
    @PostMapping("/projects/batch-delete")
    public ResponseEntity<ApiResponse<BatchOperationResult>> batchDelete(
            @RequestBody BatchOperationRequest request) {
        log.info("Batch delete projects - projectIds: {}", request.getProjectIds());

        try {
            BatchOperationResult result = new BatchOperationResult();
            List<String> successList = new ArrayList<>();
            List<String> failedList = new ArrayList<>();

            for (Long projectId : request.getProjectIds()) {
                try {
                    SyncProject project = syncProjectMapper.selectById(projectId);
                    if (project == null) {
                        failedList.add("Project ID " + projectId + " not found");
                        continue;
                    }

                    // Delete sync task first (if exists)
                    SyncTask task = syncTaskService.getTaskBySyncProjectId(projectId);
                    if (task != null) {
                        syncTaskService.deleteTask(task.getId());
                        log.info("Deleted sync task for deleted project: {}", project.getProjectKey());
                    }

                    // Delete project
                    syncProjectMapper.deleteById(projectId);
                    successList.add(project.getProjectKey());
                } catch (Exception e) {
                    failedList.add("Project ID " + projectId + ": " + e.getMessage());
                }
            }

            result.setTotal(request.getProjectIds().size());
            result.setSuccess(successList.size());
            result.setFailed(failedList.size());
            result.setSuccessList(successList);
            result.setFailedList(failedList);

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Batch delete failed", e);
            return ResponseEntity.ok(ApiResponse.error("Batch operation failed: " + e.getMessage()));
        }
    }

    /**
     * Batch clear Git cache
     * <p>
     * Clears local Git repository cache for selected projects.
     * This is useful when the local cache is corrupted or needs to be refreshed.
     *
     * POST /api/sync/projects/batch-clear-cache
     */
    @PostMapping("/projects/batch-clear-cache")
    public ResponseEntity<ApiResponse<BatchOperationResult>> batchClearCache(
            @RequestBody BatchOperationRequest request) {
        log.info("Batch clear Git cache - projectIds: {}", request.getProjectIds());

        try {
            BatchOperationResult result = new BatchOperationResult();
            List<String> successList = new ArrayList<>();
            List<String> failedList = new ArrayList<>();

            for (Long projectId : request.getProjectIds()) {
                String projectKey = null;
                try {
                    SyncProject project = syncProjectMapper.selectById(projectId);
                    if (project == null) {
                        failedList.add("Project ID " + projectId + " not found");
                        continue;
                    }
                    projectKey = project.getProjectKey();

                    // Get Pull Sync config to find local repo path
                    PullSyncConfig pullConfig = pullSyncConfigMapper.selectOne(
                            new QueryWrapper<PullSyncConfig>().eq("sync_project_id", projectId)
                    );

                    if (pullConfig == null || pullConfig.getLocalRepoPath() == null) {
                        failedList.add(projectKey + ": No local repository path configured");
                        log.warn("No local repo path for project: {}", projectKey);
                        continue;
                    }

                    // Delete Git repository directory
                    File repoDir = new File(pullConfig.getLocalRepoPath());
                    if (repoDir.exists()) {
                        deleteDirectory(repoDir);
                        log.info("Cleared Git cache for project: {}, path: {}", projectKey, pullConfig.getLocalRepoPath());
                    } else {
                        log.info("Git cache directory does not exist for project: {}, path: {}", projectKey, pullConfig.getLocalRepoPath());
                    }
                    // Always mark as success - cache directory not existing is a valid state
                    successList.add(projectKey);
                } catch (Exception e) {
                    String key = projectKey != null ? projectKey : "ID " + projectId;
                    failedList.add(key + ": " + e.getMessage());
                    log.error("Failed to clear Git cache for project {}", key, e);
                }
            }

            result.setTotal(request.getProjectIds().size());
            result.setSuccess(successList.size());
            result.setFailed(failedList.size());
            result.setSuccessList(successList);
            result.setFailedList(failedList);

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Batch clear cache failed", e);
            return ResponseEntity.ok(ApiResponse.error("Batch operation failed: " + e.getMessage()));
        }
    }

    /**
     * Recursively delete directory and all its contents
     */
    private void deleteDirectory(File directory) throws IOException {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        if (!directory.delete()) {
            throw new IOException("Failed to delete: " + directory.getAbsolutePath());
        }
    }

    /**
     * Batch operation request
     */
    @Data
    public static class BatchOperationRequest {
        private List<Long> projectIds;
    }

    /**
     * Batch operation result
     */
    @Data
    public static class BatchOperationResult {
        private Integer total;
        private Integer success;
        private Integer failed;
        private List<String> successList;
        private List<String> failedList;
    }
}
