package com.gitlab.mirror.server.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sync Statistics Model
 * <p>
 * Contains detailed statistics about a sync operation.
 * This model is designed to be stored as JSON in database fields
 * while providing strong typing in Java code.
 *
 * @author GitLab Mirror Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyncStatistics {

    /**
     * Number of branches created (new in target)
     */
    private Integer branchesCreated;

    /**
     * Number of branches updated (existing but with new commits)
     */
    private Integer branchesUpdated;

    /**
     * Number of branches deleted (removed from source)
     */
    private Integer branchesDeleted;

    /**
     * Total number of commits pushed to target
     * (sum of all new commits across all branches)
     */
    private Integer commitsPushed;

    /**
     * Total number of branches after sync
     */
    private Integer totalBranches;

    /**
     * Total number of tags synced (optional, for future use)
     */
    private Integer tagsSynced;

    /**
     * Check if there are any changes
     *
     * @return true if any branches were created, updated, or deleted
     */
    public boolean hasChanges() {
        return (branchesCreated != null && branchesCreated > 0)
            || (branchesUpdated != null && branchesUpdated > 0)
            || (branchesDeleted != null && branchesDeleted > 0);
    }

    /**
     * Get total number of branch changes
     *
     * @return sum of created + updated + deleted branches
     */
    public int getTotalBranchChanges() {
        int total = 0;
        if (branchesCreated != null) total += branchesCreated;
        if (branchesUpdated != null) total += branchesUpdated;
        if (branchesDeleted != null) total += branchesDeleted;
        return total;
    }

    /**
     * Create empty statistics (no changes)
     *
     * @return SyncStatistics with all values set to 0
     */
    public static SyncStatistics empty() {
        return SyncStatistics.builder()
            .branchesCreated(0)
            .branchesUpdated(0)
            .branchesDeleted(0)
            .commitsPushed(0)
            .build();
    }

    /**
     * Parse from Git command output
     *
     * @param output Command output with key=value pairs
     * @return Parsed SyncStatistics
     */
    public static SyncStatistics parseFromGitOutput(String output) {
        SyncStatistics.SyncStatisticsBuilder builder = SyncStatistics.builder();

        if (output == null || output.isEmpty()) {
            return empty();
        }

        String[] lines = output.split("\n");
        for (String line : lines) {
            String[] parts = line.split("=", 2);
            if (parts.length != 2) continue;

            String key = parts[0].trim();
            String value = parts[1].trim();

            try {
                int intValue = Integer.parseInt(value);
                switch (key) {
                    case "BRANCHES_CREATED":
                        builder.branchesCreated(intValue);
                        break;
                    case "BRANCHES_UPDATED":
                        builder.branchesUpdated(intValue);
                        break;
                    case "BRANCHES_DELETED":
                        builder.branchesDeleted(intValue);
                        break;
                    case "COMMITS_PUSHED":
                        builder.commitsPushed(intValue);
                        break;
                }
            } catch (NumberFormatException e) {
                // Skip invalid numbers
            }
        }

        return builder.build();
    }
}
