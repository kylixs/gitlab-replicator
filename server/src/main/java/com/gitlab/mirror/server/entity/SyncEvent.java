package com.gitlab.mirror.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.gitlab.mirror.server.model.SyncStatistics;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Sync Event Entity
 * <p>
 * Records all sync-related events for monitoring and analysis
 *
 * @author GitLab Mirror Team
 */
@Data
@TableName(value = "sync_event", autoResultMap = true)
public class SyncEvent {

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
     * Event type
     * Values: push_detected, sync_started, sync_finished, sync_failed, mirror_created, mirror_updated
     */
    @TableField("event_type")
    private String eventType;

    /**
     * Event source
     * Values: webhook, polling, manual, system
     */
    @TableField("event_source")
    private String eventSource;

    /**
     * Event status
     * Values: success, failed, running
     */
    @TableField("status")
    private String status;

    /**
     * Commit SHA
     */
    @TableField("commit_sha")
    private String commitSha;

    /**
     * Git ref (branch/tag)
     */
    @TableField("ref")
    private String ref;

    /**
     * Branch name
     */
    @TableField("branch_name")
    private String branchName;

    /**
     * Duration in seconds
     */
    @TableField("duration_seconds")
    private Integer durationSeconds;

    /**
     * Error message
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * Event detailed data (JSON)
     */
    @TableField(value = "event_data", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> eventData;

    /**
     * Detailed sync statistics (JSON)
     * Contains branch and commit statistics
     */
    @TableField(value = "statistics", typeHandler = JacksonTypeHandler.class)
    private SyncStatistics statistics;

    /**
     * Event time
     */
    @TableField("event_time")
    private LocalDateTime eventTime;

    /**
     * Sync start time
     */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /**
     * Sync completion time
     */
    @TableField("completed_at")
    private LocalDateTime completedAt;

    /**
     * Event type constants
     */
    public static class EventType {
        public static final String PUSH_DETECTED = "push_detected";
        public static final String SYNC_STARTED = "sync_started";
        public static final String SYNC_FINISHED = "sync_finished";
        public static final String SYNC_FAILED = "sync_failed";
        public static final String MIRROR_CREATED = "mirror_created";
        public static final String MIRROR_UPDATED = "mirror_updated";
        public static final String TASK_BLOCKED = "task_blocked";
        public static final String TASK_RECOVERED = "task_recovered";
    }

    /**
     * Event source constants
     */
    public static class EventSource {
        public static final String WEBHOOK = "webhook";
        public static final String POLLING = "polling";
        public static final String MANUAL = "manual";
        public static final String SYSTEM = "system";
    }

    /**
     * Event status constants
     */
    public static class Status {
        public static final String SUCCESS = "success";
        public static final String FAILED = "failed";
        public static final String RUNNING = "running";
    }
}
