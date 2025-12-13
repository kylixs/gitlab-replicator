package com.gitlab.mirror.server.scheduler;

import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import com.gitlab.mirror.server.entity.PullSyncConfig;
import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.mapper.PullSyncConfigMapper;
import com.gitlab.mirror.server.mapper.SyncTaskMapper;
import com.gitlab.mirror.server.service.PullSyncExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unified Sync Scheduler
 * <p>
 * Schedules both Push Mirror polling and Pull sync tasks
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Component
public class UnifiedSyncScheduler {

    private final SyncTaskMapper syncTaskMapper;
    private final PullSyncConfigMapper pullSyncConfigMapper;
    private final PullSyncExecutorService pullSyncExecutorService;
    private final GitLabMirrorProperties properties;
    private final Executor syncTaskExecutor;

    public UnifiedSyncScheduler(
            SyncTaskMapper syncTaskMapper,
            PullSyncConfigMapper pullSyncConfigMapper,
            PullSyncExecutorService pullSyncExecutorService,
            GitLabMirrorProperties properties,
            @Qualifier("syncTaskExecutor") Executor syncTaskExecutor) {
        this.syncTaskMapper = syncTaskMapper;
        this.pullSyncConfigMapper = pullSyncConfigMapper;
        this.pullSyncExecutorService = pullSyncExecutorService;
        this.properties = properties;
        this.syncTaskExecutor = syncTaskExecutor;
    }

    /**
     * Schedule pull sync tasks every minute
     */
    @Scheduled(cron = "0 * * * * ?")
    public void schedulePullTasks() {
        long startTime = System.currentTimeMillis();

        try {
            // 1. Check if it's peak hours
            boolean isPeakHours = isPeakHours();

            // 2. Calculate available slots
            int availableSlots = getAvailableSlots(isPeakHours);

            if (availableSlots <= 0) {
                log.debug("No available slots for scheduling, peak={}, active={}",
                    isPeakHours, getActiveTaskCount());
                return;
            }

            log.info("Pull task scheduler triggered, peak={}, availableSlots={}, active={}",
                isPeakHours, availableSlots, getActiveTaskCount());

            // 3. Query pending tasks
            List<SyncTask> tasks = queryPendingPullTasks(availableSlots);

            if (tasks.isEmpty()) {
                log.debug("No pending pull tasks to schedule");
                return;
            }

            // 4. Submit tasks for execution
            int scheduled = 0;
            int skipped = 0;

            for (SyncTask task : tasks) {
                try {
                    // Update status: waiting → pending → (executor will set to running)
                    task.setTaskStatus("pending");
                    syncTaskMapper.updateById(task);

                    // Submit to executor (async)
                    submitTaskAsync(task);
                    scheduled++;

                    log.debug("Task scheduled: taskId={}, projectId={}, type={}",
                        task.getId(), task.getSyncProjectId(), task.getTaskType());

                } catch (Exception e) {
                    log.error("Failed to schedule task: taskId={}", task.getId(), e);
                    // Reset status back to waiting
                    task.setTaskStatus("waiting");
                    syncTaskMapper.updateById(task);
                    skipped++;
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Pull task scheduler completed, scheduled={}, skipped={}, duration={}ms",
                scheduled, skipped, duration);

        } catch (Exception e) {
            log.error("Pull task scheduler failed", e);
        }
    }

    /**
     * Check if current time is in peak hours
     *
     * @return true if peak hours
     */
    private boolean isPeakHours() {
        // Parse peak hours from config (format: "9-18")
        String peakHours = properties.getSync().getPeakHours();
        if (peakHours == null || peakHours.isEmpty()) {
            return false; // No peak hours configured
        }

        try {
            String[] parts = peakHours.split("-");
            int startHour = Integer.parseInt(parts[0]);
            int endHour = Integer.parseInt(parts[1]);

            int currentHour = LocalTime.now().getHour();
            return currentHour >= startHour && currentHour < endHour;

        } catch (Exception e) {
            log.warn("Invalid peak hours configuration: {}", peakHours, e);
            return false;
        }
    }

    /**
     * Get available execution slots
     *
     * @param isPeakHours Whether it's peak hours
     * @return Number of available slots
     */
    private int getAvailableSlots(boolean isPeakHours) {
        int maxConcurrent = isPeakHours ?
            properties.getSync().getPeakConcurrent() :
            properties.getSync().getOffPeakConcurrent();

        int active = getActiveTaskCount();
        return Math.max(0, maxConcurrent - active);
    }

    /**
     * Query pending pull tasks ready for execution
     *
     * @param limit Maximum number of tasks to query
     * @return List of pending tasks ordered by priority
     */
    private List<SyncTask> queryPendingPullTasks(int limit) {
        // Query tasks that:
        // 1. task_type = 'pull'
        // 2. task_status = 'waiting'
        // 3. next_run_at <= NOW()
        // 4. Pull config is enabled
        // 5. consecutive_failures < 5
        // Order by priority (critical > high > normal > low) and next_run_at

        return syncTaskMapper.selectPullTasksWithPriority(
                Instant.now(),
                5, // max consecutive failures
                limit
        );
    }

    /**
     * Submit task for async execution
     *
     * @param task Sync task
     */
    private void submitTaskAsync(SyncTask task) {
        syncTaskExecutor.execute(() -> {
            try {
                pullSyncExecutorService.executeSync(task);
            } catch (Exception e) {
                log.error("Task execution failed: taskId={}", task.getId(), e);
            }
        });
    }

    /**
     * Get current active task count from thread pool
     *
     * @return Active task count
     */
    public int getActiveTaskCount() {
        if (syncTaskExecutor instanceof ThreadPoolTaskExecutor) {
            return ((ThreadPoolTaskExecutor) syncTaskExecutor).getActiveCount();
        }
        return 0;
    }
}
