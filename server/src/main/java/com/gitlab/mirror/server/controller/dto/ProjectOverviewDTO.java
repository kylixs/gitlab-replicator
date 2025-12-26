package com.gitlab.mirror.server.controller.dto;

import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Project Overview DTO
 * <p>
 * DTO for project overview API response
 *
 * @author GitLab Mirror Team
 */
@Data
public class ProjectOverviewDTO {

    /**
     * Sync project
     */
    private SyncProject project;

    /**
     * Source project info
     */
    private SourceProjectInfo source;

    /**
     * Target project info
     */
    private TargetProjectInfo target;

    /**
     * Diff statistics
     */
    private DiffInfo diff;

    /**
     * Delay information
     */
    private DelayInfo delay;

    /**
     * Next sync time (estimated)
     */
    private LocalDateTime nextSyncTime;

    /**
     * Cache information
     */
    private CacheInfo cache;

    /**
     * Sync task information
     */
    private TaskInfo task;

    /**
     * Diff info
     */
    @Data
    public static class DiffInfo {
        /**
         * New branches in source (not in target)
         */
        private Integer branchNew = 0;

        /**
         * Deleted branches in source (still in target)
         */
        private Integer branchDeleted = 0;

        /**
         * Branches with commit differences
         */
        private Integer branchOutdated = 0;

        /**
         * Branches where target is ahead of source
         */
        private Integer branchAhead = 0;

        /**
         * Branches where source and target have diverged
         */
        private Integer branchDiverged = 0;

        /**
         * Total commit difference (source ahead of target)
         */
        private Integer commitDiff = 0;

        /**
         * Overall diff status
         */
        private String diffStatus;
    }

    /**
     * Delay info
     */
    @Data
    public static class DelayInfo {
        /**
         * Delay in seconds
         */
        private Long seconds;

        /**
         * Formatted delay string
         */
        private String formatted;
    }

    /**
     * Cache info
     */
    @Data
    public static class CacheInfo {
        /**
         * Local repository path
         */
        private String path;

        /**
         * Cache directory size in bytes
         */
        private Long sizeBytes;

        /**
         * Formatted size string (e.g., "1.5 MB")
         */
        private String sizeFormatted;

        /**
         * Last modified time of the cache directory
         */
        private LocalDateTime lastModified;

        /**
         * Whether cache exists
         */
        private Boolean exists;
    }

    /**
     * Task info
     */
    @Data
    public static class TaskInfo {
        /**
         * Task ID
         */
        private Long id;

        /**
         * Task type: push/pull
         */
        private String taskType;

        /**
         * Task status: waiting/pending/running
         */
        private String taskStatus;

        /**
         * Next run time
         */
        private java.time.Instant nextRunAt;

        /**
         * Last run time
         */
        private java.time.Instant lastRunAt;

        /**
         * Last sync status: success/failed
         */
        private String lastSyncStatus;

        /**
         * Duration of last execution (seconds)
         */
        private Integer durationSeconds;

        /**
         * Consecutive failures count
         */
        private Integer consecutiveFailures;

        /**
         * Error message
         */
        private String errorMessage;
    }
}
