package com.gitlab.mirror.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

/**
 * Compensate command - triggers mirror compensation check
 *
 * @author GitLab Mirror Team
 */
public class CompensateCommand {
    private final ApiClient apiClient;

    public CompensateCommand(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void execute(String[] args) throws Exception {
        OutputFormatter.printInfo("Triggering mirror compensation check...");

        // Call API
        ApiClient.ApiResponse<String> response = apiClient.post(
                "/api/mirrors/compensate",
                null,
                new TypeReference<ApiClient.ApiResponse<String>>() {}
        );

        if (!response.isSuccess()) {
            OutputFormatter.printError("Failed to trigger compensation: " + response.getError().getMessage());
            return;
        }

        OutputFormatter.printSuccess("Mirror compensation check completed");
        OutputFormatter.printInfo("Check 'gitlab-mirror projects' to see the updated status");
    }
}
