package com.gitlab.mirror.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monitor command - view monitoring status and alerts
 *
 * @author GitLab Mirror Team
 */
public class MonitorCommand {
    private final ApiClient apiClient;

    public MonitorCommand(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void execute(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String subCommand = args[0];
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, args.length - 1);

        switch (subCommand) {
            case "status":
                executeStatus(subArgs);
                break;
            case "alerts":
                executeAlerts(subArgs);
                break;
            default:
                OutputFormatter.printError("Unknown subcommand: " + subCommand);
                printUsage();
        }
    }

    private void executeStatus(String[] args) throws Exception {
        OutputFormatter.printInfo("Fetching monitor status...");

        ApiClient.ApiResponse<Map<String, Object>> response = apiClient.get(
                "/api/monitor/status",
                new TypeReference<ApiClient.ApiResponse<Map<String, Object>>>() {}
        );

        if (!response.isSuccess()) {
            OutputFormatter.printError("Failed to fetch status: " + response.getError().getMessage());
            return;
        }

        Map<String, Object> data = response.getData();
        printStatusOverview(data);
    }

    private void executeAlerts(String[] args) throws Exception {
        // Parse options
        String severity = null;
        String status = null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--severity=")) {
                severity = args[i].substring("--severity=".length());
            } else if (args[i].startsWith("--status=")) {
                status = args[i].substring("--status=".length());
            }
        }

        OutputFormatter.printInfo("Fetching alerts...");

        // Build query parameters
        Map<String, String> queryParams = new HashMap<>();
        if (severity != null) {
            queryParams.put("severity", severity);
        }
        if (status != null) {
            queryParams.put("status", status);
        }

        ApiClient.ApiResponse<Map<String, Object>> response = apiClient.get(
                "/api/monitor/alerts",
                queryParams,
                new TypeReference<ApiClient.ApiResponse<Map<String, Object>>>() {}
        );

        if (!response.isSuccess()) {
            OutputFormatter.printError("Failed to fetch alerts: " + response.getError().getMessage());
            return;
        }

        Map<String, Object> data = response.getData();
        printAlerts(data);
    }

    private void printStatusOverview(Map<String, Object> data) {
        System.out.println();
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║       Monitor Status Overview          ║");
        System.out.println("╠════════════════════════════════════════╣");

        // Projects summary
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) data.get("summary");
        if (summary != null) {
            System.out.println("║ 📊 Projects Summary                    ║");
            Object totalObj = summary.get("total_projects");
            Integer total = totalObj != null ? ((Number) totalObj).intValue() : 0;
            System.out.printf("║   Total:        %-23s║%n", total);

            Object syncedObj = summary.get("synced");
            Integer synced = syncedObj != null ? ((Number) syncedObj).intValue() : 0;
            double syncedPercent = total > 0 ? (synced * 100.0 / total) : 0.0;
            System.out.printf("║   ✓ Synced:     %-6s (%.1f%%)          ║%n", synced, syncedPercent);

            Object outdatedObj = summary.get("outdated");
            Integer outdated = outdatedObj != null ? ((Number) outdatedObj).intValue() : 0;
            double outdatedPercent = total > 0 ? (outdated * 100.0 / total) : 0.0;
            System.out.printf("║   ⟳ Outdated:   %-6s (%.1f%%)          ║%n", outdated, outdatedPercent);

            Object failedObj = summary.get("failed");
            Integer failed = failedObj != null ? ((Number) failedObj).intValue() : 0;
            double failedPercent = total > 0 ? (failed * 100.0 / total) : 0.0;
            System.out.printf("║   ✗ Failed:     %-6s (%.1f%%)          ║%n", failed, failedPercent);
        }

        System.out.println("╠════════════════════════════════════════╣");

        // Alerts summary
        @SuppressWarnings("unchecked")
        Map<String, Object> alerts = (Map<String, Object>) data.get("alerts");
        if (alerts != null) {
            Object activeObj = alerts.get("active");
            Integer active = activeObj != null ? ((Number) activeObj).intValue() : 0;
            System.out.printf("║ 🚨 Active Alerts   %-19s║%n", active);

            Object criticalObj = alerts.get("critical");
            Integer critical = criticalObj != null ? ((Number) criticalObj).intValue() : 0;
            if (critical > 0) {
                System.out.printf("║   🔴 Critical:  %-23s║%n", critical);
            }

            Object highObj = alerts.get("high");
            Integer high = highObj != null ? ((Number) highObj).intValue() : 0;
            if (high > 0) {
                System.out.printf("║   🟠 High:      %-23s║%n", high);
            }
        }

        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
    }

    private void printAlerts(Map<String, Object> data) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> alerts = (List<Map<String, Object>>) data.get("alerts");

        if (alerts == null || alerts.isEmpty()) {
            OutputFormatter.printSuccess("No alerts found");
            return;
        }

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                              Active Alerts                                 ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");

        for (Map<String, Object> alert : alerts) {
            String severity = (String) alert.get("severity");
            String icon = getSeverityIcon(severity);
            String title = (String) alert.get("title");
            String description = (String) alert.get("description");
            String triggeredAt = (String) alert.get("triggered_at");

            System.out.printf("║ %s %-70s ║%n", icon, title);
            System.out.printf("║   Severity: %-64s ║%n", severity);
            System.out.printf("║   Triggered: %-63s ║%n", triggeredAt);
            if (description != null && description.length() <= 66) {
                System.out.printf("║   %s%-66s ║%n", "", description);
            }
            System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");
        }

        System.out.printf("║ Total: %d alert(s)%-60s║%n", alerts.size(), "");
        System.out.println("╚════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private String getSeverityIcon(String severity) {
        if (severity == null) return "⚪";
        switch (severity.toLowerCase()) {
            case "critical":
                return "🔴";
            case "high":
                return "🟠";
            case "medium":
                return "🟡";
            case "low":
                return "🟢";
            default:
                return "⚪";
        }
    }

    private void printUsage() {
        System.out.println("Usage: gitlab-mirror monitor <subcommand> [options]");
        System.out.println();
        System.out.println("Subcommands:");
        System.out.println("  status                    View monitoring status overview");
        System.out.println("  alerts [options]          View active alerts");
        System.out.println();
        System.out.println("Alert Options:");
        System.out.println("  --severity=<level>        Filter by severity (critical|high|medium|low)");
        System.out.println("  --status=<status>         Filter by status (active|resolved|muted)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  gitlab-mirror monitor status");
        System.out.println("  gitlab-mirror monitor alerts");
        System.out.println("  gitlab-mirror monitor alerts --severity=critical");
        System.out.println("  gitlab-mirror monitor alerts --status=active");
    }
}
