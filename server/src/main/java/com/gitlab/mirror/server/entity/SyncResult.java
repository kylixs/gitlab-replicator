package com.gitlab.mirror.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Sync Result Entity
 * <p>
 * Stores the last sync result for each project (one record per project)
 * This reduces meaningless sync events and provides quick access to last sync status
 *
 * @author GitLab Mirror Team
 */
@Data
@TableName("sync_result")
public class SyncResult {

    /**
     * Primary key ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Associated sync project ID (unique, 1:1 relationship)
     */
    @TableField("sync_project_id")
    private Long syncProjectId;

    /**
     * Last sync time
     */
    @TableField("last_sync_at")
    private LocalDateTime lastSyncAt;

    /**
     * Last sync start time
     */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /**
     * Last sync completion time
     */
    @TableField("completed_at")
    private LocalDateTime completedAt;

    /**
     * Sync status: success/failed/skipped
     */
    @TableField("sync_status")
    private String syncStatus;

    /**
     * Has changes in last sync
     */
    @TableField("has_changes")
    private Boolean hasChanges;

    /**
     * Changes count in last sync
     */
    @TableField("changes_count")
    private Integer changesCount;

    /**
     * Source commit SHA
     */
    @TableField("source_commit_sha")
    private String sourceCommitSha;

    /**
     * Target commit SHA
     */
    @TableField("target_commit_sha")
    private String targetCommitSha;

    /**
     * Execution duration (seconds)
     */
    @TableField("duration_seconds")
    private Integer durationSeconds;

    /**
     * Error message (for failed syncs)
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * Sync summary
     */
    @TableField("summary")
    private String summary;

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
     * Sync status constants
     */
    public static class Status {
        public static final String SUCCESS = "success";
        public static final String FAILED = "failed";
        public static final String SKIPPED = "skipped";
    }
}
