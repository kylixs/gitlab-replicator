package com.gitlab.mirror.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Sync command - triggers push mirror sync for a project
 *
 * @author GitLab Mirror Team
 */
public class SyncCommand {
    private final ApiClient apiClient;

    public SyncCommand(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void execute(String[] args) throws Exception {
        if (args.length == 0) {
            OutputFormatter.printError("Usage: gitlab-mirror sync <project-key>");
            OutputFormatter.printInfo("Example: gitlab-mirror sync devops/gitlab-mirror");
            return;
        }

        String projectKey = args[0];
        OutputFormatter.printInfo("Triggering mirror sync for project: " + projectKey);

        // Build query parameter
        String encodedKey = URLEncoder.encode(projectKey, StandardCharsets.UTF_8);

        // Call API with query parameter
        ApiClient.ApiResponse<Map<String, Object>> response = apiClient.post(
                "/api/mirrors/sync?project=" + encodedKey,
                null,
                new TypeReference<ApiClient.ApiResponse<Map<String, Object>>>() {}
        );

        if (!response.isSuccess()) {
            OutputFormatter.printError("Failed to trigger sync: " + response.getError().getMessage());
            return;
        }

        Map<String, Object> data = response.getData();
        OutputFormatter.printSuccess("Mirror sync triggered successfully");
        System.out.println();
        System.out.printf("  Project: %s%n", data.get("project_key"));
        System.out.printf("  Mirror ID: %s%n", data.get("mirror_id"));
        System.out.printf("  Status: %s%n", data.get("status"));
        System.out.println();
        OutputFormatter.printInfo("Use 'gitlab-mirror mirrors' to check the sync status");
    }
}
