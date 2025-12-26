package com.gitlab.mirror.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Sync Task Entity
 * <p>
 * Unified task table for both Push and Pull sync methods
 * 1:1 relationship with SYNC_PROJECT
 *
 * @author GitLab Mirror Team
 */
@Data
@TableName("sync_task")
public class SyncTask {

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
     * Task type: push/pull
     */
    @TableField("task_type")
    private String taskType;

    /**
     * Task status: waiting/pending/running
     * State cycle: waiting → pending → running → waiting
     */
    @TableField("task_status")
    private String taskStatus;

    /**
     * Next execution time
     */
    @TableField("next_run_at")
    private Instant nextRunAt;

    /**
     * Last execution time
     */
    @TableField("last_run_at")
    private Instant lastRunAt;

    /**
     * Current execution start time
     */
    @TableField("started_at")
    private Instant startedAt;

    /**
     * Current execution completion time
     */
    @TableField("completed_at")
    private Instant completedAt;

    /**
     * Current execution duration (seconds)
     */
    @TableField("duration_seconds")
    private Integer durationSeconds;

    /**
     * Current execution has changes
     */
    @TableField("has_changes")
    private Boolean hasChanges;

    /**
     * Current execution changes count
     */
    @TableField("changes_count")
    private Integer changesCount;

    /**
     * Current execution source commit SHA
     */
    @TableField("source_commit_sha")
    private String sourceCommitSha;

    /**
     * Current execution target commit SHA
     */
    @TableField("target_commit_sha")
    private String targetCommitSha;

    /**
     * Last sync status: success/failed
     */
    @TableField("last_sync_status")
    private String lastSyncStatus;

    /**
     * Error type
     */
    @TableField("error_type")
    private String errorType;

    /**
     * Error message
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * Consecutive failure count
     */
    @TableField("consecutive_failures")
    private Integer consecutiveFailures;

    /**
     * Force sync flag - bypass change detection for manual sync
     */
    @TableField("force_sync")
    private Boolean forceSync;

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
     * Task type constants
     */
    public static class TaskType {
        public static final String PUSH = "push";
        public static final String PULL = "pull";
    }

    /**
     * Task status constants
     */
    public static class TaskStatus {
        public static final String WAITING = "waiting";
        public static final String PENDING = "pending";
        public static final String RUNNING = "running";
    }

    /**
     * Sync status constants
     */
    public static class SyncStatus {
        public static final String SUCCESS = "success";
        public static final String FAILED = "failed";
    }
}
