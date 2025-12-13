package com.gitlab.mirror.server.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Project DTO for API responses
 *
 * @author GitLab Mirror Team
 */
@Data
public class ProjectDTO {
    private Long id;
    private String projectKey;
    private String syncMethod;
    private String syncStatus;
    private Boolean enabled;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
