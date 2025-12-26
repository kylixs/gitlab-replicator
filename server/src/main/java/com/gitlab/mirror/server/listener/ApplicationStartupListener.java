package com.gitlab.mirror.server.listener;

import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.mapper.SyncTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Application Startup Listener
 * <p>
 * Performs cleanup and initialization tasks when the application starts:
 * 1. Resets running tasks to waiting state
 * 2. Resets syncing projects to active state
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Component
public class ApplicationStartupListener implements ApplicationListener<ApplicationReadyEvent> {

    private final SyncTaskMapper syncTaskMapper;
    private final SyncProjectMapper syncProjectMapper;

    public ApplicationStartupListener(SyncTaskMapper syncTaskMapper, SyncProjectMapper syncProjectMapper) {
        this.syncTaskMapper = syncTaskMapper;
        this.syncProjectMapper = syncProjectMapper;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("=== Application Startup Initialization ===");

        // Execute initialization tasks
        resetRunningTasks();
        resetSyncingProjects();

        log.info("=== Application Startup Initialization Completed ===");
    }

    /**
     * Reset tasks stuck in running state to waiting
     */
    private void resetRunningTasks() {
        log.info("Checking for running tasks to reset...");

        try {
            // Find all tasks in running state
            QueryWrapper<SyncTask> query = new QueryWrapper<>();
            query.eq("task_status", "running");
            List<SyncTask> runningTasks = syncTaskMapper.selectList(query);

            if (runningTasks.isEmpty()) {
                log.info("No running tasks found - system state is clean");
                return;
            }

            log.warn("Found {} tasks in running state from previous session - resetting to waiting",
                    runningTasks.size());

            int resetCount = 0;
            for (SyncTask task : runningTasks) {
                try {
                    log.info("Resetting task: taskId={}, projectId={}, status={}",
                            task.getId(), task.getSyncProjectId(), task.getTaskStatus());

                    // Reset task to waiting state
                    task.setTaskStatus("waiting");
                    task.setErrorMessage("Task was interrupted due to service restart - reset to waiting");
                    task.setErrorType("SERVICE_RESTART");
                    task.setStartedAt(null);
                    task.setCompletedAt(null);

                    syncTaskMapper.updateById(task);
                    resetCount++;

                    log.info("Task reset successfully: taskId={}, projectId={}",
                            task.getId(), task.getSyncProjectId());

                } catch (Exception e) {
                    log.error("Failed to reset task: taskId={}", task.getId(), e);
                }
            }

            log.info("Task reset completed - reset {} out of {} running tasks",
                    resetCount, runningTasks.size());

        } catch (Exception e) {
            log.error("Task reset failed", e);
        }
    }

    /**
     * Reset projects stuck in syncing state to active
     */
    private void resetSyncingProjects() {
        log.info("Checking for syncing projects to reset...");

        try {
            // Find all projects in syncing state
            QueryWrapper<SyncProject> query = new QueryWrapper<>();
            query.eq("sync_status", SyncProject.SyncStatus.SYNCING);
            List<SyncProject> syncingProjects = syncProjectMapper.selectList(query);

            if (syncingProjects.isEmpty()) {
                log.info("No syncing projects found - system state is clean");
                return;
            }

            log.warn("Found {} projects in syncing state from previous session - resetting to active",
                    syncingProjects.size());

            int resetCount = 0;
            for (SyncProject project : syncingProjects) {
                try {
                    log.info("Resetting project: projectKey={}, status={}",
                            project.getProjectKey(), project.getSyncStatus());

                    // Reset to active state
                    project.setSyncStatus(SyncProject.SyncStatus.ACTIVE);
                    syncProjectMapper.updateById(project);
                    resetCount++;

                    log.info("Project reset successfully: projectKey={}", project.getProjectKey());

                } catch (Exception e) {
                    log.error("Failed to reset project: projectKey={}", project.getProjectKey(), e);
                }
            }

            log.info("Project reset completed - reset {} out of {} syncing projects",
                    resetCount, syncingProjects.size());

        } catch (Exception e) {
            log.error("Project reset failed", e);
        }
    }
}
