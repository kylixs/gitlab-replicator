package com.gitlab.mirror.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

import java.util.*;

/**
 * Project list command
 *
 * @author GitLab Mirror Team
 */
public class ProjectCommand {
    private final ApiClient apiClient;

    public ProjectCommand(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void execute(String[] args) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("page", "1");
        params.put("size", "20");

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--status":
                    if (i + 1 < args.length) {
                        params.put("status", args[++i]);
                    }
                    break;
                case "--method":
                    if (i + 1 < args.length) {
                        params.put("syncMethod", args[++i]);
                    }
                    break;
                case "--page":
                    if (i + 1 < args.length) {
                        params.put("page", args[++i]);
                    }
                    break;
                case "--size":
                    if (i + 1 < args.length) {
                        params.put("size", args[++i]);
                    }
                    break;
            }
        }

        // Call API
        ApiClient.ApiResponse<Map<String, Object>> response = apiClient.get(
                "/api/projects",
                params,
                new TypeReference<ApiClient.ApiResponse<Map<String, Object>>>() {}
        );

        if (!response.isSuccess()) {
            OutputFormatter.printError("Failed to fetch projects: " + response.getError().getMessage());
            return;
        }

        Map<String, Object> data = response.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");

        if (items == null || items.isEmpty()) {
            OutputFormatter.printWarning("No projects found");
            return;
        }

        // Print table
        String[] headers = {"Project Key", "Status", "Method", "Enabled", "Updated At"};
        List<String[]> rows = new ArrayList<>();

        for (Map<String, Object> item : items) {
            String[] row = new String[5];
            row[0] = OutputFormatter.truncate(String.valueOf(item.get("projectKey")), 40);
            row[1] = String.valueOf(item.get("syncStatus"));
            row[2] = String.valueOf(item.get("syncMethod"));
            row[3] = Boolean.TRUE.equals(item.get("enabled")) ? "Yes" : "No";
            row[4] = String.valueOf(item.get("updatedAt"));
            rows.add(row);
        }

        System.out.println();
        OutputFormatter.printTable(headers, rows);

        // Print pagination info
        System.out.println();
        System.out.printf("Page %s of %s | Total: %s projects%n",
                data.get("page"),
                data.get("totalPages"),
                data.get("total"));
    }
}
