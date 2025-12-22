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

    private void showSingleDiff(String projectKeyOrId) throws Exception {
        OutputFormatter.printInfo("Fetching diff for project: " + projectKeyOrId);

        // Build query parameters
        Map<String, String> params = new HashMap<>();

        // Check if input is numeric (ID) or string (key)
        if (isNumeric(projectKeyOrId)) {
            params.put("syncProjectId", projectKeyOrId);
        } else {
            params.put("projectKey", projectKeyOrId);
        }

        // Call API
        ApiClient.ApiResponse<Map<String, Object>> response = apiClient.get(
                "/api/sync/diff",
                params,
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

    /**
     * Check if string is numeric
     */
    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Long.parseLong(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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
        System.out.println("╔══════╦═══════════════════════════════════════════╦══════════════╦═══════════╦═══════╦═══════╦═══════════╗");
        System.out.println("║ ID   ║ Project Key                               ║ Status       ║ Commit    ║ Δ Cmt ║ Δ Brn ║ Delay(m)  ║");
        System.out.println("╠══════╬═══════════════════════════════════════════╬══════════════╬═══════════╬═══════╬═══════╬═══════════╣");

        for (Map<String, Object> diff : diffs) {
            // Get project ID
            Object syncProjectIdObj = diff.get("syncProjectId");
            String projectId = syncProjectIdObj != null ? String.valueOf(((Number) syncProjectIdObj).longValue()) : "N/A";

            String status = (String) diff.get("status");
            String statusIcon = getStatusIcon(status);
            String projectKey = smartTruncate((String) diff.get("projectKey"), 45);

            Map<String, Object> diffDetails = (Map<String, Object>) diff.get("diff");
            String commitMatch = "N/A";
            String commitDelta = "N/A";
            String branchDelta = "N/A";
            String delay = "N/A";

            if (diffDetails != null) {
                Object shaMatchesObj = diffDetails.get("commitShaMatches");
                if (shaMatchesObj != null) {
                    Boolean shaMatches = (Boolean) shaMatchesObj;
                    commitMatch = shaMatches ? "✓" : "✗";
                }

                Object commitDeltaObj = diffDetails.get("commitBehind");
                if (commitDeltaObj != null) {
                    Integer delta = ((Number) commitDeltaObj).intValue();
                    commitDelta = String.format("%+d", delta);
                }

                Object branchDeltaObj = diffDetails.get("branchDiff");
                if (branchDeltaObj != null) {
                    Integer delta = ((Number) branchDeltaObj).intValue();
                    branchDelta = String.format("%+d", delta);
                }

                Object delayMinutesObj = diffDetails.get("syncDelayMinutes");
                if (delayMinutesObj != null) {
                    Long delayMinutes = ((Number) delayMinutesObj).longValue();
                    delay = String.valueOf(delayMinutes);
                }
            }

            System.out.printf("║ %s ║ %s ║ %-12s ║ %-9s ║ %-5s ║ %-5s ║ %-9s ║%n",
                    String.format("%-4s", projectId),
                    String.format("%-45s", projectKey),
                    truncate(status, 12), commitMatch, commitDelta, branchDelta, delay);
        }

        System.out.println("╚══════╩═══════════════════════════════════════════╩══════════════╩═══════════╩═══════╩═══════╩═══════════╝");
    }

    @SuppressWarnings("unchecked")
    private void printDiff(Map<String, Object> diff) {
        String projectKey = (String) diff.get("projectKey");
        Object syncProjectIdObj = diff.get("syncProjectId");
        String projectId = syncProjectIdObj != null ? String.valueOf(((Number) syncProjectIdObj).longValue()) : "N/A";
        String status = (String) diff.get("status");
        String checkedAt = (String) diff.get("checkedAt");

        Map<String, Object> source = (Map<String, Object>) diff.get("source");
        Map<String, Object> target = (Map<String, Object>) diff.get("target");
        Map<String, Object> diffDetails = (Map<String, Object>) diff.get("diff");

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════════════╗");
        System.out.printf("║ Project: %-66s ║%n", truncate(projectKey, 66));
        System.out.printf("║ ID: %-71s ║%n", projectId);
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
        Object shaMatchesObj = diffDetails.get("commitShaMatches");
        Object commitDeltaObj = diffDetails.get("commitBehind");
        Object branchDeltaObj = diffDetails.get("branchDiff");
        Object sizeDiffPercentObj = diffDetails.get("sizeDiffPercent");
        Object delayMinutesObj = diffDetails.get("syncDelayMinutes");

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

        // Size diff percentage
        if (sizeDiffPercentObj != null) {
            Double sizeDiffPercent = ((Number) sizeDiffPercentObj).doubleValue();
            System.out.printf("║   Size Diff:      %.2f%% %-48s ║%n", sizeDiffPercent, "");
        }

        // Sync delay
        if (delayMinutesObj != null) {
            Long delayMinutes = ((Number) delayMinutesObj).longValue();
            System.out.printf("║   Sync Delay:     %-52d min ║%n", delayMinutes);
        }

        // Analyze and show inconsistency reasons
        boolean hasInconsistency = false;
        StringBuilder reasons = new StringBuilder();

        if (branchDeltaObj != null) {
            Integer branchDelta = ((Number) branchDeltaObj).intValue();
            if (Math.abs(branchDelta) > 0) {
                hasInconsistency = true;
                reasons.append("Branch count mismatch; ");
            }
        }

        if (sizeDiffPercentObj != null) {
            Double sizeDiffPercent = ((Number) sizeDiffPercentObj).doubleValue();
            if (sizeDiffPercent > 10.0) {
                hasInconsistency = true;
                reasons.append(String.format("Size diff %.2f%% > 10%% threshold", sizeDiffPercent));
            }
        }

        // Show inconsistency reason if exists
        if (hasInconsistency && reasons.length() > 0) {
            System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");
            System.out.println("║ ⚠️ Inconsistency Reason                                                   ║");
            System.out.println("╠════════════════════════════════════════════════════════════════════════════╣");
            String reasonStr = reasons.toString();
            if (reasonStr.endsWith("; ")) {
                reasonStr = reasonStr.substring(0, reasonStr.length() - 2);
            }
            System.out.printf("║   %s%-72s ║%n", "", reasonStr);
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

    /**
     * Smart truncate - keep prefix and suffix, omit middle
     * Prefix length = min(first group name length, 40% of total)
     * Suffix gets remaining space
     *
     * Examples:
     *   ai/test-rails-5  →  ai/test-rails-5  (no truncate)
     *   test-integration-projects-deletion_scheduled-31/test-project-1765623228747-deletion_scheduled-15
     *     →  test-integration-pr...tion_scheduled-15
     */
    private String smartTruncate(String projectKey, int maxLength) {
        if (projectKey == null) return "";
        if (projectKey.length() <= maxLength) return projectKey;

        int availableSpace = maxLength - 3; // Reserve 3 chars for "..."

        // Calculate prefix length: min(first group length, 40% of available space)
        int maxPrefixLen = (int) (availableSpace * 0.4);
        int prefixLen = maxPrefixLen;

        // Try to preserve first-level group name (before first '/')
        int firstSlash = projectKey.indexOf('/');
        if (firstSlash > 0) {
            // Prefix = min(first group length, 40%)
            prefixLen = Math.min(firstSlash, maxPrefixLen);
        }

        int suffixLen = availableSpace - prefixLen;

        String prefix = projectKey.substring(0, prefixLen);
        String suffix = projectKey.substring(projectKey.length() - suffixLen);

        return prefix + "..." + suffix;
    }

    private void printUsage() {
        System.out.println("Usage:");
        System.out.println("  gitlab-mirror diff                     List all project diffs");
        System.out.println("  gitlab-mirror diff <project-key|id>    Show single project diff by key or ID");
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
        System.out.println("  gitlab-mirror diff 984");
    }
}
