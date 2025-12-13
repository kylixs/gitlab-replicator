package com.gitlab.mirror.server.api.dto;

import lombok.Data;

/**
 * Trigger Schedule Request DTO
 *
 * @author GitLab Mirror Team
 */
@Data
public class TriggerScheduleRequest {

    private String taskType; // "pull", "push", or null for all
}
