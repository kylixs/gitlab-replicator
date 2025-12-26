package com.gitlab.mirror.server.service.monitor;

import com.gitlab.mirror.common.model.RepositoryBranch;
import com.gitlab.mirror.server.client.GitLabApiClient;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.mapper.TargetProjectInfoMapper;
import com.gitlab.mirror.server.service.monitor.model.BranchComparison;
import com.gitlab.mirror.server.service.monitor.model.DiffDetails;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import com.gitlab.mirror.server.service.monitor.model.ProjectSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Diff Calculator Service
 * <p>
 * Calculates differences between source and target GitLab projects.
 * Determines sync status and identifies anomalies.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class DiffCalculator {

    private final SyncProjectMapper syncProjectMapper;
    private final SourceProjectInfoMapper sourceProjectInfoMapper;
    private final TargetProjectInfoMapper targetProjectInfoMapper;
    private final GitLabApiClient sourceGitLabApiClient;
    private final GitLabApiClient targetGitLabApiClient;
    private final com.gitlab.mirror.server.service.BranchSnapshotService branchSnapshotService;

    public DiffCalculator(
            SyncProjectMapper syncProjectMapper,
            SourceProjectInfoMapper sourceProjectInfoMapper,
            TargetProjectInfoMapper targetProjectInfoMapper,
            @Qualifier("sourceGitLabApiClient") GitLabApiClient sourceGitLabApiClient,
            @Qualifier("targetGitLabApiClient") GitLabApiClient targetGitLabApiClient,
            com.gitlab.mirror.server.service.BranchSnapshotService branchSnapshotService) {
        this.syncProjectMapper = syncProjectMapper;
        this.sourceProjectInfoMapper = sourceProjectInfoMapper;
        this.targetProjectInfoMapper = targetProjectInfoMapper;
        this.sourceGitLabApiClient = sourceGitLabApiClient;
        this.targetGitLabApiClient = targetGitLabApiClient;
        this.branchSnapshotService = branchSnapshotService;
    }

    /**
     * Calculate diff for a single project (without detailed branch comparison)
     *
     * @param syncProjectId Sync project ID
     * @return Project diff result
     */
    public ProjectDiff calculateDiff(Long syncProjectId) {
        return calculateDiff(syncProjectId, false);
    }

    /**
     * Calculate diff for a single project
     *
     * @param syncProjectId       Sync project ID
     * @param includeDetailedBranches Whether to include detailed branch-level comparison
     * @return Project diff result
     */
    public ProjectDiff calculateDiff(Long syncProjectId, boolean includeDetailedBranches) {
        log.debug("Calculating diff for sync project {} (detailed branches: {})", syncProjectId, includeDetailedBranches);

        // Get sync project
        SyncProject syncProject = syncProjectMapper.selectById(syncProjectId);
        if (syncProject == null) {
            log.warn("Sync project not found: {}", syncProjectId);
            return null;
        }

        // Check if source project is missing based on sync_project status
        boolean isSourceMissing = SyncProject.SyncStatus.MISSING.equals(syncProject.getSyncStatus());

        // Get source and target info
        SourceProjectInfo sourceInfo = sourceProjectInfoMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SourceProjectInfo>()
                        .eq("sync_project_id", syncProjectId)
        );
        log.debug("查询 SourceProjectInfo - syncProjectId: {}, result: {}", syncProjectId, sourceInfo != null ? "找到" : "未找到");

        TargetProjectInfo targetInfo = targetProjectInfoMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TargetProjectInfo>()
                        .eq("sync_project_id", syncProjectId)
        );
        log.debug("查询 TargetProjectInfo - syncProjectId: {}, result: {}", syncProjectId, targetInfo != null ? ("找到 ID=" + targetInfo.getId()) : "未找到");

        // Build snapshots
        // If source is marked as missing, treat sourceSnapshot as null regardless of sourceInfo existence
        ProjectSnapshot sourceSnapshot = isSourceMissing ? null : buildSourceSnapshot(sourceInfo);
        ProjectSnapshot targetSnapshot = buildTargetSnapshot(targetInfo);
        log.debug("构建 Snapshot - source: {} (sourceMissing: {}), target: {}",
            sourceSnapshot != null, isSourceMissing, targetSnapshot != null);

        // Calculate diff details
        DiffDetails diffDetails = calculateDiffDetails(syncProjectId, sourceSnapshot, targetSnapshot,
            sourceInfo, targetInfo, includeDetailedBranches);

        // Determine sync status
        ProjectDiff.SyncStatus status = determineSyncStatus(sourceSnapshot, targetSnapshot, diffDetails);

        // Build result
        return ProjectDiff.builder()
                .projectKey(syncProject.getProjectKey())
                .syncProjectId(syncProjectId)
                .source(sourceSnapshot)
                .target(targetSnapshot)
                .diff(diffDetails)
                .status(status)
                .checkedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Calculate diffs for multiple projects (with detailed branch comparison)
     *
     * @param syncProjectIds List of sync project IDs
     * @return List of project diffs
     */
    public List<ProjectDiff> calculateDiffBatch(List<Long> syncProjectIds) {
        log.info("Calculating diffs for {} projects (with detailed branch comparison)", syncProjectIds.size());

        List<ProjectDiff> results = new ArrayList<>();
        for (Long id : syncProjectIds) {
            // Enable detailed branch comparison to analyze each branch's differences
            ProjectDiff diff = calculateDiff(id, true);
            if (diff != null) {
                results.add(diff);

                // Log branch comparison summary
                if (diff.getDiff() != null && diff.getDiff().getBranchSummary() != null) {
                    DiffDetails.BranchComparisonSummary summary = diff.getDiff().getBranchSummary();
                    log.debug("[DIFF] Project {}: {} total branches - {} synced, {} outdated, {} missing, {} extra",
                            diff.getProjectKey(),
                            summary.getTotalBranchCount(),
                            summary.getSyncedCount(),
                            summary.getOutdatedCount(),
                            summary.getMissingInTargetCount(),
                            summary.getExtraInTargetCount());
                }
            }
        }

        log.info("Calculated {} diffs successfully", results.size());
        return results;
    }

    /**
     * Build source project snapshot
     */
    private ProjectSnapshot buildSourceSnapshot(SourceProjectInfo info) {
        if (info == null) {
            return null;
        }

        return ProjectSnapshot.builder()
                .commitSha(info.getLatestCommitSha())
                .commitCount(info.getCommitCount())
                .branchCount(info.getBranchCount())
                .sizeBytes(info.getRepositorySize())
                .lastActivityAt(info.getLastActivityAt())
                .defaultBranch(info.getDefaultBranch())
                .build();
    }

    /**
     * Build target project snapshot
     */
    private ProjectSnapshot buildTargetSnapshot(TargetProjectInfo info) {
        if (info == null) {
            return null;
        }

        return ProjectSnapshot.builder()
                .commitSha(info.getLatestCommitSha())
                .commitCount(info.getCommitCount())
                .branchCount(info.getBranchCount())
                .sizeBytes(info.getRepositorySize())
                .lastActivityAt(info.getLastActivityAt())
                .defaultBranch(info.getDefaultBranch())
                .build();
    }

    /**
     * Calculate diff details between source and target
     */
    private DiffDetails calculateDiffDetails(
            Long syncProjectId,
            ProjectSnapshot source,
            ProjectSnapshot target,
            SourceProjectInfo sourceInfo,
            TargetProjectInfo targetInfo,
            boolean includeDetailedBranches) {

        if (source == null || target == null) {
            return DiffDetails.builder().build();
        }

        DiffDetails.DiffDetailsBuilder builder = DiffDetails.builder();

        // Commit behind calculation
        if (source.getCommitCount() != null && target.getCommitCount() != null) {
            builder.commitBehind(source.getCommitCount() - target.getCommitCount());
        }

        // Sync delay calculation (in minutes)
        if (source.getLastActivityAt() != null && target.getLastActivityAt() != null) {
            Duration duration = Duration.between(target.getLastActivityAt(), source.getLastActivityAt());
            builder.syncDelayMinutes(duration.toMinutes());
        }

        // Size diff percentage
        if (source.getSizeBytes() != null && target.getSizeBytes() != null && source.getSizeBytes() > 0) {
            long diff = Math.abs(target.getSizeBytes() - source.getSizeBytes());
            double percent = (diff * 100.0) / source.getSizeBytes();
            builder.sizeDiffPercent(percent);
        }

        // Branch diff
        if (source.getBranchCount() != null && target.getBranchCount() != null) {
            builder.branchDiff(target.getBranchCount() - source.getBranchCount());
        }

        // Commit SHA match
        boolean shaMatches = source.getCommitSha() != null && source.getCommitSha().equals(target.getCommitSha());
        builder.commitShaMatches(shaMatches);

        // Default branch match
        boolean branchMatches = source.getDefaultBranch() != null && source.getDefaultBranch().equals(target.getDefaultBranch());
        builder.defaultBranchMatches(branchMatches);

        // Detailed branch comparison (if requested)
        if (includeDetailedBranches && sourceInfo != null && targetInfo != null) {
            try {
                List<BranchComparison> branchComparisons = compareBranches(
                    syncProjectId,
                    sourceInfo.getGitlabProjectId(),
                    targetInfo.getGitlabProjectId(),
                    source.getDefaultBranch()
                );
                builder.branchComparisons(branchComparisons);

                // Build branch summary
                DiffDetails.BranchComparisonSummary summary = buildBranchSummary(branchComparisons);
                builder.branchSummary(summary);
            } catch (Exception e) {
                log.warn("Failed to compare branches: {}", e.getMessage());
            }
        }

        return builder.build();
    }

    /**
     * Determine sync status based on diff details
     * Priority: MISSING > PENDING > DIVERGED > AHEAD > INCONSISTENT > OUTDATED > SYNCED
     */
    private ProjectDiff.SyncStatus determineSyncStatus(
            ProjectSnapshot source,
            ProjectSnapshot target,
            DiffDetails diff) {

        // Source missing (shouldn't happen in normal flow)
        if (source == null) {
            return ProjectDiff.SyncStatus.MISSING;
        }

        // Target missing - newly discovered project, not yet synced
        if (target == null) {
            return ProjectDiff.SyncStatus.PENDING;
        }

        // Check branch comparison summary for DIVERGED and AHEAD status
        DiffDetails.BranchComparisonSummary summary = diff.getBranchSummary();
        if (summary != null) {
            // DIVERGED has highest priority - any diverged branch means project is diverged
            if (summary.getDivergedCount() > 0) {
                log.debug("Project has {} diverged branches", summary.getDivergedCount());
                return ProjectDiff.SyncStatus.DIVERGED;
            }

            // AHEAD - if any branch is ahead (and none diverged)
            if (summary.getAheadCount() > 0) {
                log.debug("Project has {} ahead branches", summary.getAheadCount());
                return ProjectDiff.SyncStatus.AHEAD;
            }

            // SYNCED - if all branches are synced (synced count equals total AND total > 0)
            if (summary.getSyncedCount() > 0 &&
                summary.getSyncedCount() == summary.getTotalBranchCount() &&
                summary.getOutdatedCount() == 0 &&
                summary.getMissingInTargetCount() == 0) {
                log.debug("Project is synced: all {} branches are up-to-date", summary.getSyncedCount());
                return ProjectDiff.SyncStatus.SYNCED;
            }

            // OUTDATED - if any branches are outdated or missing
            if (summary.getOutdatedCount() > 0 || summary.getMissingInTargetCount() > 0) {
                log.debug("Project is outdated: {} outdated, {} missing",
                    summary.getOutdatedCount(), summary.getMissingInTargetCount());
                return ProjectDiff.SyncStatus.OUTDATED;
            }

            // Empty project (0 branches total) - treat as PENDING (not yet synced)
            if (summary.getTotalBranchCount() == 0) {
                log.debug("Project has 0 branches, treating as PENDING");
                return ProjectDiff.SyncStatus.PENDING;
            }
        }

        // Check for inconsistencies (if no detailed branch info available)
        Integer branchDiff = diff.getBranchDiff();
        Double sizeDiffPercent = diff.getSizeDiffPercent();

        // Inconsistent: Branch count mismatch or size diff > 10%
        if ((branchDiff != null && Math.abs(branchDiff) > 0) ||
            (sizeDiffPercent != null && sizeDiffPercent > 10.0)) {
            return ProjectDiff.SyncStatus.INCONSISTENT;
        }

        // Check commit SHA and delay
        boolean shaMatches = diff.isCommitShaMatches();
        Long delayMinutes = diff.getSyncDelayMinutes();

        // Synced: SHA matches and delay < 5 minutes
        if (shaMatches && (delayMinutes == null || delayMinutes < 5)) {
            return ProjectDiff.SyncStatus.SYNCED;
        }

        // Outdated: SHA doesn't match or delay > 30 minutes
        if (!shaMatches || (delayMinutes != null && delayMinutes > 30)) {
            return ProjectDiff.SyncStatus.OUTDATED;
        }

        // Default to SYNCED
        return ProjectDiff.SyncStatus.SYNCED;
    }

    /**
     * Compare branches between source and target GitLab projects
     * <p>
     * Priority: Use database snapshots if available, otherwise fetch from GitLab API
     *
     * @param syncProjectId   Sync project ID (for database snapshot lookup)
     * @param sourceProjectId Source GitLab project ID (for API fallback)
     * @param targetProjectId Target GitLab project ID (for API fallback)
     * @param defaultBranch   Default branch name
     * @return List of branch comparisons
     */
    private List<BranchComparison> compareBranches(Long syncProjectId, Long sourceProjectId, Long targetProjectId, String defaultBranch) {
        log.debug("Comparing branches: syncProjectId={}, sourceGitLabId={}, targetGitLabId={}",
                syncProjectId, sourceProjectId, targetProjectId);

        // Try to use database snapshots first (for better performance and accuracy)
        List<com.gitlab.mirror.server.entity.ProjectBranchSnapshot> sourceSnapshots =
            branchSnapshotService.getBranchSnapshots(syncProjectId, com.gitlab.mirror.server.entity.ProjectBranchSnapshot.ProjectType.SOURCE);
        List<com.gitlab.mirror.server.entity.ProjectBranchSnapshot> targetSnapshots =
            branchSnapshotService.getBranchSnapshots(syncProjectId, com.gitlab.mirror.server.entity.ProjectBranchSnapshot.ProjectType.TARGET);

        if (!sourceSnapshots.isEmpty() || !targetSnapshots.isEmpty()) {
            log.debug("Using database snapshots: {} source, {} target branches", sourceSnapshots.size(), targetSnapshots.size());
            return compareBranchesFromSnapshots(sourceSnapshots, targetSnapshots, defaultBranch);
        }

        // Fallback: Fetch branches from GitLab API (live data)
        log.debug("No database snapshots found, fetching branches from GitLab API");
        List<RepositoryBranch> sourceBranches = sourceGitLabApiClient.getBranches(sourceProjectId);
        List<RepositoryBranch> targetBranches = targetGitLabApiClient.getBranches(targetProjectId);

        log.debug("Fetched {} source branches, {} target branches from API", sourceBranches.size(), targetBranches.size());

        return compareBranchesFromApi(sourceBranches, targetBranches, defaultBranch);
    }

    /**
     * Compare branches from database snapshots
     */
    private List<BranchComparison> compareBranchesFromSnapshots(
            List<com.gitlab.mirror.server.entity.ProjectBranchSnapshot> sourceSnapshots,
            List<com.gitlab.mirror.server.entity.ProjectBranchSnapshot> targetSnapshots,
            String defaultBranch) {

        // Build maps with full snapshot info for quick lookup
        Map<String, com.gitlab.mirror.server.entity.ProjectBranchSnapshot> sourceMap = sourceSnapshots.stream()
            .collect(Collectors.toMap(
                com.gitlab.mirror.server.entity.ProjectBranchSnapshot::getBranchName,
                snapshot -> snapshot,
                (a, b) -> a
            ));

        Map<String, com.gitlab.mirror.server.entity.ProjectBranchSnapshot> targetMap = targetSnapshots.stream()
            .collect(Collectors.toMap(
                com.gitlab.mirror.server.entity.ProjectBranchSnapshot::getBranchName,
                snapshot -> snapshot,
                (a, b) -> a
            ));

        return compareBranchSnapshots(sourceMap, targetMap, defaultBranch);
    }

    /**
     * Compare branches from GitLab API response
     */
    private List<BranchComparison> compareBranchesFromApi(
            List<RepositoryBranch> sourceBranches,
            List<RepositoryBranch> targetBranches,
            String defaultBranch) {

        // Build maps for quick lookup
        Map<String, String> sourceMap = sourceBranches.stream()
            .collect(Collectors.toMap(
                RepositoryBranch::getName,
                b -> b.getCommit() != null && b.getCommit().getId() != null ? b.getCommit().getId() : "",
                (a, b) -> a
            ));

        Map<String, String> targetMap = targetBranches.stream()
            .collect(Collectors.toMap(
                RepositoryBranch::getName,
                b -> b.getCommit() != null && b.getCommit().getId() != null ? b.getCommit().getId() : "",
                (a, b) -> a
            ));

        return compareBranchMaps(sourceMap, targetMap, defaultBranch);
    }

    /**
     * Compare branch snapshots with time-based status detection
     */
    private List<BranchComparison> compareBranchSnapshots(
            Map<String, com.gitlab.mirror.server.entity.ProjectBranchSnapshot> sourceMap,
            Map<String, com.gitlab.mirror.server.entity.ProjectBranchSnapshot> targetMap,
            String defaultBranch) {

        // Get all unique branch names
        Set<String> allBranchNames = new HashSet<>();
        allBranchNames.addAll(sourceMap.keySet());
        allBranchNames.addAll(targetMap.keySet());

        // Compare each branch
        List<BranchComparison> comparisons = new ArrayList<>();
        for (String branchName : allBranchNames) {
            var sourceSnapshot = sourceMap.get(branchName);
            var targetSnapshot = targetMap.get(branchName);

            BranchComparison.BranchSyncStatus status;
            String sourceSha = sourceSnapshot != null ? sourceSnapshot.getCommitSha() : null;
            String targetSha = targetSnapshot != null ? targetSnapshot.getCommitSha() : null;
            LocalDateTime sourceTime = sourceSnapshot != null ? sourceSnapshot.getCommittedAt() : null;
            LocalDateTime targetTime = targetSnapshot != null ? targetSnapshot.getCommittedAt() : null;
            Long timeDiffSeconds = null;

            if (sourceSha != null && targetSha != null) {
                // Branch exists in both
                if (sourceSha.equals(targetSha)) {
                    status = BranchComparison.BranchSyncStatus.SYNCED;
                } else {
                    // Different SHAs - determine direction based on commit time
                    status = determineBranchStatus(sourceTime, targetTime);
                    if (sourceTime != null && targetTime != null) {
                        timeDiffSeconds = java.time.Duration.between(sourceTime, targetTime).getSeconds();
                    }
                }
            } else if (sourceSha != null) {
                // Only in source
                status = BranchComparison.BranchSyncStatus.MISSING_IN_TARGET;
            } else {
                // Only in target (orphaned branch)
                status = BranchComparison.BranchSyncStatus.EXTRA_IN_TARGET;
            }

            BranchComparison comparison = BranchComparison.builder()
                .branchName(branchName)
                .sourceCommitSha(sourceSha)
                .targetCommitSha(targetSha)
                .sourceCommittedAt(sourceTime)
                .targetCommittedAt(targetTime)
                .commitTimeDiffSeconds(timeDiffSeconds)
                .status(status)
                .isDefault(branchName.equals(defaultBranch))
                .isProtected(sourceSnapshot != null && Boolean.TRUE.equals(sourceSnapshot.getIsProtected()))
                .build();

            comparisons.add(comparison);
        }

        // Sort: default branch first, then by name
        comparisons.sort((a, b) -> {
            if (a.isDefault() && !b.isDefault()) return -1;
            if (!a.isDefault() && b.isDefault()) return 1;
            return a.getBranchName().compareTo(b.getBranchName());
        });

        return comparisons;
    }

    /**
     * Determine branch sync status based on commit times
     */
    private BranchComparison.BranchSyncStatus determineBranchStatus(
            LocalDateTime sourceTime, LocalDateTime targetTime) {

        if (sourceTime == null || targetTime == null) {
            // If we can't determine time, default to OUTDATED
            return BranchComparison.BranchSyncStatus.OUTDATED;
        }

        long diffSeconds = java.time.Duration.between(sourceTime, targetTime).getSeconds();
        long absSeconds = Math.abs(diffSeconds);

        // Divergence detection: if commits are very close in time (< 1 hour) but different SHA
        // This is a heuristic - true divergence requires checking git history
        if (absSeconds < 3600) {
            return BranchComparison.BranchSyncStatus.DIVERGED;
        }

        // Target is newer (ahead)
        if (diffSeconds > 0) {
            return BranchComparison.BranchSyncStatus.AHEAD;
        }

        // Source is newer (outdated)
        return BranchComparison.BranchSyncStatus.OUTDATED;
    }

    /**
     * Compare branch maps (common logic) - for API-based comparison
     */
    private List<BranchComparison> compareBranchMaps(
            Map<String, String> sourceMap,
            Map<String, String> targetMap,
            String defaultBranch) {

        // Get all unique branch names
        Set<String> allBranchNames = new HashSet<>();
        allBranchNames.addAll(sourceMap.keySet());
        allBranchNames.addAll(targetMap.keySet());

        // Compare each branch
        List<BranchComparison> comparisons = new ArrayList<>();
        for (String branchName : allBranchNames) {
            String sourceSha = sourceMap.get(branchName);
            String targetSha = targetMap.get(branchName);

            BranchComparison.BranchSyncStatus status;
            if (sourceSha != null && targetSha != null) {
                // Branch exists in both
                status = sourceSha.equals(targetSha)
                    ? BranchComparison.BranchSyncStatus.SYNCED
                    : BranchComparison.BranchSyncStatus.OUTDATED;
            } else if (sourceSha != null) {
                // Only in source
                status = BranchComparison.BranchSyncStatus.MISSING_IN_TARGET;
            } else {
                // Only in target (orphaned branch)
                status = BranchComparison.BranchSyncStatus.EXTRA_IN_TARGET;
            }

            BranchComparison comparison = BranchComparison.builder()
                .branchName(branchName)
                .sourceCommitSha(sourceSha)
                .targetCommitSha(targetSha)
                .status(status)
                .isDefault(branchName.equals(defaultBranch))
                .build();

            comparisons.add(comparison);
        }

        // Sort: default branch first, then by name
        comparisons.sort((a, b) -> {
            if (a.isDefault() && !b.isDefault()) return -1;
            if (!a.isDefault() && b.isDefault()) return 1;
            return a.getBranchName().compareTo(b.getBranchName());
        });

        return comparisons;
    }

    /**
     * Build branch comparison summary
     *
     * @param branchComparisons List of branch comparisons
     * @return Branch comparison summary
     */
    private DiffDetails.BranchComparisonSummary buildBranchSummary(List<BranchComparison> branchComparisons) {
        if (branchComparisons == null || branchComparisons.isEmpty()) {
            return DiffDetails.BranchComparisonSummary.builder()
                .syncedCount(0)
                .outdatedCount(0)
                .aheadCount(0)
                .divergedCount(0)
                .missingInTargetCount(0)
                .extraInTargetCount(0)
                .totalBranchCount(0)
                .build();
        }

        int synced = 0, outdated = 0, ahead = 0, diverged = 0, missing = 0, extra = 0;
        for (BranchComparison comparison : branchComparisons) {
            switch (comparison.getStatus()) {
                case SYNCED:
                    synced++;
                    break;
                case OUTDATED:
                    outdated++;
                    break;
                case AHEAD:
                    ahead++;
                    break;
                case DIVERGED:
                    diverged++;
                    break;
                case MISSING_IN_TARGET:
                    missing++;
                    break;
                case EXTRA_IN_TARGET:
                    extra++;
                    break;
            }
        }

        return DiffDetails.BranchComparisonSummary.builder()
            .syncedCount(synced)
            .outdatedCount(outdated)
            .aheadCount(ahead)
            .divergedCount(diverged)
            .missingInTargetCount(missing)
            .extraInTargetCount(extra)
            .totalBranchCount(branchComparisons.size())
            .build();
    }
}
