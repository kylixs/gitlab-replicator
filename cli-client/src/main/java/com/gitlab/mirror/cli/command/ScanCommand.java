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

        OutputFormatter.printSuccess("Scan completed successfully");
    }
}
