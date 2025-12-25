package com.gitlab.mirror.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AuthToken Entity
 * <p>
 * Authentication token table for session management
 *
 * @author GitLab Mirror Team
 */
@Data
@TableName("auth_tokens")
public class AuthToken {

    /**
     * Primary key ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Token value (UUID, 64 characters)
     */
    @TableField("token")
    private String token;

    /**
     * User ID (foreign key to users.id)
     */
    @TableField("user_id")
    private Long userId;

    /**
     * Token creation timestamp
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Token expiration timestamp
     */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /**
     * Last used timestamp
     */
    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;
}
