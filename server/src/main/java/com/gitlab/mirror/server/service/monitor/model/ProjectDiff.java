package com.gitlab.mirror.server.service.monitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Project Diff
 * <p>
 * Represents the difference between source and target GitLab projects.
 * Used for monitoring sync status and detecting anomalies.
 *
 * @author GitLab Mirror Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDiff {

    /**
     * Project key (unique identifier from SYNC_PROJECT)
     */
    private String projectKey;

    /**
     * Sync project ID
     */
    private Long syncProjectId;

    /**
     * Source project snapshot
     */
    private ProjectSnapshot source;

    /**
     * Target project snapshot
     */
    private ProjectSnapshot target;

    /**
     * Diff details
     */
    private DiffDetails diff;

    /**
     * Sync status
     */
    private SyncStatus status;

    /**
     * When this diff was calculated
     */
    private LocalDateTime checkedAt;

    /**
     * Sync Status Enum
     */
    public enum SyncStatus {
        /**
         * Synced: SHA matches and delay < 5 minutes
         */
        SYNCED,

        /**
         * Outdated: SHA doesn't match or delay > 30 minutes (target is behind source)
         */
        OUTDATED,

        /**
         * Ahead: Target is ahead of source (target has newer commits)
         */
        AHEAD,

        /**
         * Diverged: Source and target have diverged (both have independent commits)
         */
        DIVERGED,

        /**
         * Pending: Target project not yet created (newly discovered project)
         */
        PENDING,

        /**
         * Failed: Sync process failed or has errors
         */
        FAILED,

        /**
         * Inconsistent: Branch count or size diff too large
         */
        INCONSISTENT,

        /**
         * Missing: Source project not found or deleted
         */
        MISSING
    }
}
