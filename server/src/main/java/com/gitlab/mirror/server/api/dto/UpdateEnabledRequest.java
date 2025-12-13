package com.gitlab.mirror.server.api.dto;

import lombok.Data;

/**
 * Update Enabled Request DTO
 *
 * @author GitLab Mirror Team
 */
@Data
public class UpdateEnabledRequest {

    private Boolean enabled;
}
