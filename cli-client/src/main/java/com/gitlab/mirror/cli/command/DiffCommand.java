package com.gitlab.mirror.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Diff command - show source/target project diff
 *
 * @author GitLab Mirror Team
 */
public class DiffCommand {
    private final ApiClient apiClient;

    public DiffCommand(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void execute(String[] args) throws Exception {
        // Parse arguments
        String projectKey = null;
        String status = null;
        Integer page = 1;
        Integer size = 20;

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--status=")) {
                status = args[i].substring("--status=".length());
            } else if (args[i].startsWith("--page=")) {
                page = Integer.parseInt(args[i].substring("--page=".length()));
            } else if (args[i].startsWith("--size=")) {
                size = Integer.parseInt(args[i].substring("--size=".length()));
            } else if (!args[i].startsWith("--")) {
                projectKey = args[i];
            }
        }

        // If project key is provided, show single project diff
        if (projectKey != null) {
            showSingleDiff(projectKey);
        } else {
            showDiffList(status, page, size);
        }
    }

    private void showSingleDiff(String projectKey) throws Exception {
        OutputFormatter.printInfo("Fetching diff for project: " + projectKey);

        // Call API
        ApiClient.ApiResponse<Map<String, Object>> response = apiClient.get(
                "/api/sync/projects/" + projectKey + "/diff",
                new HashMap<>(),
                new TypeReference<ApiClient.ApiResponse<Map<String, Object>>>() {}
        );

        if (!response.isSuccess()) {
            OutputFormatter.printError("Failed to fetch diff: " + response.getError().getMessage());
            return;
        }

        Map<String, Object> diff = response.getData();
        if (diff == null) {
            OutputFormatter.printWarning("No diff data available");
            return;
        }

        printDiff(diff);
    }

    @SuppressWarnings("unchecked")
    private void showDiffList(String status, Integer page, Integer size) throws Exception {
        OutputFormatter.printInfo("Fetching project diffs...");

        // Build query parameters
        Map<String, String> params = new HashMap<>();
        if (status != null) {
            params.put("status", status);
        }
        params.put("page", String.valueOf(page));
        params.put("size", String.valueOf(size));

        // Call API
        ApiClient.ApiResponse<Map<String, Object>> response = apiClient.get(
                "/api/sync/diffs",
                params,
                new TypeReference<ApiClient.ApiResponse<Map<String, Object>>>() {}
        );

        if (!response.isSuccess()) {
            OutputFormatter.printError("Failed to fetch diffs: " + response.getError().getMessage());
            return;
        }

        Map<String, Object> data = response.getData();
        if (data == null) {
            OutputFormatter.printWarning("No diff data available");
            return;
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        if (items == null || items.isEmpty()) {
            OutputFormatter.printWarning("No diffs found");
            return;
        }

        printDiffTable(items);

        // Print pagination info
        System.out.println();
        System.out.printf("Page %s | Total: %s projects%n",
                data.get("page"),
                data.get("total"));
    }

    @SuppressWarnings("unchecked")
    private void printDiffTable(List<Map<String, Object>> diffs) {
        System.out.println();
        System.out.println("╔═══╦═══════════════════════════╦══════════════╦═══════════╦═══════╦═══════╦═══════════╗");
        System.out.println("║   ║ Project Key               ║ Status       ║ Commit    ║ Δ Cmt ║ Δ Brn ║ Delay(m)  ║");
        System.out.println("╠═══╬═══════════════════════════╬══════════════╬═══════════╬═══════╬═══════╬═══════════╣");

        for (Map<String, Object> diff : diffs) {
            String status = (String) diff.get("status");
            String statusIcon = getStatusIcon(status);
            String projectKey = truncate((String) diff.get("projectKey"), 25);

            Map<String, Object> diffDetails = (Map<String, Object>) diff.get("diff");
            String commitMatch = "N/A";
            String commitDelta = "N/A";
            String branchDelta = "N/A";
            String delay = "N/A";

            if (diffDetails != null) {
                Object shaMatchesObj = diffDetails.get("shaMatches");
                if (shaMatchesObj != null) {
                    Boolean shaMatches = (Boolean) shaMatchesObj;
                    commitMatch = shaMatches ? "✓" : "✗";
                }

                Object commitDeltaObj = diffDetails.get("commitDelta");
                if (commitDeltaObj != null) {
                    Integer delta = ((Number) commitDeltaObj).intValue();
                    commitDelta = String.format("%+d", delta);
                }

                Object branchDeltaObj = diffDetails.get("branchDelta");
                if (branchDeltaObj != null) {
                    Integer delta = ((Number) branchDeltaObj).intValue();
                    branchDelta = String.format("%+d", delta);
                }

                Object delayMinutesObj = diffDetails.get("delayMinutes");
                if (delayMinutesObj != null) {
                    Long delayMinutes = ((Number) delayMinutesObj).longValue();
                    delay = String.valueOf(delayMinutes);
                }
            }

            System.out.printf("║ %s ║ %-25s ║ %-12s ║ %-9s ║ %-5s ║ %-5s ║ %-9s ║%n",
                    statusIcon, projectKey, truncate(status, 12), commitMatch, commitDelta, branchDelta, delay);
        }

        System.out.println("╚═══╩═══════════════════════════╩══════════════╩═══════════╩═══════╩═══════╩═══════════╝");
    }

    @SuppressWarnings("unchecked")
    private void printDiff(Map<String, Object> diff) {
        String projectKey = (String) diff.get("projectKey");
        String status = (String) diff.get("status");
        String checkedAt = (String) diff.get("checkedAt");

        Map<String, Object> source = (Map<String, Object>) diff.get("source");
        Map<String, Object> target = (Map<String, Object>) diff.get("target");
        Map<String, Object> diffDetails = (Map<String, Object>) diff.get("diff");

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════════════╗");
        System.out.printf("║ Project: %-66s ║%n", truncate(projectKey, 66));
        System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");

        // Status
        String statusIcon = getStatusIcon(status);
        System.out.printf("║ Status: %s %-64s ║%n", statusIcon, status);
        System.out.printf("║ Checked: %-66s ║%n", checkedAt != null ? checkedAt : "N/A");
        System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");

        // Source snapshot
        System.out.println("║ 📥 Source Project                                                          ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");
        printSnapshot(source, "Source");

        System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");

        // Target snapshot
        System.out.println("║ 📤 Target Project                                                          ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");
        if (target != null) {
            printSnapshot(target, "Target");
        } else {
            System.out.println("║   Target project not found                                                 ║");
        }

        // Diff details
        if (diffDetails != null) {
            System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");
            System.out.println("║ 📊 Diff Details                                                            ║");
            System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");
            printDiffDetails(diffDetails);
        }

        System.out.println("╚════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private void printSnapshot(Map<String, Object> snapshot, String prefix) {
        if (snapshot == null) {
            System.out.printf("║   %s: No data                                                        ║%n", prefix);
            return;
        }

        String commitSha = (String) snapshot.get("commitSha");
        Object commitCountObj = snapshot.get("commitCount");
        Object branchCountObj = snapshot.get("branchCount");
        Object sizeBytesObj = snapshot.get("sizeBytes");
        String lastActivityAt = (String) snapshot.get("lastActivityAt");
        String defaultBranch = (String) snapshot.get("defaultBranch");

        // Commit SHA
        if (commitSha != null) {
            System.out.printf("║   Latest Commit:  %-56s ║%n", truncate(commitSha, 56));
        } else {
            System.out.println("║   Latest Commit:  N/A                                                      ║");
        }

        // Commit count
        if (commitCountObj != null) {
            Integer commitCount = ((Number) commitCountObj).intValue();
            System.out.printf("║   Commit Count:   %-56d ║%n", commitCount);
        } else {
            System.out.println("║   Commit Count:   N/A                                                      ║");
        }

        // Branch count
        if (branchCountObj != null) {
            Integer branchCount = ((Number) branchCountObj).intValue();
            System.out.printf("║   Branch Count:   %-56d ║%n", branchCount);
        } else {
            System.out.println("║   Branch Count:   N/A                                                      ║");
        }

        // Repository size
        if (sizeBytesObj != null) {
            Long sizeBytes = ((Number) sizeBytesObj).longValue();
            String sizeStr = formatBytes(sizeBytes);
            System.out.printf("║   Size:           %-56s ║%n", sizeStr);
        } else {
            System.out.println("║   Size:           N/A                                                      ║");
        }

        // Last activity
        if (lastActivityAt != null) {
            System.out.printf("║   Last Activity:  %-56s ║%n", truncate(lastActivityAt, 56));
        } else {
            System.out.println("║   Last Activity:  N/A                                                      ║");
        }

        // Default branch
        if (defaultBranch != null) {
            System.out.printf("║   Default Branch: %-56s ║%n", truncate(defaultBranch, 56));
        } else {
            System.out.println("║   Default Branch: N/A                                                      ║");
        }
    }

    private void printDiffDetails(Map<String, Object> diffDetails) {
        Object shaMatchesObj = diffDetails.get("shaMatches");
        Object commitDeltaObj = diffDetails.get("commitDelta");
        Object branchDeltaObj = diffDetails.get("branchDelta");
        Object sizeDeltaBytesObj = diffDetails.get("sizeDeltaBytes");
        Object delayMinutesObj = diffDetails.get("delayMinutes");

        // SHA match
        if (shaMatchesObj != null) {
            Boolean shaMatches = (Boolean) shaMatchesObj;
            String icon = shaMatches ? "✓" : "✗";
            System.out.printf("║   %s Commit SHA Match: %-54s ║%n", icon, shaMatches);
        }

        // Commit delta
        if (commitDeltaObj != null) {
            Integer commitDelta = ((Number) commitDeltaObj).intValue();
            String sign = commitDelta >= 0 ? "+" : "";
            System.out.printf("║   Commit Delta:   %s%-55d ║%n", sign, commitDelta);
        }

        // Branch delta
        if (branchDeltaObj != null) {
            Integer branchDelta = ((Number) branchDeltaObj).intValue();
            String sign = branchDelta >= 0 ? "+" : "";
            System.out.printf("║   Branch Delta:   %s%-55d ║%n", sign, branchDelta);
        }

        // Size delta
        if (sizeDeltaBytesObj != null) {
            Long sizeDeltaBytes = ((Number) sizeDeltaBytesObj).longValue();
            String sizeStr = formatBytes(Math.abs(sizeDeltaBytes));
            String sign = sizeDeltaBytes >= 0 ? "+" : "-";
            System.out.printf("║   Size Delta:     %s%-55s ║%n", sign, sizeStr);
        }

        // Sync delay
        if (delayMinutesObj != null) {
            Long delayMinutes = ((Number) delayMinutesObj).longValue();
            System.out.printf("║   Sync Delay:     %-52d min ║%n", delayMinutes);
        }
    }

    private String getStatusIcon(String status) {
        if (status == null) return "⚪";
        switch (status.toUpperCase()) {
            case "SYNCED":
                return "✅";
            case "OUTDATED":
                return "🔄";
            case "FAILED":
                return "❌";
            case "INCONSISTENT":
                return "⚠️";
            default:
                return "⚪";
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    private void printUsage() {
        System.out.println("Usage:");
        System.out.println("  gitlab-mirror diff                     List all project diffs");
        System.out.println("  gitlab-mirror diff <project-key>       Show single project diff");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --status=<status>   Filter by status (SYNCED|OUTDATED|FAILED|INCONSISTENT)");
        System.out.println("  --page=<n>          Page number (default: 1)");
        System.out.println("  --size=<n>          Page size (default: 20)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  gitlab-mirror diff");
        System.out.println("  gitlab-mirror diff --status=OUTDATED");
        System.out.println("  gitlab-mirror diff mygroup/myproject");
    }
}
