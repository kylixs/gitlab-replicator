package com.gitlab.mirror.common.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Challenge Response
 * <p>
 * Response model containing authentication challenge information
 *
 * @author GitLab Mirror Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeResponse {

    /**
     * Challenge code (UUID)
     */
    private String challenge;

    /**
     * Salt value (hex encoded)
     */
    private String salt;

    /**
     * PBKDF2 iteration count
     */
    private Integer iterations;

    /**
     * Challenge expiration timestamp
     */
    private LocalDateTime expiresAt;
}
