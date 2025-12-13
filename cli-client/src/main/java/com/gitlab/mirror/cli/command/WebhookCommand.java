package com.gitlab.mirror.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.formatter.JsonParser;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

import java.util.ArrayList;
import java.util.List;

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
        String format = "table"; // Default format

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
            } else if (args[i].startsWith("--format=")) {
                format = args[i].substring("--format=".length());
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

        if ("json".equalsIgnoreCase(format)) {
            OutputFormatter.printJson(response);
        } else {
            printEventsTable(response);
        }
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
        String format = "table"; // Default format

        // Parse format option
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--format=")) {
                format = args[i].substring("--format=".length());
            }
        }

        String response = apiClient.get("/api/webhook/stats");

        if ("json".equalsIgnoreCase(format)) {
            OutputFormatter.printJson(response);
        } else {
            printStatsTable(response);
        }
    }

    private void printEventsTable(String json) {
        JsonNode root = JsonParser.parse(json);

        if (!JsonParser.getBoolean(root, "success")) {
            OutputFormatter.printError("Request failed: " + JsonParser.getString(root, "message"));
            return;
        }

        List<JsonNode> items = JsonParser.getArray(root, "data", "items");
        if (items.isEmpty()) {
            OutputFormatter.printWarning("No webhook events found");
            return;
        }

        // Prepare table data
        String[] headers = {"ID", "Project", "Event Type", "Status", "Trigger", "Received At"};
        List<String[]> rows = new ArrayList<>();

        for (JsonNode item : items) {
            String[] row = {
                String.valueOf(JsonParser.getInt(item, "id")),
                OutputFormatter.truncate(JsonParser.getString(item, "projectKey"), 30),
                JsonParser.getString(item, "eventType"),
                formatEventStatus(JsonParser.getString(item, "status")),
                JsonParser.getString(item, "triggerAction"),
                formatTimestamp(JsonParser.getString(item, "receivedAt"))
            };
            rows.add(row);
        }

        // Print summary
        long total = JsonParser.getLong(root, "data", "total");
        int currentPage = JsonParser.getInt(root, "data", "page");
        OutputFormatter.printInfo(String.format("Webhook Events (Total: %d, Page: %d)", total, currentPage));
        System.out.println();

        // Print table
        OutputFormatter.printTable(headers, rows);
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

        OutputFormatter.printHeader("Webhook Statistics");
        System.out.println();

        // Overall stats
        OutputFormatter.printKeyValue("Total Events", JsonParser.formatNumber(JsonParser.getLong(data, "totalEvents")));
        System.out.println();

        // By status
        System.out.println(OutputFormatter.BOLD + "By Status:" + OutputFormatter.RESET);
        OutputFormatter.printKeyValue("  Accepted", JsonParser.formatNumber(JsonParser.getLong(data, "acceptedEvents")));
        OutputFormatter.printKeyValue("  Ignored", JsonParser.formatNumber(JsonParser.getLong(data, "ignoredEvents")));
        OutputFormatter.printKeyValue("  Rejected", JsonParser.formatNumber(JsonParser.getLong(data, "rejectedEvents")));
        System.out.println();

        // By event type
        System.out.println(OutputFormatter.BOLD + "By Event Type:" + OutputFormatter.RESET);
        OutputFormatter.printKeyValue("  Push Events", JsonParser.formatNumber(JsonParser.getLong(data, "pushEvents")));
        OutputFormatter.printKeyValue("  Tag Push Events", JsonParser.formatNumber(JsonParser.getLong(data, "tagPushEvents")));
        OutputFormatter.printKeyValue("  Other Events", JsonParser.formatNumber(JsonParser.getLong(data, "otherEvents")));
        System.out.println();

        // Time-based stats
        System.out.println(OutputFormatter.BOLD + "Recent Activity:" + OutputFormatter.RESET);
        OutputFormatter.printKeyValue("  Last 24 Hours", JsonParser.formatNumber(JsonParser.getLong(data, "eventsLast24h")));
        OutputFormatter.printKeyValue("  Last 7 Days", JsonParser.formatNumber(JsonParser.getLong(data, "eventsLast7d")));
        System.out.println();
    }

    private String formatEventStatus(String status) {
        if (status == null) return "-";
        switch (status.toLowerCase()) {
            case "accepted":
                return OutputFormatter.GREEN + "ACCEPTED" + OutputFormatter.RESET;
            case "ignored":
                return OutputFormatter.YELLOW + "IGNORED" + OutputFormatter.RESET;
            case "rejected":
                return OutputFormatter.RED + "REJECTED" + OutputFormatter.RESET;
            default:
                return status.toUpperCase();
        }
    }

    private String formatTimestamp(String timestamp) {
        if (timestamp == null || timestamp.equals("-")) {
            return "-";
        }
        // Extract date and time
        if (timestamp.length() > 16) {
            return timestamp.substring(0, 16).replace("T", " ");
        }
        return timestamp;
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
        System.out.println("  --format=<fmt>        Output format (table|json, default: table)");
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Examples:" + OutputFormatter.RESET);
        System.out.println("  gitlab-mirror webhook list --project=group/project");
        System.out.println("  gitlab-mirror webhook list --status=accepted");
        System.out.println("  gitlab-mirror webhook list --format=json");
        System.out.println("  gitlab-mirror webhook show 123");
        System.out.println("  gitlab-mirror webhook stats");
        System.out.println("  gitlab-mirror webhook stats --format=json");
        System.out.println();
    }
}
