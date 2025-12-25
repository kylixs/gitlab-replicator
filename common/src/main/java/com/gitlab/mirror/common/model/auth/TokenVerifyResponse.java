package com.gitlab.mirror.common.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Token Verify Response
 * <p>
 * Response model for token verification
 *
 * @author GitLab Mirror Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenVerifyResponse {

    /**
     * Whether token is valid
     */
    private boolean valid;

    /**
     * Token expiration timestamp (if valid)
     */
    private LocalDateTime expiresAt;

    /**
     * User information (if valid)
     */
    private UserInfo user;
}
