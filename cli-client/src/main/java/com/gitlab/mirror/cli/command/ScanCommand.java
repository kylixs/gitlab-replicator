package com.gitlab.mirror.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

import java.util.HashMap;
import java.util.Map;

/**
 * Scan command for triggering project scans
 *
 * @author GitLab Mirror Team
 */
public class ScanCommand {
    private final ApiClient apiClient;

    public ScanCommand(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void execute(String[] args) throws Exception {
        String scanType = "incremental";

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--type=")) {
                scanType = args[i].substring("--type=".length());
            }
        }

        OutputFormatter.printInfo("Triggering " + scanType + " scan...");

        // Call API with query parameter
        String path = "/api/sync/scan?type=" + scanType;

        ApiClient.ApiResponse<Map<String, Object>> response = apiClient.post(
                path,
                new TypeReference<ApiClient.ApiResponse<Map<String, Object>>>() {}
        );

        if (!response.isSuccess()) {
            OutputFormatter.printError("Scan failed: " + response.getError().getMessage());
            return;
        }

        Map<String, Object> data = response.getData();

        // Print scan results
        System.out.println();
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║         Scan Results                   ║");
        System.out.println("╠════════════════════════════════════════╣");
        System.out.printf("║ Scan Type:         %-19s║%n", data.get("scanType"));
        System.out.printf("║ Projects Scanned:  %-19s║%n", data.get("projectsScanned"));
        System.out.printf("║ Projects Updated:  %-19s║%n", data.get("projectsUpdated"));
        System.out.printf("║ Changes Detected:  %-19s║%n", data.get("changesDetected"));
        System.out.printf("║ Duration:          %-16sms ║%n", data.get("durationMs"));
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();

        // Print project changes if any
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> projectChanges =
            (java.util.List<Map<String, Object>>) data.get("projectChanges");

        if (projectChanges != null && !projectChanges.isEmpty()) {
            System.out.println("╔═══════════════════════════════════════════════════════════════════╗");
            System.out.println("║                        Project Changes                            ║");
            System.out.println("╠═══════════════════════════════════════════════════════════════════╣");

            for (Map<String, Object> change : projectChanges) {
                String projectKey = (String) change.get("projectKey");
                String projectType = (String) change.get("projectType");

                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> fieldChanges =
                    (java.util.List<Map<String, Object>>) change.get("fieldChanges");

                System.out.println("║                                                                   ║");
                System.out.printf("║ %s%-65s%s║%n",
                    OutputFormatter.CYAN, projectKey + " (" + projectType + ")", OutputFormatter.RESET);

                if (fieldChanges != null) {
                    for (Map<String, Object> fieldChange : fieldChanges) {
                        String fieldName = (String) fieldChange.get("fieldName");
                        String oldValue = (String) fieldChange.get("oldValue");
                        String newValue = (String) fieldChange.get("newValue");

                        // Format field name
                        String formattedField = formatFieldName(fieldName);

                        // Truncate long values
                        String displayOld = truncateValue(oldValue, 15);
                        String displayNew = truncateValue(newValue, 15);

                        System.out.printf("║   %-20s: %s%s%s -> %s%s%s%s║%n",
                            formattedField,
                            OutputFormatter.RED, displayOld, OutputFormatter.RESET,
                            OutputFormatter.GREEN, displayNew, OutputFormatter.RESET,
                            " ".repeat(Math.max(0, 30 - displayOld.length() - displayNew.length())));
                    }
                }
            }

            System.out.println("╚═══════════════════════════════════════════════════════════════════╝");
            System.out.println();
        }

        OutputFormatter.printSuccess("Scan completed successfully");
    }

    /**
     * Format field name for display
     */
    private String formatFieldName(String fieldName) {
        switch (fieldName) {
            case "latestCommitSha":
                return "Commit SHA";
            case "commitCount":
                return "Commits";
            case "branchCount":
                return "Branches";
            case "repositorySize":
                return "Size (bytes)";
            case "lastActivityAt":
                return "Last Activity";
            case "defaultBranch":
                return "Default Branch";
            default:
                return fieldName;
        }
    }

    /**
     * Truncate long values for display
     */
    private String truncateValue(String value, int maxLength) {
        if (value == null) {
            return "null";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        // For commit SHA, show first 8 chars
        if (value.length() == 40 && value.matches("[0-9a-f]+")) {
            return value.substring(0, 8) + "...";
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
