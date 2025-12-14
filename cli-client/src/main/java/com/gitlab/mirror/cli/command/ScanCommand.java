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

        // Call API
        Map<String, String> params = new HashMap<>();
        params.put("type", scanType);

        ApiClient.ApiResponse<Map<String, Object>> response = apiClient.post(
                "/api/sync/scan",
                params,
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
        System.out.printf("║ Scan Type:         %-19s║%n", data.get("scan_type"));
        System.out.printf("║ Projects Scanned:  %-19s║%n", data.get("projects_scanned"));
        System.out.printf("║ Projects Updated:  %-19s║%n", data.get("projects_updated"));
        System.out.printf("║ Changes Detected:  %-19s║%n", data.get("changes_detected"));
        System.out.printf("║ Duration:          %-16sms ║%n", data.get("duration_ms"));
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();

        OutputFormatter.printSuccess("Scan completed successfully");
    }
}
