package com.gitlab.mirror.server.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Pull Sync Configuration DTO
 *
 * @author GitLab Mirror Team
 */
@Data
public class PullSyncConfigDTO {

    private Long id;

    private Long syncProjectId;

    private String projectKey;

    private String priority;

    private Boolean enabled;

    private String localRepoPath;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // Additional metadata
    private String syncStatus;

    private LocalDateTime lastSyncAt;

    private Integer consecutiveFailures;
}
