package com.gitlab.mirror.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.formatter.JsonParser;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Pull Sync Management Command
 *
 * @author GitLab Mirror Team
 */
public class PullSyncCommand {

    private final ApiClient apiClient;

    public PullSyncCommand(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void execute(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String subCommand = args[0];

        switch (subCommand) {
            case "list":
                listConfigs(args);
                break;
            case "show":
                showConfig(args);
                break;
            case "priority":
                updatePriority(args);
                break;
            case "enable":
                enableConfig(args);
                break;
            case "disable":
                disableConfig(args);
                break;
            case "help":
            case "--help":
            case "-h":
                printUsage();
                break;
            default:
                OutputFormatter.printError("Unknown subcommand: " + subCommand);
                printUsage();
                System.exit(1);
        }
    }

    private void listConfigs(String[] args) throws Exception {
        String priority = null;
        Boolean enabled = null;
        int page = 1;
        int size = 20;
        String format = "table"; // Default format

        // Parse options
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--priority=")) {
                priority = args[i].substring("--priority=".length());
            } else if (args[i].equals("--enabled")) {
                enabled = true;
            } else if (args[i].equals("--disabled")) {
                enabled = false;
            } else if (args[i].startsWith("--page=")) {
                page = Integer.parseInt(args[i].substring("--page=".length()));
            } else if (args[i].startsWith("--size=")) {
                size = Integer.parseInt(args[i].substring("--size=".length()));
            } else if (args[i].startsWith("--format=")) {
                format = args[i].substring("--format=".length());
            }
        }

        StringBuilder url = new StringBuilder("/api/pull-sync/config?page=" + page + "&size=" + size);
        if (priority != null) {
            url.append("&priority=").append(priority);
        }
        if (enabled != null) {
            url.append("&enabled=").append(enabled);
        }

        String response = apiClient.get(url.toString());

