package com.gitlab.mirror.cli.command;

import com.gitlab.mirror.cli.client.ApiClient;
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
        String response = apiClient.get("/api/scheduler/status");

        OutputFormatter.printInfo("Scheduler Status:");
        OutputFormatter.printJson(response);
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
        String response = apiClient.get("/api/scheduler/metrics");

        OutputFormatter.printInfo("Scheduler Metrics:");
        OutputFormatter.printJson(response);
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
        System.out.println(OutputFormatter.YELLOW + "Examples:" + OutputFormatter.RESET);
        System.out.println("  gitlab-mirror scheduler status");
        System.out.println("  gitlab-mirror scheduler trigger");
        System.out.println("  gitlab-mirror scheduler trigger --type=pull");
        System.out.println("  gitlab-mirror scheduler metrics");
        System.out.println();
    }
}
