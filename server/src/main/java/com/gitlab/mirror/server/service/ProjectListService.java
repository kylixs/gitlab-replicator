package com.gitlab.mirror.server.service;

import com.gitlab.mirror.server.controller.dto.ProjectListDTO;
import com.gitlab.mirror.server.controller.dto.ProjectOverviewDTO;
import com.gitlab.mirror.server.entity.ProjectBranchSnapshot;
import com.gitlab.mirror.server.entity.PullSyncConfig;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import com.gitlab.mirror.server.mapper.PullSyncConfigMapper;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.TargetProjectInfoMapper;
import com.gitlab.mirror.server.service.monitor.DiffCalculator;
import com.gitlab.mirror.server.service.monitor.model.DiffDetails;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Project List Service
 * <p>
 * Service for building project list with diff and delay information
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class ProjectListService {

    private final BranchSnapshotService branchSnapshotService;
    private final SourceProjectInfoMapper sourceProjectInfoMapper;
    private final TargetProjectInfoMapper targetProjectInfoMapper;
    private final PullSyncConfigMapper pullSyncConfigMapper;
    private final DiffCalculator diffCalculator;
    private final SyncTaskService syncTaskService;

    public ProjectListService(
            BranchSnapshotService branchSnapshotService,
            SourceProjectInfoMapper sourceProjectInfoMapper,
            TargetProjectInfoMapper targetProjectInfoMapper,
            PullSyncConfigMapper pullSyncConfigMapper,
            DiffCalculator diffCalculator,
            SyncTaskService syncTaskService) {
        this.branchSnapshotService = branchSnapshotService;
        this.sourceProjectInfoMapper = sourceProjectInfoMapper;
        this.targetProjectInfoMapper = targetProjectInfoMapper;
        this.pullSyncConfigMapper = pullSyncConfigMapper;
        this.diffCalculator = diffCalculator;
        this.syncTaskService = syncTaskService;
    }

    /**
     * Build project list DTO from sync project
     */
    public ProjectListDTO buildProjectListDTO(SyncProject project) {
        ProjectListDTO dto = new ProjectListDTO();
        dto.setId(project.getId());
        dto.setProjectKey(project.getProjectKey());
        dto.setSyncStatus(project.getSyncStatus());
        dto.setSyncMethod(project.getSyncMethod());
        dto.setLastSyncAt(project.getLastSyncAt());
        dto.setUpdatedAt(project.getUpdatedAt());

        // Get group path from source project info
        QueryWrapper<SourceProjectInfo> sourceQuery = new QueryWrapper<>();
        sourceQuery.eq("sync_project_id", project.getId());
        SourceProjectInfo sourceInfo = sourceProjectInfoMapper.selectOne(sourceQuery);
        if (sourceInfo != null) {
            dto.setGroupPath(sourceInfo.getGroupPath());
        }

        // Calculate diff
        ProjectListDTO.DiffInfo diff = calculateDiff(project.getId());
        dto.setDiff(diff);

        // Calculate delay
        Long delaySeconds = calculateDelay(project.getId());
        dto.setDelaySeconds(delaySeconds);
        dto.setDelayFormatted(formatDelay(delaySeconds));

        return dto;
    }

    /**
     * Calculate diff for project
     */
    private ProjectListDTO.DiffInfo calculateDiff(Long syncProjectId) {
        ProjectListDTO.DiffInfo diff = new ProjectListDTO.DiffInfo();

        try {
            List<ProjectBranchSnapshot> sourceBranches = branchSnapshotService.getBranchSnapshots(syncProjectId, "source");
            List<ProjectBranchSnapshot> targetBranches = branchSnapshotService.getBranchSnapshots(syncProjectId, "target");

            Map<String, ProjectBranchSnapshot> sourceMap = sourceBranches.stream()
                    .collect(Collectors.toMap(ProjectBranchSnapshot::getBranchName, b -> b));
            Map<String, ProjectBranchSnapshot> targetMap = targetBranches.stream()
                    .collect(Collectors.toMap(ProjectBranchSnapshot::getBranchName, b -> b));

            int branchNew = 0;
            int branchDeleted = 0;
            int branchOutdated = 0;
            int commitDiff = 0;

            // Count new and outdated branches
            for (ProjectBranchSnapshot source : sourceBranches) {
                ProjectBranchSnapshot target = targetMap.get(source.getBranchName());
                if (target == null) {
                    branchNew++;
                } else if (!source.getCommitSha().equals(target.getCommitSha())) {
                    branchOutdated++;
                    // Simplified commit diff (could be enhanced with actual commit count)
                    commitDiff += 1;
                }
            }

            // Count deleted branches
            for (ProjectBranchSnapshot target : targetBranches) {
                if (!sourceMap.containsKey(target.getBranchName())) {
                    branchDeleted++;
                }
            }

            diff.setBranchNew(branchNew);
            diff.setBranchDeleted(branchDeleted);
            diff.setBranchOutdated(branchOutdated);
            diff.setCommitDiff(commitDiff);

        } catch (Exception e) {
            log.warn("Failed to calculate diff for project {}: {}", syncProjectId, e.getMessage());
        }

        return diff;
    }

    /**
     * Calculate delay for project (in seconds)
     */
    private Long calculateDelay(Long syncProjectId) {
        try {
            QueryWrapper<SourceProjectInfo> sourceQuery = new QueryWrapper<>();
            sourceQuery.eq("sync_project_id", syncProjectId);
            SourceProjectInfo sourceInfo = sourceProjectInfoMapper.selectOne(sourceQuery);

            QueryWrapper<TargetProjectInfo> targetQuery = new QueryWrapper<>();
            targetQuery.eq("sync_project_id", syncProjectId);
            TargetProjectInfo targetInfo = targetProjectInfoMapper.selectOne(targetQuery);

            if (sourceInfo != null && targetInfo != null &&
                    sourceInfo.getLastActivityAt() != null && targetInfo.getLastActivityAt() != null) {
                Duration duration = Duration.between(targetInfo.getLastActivityAt(), sourceInfo.getLastActivityAt());
                return duration.getSeconds();
            }
        } catch (Exception e) {
            log.warn("Failed to calculate delay for project {}: {}", syncProjectId, e.getMessage());
        }

        return 0L;
    }

    /**
     * Format delay seconds to human-readable string
     */
    private String formatDelay(Long seconds) {
        if (seconds == null || seconds < 0) {
            return "未知";
        }

        if (seconds < 60) {
            return "刚刚";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + "分钟";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            return hours + "小时";
        } else {
            long days = seconds / 86400;
            return days + "天";
        }
    }

    /**
     * Build project overview DTO
     */
    public ProjectOverviewDTO buildProjectOverview(SyncProject project) {
        ProjectOverviewDTO overview = new ProjectOverviewDTO();
        overview.setProject(project);

        // Get source and target project info
        QueryWrapper<SourceProjectInfo> sourceQuery = new QueryWrapper<>();
        sourceQuery.eq("sync_project_id", project.getId());
        SourceProjectInfo sourceInfo = sourceProjectInfoMapper.selectOne(sourceQuery);
        overview.setSource(sourceInfo);

        QueryWrapper<TargetProjectInfo> targetQuery = new QueryWrapper<>();
        targetQuery.eq("sync_project_id", project.getId());
        TargetProjectInfo targetInfo = targetProjectInfoMapper.selectOne(targetQuery);
        overview.setTarget(targetInfo);

        // Calculate diff using DiffCalculator
        ProjectOverviewDTO.DiffInfo diff = new ProjectOverviewDTO.DiffInfo();
        try {
            ProjectDiff projectDiff = diffCalculator.calculateDiff(project.getId(), true);
            if (projectDiff != null && projectDiff.getDiff() != null) {
                DiffDetails details = projectDiff.getDiff();
                DiffDetails.BranchComparisonSummary summary = details.getBranchSummary();

                if (summary != null) {
                    diff.setBranchNew(summary.getMissingInTargetCount());
                    diff.setBranchDeleted(summary.getExtraInTargetCount());
                    diff.setBranchOutdated(summary.getOutdatedCount());
                    diff.setBranchAhead(summary.getAheadCount());
                    diff.setBranchDiverged(summary.getDivergedCount());
                }

                diff.setCommitDiff(details.getCommitBehind() != null ? details.getCommitBehind() : 0);
                diff.setDiffStatus(projectDiff.getStatus() != null ? projectDiff.getStatus().name() : "UNKNOWN");
            }
        } catch (Exception e) {
            log.warn("Failed to calculate diff using DiffCalculator for project {}: {}", project.getId(), e.getMessage());
            // Fallback to legacy calculation
            ProjectListDTO.DiffInfo listDiff = calculateDiff(project.getId());
            diff.setBranchNew(listDiff.getBranchNew());
            diff.setBranchDeleted(listDiff.getBranchDeleted());
            diff.setBranchOutdated(listDiff.getBranchOutdated());
            diff.setCommitDiff(listDiff.getCommitDiff());
        }
        overview.setDiff(diff);

        // Calculate delay
        Long delaySeconds = calculateDelay(project.getId());
        ProjectOverviewDTO.DelayInfo delay = new ProjectOverviewDTO.DelayInfo();
        delay.setSeconds(delaySeconds);
        delay.setFormatted(formatDelay(delaySeconds));
        overview.setDelay(delay);

        // Estimate next sync time (simplified - just add 5 minutes to last sync)
        if (project.getLastSyncAt() != null) {
            overview.setNextSyncTime(project.getLastSyncAt().plusMinutes(5));
        }

        // Build cache info
        ProjectOverviewDTO.CacheInfo cacheInfo = buildCacheInfo(project.getId());
        overview.setCache(cacheInfo);

        // Build task info
        ProjectOverviewDTO.TaskInfo taskInfo = buildTaskInfo(project.getId());
        overview.setTask(taskInfo);

        return overview;
    }

    /**
     * Build cache information for a project
     */
    private ProjectOverviewDTO.CacheInfo buildCacheInfo(Long projectId) {
        ProjectOverviewDTO.CacheInfo cacheInfo = new ProjectOverviewDTO.CacheInfo();

        try {
            // Get pull sync config to find local repo path
            QueryWrapper<PullSyncConfig> query = new QueryWrapper<>();
            query.eq("sync_project_id", projectId);
            PullSyncConfig pullConfig = pullSyncConfigMapper.selectOne(query);

            if (pullConfig == null || pullConfig.getLocalRepoPath() == null) {
                cacheInfo.setExists(false);
                return cacheInfo;
            }

            cacheInfo.setPath(pullConfig.getLocalRepoPath());

            File repoDir = new File(pullConfig.getLocalRepoPath());
            if (!repoDir.exists()) {
                cacheInfo.setExists(false);
                return cacheInfo;
            }

            cacheInfo.setExists(true);

            // Calculate directory size
            long size = calculateDirectorySize(repoDir);
            cacheInfo.setSizeBytes(size);
            cacheInfo.setSizeFormatted(formatSize(size));

            // Get last modified time
            BasicFileAttributes attrs = Files.readAttributes(repoDir.toPath(), BasicFileAttributes.class);
            LocalDateTime lastModified = LocalDateTime.ofInstant(
                attrs.lastModifiedTime().toInstant(),
                ZoneId.systemDefault()
            );
            cacheInfo.setLastModified(lastModified);

        } catch (Exception e) {
            log.warn("Failed to get cache info for project {}: {}", projectId, e.getMessage());
            cacheInfo.setExists(false);
        }

        return cacheInfo;
    }

    /**
     * Calculate total size of a directory
     */
    private long calculateDirectorySize(File directory) {
        long size = 0;

        if (directory.isFile()) {
            return directory.length();
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                size += calculateDirectorySize(file);
            }
        }

        return size;
    }

    /**
     * Format file size to human readable string
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * Build task information for a project
     */
    private ProjectOverviewDTO.TaskInfo buildTaskInfo(Long projectId) {
        ProjectOverviewDTO.TaskInfo taskInfo = new ProjectOverviewDTO.TaskInfo();

        try {
            SyncTask task = syncTaskService.getTaskBySyncProjectId(projectId);

            if (task != null) {
                taskInfo.setId(task.getId());
                taskInfo.setTaskType(task.getTaskType());
                taskInfo.setTaskStatus(task.getTaskStatus());
                taskInfo.setNextRunAt(task.getNextRunAt());
                taskInfo.setLastRunAt(task.getLastRunAt());
                taskInfo.setLastSyncStatus(task.getLastSyncStatus());
                taskInfo.setDurationSeconds(task.getDurationSeconds());
                taskInfo.setConsecutiveFailures(task.getConsecutiveFailures());
                taskInfo.setErrorMessage(task.getErrorMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to get task info for project {}: {}", projectId, e.getMessage());
        }

        return taskInfo;
    }
}
