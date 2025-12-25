package com.gitlab.mirror.server.service.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ChallengeInfo Model
 * <p>
 * In-memory data structure for storing challenge codes
 * (Not a database entity)
 *
 * @author GitLab Mirror Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeInfo {

    /**
     * Username associated with the challenge
     */
    private String username;

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
     * Challenge creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Challenge expiration timestamp
     */
    private LocalDateTime expiresAt;

    /**
     * Whether the challenge has been used (single-use)
     */
    private boolean used;
}
