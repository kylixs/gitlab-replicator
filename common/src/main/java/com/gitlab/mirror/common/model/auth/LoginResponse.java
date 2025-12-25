package com.gitlab.mirror.common.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Login Response
 * <p>
 * Response model containing authentication token and user info
 *
 * @author GitLab Mirror Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * Authentication token (UUID)
     */
    private String token;

    /**
     * Token expiration timestamp
     */
    private LocalDateTime expiresAt;

    /**
     * User information
     */
    private UserInfo user;
}
