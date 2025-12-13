package com.gitlab.mirror.server.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gitlab.mirror.server.api.dto.*;
import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.mapper.SyncTaskMapper;
import com.gitlab.mirror.server.scheduler.UnifiedSyncScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalTime;
import java.util.concurrent.Executor;

/**
 * Scheduler Control API Controller
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@RestController
@RequestMapping("/api/scheduler")
@RequiredArgsConstructor
public class SchedulerController {

    private final UnifiedSyncScheduler scheduler;
    private final SyncTaskMapper syncTaskMapper;
    private final GitLabMirrorProperties properties;
    private final Executor syncTaskExecutor;

    /**
     * Get scheduler status
     *
     * GET /api/scheduler/status
     */
    @GetMapping("/status")
    public ApiResponse<SchedulerStatusDTO> getSchedulerStatus() {
        log.info("Getting scheduler status");

        SchedulerStatusDTO status = new SchedulerStatusDTO();

        // Enabled status
        status.setEnabled(properties.getSync().getEnabled());

        // Peak hours detection
        boolean isPeakHours = isPeakHours();
        status.setIsPeakHours(isPeakHours);
        status.setPeakHoursRange(properties.getSync().getPeakHours());

        // Concurrency limits
        status.setPeakConcurrency(properties.getSync().getPeakConcurrent());
        status.setOffPeakConcurrency(properties.getSync().getOffPeakConcurrent());

        // Active tasks
        status.setActiveTasksCount(scheduler.getActiveTaskCount());

        // Queued tasks (waiting status)
        Long queuedTasks = syncTaskMapper.selectCount(
                new LambdaQueryWrapper<SyncTask>().eq(SyncTask::getTaskStatus, "waiting")
        );
        status.setQueuedTasksCount(queuedTasks.intValue());

        // Last schedule time (approximate - current minute)
        status.setLastScheduleTime(Instant.now().minusSeconds(Instant.now().getEpochSecond() % 60));
        status.setLastScheduledCount(0); // TODO: Track this in scheduler

        return ApiResponse.success(status);
    }

    /**
     * Manually trigger scheduling
     *
     * POST /api/scheduler/trigger
     * Body: {"taskType": "pull"}
     */
    @PostMapping("/trigger")
    public ApiResponse<String> triggerSchedule(
            @RequestBody(required = false) TriggerScheduleRequest request) {

        log.info("Manually triggering scheduler: taskType={}",
                request != null ? request.getTaskType() : "all");

        try {
            // Trigger Pull task scheduling
            scheduler.schedulePullTasks();

            return ApiResponse.success("Scheduler triggered successfully");

        } catch (Exception e) {
            log.error("Failed to trigger scheduler", e);
            return ApiResponse.error("SCHEDULER_ERROR", "Failed to trigger scheduler: " + e.getMessage());
        }
    }

    /**
     * Get scheduler metrics
     *
     * GET /api/scheduler/metrics
     */
    @GetMapping("/metrics")
    public ApiResponse<SchedulerMetricsDTO> getSchedulerMetrics() {
        log.info("Getting scheduler metrics");

        SchedulerMetricsDTO metrics = new SchedulerMetricsDTO();

        // Total scheduled (tasks with last_run_at set)
        metrics.setTotalScheduled(syncTaskMapper.selectCount(
                new LambdaQueryWrapper<SyncTask>().isNotNull(SyncTask::getLastRunAt)
        ));

        // Pull tasks scheduled
        metrics.setPullTasksScheduled(syncTaskMapper.selectCount(
                new LambdaQueryWrapper<SyncTask>()
                        .eq(SyncTask::getTaskType, "pull")
                        .isNotNull(SyncTask::getLastRunAt)
        ));

        // Push tasks scheduled
        metrics.setPushTasksScheduled(syncTaskMapper.selectCount(
                new LambdaQueryWrapper<SyncTask>()
                        .eq(SyncTask::getTaskType, "push")
                        .isNotNull(SyncTask::getLastRunAt)
        ));

        // Successful executions
        metrics.setSuccessfulExecutions(syncTaskMapper.selectCount(
                new LambdaQueryWrapper<SyncTask>()
                        .eq(SyncTask::getLastSyncStatus, "success")
        ));

        // Failed executions
        metrics.setFailedExecutions(syncTaskMapper.selectCount(
                new LambdaQueryWrapper<SyncTask>()
                        .ge(SyncTask::getConsecutiveFailures, 1)
        ));

        // Average execution time (placeholder - would need tracking)
        metrics.setAverageExecutionTimeMs(0L);

        // Peak/off-peak scheduling counts (placeholder - would need tracking)
        metrics.setPeakSchedulingCount(0L);
        metrics.setOffPeakSchedulingCount(0L);

        return ApiResponse.success(metrics);
    }

    /**
     * Check if current time is in peak hours
     */
    private boolean isPeakHours() {
        String peakHours = properties.getSync().getPeakHours();
        if (peakHours == null || peakHours.isEmpty()) {
            return false;
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
}
