package com.gitlab.mirror.server.controller.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Project List DTO
 * <p>
 * DTO for project list API response with diff and delay information
 *
 * @author GitLab Mirror Team
 */
@Data
public class ProjectListDTO {

    /**
     * Sync project ID
     */
    private Long id;

    /**
     * Project key (path)
     */
    private String projectKey;

    /**
     * Sync status (synced/syncing/outdated/paused/failed/pending)
     */
    private String syncStatus;

    /**
     * Sync method (pull_sync/push_mirror)
     */
    private String syncMethod;

    /**
     * Last sync time
     */
    private LocalDateTime lastSyncAt;

    /**
     * Last update time
     */
    private LocalDateTime updatedAt;

    /**
     * Last sync status (success/failed/skipped)
     */
    private String lastSyncStatus;

    /**
     * Consecutive failures count
     */
    private Integer consecutiveFailures;

    /**
     * Project diff statistics
     */
    private DiffInfo diff;

    /**
     * Delay in seconds
     */
    private Long delaySeconds;

    /**
     * Formatted delay string
     */
    private String delayFormatted;

    /**
     * Group path
     */
    private String groupPath;

    /**
     * Diff info
     */
    @Data
    public static class DiffInfo {
        /**
         * Diff status (SYNCED/OUTDATED/AHEAD/DIVERGED/SOURCE_MISSING/PENDING/UNKNOWN)
         */
        private String diffStatus;

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
}
