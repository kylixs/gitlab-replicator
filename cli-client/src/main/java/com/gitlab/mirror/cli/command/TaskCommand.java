package com.gitlab.mirror.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.formatter.JsonParser;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Task Management Command
 *
 * @author GitLab Mirror Team
 */
public class TaskCommand {

    private final ApiClient apiClient;

    public TaskCommand(ApiClient apiClient) {
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
                listTasks(args);
                break;
            case "show":
                showTask(args);
                break;
            case "retry":
                retryTask(args);
                break;
            case "reset":
                resetFailures(args);
                break;
            case "stats":
                showStats(args);
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

    private void listTasks(String[] args) throws Exception {
        String type = null;
        String status = null;
        String priority = null;
        Boolean enabled = null;
        int page = 1;
        int size = 20;
        String format = "table"; // Default format

        // Parse options
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--type=")) {
                type = args[i].substring("--type=".length());
            } else if (args[i].startsWith("--status=")) {
                status = args[i].substring("--status=".length());
            } else if (args[i].startsWith("--priority=")) {
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

        StringBuilder url = new StringBuilder("/api/tasks?page=" + page + "&size=" + size);
        if (type != null) {
            url.append("&type=").append(type);
        }
        if (status != null) {
            url.append("&status=").append(status);
        }
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
            printTaskTable(response);
        }
    }

    private void printTaskTable(String json) {
        JsonNode root = JsonParser.parse(json);

        if (!JsonParser.getBoolean(root, "success")) {
            OutputFormatter.printError("Request failed: " + JsonParser.getString(root, "message"));
            return;
        }

        List<JsonNode> items = JsonParser.getArray(root, "data", "items");
        if (items.isEmpty()) {
            OutputFormatter.printWarning("No tasks found");
            return;
        }

        // Prepare table data
        String[] headers = {"ID", "Project", "Type", "Status", "Priority", "Next Run", "Failures", "Enabled"};
        List<String[]> rows = new ArrayList<>();

        for (JsonNode item : items) {
            String[] row = {
                String.valueOf(JsonParser.getInt(item, "id")),
                OutputFormatter.truncate(JsonParser.getString(item, "projectKey"), 30),
                JsonParser.getString(item, "taskType").toUpperCase(),
                OutputFormatter.formatStatus(JsonParser.getString(item, "taskStatus")),
                JsonParser.formatPriority(JsonParser.getString(item, "priority")),
                formatNextRun(JsonParser.getString(item, "nextRunAt")),
                String.valueOf(JsonParser.getInt(item, "consecutiveFailures")),
                JsonParser.formatEnabled(JsonParser.getBoolean(item, "enabled"))
            };
            rows.add(row);
        }

        // Print summary
        long total = JsonParser.getLong(root, "data", "total");
        int currentPage = JsonParser.getInt(root, "data", "page");
        OutputFormatter.printInfo(String.format("Tasks (Total: %d, Page: %d)", total, currentPage));
        System.out.println();

        // Print table
        OutputFormatter.printTable(headers, rows);
    }

    private String formatNextRun(String nextRunAt) {
        if (nextRunAt == null || nextRunAt.equals("-")) {
            return "-";
        }
        // Extract date and time
        if (nextRunAt.length() > 16) {
            return nextRunAt.substring(0, 16).replace("T", " ");
        }
        return nextRunAt;
    }

