package com.gitlab.mirror.server.service.monitor;

import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.mapper.TargetProjectInfoMapper;
import com.gitlab.mirror.server.service.monitor.model.DiffDetails;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import com.gitlab.mirror.server.service.monitor.model.ProjectSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    public DiffCalculator(
            SyncProjectMapper syncProjectMapper,
            SourceProjectInfoMapper sourceProjectInfoMapper,
            TargetProjectInfoMapper targetProjectInfoMapper) {
        this.syncProjectMapper = syncProjectMapper;
        this.sourceProjectInfoMapper = sourceProjectInfoMapper;
        this.targetProjectInfoMapper = targetProjectInfoMapper;
    }

    /**
     * Calculate diff for a single project
     *
     * @param syncProjectId Sync project ID
     * @return Project diff result
     */
    public ProjectDiff calculateDiff(Long syncProjectId) {
        log.debug("Calculating diff for sync project {}", syncProjectId);

        // Get sync project
        SyncProject syncProject = syncProjectMapper.selectById(syncProjectId);
        if (syncProject == null) {
            log.warn("Sync project not found: {}", syncProjectId);
            return null;
        }

        // Get source and target info
        SourceProjectInfo sourceInfo = sourceProjectInfoMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SourceProjectInfo>()
                        .eq("sync_project_id", syncProjectId)
        );

        TargetProjectInfo targetInfo = targetProjectInfoMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TargetProjectInfo>()
                        .eq("sync_project_id", syncProjectId)
        );

        // Build snapshots
        ProjectSnapshot sourceSnapshot = buildSourceSnapshot(sourceInfo);
        ProjectSnapshot targetSnapshot = buildTargetSnapshot(targetInfo);

        // Calculate diff details
        DiffDetails diffDetails = calculateDiffDetails(sourceSnapshot, targetSnapshot);

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
     * Calculate diffs for multiple projects
     *
     * @param syncProjectIds List of sync project IDs
     * @return List of project diffs
     */
    public List<ProjectDiff> calculateDiffBatch(List<Long> syncProjectIds) {
        log.info("Calculating diffs for {} projects", syncProjectIds.size());

        List<ProjectDiff> results = new ArrayList<>();
        for (Long id : syncProjectIds) {
            ProjectDiff diff = calculateDiff(id);
            if (diff != null) {
                results.add(diff);
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
    private DiffDetails calculateDiffDetails(ProjectSnapshot source, ProjectSnapshot target) {
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

        return builder.build();
    }

    /**
     * Determine sync status based on diff details
     */
    private ProjectDiff.SyncStatus determineSyncStatus(
            ProjectSnapshot source,
            ProjectSnapshot target,
            DiffDetails diff) {

        // Target missing
        if (target == null) {
            return ProjectDiff.SyncStatus.FAILED;
        }

        // Source missing (shouldn't happen)
        if (source == null) {
            return ProjectDiff.SyncStatus.FAILED;
        }

        // Check for inconsistencies first (highest priority)
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
}
