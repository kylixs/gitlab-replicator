package com.gitlab.mirror.common.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API Error
 * <p>
 * Error information model
 *
 * @author GitLab Mirror Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {

    /**
     * Error code
     */
    private String code;

    /**
     * Error message
     */
    private String message;

    /**
     * Retry after seconds (for rate limiting)
     */
    private Integer retryAfter;

    /**
     * Failed attempts count (for account lockout)
     */
    private Integer failedAttempts;
}
