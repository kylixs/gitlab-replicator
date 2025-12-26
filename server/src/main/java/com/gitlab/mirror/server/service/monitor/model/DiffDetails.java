package com.gitlab.mirror.server.service.monitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Diff Details
 * <p>
 * Contains detailed comparison metrics between source and target projects.
 *
 * @author GitLab Mirror Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiffDetails {

    /**
     * Number of commits behind (target vs source)
     * Calculated based on commit count difference
     */
    private Integer commitBehind;

    /**
     * Sync delay in minutes
     * Difference between source and target last_activity_at
     */
    private Long syncDelayMinutes;

    /**
     * Repository size difference percentage
     * Formula: abs(target_size - source_size) / source_size * 100
     */
    private Double sizeDiffPercent;

    /**
     * Branch count difference (target - source)
     * Negative means target has fewer branches
     */
    private Integer branchDiff;

    /**
     * Commit SHA match status
     */
    private boolean commitShaMatches;

    /**
     * Default branch match status
     */
    private boolean defaultBranchMatches;

    /**
     * Detailed branch-level comparison
     * Only populated when detailed diff is requested
     */
    private List<BranchComparison> branchComparisons;

    /**
     * Branch comparison summary
     */
    private BranchComparisonSummary branchSummary;

    /**
     * Branch Comparison Summary
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchComparisonSummary {
        /**
         * Number of branches that are synced (SHA matches)
         */
        private int syncedCount;

        /**
         * Number of branches that are outdated (target is behind source)
         */
        private int outdatedCount;

        /**
         * Number of branches that are ahead (target is ahead of source)
         */
        private int aheadCount;

        /**
         * Number of branches that have diverged (both have independent commits)
         */
        private int divergedCount;

        /**
         * Number of branches missing in target
         */
        private int missingInTargetCount;

        /**
         * Number of extra branches in target (not in source)
         */
        private int extraInTargetCount;

        /**
         * Total number of unique branches across source and target
         */
        private int totalBranchCount;
    }
}