    private void showTask(String[] args) throws Exception {
        if (args.length < 2) {
            OutputFormatter.printError("Missing task ID");
            OutputFormatter.printInfo("Usage: gitlab-mirror task show <task-id> [--format=json]");
            System.exit(1);
        }

        String taskId = args[1];
        String format = "table";

        // Parse format option
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("--format=")) {
                format = args[i].substring("--format=".length());
            }
        }

        String response = apiClient.get("/api/tasks/" + taskId);

        if ("json".equalsIgnoreCase(format)) {
            OutputFormatter.printJson(response);
        } else {
            printTaskDetails(response);
        }
    }

    private void printTaskDetails(String json) {
        JsonNode root = JsonParser.parse(json);

        if (!JsonParser.getBoolean(root, "success")) {
            OutputFormatter.printError("Request failed: " + JsonParser.getString(root, "message"));
            return;
        }

        JsonNode task = root.get("data");
        if (task == null) {
            OutputFormatter.printWarning("Task not found");
            return;
        }

        OutputFormatter.printHeader("Task Details");
        System.out.println();

        OutputFormatter.printKeyValue("ID", String.valueOf(JsonParser.getInt(task, "id")));
        OutputFormatter.printKeyValue("Project", JsonParser.getString(task, "projectKey"));
        OutputFormatter.printKeyValue("Type", JsonParser.getString(task, "taskType").toUpperCase());
        OutputFormatter.printKeyValue("Status", OutputFormatter.formatStatus(JsonParser.getString(task, "taskStatus")));
        OutputFormatter.printKeyValue("Priority", JsonParser.formatPriority(JsonParser.getString(task, "priority")));
        OutputFormatter.printKeyValue("Enabled", JsonParser.formatEnabled(JsonParser.getBoolean(task, "enabled")));
        System.out.println();

        OutputFormatter.printKeyValue("Next Run At", JsonParser.getString(task, "nextRunAt"));
        OutputFormatter.printKeyValue("Last Run At", JsonParser.getString(task, "lastRunAt"));
        OutputFormatter.printKeyValue("Last Sync Status", JsonParser.getString(task, "lastSyncStatus"));
        System.out.println();

        int failures = JsonParser.getInt(task, "consecutiveFailures");
        String failuresStr = failures > 0
                ? OutputFormatter.RED + String.valueOf(failures) + OutputFormatter.RESET
                : String.valueOf(failures);
        OutputFormatter.printKeyValue("Consecutive Failures", failuresStr);

        String errorMsg = JsonParser.getString(task, "errorMessage");
        if (!"-".equals(errorMsg)) {
            OutputFormatter.printKeyValue("Error Message", OutputFormatter.truncate(errorMsg, 50));
        }

        System.out.println();
        OutputFormatter.printKeyValue("Created At", JsonParser.getString(task, "createdAt"));
        OutputFormatter.printKeyValue("Updated At", JsonParser.getString(task, "updatedAt"));
        System.out.println();
    }

    private void retryTask(String[] args) throws Exception {
        if (args.length < 2) {
            OutputFormatter.printError("Missing task ID");
            OutputFormatter.printInfo("Usage: gitlab-mirror task retry <task-id>");
            System.exit(1);
        }

        String taskId = args[1];
        String response = apiClient.post("/api/tasks/" + taskId + "/retry", "{}");

        OutputFormatter.printSuccess("Task scheduled for immediate retry: " + taskId);
        OutputFormatter.printJson(response);
    }

    private void resetFailures(String[] args) throws Exception {
        if (args.length < 2) {
            OutputFormatter.printError("Missing task ID");
            OutputFormatter.printInfo("Usage: gitlab-mirror task reset <task-id>");
            System.exit(1);
        }

        String taskId = args[1];
        String response = apiClient.put("/api/tasks/" + taskId + "/reset-failures", "{}");

        OutputFormatter.printSuccess("Failure count reset for task: " + taskId);
        OutputFormatter.printJson(response);
    }

    private void showStats(String[] args) throws Exception {
        String format = "table";

        // Parse format option
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--format=")) {
                format = args[i].substring("--format=".length());
            }
        }

        String response = apiClient.get("/api/tasks/stats");

        if ("json".equalsIgnoreCase(format)) {
            OutputFormatter.printJson(response);
        } else {
            printStatsTable(response);
        }
    }

    private void printStatsTable(String json) {
        JsonNode root = JsonParser.parse(json);

        if (!JsonParser.getBoolean(root, "success")) {
            OutputFormatter.printError("Request failed: " + JsonParser.getString(root, "message"));
            return;
        }

        JsonNode data = root.get("data");
        if (data == null) {
            OutputFormatter.printWarning("No statistics available");
            return;
        }

        OutputFormatter.printHeader("Task Statistics");
        System.out.println();

        // Overall stats
        OutputFormatter.printKeyValue("Total Tasks", JsonParser.formatNumber(JsonParser.getLong(data, "totalTasks")));
        System.out.println();

        // By type
        System.out.println(OutputFormatter.BOLD + "By Type:" + OutputFormatter.RESET);
        OutputFormatter.printKeyValue("  Pull Tasks", JsonParser.formatNumber(JsonParser.getLong(data, "pullTasks")));
        OutputFormatter.printKeyValue("  Push Tasks", JsonParser.formatNumber(JsonParser.getLong(data, "pushTasks")));
        System.out.println();

        // By status
        System.out.println(OutputFormatter.BOLD + "By Status:" + OutputFormatter.RESET);
        OutputFormatter.printKeyValue("  Waiting", JsonParser.formatNumber(JsonParser.getLong(data, "waitingTasks")));
        OutputFormatter.printKeyValue("  Running", JsonParser.formatNumber(JsonParser.getLong(data, "runningTasks")));
        OutputFormatter.printKeyValue("  Completed", JsonParser.formatNumber(JsonParser.getLong(data, "completedTasks")));
        OutputFormatter.printKeyValue("  Failed", JsonParser.formatNumber(JsonParser.getLong(data, "failedTasks")));
        OutputFormatter.printKeyValue("  Disabled", JsonParser.formatNumber(JsonParser.getLong(data, "disabledTasks")));
        System.out.println();

        // By priority
        System.out.println(OutputFormatter.BOLD + "By Priority:" + OutputFormatter.RESET);
        OutputFormatter.printKeyValue("  Critical", JsonParser.formatNumber(JsonParser.getLong(data, "criticalTasks")));
        OutputFormatter.printKeyValue("  High", JsonParser.formatNumber(JsonParser.getLong(data, "highPriorityTasks")));
        OutputFormatter.printKeyValue("  Normal", JsonParser.formatNumber(JsonParser.getLong(data, "normalPriorityTasks")));
        OutputFormatter.printKeyValue("  Low", JsonParser.formatNumber(JsonParser.getLong(data, "lowPriorityTasks")));
        System.out.println();
    }

    private void printUsage() {
        System.out.println(OutputFormatter.CYAN + "Task Management" + OutputFormatter.RESET);
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Usage:" + OutputFormatter.RESET);
        System.out.println("  gitlab-mirror task <subcommand> [options]");
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Subcommands:" + OutputFormatter.RESET);
        System.out.println("  list                  List tasks");
        System.out.println("  show <task-id>        Show task details");
        System.out.println("  retry <task-id>       Manually retry a task");
        System.out.println("  reset <task-id>       Reset failure count");
        System.out.println("  stats                 Show task statistics");
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "List Options:" + OutputFormatter.RESET);
        System.out.println("  --type=<t>            Filter by type (pull|push)");
        System.out.println("  --status=<s>          Filter by status (waiting|running)");
        System.out.println("  --priority=<p>        Filter by priority (critical|high|normal|low)");
        System.out.println("  --enabled             Filter enabled only");
        System.out.println("  --disabled            Filter disabled only");
        System.out.println("  --page=<n>            Page number (default: 1)");
        System.out.println("  --size=<n>            Page size (default: 20)");
        System.out.println("  --format=<fmt>        Output format (table|json, default: table)");
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Examples:" + OutputFormatter.RESET);
        System.out.println("  gitlab-mirror task list --type=pull --status=waiting");
        System.out.println("  gitlab-mirror task show 123");
        System.out.println("  gitlab-mirror task retry 123");
        System.out.println("  gitlab-mirror task reset 123");
        System.out.println("  gitlab-mirror task stats");
        System.out.println();
    }
}
