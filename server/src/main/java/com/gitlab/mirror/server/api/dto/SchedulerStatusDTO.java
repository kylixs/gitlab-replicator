package com.gitlab.mirror.server.api.dto;

import lombok.Data;

import java.time.Instant;

/**
 * Scheduler Status DTO
 *
 * @author GitLab Mirror Team
 */
@Data
public class SchedulerStatusDTO {

    private Boolean enabled;

    private Boolean isPeakHours;

    private Integer peakConcurrency;

    private Integer offPeakConcurrency;

    private Instant lastScheduleTime;

    private Integer lastScheduledCount;

    private Integer activeTasksCount;

    private Integer queuedTasksCount;

    private String peakHoursRange;
}
