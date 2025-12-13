package com.gitlab.mirror.server.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Mirror DTO for API responses
 *
 * @author GitLab Mirror Team
 */
@Data
public class MirrorDTO {
    private Long gitlabMirrorId;
    private Long syncProjectId;
    private String projectKey;
    private String mirrorUrl;
    private String lastUpdateStatus;
    private LocalDateTime lastUpdateAt;
    private LocalDateTime lastSuccessfulUpdateAt;
    private Integer consecutiveFailures;
    private String errorMessage;
}
