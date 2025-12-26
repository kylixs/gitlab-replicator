package com.gitlab.mirror.server.scheduler;

import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.mapper.SyncTaskMapper;
import com.gitlab.mirror.server.service.SyncTaskService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Task Management Scheduler
 * <p>
 * Manages sync tasks based on project sync eligibility:
 * 1. Auto-creates sync tasks for enabled projects that don't have tasks
 * 2. Disables or deletes tasks for projects that can't sync (disabled/failed)
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Component
public class TaskManagementScheduler {

    private final SyncProjectMapper syncProjectMapper;
    private final SyncTaskMapper syncTaskMapper;
    private final SyncTaskService syncTaskService;

    public TaskManagementScheduler(
            SyncProjectMapper syncProjectMapper,
            SyncTaskMapper syncTaskMapper,
            SyncTaskService syncTaskService) {
        this.syncProjectMapper = syncProjectMapper;
        this.syncTaskMapper = syncTaskMapper;
        this.syncTaskService = syncTaskService;
    }

    /**
     * Check and manage sync tasks every 10 minutes
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void manageSyncTasks() {
        log.debug("Task management check started");

        try {
            // Auto-create tasks for enabled projects
            autoCreateMissingTasks();

            // Disable tasks for non-syncable projects
            disableTasksForNonSyncableProjects();

            log.debug("Task management check completed");
        } catch (Exception e) {
            log.error("Task management check failed", e);
        }
    }

    /**
     * Auto-create sync tasks for enabled projects that don't have tasks
     */
    private void autoCreateMissingTasks() {
        try {
            // Find all enabled projects
            QueryWrapper<SyncProject> projectQuery = new QueryWrapper<>();
            projectQuery.eq("enabled", true);
            List<SyncProject> enabledProjects = syncProjectMapper.selectList(projectQuery);

            if (enabledProjects.isEmpty()) {
                log.debug("No enabled projects found");
                return;
            }

            log.info("Checking {} enabled projects for missing sync tasks", enabledProjects.size());

            // Get all existing tasks
            List<SyncTask> allTasks = syncTaskMapper.selectList(null);
            Map<Long, SyncTask> taskMap = allTasks.stream()
                    .collect(Collectors.toMap(SyncTask::getSyncProjectId, task -> task, (t1, t2) -> t1));

            int createdCount = 0;
            for (SyncProject project : enabledProjects) {
                try {
                    // Skip if task already exists
                    if (taskMap.containsKey(project.getId())) {
                        continue;
                    }

                    // Create sync task based on sync method
                    String taskType;
                    if ("pull_sync".equals(project.getSyncMethod())) {
                        taskType = "pull";
                    } else if ("push_mirror".equals(project.getSyncMethod())) {
                        taskType = "push";
                    } else {
                        log.warn("Unknown sync method for project {}: {}",
                                project.getProjectKey(), project.getSyncMethod());
                        continue;
                    }

                    log.info("Auto-creating sync task for project: {}, type: {}",
                            project.getProjectKey(), taskType);

                    SyncTask task = syncTaskService.initializeTask(project.getId(), taskType);
                    createdCount++;

                    log.info("Sync task created successfully: taskId={}, projectId={}, projectKey={}",
                            task.getId(), project.getId(), project.getProjectKey());

                } catch (Exception e) {
                    log.error("Failed to create sync task for project: {}", project.getProjectKey(), e);
                }
            }

            if (createdCount > 0) {
                log.info("Auto-created {} sync tasks for enabled projects", createdCount);
            } else {
                log.debug("All enabled projects already have sync tasks");
            }

        } catch (Exception e) {
            log.error("Failed to auto-create missing tasks", e);
        }
    }

    /**
     * Disable tasks for projects that can't sync (disabled or failed)
     */
    private void disableTasksForNonSyncableProjects() {
        try {
            // Find all disabled or failed projects
            QueryWrapper<SyncProject> projectQuery = new QueryWrapper<>();
            projectQuery.and(wrapper -> wrapper
                    .eq("enabled", false)
                    .or()
                    .eq("sync_status", "failed")
                    .or()
                    .eq("sync_status", "error"));
            List<SyncProject> nonSyncableProjects = syncProjectMapper.selectList(projectQuery);

            if (nonSyncableProjects.isEmpty()) {
                log.debug("No non-syncable projects found");
                return;
            }

            log.info("Checking {} non-syncable projects for tasks to disable", nonSyncableProjects.size());

            // Get project IDs
            Set<Long> nonSyncableProjectIds = nonSyncableProjects.stream()
                    .map(SyncProject::getId)
                    .collect(Collectors.toSet());

            // Find tasks for these projects
            QueryWrapper<SyncTask> taskQuery = new QueryWrapper<>();
            taskQuery.in("sync_project_id", nonSyncableProjectIds);
            taskQuery.ne("task_status", "disabled"); // Only find non-disabled tasks
            List<SyncTask> tasksToDisable = syncTaskMapper.selectList(taskQuery);

            if (tasksToDisable.isEmpty()) {
                log.debug("No tasks to disable for non-syncable projects");
                return;
            }

            log.info("Disabling {} tasks for non-syncable projects", tasksToDisable.size());

            int disabledCount = 0;
            for (SyncTask task : tasksToDisable) {
                try {
                    SyncProject project = syncProjectMapper.selectById(task.getSyncProjectId());
                    String reason = project != null && !project.getEnabled()
                            ? "Project sync disabled"
                            : "Project in failed/error state";

                    log.info("Disabling task for non-syncable project: taskId={}, projectId={}, reason={}",
                            task.getId(), task.getSyncProjectId(), reason);

                    // Update task status to disabled
                    task.setTaskStatus("disabled");
                    task.setErrorMessage("Task disabled - " + reason);
                    task.setErrorType("PROJECT_DISABLED");
                    task.setNextRunAt(null);

                    syncTaskMapper.updateById(task);
                    disabledCount++;

                    log.info("Task disabled successfully: taskId={}, projectId={}",
                            task.getId(), task.getSyncProjectId());

                } catch (Exception e) {
                    log.error("Failed to disable task: taskId={}", task.getId(), e);
                }
            }

            log.info("Disabled {} tasks for non-syncable projects", disabledCount);

        } catch (Exception e) {
            log.error("Failed to disable tasks for non-syncable projects", e);
        }
    }
}
