package com.gitlab.mirror.server.api.dto;

import lombok.Data;

/**
 * Task Statistics DTO
 *
 * @author GitLab Mirror Team
 */
@Data
public class TaskStatsDTO {

    private Long totalTasks;

    private Long pullTasks;

    private Long pushTasks;

    private Long waitingTasks;

    private Long runningTasks;

    private Long completedTasks;

    private Long failedTasks;

    private Long disabledTasks;

    // Pull sync specific
    private Long criticalTasks;

    private Long highPriorityTasks;

    private Long normalPriorityTasks;

    private Long lowPriorityTasks;
}
