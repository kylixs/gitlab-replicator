package com.gitlab.mirror.server.listener;

import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.mapper.SyncTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Startup Task Reset Listener
 * <p>
 * Resets all running tasks to waiting state when the application starts.
 * This prevents tasks that were stuck in running state from previous sessions
 * from blocking new sync operations.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Component
public class StartupTaskResetListener implements ApplicationListener<ApplicationReadyEvent> {

    private final SyncTaskMapper syncTaskMapper;

    public StartupTaskResetListener(SyncTaskMapper syncTaskMapper) {
        this.syncTaskMapper = syncTaskMapper;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("Application started - checking for running tasks to reset");

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

                    // Don't increment consecutive failures - this is not a real failure
                    // Just preserve the existing failure count

                    syncTaskMapper.updateById(task);
                    resetCount++;

                    log.info("Task reset successfully: taskId={}, projectId={}",
                            task.getId(), task.getSyncProjectId());

                } catch (Exception e) {
                    log.error("Failed to reset task: taskId={}", task.getId(), e);
                }
            }

            log.info("Startup task reset completed - reset {} out of {} running tasks",
                    resetCount, runningTasks.size());

        } catch (Exception e) {
            log.error("Startup task reset failed", e);
        }
    }
}
