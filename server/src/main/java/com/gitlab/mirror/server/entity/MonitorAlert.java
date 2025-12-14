package com.gitlab.mirror.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Monitor Alert Entity
 * <p>
 * Stores alert events triggered by monitoring system
 *
 * @author GitLab Mirror Team
 */
@Data
@TableName("monitor_alert")
public class MonitorAlert {

    /**
     * Primary key ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Associated sync project ID
     */
    @TableField("sync_project_id")
    private Long syncProjectId;

    /**
     * Alert type
     * Values: sync_delay, commit_diff, branch_diff, size_diff, target_missing
     */
    @TableField("alert_type")
    private String alertType;

    /**
     * Alert severity
     * Values: critical, high, medium, low
     */
    @TableField("severity")
    private String severity;

    /**
     * Alert title
     */
    @TableField("title")
    private String title;

    /**
     * Alert description
     */
    @TableField("description")
    private String description;

    /**
     * Metadata in JSON format (contains diff details)
     */
    @TableField("metadata")
    private String metadata;

    /**
     * Alert status
     * Values: active, acknowledged, resolved, muted
     */
    @TableField("status")
    private String status;

    /**
     * Triggered timestamp
     */
    @TableField("triggered_at")
    private LocalDateTime triggeredAt;

    /**
     * Resolved timestamp
     */
    @TableField("resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * Created time (auto-fill on insert)
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Updated time (auto-fill on update)
     */
    @TableField(value = "updated_at", fill = FieldFill.UPDATE)
    private LocalDateTime updatedAt;

    /**
     * Alert type constants
     */
    public static class AlertType {
        public static final String SYNC_DELAY = "sync_delay";
        public static final String COMMIT_DIFF = "commit_diff";
        public static final String BRANCH_DIFF = "branch_diff";
        public static final String SIZE_DIFF = "size_diff";
        public static final String TARGET_MISSING = "target_missing";
    }

    /**
     * Severity constants
     */
    public static class Severity {
        public static final String CRITICAL = "critical";
        public static final String HIGH = "high";
        public static final String MEDIUM = "medium";
        public static final String LOW = "low";
    }

    /**
     * Status constants
     */
    public static class Status {
        public static final String ACTIVE = "active";
        public static final String ACKNOWLEDGED = "acknowledged";
        public static final String RESOLVED = "resolved";
        public static final String MUTED = "muted";
    }
}
