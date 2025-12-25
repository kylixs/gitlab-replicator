package com.gitlab.mirror.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * LoginAuditLog Entity
 * <p>
 * Login audit log for security tracking
 *
 * @author GitLab Mirror Team
 */
@Data
@TableName("login_audit_log")
public class LoginAuditLog {

    /**
     * Primary key ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Username attempting login
     */
    @TableField("username")
    private String username;

    /**
     * Client IP address (supports IPv6)
     */
    @TableField("ip_address")
    private String ipAddress;

    /**
     * User agent string
     */
    @TableField("user_agent")
    private String userAgent;

    /**
     * Login attempt result
     * Enum values: SUCCESS, FAILURE, LOCKED, RATE_LIMITED
     */
    @TableField("login_result")
    private LoginResult loginResult;

    /**
     * Reason for failure (null if successful)
     */
    @TableField("failure_reason")
    private String failureReason;

    /**
     * Log creation timestamp
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Login result enum
     */
    public enum LoginResult {
        SUCCESS,
        FAILURE,
        LOCKED,
        RATE_LIMITED
    }
}
