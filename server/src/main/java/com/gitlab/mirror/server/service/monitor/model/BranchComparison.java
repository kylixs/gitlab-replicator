package com.gitlab.mirror.server.service.monitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Branch Comparison
 * <p>
 * Represents the comparison between a branch in source and target GitLab.
 *
 * @author GitLab Mirror Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchComparison {

    /**
     * Branch name
     */
    private String branchName;

    /**
     * Source commit SHA (null if branch doesn't exist in source)
     */
    private String sourceCommitSha;

    /**
     * Target commit SHA (null if branch doesn't exist in target)
     */
    private String targetCommitSha;

    /**
     * Sync status of this branch
     */
    private BranchSyncStatus status;

    /**
     * Is this the default branch?
     */
    private boolean isDefault;

    /**
     * Is this a protected branch?
     */
    private boolean isProtected;

    /**
     * Source commit timestamp
     */
    private java.time.LocalDateTime sourceCommittedAt;

    /**
     * Target commit timestamp
     */
    private java.time.LocalDateTime targetCommittedAt;

    /**
     * Commit time difference in seconds (target - source)
     * Positive value means target is newer, negative means source is newer
     */
    private Long commitTimeDiffSeconds;

    /**
     * Branch Sync Status
     */
    public enum BranchSyncStatus {
        /**
         * Synced: Branch exists in both and commit SHA matches
         */
        SYNCED,

        /**
         * Outdated: Branch exists in both, target is behind source
         */
        OUTDATED,

        /**
         * Ahead: Branch exists in both, target is ahead of source
         */
        AHEAD,

        /**
         * Diverged: Branch exists in both, source and target have diverged
         */
        DIVERGED,

        /**
         * Missing in target: Branch exists in source but not in target
         */
        MISSING_IN_TARGET,

        /**
         * Extra in target: Branch exists in target but not in source (orphaned)
         */
        EXTRA_IN_TARGET
    }

    /**
     * Check if commits match
     */
    public boolean isCommitMatch() {
        return sourceCommitSha != null && sourceCommitSha.equals(targetCommitSha);
    }

    /**
     * Get shortened commit SHAs for display (first 8 chars)
     */
    public String getShortSourceSha() {
        return sourceCommitSha != null && sourceCommitSha.length() >= 8
            ? sourceCommitSha.substring(0, 8)
            : sourceCommitSha;
    }

    public String getShortTargetSha() {
        return targetCommitSha != null && targetCommitSha.length() >= 8
            ? targetCommitSha.substring(0, 8)
            : targetCommitSha;
    }
}
