package com.gitlab.mirror.server.service;

import com.gitlab.mirror.server.controller.dto.ProjectListDTO;
import com.gitlab.mirror.server.controller.dto.ProjectOverviewDTO;
import com.gitlab.mirror.server.entity.ProjectBranchSnapshot;
import com.gitlab.mirror.server.entity.PullSyncConfig;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.SyncResult;
import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import com.gitlab.mirror.server.mapper.ProjectBranchSnapshotMapper;
import com.gitlab.mirror.server.mapper.PullSyncConfigMapper;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.SyncResultMapper;
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
    private final ProjectBranchSnapshotMapper projectBranchSnapshotMapper;
    private final DiffCalculator diffCalculator;
    private final SyncTaskService syncTaskService;
    private final SyncResultMapper syncResultMapper;
    private final com.gitlab.mirror.server.mapper.SyncEventMapper syncEventMapper;

    public ProjectListService(
            BranchSnapshotService branchSnapshotService,
            SourceProjectInfoMapper sourceProjectInfoMapper,
            TargetProjectInfoMapper targetProjectInfoMapper,
            PullSyncConfigMapper pullSyncConfigMapper,
            ProjectBranchSnapshotMapper projectBranchSnapshotMapper,
            DiffCalculator diffCalculator,
            SyncTaskService syncTaskService,
            SyncResultMapper syncResultMapper,
            com.gitlab.mirror.server.mapper.SyncEventMapper syncEventMapper) {
        this.branchSnapshotService = branchSnapshotService;
        this.sourceProjectInfoMapper = sourceProjectInfoMapper;
        this.targetProjectInfoMapper = targetProjectInfoMapper;
        this.pullSyncConfigMapper = pullSyncConfigMapper;
        this.projectBranchSnapshotMapper = projectBranchSnapshotMapper;
        this.diffCalculator = diffCalculator;
        this.syncTaskService = syncTaskService;
        this.syncResultMapper = syncResultMapper;
        this.syncEventMapper = syncEventMapper;
    }

    /**
     * Build project list DTO from sync project
     */
    public ProjectListDTO buildProjectListDTO(SyncProject project) {
        return buildProjectListDTO(project, null, null, null);
    }

    /**
     * Build project list DTOs in batch (optimized for performance)
     * This method fetches all related data in batch queries instead of one-by-one
     */
    public List<ProjectListDTO> buildProjectListDTOsBatch(List<SyncProject> projects) {
        if (projects.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        // Collect all project IDs
        List<Long> projectIds = projects.stream()
                .map(SyncProject::getId)
                .collect(Collectors.toList());

        // Batch query source project info
        List<SourceProjectInfo> sourceInfos = sourceProjectInfoMapper.selectList(
                new QueryWrapper<SourceProjectInfo>().in("sync_project_id", projectIds));
        Map<Long, SourceProjectInfo> sourceInfoMap = sourceInfos.stream()
                .collect(Collectors.toMap(SourceProjectInfo::getSyncProjectId, info -> info));

        // Batch query sync tasks
        List<SyncTask> tasks = syncTaskService.getTasksBySyncProjectIds(projectIds);
        Map<Long, SyncTask> taskMap = tasks.stream()
                .collect(Collectors.toMap(SyncTask::getSyncProjectId, task -> task));

        // Batch query sync results
        List<SyncResult> syncResults = syncResultMapper.selectBySyncProjectIds(projectIds);
        Map<Long, SyncResult> syncResultMap = syncResults.stream()
                .collect(Collectors.toMap(SyncResult::getSyncProjectId, result -> result));

        // Build DTOs with pre-fetched data
        return projects.stream()
                .map(project -> buildProjectListDTO(project, sourceInfoMap, taskMap, syncResultMap))
                .collect(Collectors.toList());
    }

    /**
     * Build project list DTO with pre-fetched related data
     * This method is optimized for batch operations
     */
    public ProjectListDTO buildProjectListDTO(SyncProject project,
                                             Map<Long, SourceProjectInfo> sourceInfoMap,
                                             Map<Long, SyncTask> taskMap,
                                             Map<Long, SyncResult> syncResultMap) {
        ProjectListDTO dto = new ProjectListDTO();
        dto.setId(project.getId());
        dto.setProjectKey(project.getProjectKey());
        dto.setSyncStatus(project.getSyncStatus());
        dto.setSyncMethod(project.getSyncMethod());
        dto.setLastSyncAt(project.getLastSyncAt());

        // Get group path and last commit time from source project info
        SourceProjectInfo sourceInfo = null;
        if (sourceInfoMap != null) {
            sourceInfo = sourceInfoMap.get(project.getId());
        } else {
            QueryWrapper<SourceProjectInfo> sourceQuery = new QueryWrapper<>();
            sourceQuery.eq("sync_project_id", project.getId());
            sourceInfo = sourceProjectInfoMapper.selectOne(sourceQuery);
        }
        if (sourceInfo != null) {
            dto.setGroupPath(sourceInfo.getGroupPath());
            dto.setLastCommitTime(sourceInfo.getLastActivityAt());
        }

        // Get consecutive failures from task
        SyncTask task = null;
        if (taskMap != null) {
            task = taskMap.get(project.getId());
        } else {
            task = syncTaskService.getTaskBySyncProjectId(project.getId());
        }
        if (task != null) {
            dto.setConsecutiveFailures(task.getConsecutiveFailures());
            dto.setTaskStatus(task.getTaskStatus());
            // Convert Instant to LocalDateTime for last check time
            if (task.getLastRunAt() != null) {
                dto.setLastCheckAt(LocalDateTime.ofInstant(
                    task.getLastRunAt(),
                    java.time.ZoneId.systemDefault()
                ));
            }
        } else {
            dto.setConsecutiveFailures(0);
            dto.setTaskStatus(null);
            dto.setLastCheckAt(null);
        }

        // Get last sync status, summary and error message from sync result
        SyncResult syncResult = null;
        if (syncResultMap != null) {
            syncResult = syncResultMap.get(project.getId());
        } else {
            syncResult = syncResultMapper.selectBySyncProjectId(project.getId());
        }
        if (syncResult != null) {
            dto.setLastSyncStatus(syncResult.getSyncStatus());
            dto.setLastSyncSummary(syncResult.getSummary());
            dto.setLastSyncErrorMessage(syncResult.getErrorMessage());
        } else {
            dto.setLastSyncStatus(null);
        }

        // Calculate diff using DiffCalculator (not legacy method)
        ProjectListDTO.DiffInfo diff = calculateDiffWithCalculator(project);
        dto.setDiff(diff);

        // Calculate delay
        Long delaySeconds = calculateDelay(project.getId());
        dto.setDelaySeconds(delaySeconds);
        dto.setDelayFormatted(formatDelay(delaySeconds));

        return dto;
    }

    /**
     * Calculate diff for project list using DiffCalculator
     */
    private ProjectListDTO.DiffInfo calculateDiffWithCalculator(SyncProject project) {
        ProjectListDTO.DiffInfo diff = new ProjectListDTO.DiffInfo();

        try {
            // Use DiffCalculator for accurate status detection
            ProjectDiff projectDiff = diffCalculator.calculateDiff(project.getId(), true);

            if (projectDiff != null) {
                // Set diff status first
                diff.setDiffStatus(projectDiff.getStatus() != null ? projectDiff.getStatus().name() : "UNKNOWN");

                // Set diff details if available
                if (projectDiff.getDiff() != null) {
                    DiffDetails details = projectDiff.getDiff();
                    DiffDetails.BranchComparisonSummary summary = details.getBranchSummary();

                    if (summary != null) {
                        diff.setBranchNew(summary.getMissingInTargetCount());
                        diff.setBranchDeleted(summary.getExtraInTargetCount());
                        diff.setBranchOutdated(summary.getOutdatedCount());
                        // Don't set ahead/diverged in list view to keep it simple
                    } else {
                        // No branch summary (e.g., source missing) - set defaults
                        diff.setBranchNew(0);
                        diff.setBranchDeleted(0);
                        diff.setBranchOutdated(0);
                    }

                    diff.setCommitDiff(details.getCommitBehind() != null ? details.getCommitBehind() : 0);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to calculate diff using DiffCalculator for project {}, falling back to legacy: {}",
                project.getId(), e.getMessage());
            // Fallback to legacy calculation
            ProjectListDTO.DiffInfo legacyDiff = calculateDiff(project.getId());
            diff.setBranchNew(legacyDiff.getBranchNew());
            diff.setBranchDeleted(legacyDiff.getBranchDeleted());
            diff.setBranchOutdated(legacyDiff.getBranchOutdated());
            diff.setCommitDiff(legacyDiff.getCommitDiff());
        }

        return diff;
    }

    /**
     * Calculate diff for project (legacy method, kept for fallback)
     * @deprecated Use calculateDiffWithCalculator instead
     */
    @Deprecated
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
     * <p>
     * Calculate based on branch update times (committed_at) instead of project activity time
     * to focus on content differences
     */
    private Long calculateDelay(Long syncProjectId) {
        try {
            // Get latest commit time from source branches
            LocalDateTime sourceLatestCommit = projectBranchSnapshotMapper.selectList(
                    new QueryWrapper<ProjectBranchSnapshot>()
                            .eq("sync_project_id", syncProjectId)
                            .eq("project_type", "source")
                            .orderByDesc("committed_at")
                            .last("LIMIT 1")
            ).stream()
                    .findFirst()
                    .map(ProjectBranchSnapshot::getCommittedAt)
                    .orElse(null);

            // Get latest commit time from target branches
            LocalDateTime targetLatestCommit = projectBranchSnapshotMapper.selectList(
                    new QueryWrapper<ProjectBranchSnapshot>()
                            .eq("sync_project_id", syncProjectId)
                            .eq("project_type", "target")
                            .orderByDesc("committed_at")
                            .last("LIMIT 1")
            ).stream()
                    .findFirst()
                    .map(ProjectBranchSnapshot::getCommittedAt)
                    .orElse(null);

            // Calculate delay based on branch commit times
            if (sourceLatestCommit != null && targetLatestCommit != null) {
                Duration duration = Duration.between(targetLatestCommit, sourceLatestCommit);
                return Math.max(0, duration.getSeconds()); // Ensure non-negative
            } else if (sourceLatestCommit != null) {
                // Target has no commits yet, calculate delay from source latest commit
                Duration duration = Duration.between(sourceLatestCommit, LocalDateTime.now());
                return Math.max(0, duration.getSeconds());
            }
        } catch (Exception e) {
            log.warn("Failed to calculate delay for project {}: {}", syncProjectId, e.getMessage());
        }

        // Return null when no branch/commit information available
        return null;
    }

    /**
     * Format delay seconds to human-readable string
     */
    private String formatDelay(Long seconds) {
        if (seconds == null) {
            return null;  // Return null when no branch/commit data available
        }

        if (seconds < 0) {
            return "0s";
        }

        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + "m";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            return hours + "h";
        } else {
            long days = seconds / 86400;
            return days + "d";
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
            if (projectDiff != null) {
                // Always set diffStatus first
                diff.setDiffStatus(projectDiff.getStatus() != null ? projectDiff.getStatus().name() : "UNKNOWN");

                // Set diff details if available
                if (projectDiff.getDiff() != null) {
                    DiffDetails details = projectDiff.getDiff();
                    DiffDetails.BranchComparisonSummary summary = details.getBranchSummary();

                    if (summary != null) {
                        diff.setBranchNew(summary.getMissingInTargetCount());
                        diff.setBranchDeleted(summary.getExtraInTargetCount());
                        diff.setBranchOutdated(summary.getOutdatedCount());
                        diff.setBranchAhead(summary.getAheadCount());
                        diff.setBranchDiverged(summary.getDivergedCount());
                    } else {
                        // No branch summary (e.g., source missing) - set defaults
                        diff.setBranchNew(0);
                        diff.setBranchDeleted(0);
                        diff.setBranchOutdated(0);
                        diff.setBranchAhead(0);
                        diff.setBranchDiverged(0);
                    }

                    diff.setCommitDiff(details.getCommitBehind() != null ? details.getCommitBehind() : 0);
                }
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
                taskInfo.setHasChanges(task.getHasChanges());
                taskInfo.setChangesCount(task.getChangesCount());

                // Build last sync summary from recent events
                String summary = buildLastSyncSummary(projectId, task);
                taskInfo.setLastSyncSummary(summary);
            }
        } catch (Exception e) {
            log.warn("Failed to get task info for project {}: {}", projectId, e.getMessage());
        }

        return taskInfo;
    }

    /**
     * Build last sync summary from recent sync events
     */
    private String buildLastSyncSummary(Long projectId, SyncTask task) {
        try {
            // Query recent sync_finished events
            QueryWrapper<com.gitlab.mirror.server.entity.SyncEvent> query = new QueryWrapper<>();
            query.eq("sync_project_id", projectId);
            query.eq("event_type", "sync_finished");
            query.orderByDesc("event_time");
            query.last("LIMIT 1");

            com.gitlab.mirror.server.entity.SyncEvent lastEvent = syncEventMapper.selectOne(query);

            if (lastEvent == null) {
                return "无同步记录";
            }

            // Build summary based on event data
            StringBuilder summary = new StringBuilder();

            // Check if sync was skipped
            if (lastEvent.getErrorMessage() != null &&
                (lastEvent.getErrorMessage().toLowerCase().contains("skipped") ||
                 lastEvent.getErrorMessage().toLowerCase().contains("no changes") ||
                 lastEvent.getErrorMessage().toLowerCase().contains("跳过"))) {
                summary.append("✓ 跳过同步 (无变更)");
            } else if ("success".equals(lastEvent.getStatus())) {
                // Successful sync with changes
                summary.append("✓ 同步成功");

                if (task.getChangesCount() != null && task.getChangesCount() > 0) {
                    summary.append(" - ").append(task.getChangesCount()).append(" 个变更");
                }

                if (lastEvent.getBranchName() != null) {
                    summary.append(" (分支: ").append(lastEvent.getBranchName()).append(")");
                }
            } else if ("failed".equals(lastEvent.getStatus())) {
                summary.append("✗ 同步失败");
                if (lastEvent.getErrorMessage() != null && !lastEvent.getErrorMessage().isEmpty()) {
                    String errorMsg = lastEvent.getErrorMessage();
                    if (errorMsg.length() > 50) {
                        errorMsg = errorMsg.substring(0, 50) + "...";
                    }
                    summary.append(": ").append(errorMsg);
                }
            } else {
                summary.append("状态: ").append(lastEvent.getStatus());
            }

            // Add duration if available
            if (lastEvent.getDurationSeconds() != null && lastEvent.getDurationSeconds() > 0) {
                summary.append(" (耗时: ").append(lastEvent.getDurationSeconds()).append("秒)");
            }

            return summary.toString();
        } catch (Exception e) {
            log.warn("Failed to build sync summary for project {}: {}", projectId, e.getMessage());
            return "无法获取同步摘要";
        }
    }
}
