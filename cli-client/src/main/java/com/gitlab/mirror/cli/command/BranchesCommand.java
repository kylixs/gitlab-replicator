package com.gitlab.mirror.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Branches command - show project branches comparison
 *
 * @author GitLab Mirror Team
 */
public class BranchesCommand {
    private final ApiClient apiClient;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public BranchesCommand(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void execute(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String projectKey = args[0];

        OutputFormatter.printInfo("Fetching branches for project: " + projectKey);

        // Build query parameters
        Map<String, String> params = new HashMap<>();

        // Check if input is numeric (ID) or string (key)
        if (isNumeric(projectKey)) {
            params.put("syncProjectId", projectKey);
        } else {
            params.put("projectKey", projectKey);
        }

        // Call API
        ApiClient.ApiResponse<Map<String, Object>> response = apiClient.get(
                "/api/sync/branches",
                params,
                new TypeReference<ApiClient.ApiResponse<Map<String, Object>>>() {}
        );

        if (!response.isSuccess()) {
            OutputFormatter.printError("Failed to fetch branches: " + response.getError().getMessage());
            return;
        }

        Map<String, Object> data = response.getData();
        if (data == null) {
            OutputFormatter.printWarning("No branch data available");
            return;
        }

        printBranchComparison(data);
    }

    /**
     * Check if string is numeric
     */
    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Long.parseLong(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void printBranchComparison(Map<String, Object> data) {
        String projectKey = (String) data.get("projectKey");
        Object syncProjectIdObj = data.get("syncProjectId");
        String projectId = syncProjectIdObj != null ? String.valueOf(((Number) syncProjectIdObj).longValue()) : "N/A";

        Integer sourceBranchCount = getInteger(data, "sourceBranchCount");
        Integer targetBranchCount = getInteger(data, "targetBranchCount");
        Integer syncedCount = getInteger(data, "syncedCount");
        Integer outdatedCount = getInteger(data, "outdatedCount");
        Integer missingInTargetCount = getInteger(data, "missingInTargetCount");
        Integer extraInTargetCount = getInteger(data, "extraInTargetCount");

        List<Map<String, Object>> branches = (List<Map<String, Object>>) data.get("branches");

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════════════╗");
        System.out.printf("║ Project: %-66s ║%n", truncate(projectKey, 66));
        System.out.printf("║ ID: %-71s ║%n", projectId);
        System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║ 📊 Branch Summary                                                          ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║   Source Branches:        %-48d ║%n", sourceBranchCount);
        System.out.printf("║   Target Branches:        %-48d ║%n", targetBranchCount);
        System.out.printf("║   ✅ Synced:              %-48d ║%n", syncedCount);
        System.out.printf("║   🔄 Outdated:            %-48d ║%n", outdatedCount);
        System.out.printf("║   ⚠️  Missing in Target:  %-48d ║%n", missingInTargetCount);
        System.out.printf("║   ➕ Extra in Target:     %-48d ║%n", extraInTargetCount);
        System.out.println("╚════════════════════════════════════════════════════════════════════════════╝");

        if (branches == null || branches.isEmpty()) {
            System.out.println();
            OutputFormatter.printWarning("No branches found");
            return;
        }

        System.out.println();
        printBranchTable(branches);
    }

    @SuppressWarnings("unchecked")
    private void printBranchTable(List<Map<String, Object>> branches) {
        System.out.println("╔══════════════════════════════╦════════════╦═══════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║ Branch                       ║ Status     ║ Last Commit (Source → Target)                                                             ║");
        System.out.println("╠══════════════════════════════╬════════════╬═══════════════════════════════════════════════════════════════════════════════════════════╣");

        for (Map<String, Object> branch : branches) {
            String branchName = (String) branch.get("branchName");
            String status = (String) branch.get("status");
            Boolean isDefault = (Boolean) branch.get("isDefault");
            Boolean isProtected = (Boolean) branch.get("isProtected");

            String sourceCommitSha = (String) branch.get("sourceCommitSha");
            String sourceCommitMessage = (String) branch.get("sourceCommitMessage");
            String sourceCommittedAt = (String) branch.get("sourceCommittedAt");

            String targetCommitSha = (String) branch.get("targetCommitSha");
            String targetCommitMessage = (String) branch.get("targetCommitMessage");
            String targetCommittedAt = (String) branch.get("targetCommittedAt");

            // Format branch name with badges
            String branchDisplay = branchName;
            if (Boolean.TRUE.equals(isDefault)) {
                branchDisplay = "⭐ " + branchDisplay;
            }
            if (Boolean.TRUE.equals(isProtected)) {
                branchDisplay = "🔒 " + branchDisplay;
            }

            String statusIcon = getStatusIcon(status);
            String statusDisplay = statusIcon + " " + formatStatus(status);

            // Print branch header
            System.out.printf("║ %-28s ║ %-10s ║                                                                                               ║%n",
                    truncate(branchDisplay, 28),
                    truncate(statusDisplay, 10));

            // Print source commit info
            if (sourceCommitSha != null) {
                String sourceLine = String.format("📥 %s | %s | %s",
                        truncate(sourceCommitSha.substring(0, Math.min(8, sourceCommitSha.length())), 8),
                        formatDateTime(sourceCommittedAt),
                        truncate(sourceCommitMessage != null ? sourceCommitMessage : "N/A", 60));
                System.out.printf("║                              ║            ║ %-93s ║%n", sourceLine);
            } else {
                System.out.printf("║                              ║            ║ %-93s ║%n", "📥 (no source commit)");
            }

            // Print target commit info
            if (targetCommitSha != null) {
                String targetLine = String.format("📤 %s | %s | %s",
                        truncate(targetCommitSha.substring(0, Math.min(8, targetCommitSha.length())), 8),
                        formatDateTime(targetCommittedAt),
                        truncate(targetCommitMessage != null ? targetCommitMessage : "N/A", 60));
                System.out.printf("║                              ║            ║ %-93s ║%n", targetLine);
            } else {
                System.out.printf("║                              ║            ║ %-93s ║%n", "📤 (no target commit)");
            }

            System.out.println("╠══════════════════════════════╬════════════╬═══════════════════════════════════════════════════════════════════════════════════════════╣");
        }

        System.out.println("╚══════════════════════════════╩════════════╩═══════════════════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private String getStatusIcon(String status) {
        if (status == null) return "⚪";
        switch (status.toLowerCase()) {
            case "synced":
                return "✅";
            case "outdated":
                return "🔄";
            case "missing_in_target":
                return "⚠️";
            case "extra_in_target":
                return "➕";
            default:
                return "⚪";
        }
    }

    private String formatStatus(String status) {
        if (status == null) return "Unknown";
        switch (status.toLowerCase()) {
            case "synced":
                return "Synced";
            case "outdated":
                return "Outdated";
            case "missing_in_target":
                return "Missing";
            case "extra_in_target":
                return "Extra";
            default:
                return status;
        }
    }

    private String formatDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return "N/A";
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(dateTimeStr);
            return dateTime.format(DATE_FORMATTER);
        } catch (Exception e) {
            return truncate(dateTimeStr, 19);
        }
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    private void printUsage() {
        System.out.println("Usage:");
        System.out.println("  gitlab-mirror branches <project-key|id>    Show branch comparison for project");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  gitlab-mirror branches ai/test-android-app-3");
        System.out.println("  gitlab-mirror branches 986");
        System.out.println();
        System.out.println("Legend:");
        System.out.println("  ⭐ Default branch");
        System.out.println("  🔒 Protected branch");
        System.out.println("  ✅ Synced - commits match between source and target");
        System.out.println("  🔄 Outdated - target is behind source");
        System.out.println("  ⚠️  Missing - branch exists in source but not in target");
        System.out.println("  ➕ Extra - branch exists in target but not in source");
    }
}
