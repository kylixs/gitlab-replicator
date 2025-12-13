package com.gitlab.mirror.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Push Mirror Config Entity
 * <p>
 * Stores Push Mirror specific configuration and status
 *
 * @author GitLab Mirror Team
 */
@Data
@TableName("push_mirror_config")
public class PushMirrorConfig {

    /**
     * Primary key ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Associated sync project ID (unique)
     */
    @TableField("sync_project_id")
    private Long syncProjectId;

    /**
     * GitLab Remote Mirror ID (returned by API)
     */
    @TableField("gitlab_mirror_id")
    private Long gitlabMirrorId;

    /**
     * Mirror URL (without token)
     */
    @TableField("mirror_url")
    private String mirrorUrl;

    /**
     * Last update status
     * Values: finished, failed, started, pending
     */
    @TableField("last_update_status")
    private String lastUpdateStatus;

    /**
     * Last update timestamp
     */
    @TableField("last_update_at")
    private LocalDateTime lastUpdateAt;

    /**
     * Last successful update timestamp
     */
    @TableField("last_successful_update_at")
    private LocalDateTime lastSuccessfulUpdateAt;

    /**
     * Consecutive failure count
     */
    @TableField("consecutive_failures")
    private Integer consecutiveFailures;

    /**
     * Error message
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * Created time (auto-fill on insert)
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Updated time (auto-fill on insert and update)
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * Mirror update status constants
     */
    public static class UpdateStatus {
        public static final String FINISHED = "finished";
        public static final String FAILED = "failed";
        public static final String STARTED = "started";
        public static final String PENDING = "pending";
    }
}
