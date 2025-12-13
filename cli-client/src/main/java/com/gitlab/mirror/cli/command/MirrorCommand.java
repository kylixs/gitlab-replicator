package com.gitlab.mirror.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Mirror management command
 *
 * @author GitLab Mirror Team
 */
public class MirrorCommand {
    private final ApiClient apiClient;

    public MirrorCommand(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void execute(String[] args) throws Exception {
        if (args.length == 0) {
            OutputFormatter.printError("Project key is required");
            OutputFormatter.printInfo("Usage: gitlab-mirror mirror <project-key> [--setup|--poll]");
            return;
        }

        String projectKey = args[0];
        boolean setup = false;
        boolean poll = false;

        // Parse options
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--setup":
                    setup = true;
                    break;
                case "--poll":
                    poll = true;
                    break;
            }
        }

        if (setup) {
            setupMirror(projectKey);
        } else if (poll) {
            pollMirrors();
        } else {
            showMirrorDetails(projectKey);
        }
    }

    private void setupMirror(String projectKey) throws Exception {
        OutputFormatter.printInfo("Setting up mirror for project: " + projectKey);

        String encodedKey = URLEncoder.encode(projectKey, StandardCharsets.UTF_8);
        ApiClient.ApiResponse<Map<String, Object>> response = apiClient.post(
                "/api/mirrors/" + encodedKey + "/setup",
                new TypeReference<ApiClient.ApiResponse<Map<String, Object>>>() {}
        );

        if (!response.isSuccess()) {
            OutputFormatter.printError("Failed to setup mirror: " + response.getError().getMessage());
            return;
        }

        OutputFormatter.printSuccess("Mirror setup completed");

        Map<String, Object> data = response.getData();
        if (data != null) {
            OutputFormatter.printInfo("Mirror ID: " + data.get("gitlabMirrorId"));
            OutputFormatter.printInfo("Mirror URL: " + data.get("mirrorUrl"));
        }
    }

    private void pollMirrors() throws Exception {
        OutputFormatter.printInfo("Polling mirror status...");

        ApiClient.ApiResponse<Map<String, Object>> response = apiClient.post(
                "/api/mirrors/poll",
                new TypeReference<ApiClient.ApiResponse<Map<String, Object>>>() {}
        );

        if (!response.isSuccess()) {
            OutputFormatter.printError("Failed to poll mirrors: " + response.getError().getMessage());
            return;
        }

        Map<String, Object> data = response.getData();
        OutputFormatter.printSuccess("Mirror polling completed");
        OutputFormatter.printInfo("Total checked: " + data.get("total_checked"));
        OutputFormatter.printInfo("Changed: " + data.get("changed_count"));
    }

    private void showMirrorDetails(String projectKey) throws Exception {
        OutputFormatter.printError("Mirror details view not implemented yet");
        OutputFormatter.printInfo("Use: gitlab-mirror mirrors --status <status> to list all mirrors");
    }
}
