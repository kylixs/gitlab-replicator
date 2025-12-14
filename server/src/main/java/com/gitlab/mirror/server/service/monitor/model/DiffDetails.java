package com.gitlab.mirror.server.service.monitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
