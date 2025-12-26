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
         * Total commit difference (source ahead of target)
         */
        private Integer commitDiff = 0;
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
}
