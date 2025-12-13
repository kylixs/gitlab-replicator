package com.gitlab.mirror.cli.command;

import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

/**
 * Webhook Monitoring Command
 *
 * @author GitLab Mirror Team
 */
public class WebhookCommand {

    private final ApiClient apiClient;

    public WebhookCommand(ApiClient apiClient) {
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
                listWebhookEvents(args);
                break;
            case "show":
                showWebhookEvent(args);
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

    private void listWebhookEvents(String[] args) throws Exception {
        String projectKey = null;
        String status = null;
        int page = 1;
        int size = 20;

        // Parse options
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--project=")) {
                projectKey = args[i].substring("--project=".length());
            } else if (args[i].startsWith("--status=")) {
                status = args[i].substring("--status=".length());
            } else if (args[i].startsWith("--page=")) {
                page = Integer.parseInt(args[i].substring("--page=".length()));
            } else if (args[i].startsWith("--size=")) {
                size = Integer.parseInt(args[i].substring("--size=".length()));
            }
        }

        StringBuilder url = new StringBuilder("/api/webhook/events?page=" + page + "&size=" + size);
        if (projectKey != null) {
            url.append("&projectKey=").append(projectKey);
        }
        if (status != null) {
            url.append("&status=").append(status);
        }

        String response = apiClient.get(url.toString());
        OutputFormatter.printJson(response);
    }

    private void showWebhookEvent(String[] args) throws Exception {
        if (args.length < 2) {
            OutputFormatter.printError("Missing event ID");
            OutputFormatter.printInfo("Usage: gitlab-mirror webhook show <event-id>");
            System.exit(1);
        }

        String eventId = args[1];
        String response = apiClient.get("/api/webhook/events/" + eventId);
        OutputFormatter.printJson(response);
    }

    private void showStats(String[] args) throws Exception {
        String response = apiClient.get("/api/webhook/stats");

        OutputFormatter.printInfo("Webhook Statistics:");
        OutputFormatter.printJson(response);
    }

    private void printUsage() {
        System.out.println(OutputFormatter.CYAN + "Webhook Monitoring" + OutputFormatter.RESET);
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Usage:" + OutputFormatter.RESET);
        System.out.println("  gitlab-mirror webhook <subcommand> [options]");
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Subcommands:" + OutputFormatter.RESET);
        System.out.println("  list                  List webhook events");
        System.out.println("  show <event-id>       Show event details");
        System.out.println("  stats                 Show webhook statistics");
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "List Options:" + OutputFormatter.RESET);
        System.out.println("  --project=<key>       Filter by project key");
        System.out.println("  --status=<s>          Filter by status (accepted|ignored|rejected)");
        System.out.println("  --page=<n>            Page number (default: 1)");
        System.out.println("  --size=<n>            Page size (default: 20)");
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Examples:" + OutputFormatter.RESET);
        System.out.println("  gitlab-mirror webhook list --project=group/project");
        System.out.println("  gitlab-mirror webhook list --status=accepted");
        System.out.println("  gitlab-mirror webhook show 123");
        System.out.println("  gitlab-mirror webhook stats");
        System.out.println();
    }
}
