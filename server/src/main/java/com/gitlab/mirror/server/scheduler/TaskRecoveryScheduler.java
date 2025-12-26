package com.gitlab.mirror.server.scheduler;

import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.mapper.SyncTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Task Recovery Scheduler
 * <p>
 * Recovers stuck tasks that have been in running state for too long
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Component
public class TaskRecoveryScheduler {

    private final SyncTaskMapper syncTaskMapper;

    // Timeout threshold: tasks running longer than this will be recovered (in minutes)
    private static final long TASK_TIMEOUT_MINUTES = 30;

    public TaskRecoveryScheduler(SyncTaskMapper syncTaskMapper) {
        this.syncTaskMapper = syncTaskMapper;
    }

    /**
     * Check for stuck tasks every 5 minutes
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void recoverStuckTasks() {
        log.debug("Task recovery check started");

        try {
            // Find tasks stuck in running state
            QueryWrapper<SyncTask> query = new QueryWrapper<>();
            query.eq("task_status", "running");

            List<SyncTask> runningTasks = syncTaskMapper.selectList(query);

            if (runningTasks.isEmpty()) {
                log.debug("No running tasks found");
                return;
            }

            log.info("Found {} running tasks, checking for stuck tasks", runningTasks.size());

            int recovered = 0;
            Instant now = Instant.now();

            for (SyncTask task : runningTasks) {
                try {
                    // Check if task has been running for too long
                    if (task.getStartedAt() != null) {
                        long minutesRunning = ChronoUnit.MINUTES.between(task.getStartedAt(), now);

                        if (minutesRunning > TASK_TIMEOUT_MINUTES) {
                            log.warn("Recovering stuck task: taskId={}, projectId={}, runningFor={}min",
                                    task.getId(), task.getSyncProjectId(), minutesRunning);

                            // Reset task to waiting state with error message
                            task.setTaskStatus("waiting");
                            task.setErrorMessage(String.format(
                                    "Task timed out after %d minutes - auto-recovered", minutesRunning));
                            task.setErrorType("TIMEOUT");
                            task.setStartedAt(null);
                            task.setCompletedAt(null);

                            // Increase consecutive failures but don't disable
                            if (task.getConsecutiveFailures() == null) {
                                task.setConsecutiveFailures(0);
                            }
                            task.setConsecutiveFailures(task.getConsecutiveFailures() + 1);

                            syncTaskMapper.updateById(task);
                            recovered++;

                            log.info("Task recovered: taskId={}, projectId={}",
                                    task.getId(), task.getSyncProjectId());
                        }
                    } else {
                        // Task is running but has no start time - this is abnormal
                        log.warn("Task has no start time but is in running state: taskId={}, projectId={}",
                                task.getId(), task.getSyncProjectId());

                        task.setTaskStatus("waiting");
                        task.setErrorMessage("Task was in running state without start time - auto-recovered");
                        task.setErrorType("INVALID_STATE");
                        syncTaskMapper.updateById(task);
                        recovered++;
                    }

                } catch (Exception e) {
                    log.error("Failed to recover task: taskId={}", task.getId(), e);
                }
            }

            if (recovered > 0) {
                log.info("Task recovery completed, recovered={} out of {} running tasks",
                        recovered, runningTasks.size());
            } else {
                log.debug("All {} running tasks are healthy", runningTasks.size());
            }

        } catch (Exception e) {
            log.error("Task recovery check failed", e);
        }
    }
}
