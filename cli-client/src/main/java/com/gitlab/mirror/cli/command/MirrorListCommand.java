package com.gitlab.mirror.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

import java.util.*;

/**
 * Mirror list command
 *
 * @author GitLab Mirror Team
 */
public class MirrorListCommand {
    private final ApiClient apiClient;

    public MirrorListCommand(ApiClient apiClient) {
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
                "/api/mirrors",
                params,
                new TypeReference<ApiClient.ApiResponse<Map<String, Object>>>() {}
        );

        if (!response.isSuccess()) {
            OutputFormatter.printError("Failed to fetch mirrors: " + response.getError().getMessage());
            return;
        }

        Map<String, Object> data = response.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");

        if (items == null || items.isEmpty()) {
            OutputFormatter.printWarning("No mirrors found");
            return;
        }

        // Print table
        String[] headers = {"Mirror ID", "Sync Project ID", "Status", "Last Update", "Failures"};
        List<String[]> rows = new ArrayList<>();

        for (Map<String, Object> item : items) {
            String[] row = new String[5];
            row[0] = String.valueOf(item.get("gitlabMirrorId"));
            row[1] = String.valueOf(item.get("syncProjectId"));
            row[2] = String.valueOf(item.get("lastUpdateStatus"));
            row[3] = String.valueOf(item.get("lastUpdateAt"));
            row[4] = String.valueOf(item.get("consecutiveFailures"));
            rows.add(row);
        }

        System.out.println();
        OutputFormatter.printTable(headers, rows);

        // Print pagination info
        System.out.println();
        System.out.printf("Page %s of %s | Total: %s mirrors%n",
                data.get("page"),
                data.get("totalPages"),
                data.get("total"));
    }
}
