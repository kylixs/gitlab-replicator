package com.gitlab.mirror.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

import java.util.*;

/**
 * Event list command
 *
 * @author GitLab Mirror Team
 */
public class EventCommand {
    private final ApiClient apiClient;

    public EventCommand(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void execute(String[] args) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("page", "1");
        params.put("size", "50");

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--project":
                    if (i + 1 < args.length) {
                        params.put("projectId", args[++i]);
                    }
                    break;
                case "--type":
                    if (i + 1 < args.length) {
                        params.put("eventType", args[++i]);
                    }
                    break;
                case "--status":
                    if (i + 1 < args.length) {
                        params.put("status", args[++i]);
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
                "/api/events",
                params,
                new TypeReference<ApiClient.ApiResponse<Map<String, Object>>>() {}
        );

        if (!response.isSuccess()) {
            OutputFormatter.printError("Failed to fetch events: " + response.getError().getMessage());
            return;
        }

        Map<String, Object> data = response.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");

        if (items == null || items.isEmpty()) {
            OutputFormatter.printWarning("No events found");
            return;
        }

        // Print table
        String[] headers = {"Event Type", "Status", "Project", "Event Time", "Duration(s)"};
        List<String[]> rows = new ArrayList<>();

        for (Map<String, Object> item : items) {
            String[] row = new String[5];
            row[0] = String.valueOf(item.get("eventType"));
            row[1] = String.valueOf(item.get("status"));
            row[2] = String.valueOf(item.get("projectKey"));
            row[3] = String.valueOf(item.get("eventTime"));
            row[4] = String.valueOf(item.get("durationSeconds"));
            rows.add(row);
        }

        System.out.println();
        OutputFormatter.printTable(headers, rows);

        // Print pagination info
        System.out.println();
        System.out.printf("Page %s of %s | Total: %s events%n",
                data.get("page"),
                data.get("totalPages"),
                data.get("total"));
    }
}
