package com.gitlab.mirror.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.formatter.JsonParser;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

/**
 * Scheduler Management Command
 *
 * @author GitLab Mirror Team
 */
public class SchedulerCommand {

    private final ApiClient apiClient;

    public SchedulerCommand(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void execute(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String subCommand = args[0];

        switch (subCommand) {
            case "status":
                showStatus(args);
                break;
            case "trigger":
                triggerSchedule(args);
                break;
            case "metrics":
                showMetrics(args);
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

    private void showStatus(String[] args) throws Exception {
        String format = "table"; // Default format

        // Parse format option
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--format=")) {
                format = args[i].substring("--format=".length());
            }
        }

        String response = apiClient.get("/api/scheduler/status");

        if ("json".equalsIgnoreCase(format)) {
            OutputFormatter.printJson(response);
        } else {
            printStatusTable(response);
        }
    }

    private void triggerSchedule(String[] args) throws Exception {
        String taskType = null;

        // Parse options
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--type=")) {
                taskType = args[i].substring("--type=".length());
            }
        }

        String requestBody = taskType != null
                ? "{\"taskType\":\"" + taskType + "\"}"
                : "{}";

        String response = apiClient.post("/api/scheduler/trigger", requestBody);

        OutputFormatter.printSuccess("Scheduler triggered successfully");
        if (taskType != null) {
            OutputFormatter.printInfo("Task type: " + taskType);
        }
        System.out.println(response);
    }

    private void showMetrics(String[] args) throws Exception {
        String format = "table"; // Default format

        // Parse format option
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--format=")) {
                format = args[i].substring("--format=".length());
            }
        }

        String response = apiClient.get("/api/scheduler/metrics");

        if ("json".equalsIgnoreCase(format)) {
            OutputFormatter.printJson(response);
        } else {
            printMetricsTable(response);
        }
    }

    private void printStatusTable(String json) {
        JsonNode root = JsonParser.parse(json);

        if (!JsonParser.getBoolean(root, "success")) {
            OutputFormatter.printError("Request failed: " + JsonParser.getString(root, "message"));
            return;
        }

        JsonNode data = root.get("data");
        if (data == null) {
            OutputFormatter.printWarning("No status information available");
            return;
        }

        OutputFormatter.printHeader("Scheduler Status");
        System.out.println();

        OutputFormatter.printKeyValue("Enabled", JsonParser.formatEnabled(JsonParser.getBoolean(data, "enabled")));
        OutputFormatter.printKeyValue("Running", JsonParser.getBoolean(data, "running") ? "Yes" : "No");
        System.out.println();

        OutputFormatter.printKeyValue("Last Run", JsonParser.getString(data, "lastRunAt"));
        OutputFormatter.printKeyValue("Next Run", JsonParser.getString(data, "nextRunAt"));
        System.out.println();

        OutputFormatter.printKeyValue("Total Tasks", JsonParser.formatNumber(JsonParser.getLong(data, "totalTasks")));
        OutputFormatter.printKeyValue("Pending Tasks", JsonParser.formatNumber(JsonParser.getLong(data, "pendingTasks")));
        OutputFormatter.printKeyValue("Running Tasks", JsonParser.formatNumber(JsonParser.getLong(data, "runningTasks")));
        System.out.println();
    }

    private void printMetricsTable(String json) {
        JsonNode root = JsonParser.parse(json);

        if (!JsonParser.getBoolean(root, "success")) {
            OutputFormatter.printError("Request failed: " + JsonParser.getString(root, "message"));
            return;
        }

        JsonNode data = root.get("data");
        if (data == null) {
            OutputFormatter.printWarning("No metrics available");
            return;
        }

        OutputFormatter.printHeader("Scheduler Metrics");
        System.out.println();

        // Execution stats
        System.out.println(OutputFormatter.BOLD + "Execution Statistics:" + OutputFormatter.RESET);
        OutputFormatter.printKeyValue("  Total Executions", JsonParser.formatNumber(JsonParser.getLong(data, "totalExecutions")));
        OutputFormatter.printKeyValue("  Successful", JsonParser.formatNumber(JsonParser.getLong(data, "successfulExecutions")));
        OutputFormatter.printKeyValue("  Failed", JsonParser.formatNumber(JsonParser.getLong(data, "failedExecutions")));
        System.out.println();

        // Task stats
        System.out.println(OutputFormatter.BOLD + "Task Statistics:" + OutputFormatter.RESET);
        OutputFormatter.printKeyValue("  Total Tasks Processed", JsonParser.formatNumber(JsonParser.getLong(data, "totalTasksProcessed")));
        OutputFormatter.printKeyValue("  Average Tasks/Run", String.format("%.1f", JsonParser.getDouble(data, "avgTasksPerRun")));
        System.out.println();

        // Timing stats
        System.out.println(OutputFormatter.BOLD + "Timing:" + OutputFormatter.RESET);
        OutputFormatter.printKeyValue("  Average Duration", formatDuration(JsonParser.getLong(data, "avgDurationMs")));
        OutputFormatter.printKeyValue("  Max Duration", formatDuration(JsonParser.getLong(data, "maxDurationMs")));
        System.out.println();
    }

    private String formatDuration(long durationMs) {
        if (durationMs < 1000) {
            return durationMs + "ms";
        } else if (durationMs < 60000) {
            return String.format("%.1fs", durationMs / 1000.0);
        } else {
            return String.format("%.1fm", durationMs / 60000.0);
        }
    }

    private void printUsage() {
        System.out.println(OutputFormatter.CYAN + "Scheduler Management" + OutputFormatter.RESET);
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Usage:" + OutputFormatter.RESET);
        System.out.println("  gitlab-mirror scheduler <subcommand> [options]");
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Subcommands:" + OutputFormatter.RESET);
        System.out.println("  status                Show scheduler status");
        System.out.println("  trigger               Manually trigger scheduling");
        System.out.println("  metrics               Show scheduler metrics");
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Trigger Options:" + OutputFormatter.RESET);
        System.out.println("  --type=<t>            Task type to trigger (pull|push)");
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Common Options:" + OutputFormatter.RESET);
        System.out.println("  --format=<fmt>        Output format (table|json, default: table)");
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Examples:" + OutputFormatter.RESET);
        System.out.println("  gitlab-mirror scheduler status");
        System.out.println("  gitlab-mirror scheduler status --format=json");
        System.out.println("  gitlab-mirror scheduler trigger");
        System.out.println("  gitlab-mirror scheduler trigger --type=pull");
        System.out.println("  gitlab-mirror scheduler metrics");
        System.out.println();
    }
}
