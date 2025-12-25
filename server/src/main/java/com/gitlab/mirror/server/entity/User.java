package com.gitlab.mirror.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * User Entity
 * <p>
 * User account table with SCRAM-SHA-256 credentials
 *
 * @author GitLab Mirror Team
 */
@Data
@TableName("users")
public class User {

    /**
     * Primary key ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Username (unique identifier)
     */
    @TableField("username")
    private String username;

    /**
     * SCRAM StoredKey (hex encoded, 64 characters)
     * StoredKey = SHA256(ClientKey)
     */
    @TableField("stored_key")
    private String storedKey;

    /**
     * Salt for PBKDF2 (16 bytes hex encoded, 32 characters)
     */
    @TableField("salt")
    private String salt;

    /**
     * PBKDF2 iteration count (default: 4096)
     */
    @TableField("iterations")
    private Integer iterations;

    /**
     * Display name for UI
     */
    @TableField("display_name")
    private String displayName;

    /**
     * Whether the account is enabled
     */
    @TableField("enabled")
    private Boolean enabled;

    /**
     * Creation timestamp
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Last update timestamp
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
