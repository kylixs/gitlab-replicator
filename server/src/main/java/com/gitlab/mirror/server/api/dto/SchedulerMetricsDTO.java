package com.gitlab.mirror.server.api.dto;

import lombok.Data;

/**
 * Scheduler Metrics DTO
 *
 * @author GitLab Mirror Team
 */
@Data
public class SchedulerMetricsDTO {

    private Long totalScheduled;

    private Long pullTasksScheduled;

    private Long pushTasksScheduled;

    private Long successfulExecutions;

    private Long failedExecutions;

    private Long averageExecutionTimeMs;

    private Long peakSchedulingCount;

    private Long offPeakSchedulingCount;
}
