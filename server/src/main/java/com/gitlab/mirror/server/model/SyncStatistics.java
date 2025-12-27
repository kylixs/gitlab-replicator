package com.gitlab.mirror.server.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
     * Changed branches (last 5, sorted by commit time desc)
     */
    private List<ChangedBranch> changedBranches;

    /**
     * Changed branch details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChangedBranch {
        private String branchName;
        private String commitSha;
        private String commitTime;
        private String commitTitle;
        private String commitAuthor;
        private String changeType;  // created, updated, deleted
    }

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

        java.util.List<ChangedBranch> changedBranches = new java.util.ArrayList<>();

        String[] lines = output.split("\n");
        for (String line : lines) {
            String[] parts = line.split("=", 2);
            if (parts.length != 2) continue;

            String key = parts[0].trim();
            String value = parts[1].trim();

            // Parse changed branch info: CHANGED_BRANCH_0=branch|sha|time|title|author|type
            if (key.startsWith("CHANGED_BRANCH_") && !key.equals("CHANGED_BRANCH_COUNT")) {
                String[] branchParts = value.split("\\|");  // No limit to allow all parts
                if (branchParts.length >= 6) {
                    ChangedBranch branch = ChangedBranch.builder()
                        .branchName(branchParts[0])
                        .commitSha(branchParts[1])
                        .commitTime(branchParts[2])
                        .commitTitle(branchParts[3])
                        .commitAuthor(branchParts[4])
                        .changeType(branchParts[5])
                        .build();
                    changedBranches.add(branch);
                }
                continue;
            }

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

        if (!changedBranches.isEmpty()) {
            builder.changedBranches(changedBranches);
        }

        return builder.build();
    }
}
