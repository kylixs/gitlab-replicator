package com.gitlab.mirror.server.api.dto;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Task DTO
 *
 * @author GitLab Mirror Team
 */
@Data
public class TaskDTO {

    private Long id;

    private Long syncProjectId;

    private String projectKey;

    private String taskType;

    private String taskStatus;

    private Instant nextRunAt;

    private Instant lastRunAt;

    private String lastSyncStatus;

    private Integer consecutiveFailures;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // Sync project metadata
    private String syncMethod;

    private Boolean enabled;

    // Pull sync specific
    private String priority;
}
