package com.gitlab.mirror.server.service.monitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Alert Information
 * <p>
 * Represents an alert generated when a project difference exceeds configured thresholds.
 *
 * @author GitLab Mirror Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertInfo {

    /**
     * Project key (unique identifier)
     */
    private String projectKey;

    /**
     * Sync project ID
     */
    private Long syncProjectId;

    /**
     * Alert type
     */
    private AlertType alertType;

    /**
     * Severity level
     */
    private Severity severity;

    /**
     * Alert message
     */
    private String message;

    /**
     * Current sync status
     */
    private ProjectDiff.SyncStatus syncStatus;

    /**
     * Alert Type Enum
     */
    public enum AlertType {
        /**
         * Sync delay exceeds threshold
         */
        SYNC_DELAY,

        /**
         * Commit difference exceeds threshold
         */
        COMMIT_DIFF,

        /**
         * Branch count mismatch
         */
        BRANCH_DIFF,

        /**
         * Repository size difference exceeds tolerance
         */
        SIZE_DIFF,

        /**
         * Target project does not exist
         */
        TARGET_MISSING
    }

    /**
     * Severity Level Enum
     */
    public enum Severity {
        /**
         * Critical - requires immediate attention
         */
        CRITICAL,

        /**
         * High - important issue
         */
        HIGH,

        /**
         * Medium - moderate issue
         */
        MEDIUM,

        /**
         * Low - minor issue
         */
        LOW
    }
}
