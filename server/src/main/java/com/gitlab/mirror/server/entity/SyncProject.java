package com.gitlab.mirror.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Sync Project Entity
 * <p>
 * Core table managing projects that need to be synchronized
 *
 * @author GitLab Mirror Team
 */
@Data
@TableName("sync_project")
public class SyncProject {

    /**
     * Primary key ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Project unique key (source project path)
     * Example: "group1/project-a"
     */
    @TableField("project_key")
    private String projectKey;

    /**
     * Sync method
     * Values: push_mirror, pull_mirror, clone_push
     */
    @TableField("sync_method")
    private String syncMethod;

    /**
     * Sync status
     * Values: pending, target_created, mirror_configured, active, failed
     */
    @TableField("sync_status")
    private String syncStatus;

    /**
     * Whether sync is enabled
     */
    @TableField("enabled")
    private Boolean enabled;

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
     * Last successful sync time
     */
    @TableField("last_sync_at")
    private LocalDateTime lastSyncAt;

    /**
     * Sync method constants
     */
    public static class SyncMethod {
        public static final String PUSH_MIRROR = "push_mirror";
        public static final String PULL_MIRROR = "pull_mirror";
        public static final String CLONE_PUSH = "clone_push";
    }

    /**
     * Sync status constants
     */
    public static class SyncStatus {
        public static final String PENDING = "pending";
        public static final String TARGET_CREATED = "target_created";
        public static final String MIRROR_CONFIGURED = "mirror_configured";
        public static final String ACTIVE = "active";
        public static final String SYNCING = "syncing";
        public static final String FAILED = "failed";
        public static final String DELETED = "deleted";
        public static final String MISSING = "missing";

        // Legacy constant for compatibility - will be removed in future version
        @Deprecated
        public static final String SOURCE_NOT_FOUND = "source_not_found";
    }
}
