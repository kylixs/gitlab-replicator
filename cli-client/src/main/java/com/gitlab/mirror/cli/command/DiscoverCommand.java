package com.gitlab.mirror.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

import java.util.Map;

/**
 * Project discovery command
 *
 * @author GitLab Mirror Team
 */
public class DiscoverCommand {
    private final ApiClient apiClient;

    public DiscoverCommand(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void execute(String[] args) throws Exception {
        OutputFormatter.printInfo("Triggering project discovery...");

        ApiClient.ApiResponse<Map<String, Object>> response = apiClient.post(
                "/api/projects/discover",
                new TypeReference<ApiClient.ApiResponse<Map<String, Object>>>() {}
        );

        if (!response.isSuccess()) {
            OutputFormatter.printError("Failed to trigger discovery: " + response.getError().getMessage());
            return;
        }

        Map<String, Object> data = response.getData();
        Integer count = (Integer) data.get("discovered_count");

        OutputFormatter.printSuccess("Discovery completed");
        OutputFormatter.printInfo("Discovered " + count + " projects");
    }
}