        if ("json".equalsIgnoreCase(format)) {
            OutputFormatter.printJson(response);
        } else {
            printConfigTable(response);
        }
    }

    private void showConfig(String[] args) throws Exception {
        if (args.length < 2) {
            OutputFormatter.printError("Missing project ID");
            OutputFormatter.printInfo("Usage: gitlab-mirror pull show <project-id>");
            System.exit(1);
        }

        String projectId = args[1];
        String response = apiClient.get("/api/pull-sync/config/" + projectId);
        OutputFormatter.printJson(response);
    }

    private void updatePriority(String[] args) throws Exception {
        if (args.length < 3) {
            OutputFormatter.printError("Missing project ID or priority");
            OutputFormatter.printInfo("Usage: gitlab-mirror pull priority <project-id> <priority>");
            OutputFormatter.printInfo("Priority: critical, high, normal, low");
            System.exit(1);
        }

        String projectId = args[1];
        String priority = args[2];

        // Validate priority
        if (!priority.matches("critical|high|normal|low")) {
            OutputFormatter.printError("Invalid priority: " + priority);
            OutputFormatter.printInfo("Valid priorities: critical, high, normal, low");
            System.exit(1);
        }

        String requestBody = "{\"priority\":\"" + priority + "\"}";
        String response = apiClient.put("/api/pull-sync/config/" + projectId + "/priority", requestBody);

        OutputFormatter.printSuccess("Priority updated to: " + priority);
        OutputFormatter.printJson(response);
    }

    private void enableConfig(String[] args) throws Exception {
        if (args.length < 2) {
            OutputFormatter.printError("Missing project ID");
            OutputFormatter.printInfo("Usage: gitlab-mirror pull enable <project-id>");
            System.exit(1);
        }

        String projectId = args[1];
        String requestBody = "{\"enabled\":true}";
        String response = apiClient.put("/api/pull-sync/config/" + projectId + "/enabled", requestBody);

        OutputFormatter.printSuccess("Pull sync enabled for project: " + projectId);
        OutputFormatter.printJson(response);
    }

    private void disableConfig(String[] args) throws Exception {
        if (args.length < 2) {
            OutputFormatter.printError("Missing project ID");
            OutputFormatter.printInfo("Usage: gitlab-mirror pull disable <project-id>");
            System.exit(1);
        }

        String projectId = args[1];
        String requestBody = "{\"enabled\":false}";
        String response = apiClient.put("/api/pull-sync/config/" + projectId + "/enabled", requestBody);

        OutputFormatter.printWarning("Pull sync disabled for project: " + projectId);
        OutputFormatter.printJson(response);
    }

    private void printConfigTable(String json) {
        JsonNode root = JsonParser.parse(json);

        if (!JsonParser.getBoolean(root, "success")) {
            OutputFormatter.printError("Request failed: " + JsonParser.getString(root, "message"));
            return;
        }

        List<JsonNode> items = JsonParser.getArray(root, "data", "items");
        if (items.isEmpty()) {
            OutputFormatter.printWarning("No pull sync configurations found");
            return;
        }

        // Prepare table data
        String[] headers = {"ID", "Project", "Priority", "Sync Interval", "Last Sync", "Status", "Enabled"};
        List<String[]> rows = new ArrayList<>();

        for (JsonNode item : items) {
            String[] row = {
                String.valueOf(JsonParser.getInt(item, "syncProjectId")),
                OutputFormatter.truncate(JsonParser.getString(item, "projectKey"), 30),
                JsonParser.formatPriority(JsonParser.getString(item, "priority")),
                formatInterval(JsonParser.getInt(item, "syncIntervalMinutes")),
                formatLastSync(JsonParser.getString(item, "lastSyncAt")),
                OutputFormatter.formatStatus(JsonParser.getString(item, "syncStatus")),
                JsonParser.formatEnabled(JsonParser.getBoolean(item, "enabled"))
            };
            rows.add(row);
        }

        // Print summary
        long total = JsonParser.getLong(root, "data", "total");
        int currentPage = JsonParser.getInt(root, "data", "page");
        OutputFormatter.printInfo(String.format("Pull Sync Configurations (Total: %d, Page: %d)", total, currentPage));
        System.out.println();

        // Print table
        OutputFormatter.printTable(headers, rows);
    }

    private String formatInterval(int minutes) {
        if (minutes < 60) {
            return minutes + "m";
        } else if (minutes < 1440) {
            return (minutes / 60) + "h";
        } else {
            return (minutes / 1440) + "d";
        }
    }

    private String formatLastSync(String lastSyncAt) {
        if (lastSyncAt == null || lastSyncAt.equals("-")) {
            return "-";
        }
        // Extract date and time
        if (lastSyncAt.length() > 16) {
            return lastSyncAt.substring(0, 16).replace("T", " ");
        }
        return lastSyncAt;
    }

    private void printUsage() {
        System.out.println(OutputFormatter.CYAN + "Pull Sync Management" + OutputFormatter.RESET);
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Usage:" + OutputFormatter.RESET);
        System.out.println("  gitlab-mirror pull <subcommand> [options]");
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Subcommands:" + OutputFormatter.RESET);
        System.out.println("  list                  List Pull sync configurations");
        System.out.println("  show <project-id>     Show config for a project");
        System.out.println("  priority <id> <p>     Update priority (critical|high|normal|low)");
        System.out.println("  enable <project-id>   Enable Pull sync for a project");
        System.out.println("  disable <project-id>  Disable Pull sync for a project");
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "List Options:" + OutputFormatter.RESET);
        System.out.println("  --priority=<p>        Filter by priority");
        System.out.println("  --enabled             Filter enabled only");
        System.out.println("  --disabled            Filter disabled only");
        System.out.println("  --page=<n>            Page number (default: 1)");
        System.out.println("  --size=<n>            Page size (default: 20)");
        System.out.println("  --format=<fmt>        Output format (table|json, default: table)");
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Examples:" + OutputFormatter.RESET);
        System.out.println("  gitlab-mirror pull list --priority=high --enabled");
        System.out.println("  gitlab-mirror pull show 123");
        System.out.println("  gitlab-mirror pull priority 123 critical");
        System.out.println("  gitlab-mirror pull enable 123");
        System.out.println();
    }
}
