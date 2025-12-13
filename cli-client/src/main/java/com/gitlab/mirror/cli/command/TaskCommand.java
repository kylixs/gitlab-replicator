package com.gitlab.mirror.cli.command;

import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

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
        OutputFormatter.printJson(response);
    }

    private void showTask(String[] args) throws Exception {
        if (args.length < 2) {
            OutputFormatter.printError("Missing task ID");
            OutputFormatter.printInfo("Usage: gitlab-mirror task show <task-id>");
            System.exit(1);
        }

        String taskId = args[1];
        String response = apiClient.get("/api/tasks/" + taskId);
        OutputFormatter.printJson(response);
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
        String response = apiClient.get("/api/tasks/stats");

        OutputFormatter.printInfo("Task Statistics:");
        OutputFormatter.printJson(response);
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
