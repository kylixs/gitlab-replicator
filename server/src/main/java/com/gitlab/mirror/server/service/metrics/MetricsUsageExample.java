package com.gitlab.mirror.server.service.metrics;

import com.gitlab.mirror.server.entity.SyncEvent;
import com.gitlab.mirror.server.model.SyncStatistics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Metrics Usage Examples
 * <p>
 * This class demonstrates how to use the GitLabMirrorMetricsCollector
 * to record various project-level metrics.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Component
public class MetricsUsageExample {

    private final GitLabMirrorMetricsCollector metricsCollector;

    public MetricsUsageExample(GitLabMirrorMetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    /**
     * Example 1: Record metrics when a sync event completes
     * Note: This is an example - you need to pass projectKey separately
     */
    public void onSyncEventCompleted(SyncEvent event, String projectKey) {
        String eventType = event.getEventType();
        String status = event.getStatus();

        // Determine category from event source
        String category = determineSyncCategory(event.getEventSource());

        // Determine task type from event type
        String taskType = determineTaskType(eventType);

        // Extract statistics
        SyncStatistics stats = event.getStatistics();
        int branchesCreated = stats != null && stats.getBranchesCreated() != null
                ? stats.getBranchesCreated() : 0;
        int branchesUpdated = stats != null && stats.getBranchesUpdated() != null
                ? stats.getBranchesUpdated() : 0;
        int branchesDeleted = stats != null && stats.getBranchesDeleted() != null
                ? stats.getBranchesDeleted() : 0;
        int commitsPushed = stats != null && stats.getCommitsPushed() != null
                ? stats.getCommitsPushed() : 0;

        // Calculate current delay (example)
        long delaySeconds = calculateDelaySeconds(event);

        // Record all metrics in one call
        metricsCollector.recordComprehensiveSyncMetrics(
                projectKey,
                eventType,
                status,
                category,
                taskType,
                branchesCreated,
                branchesUpdated,
                branchesDeleted,
                commitsPushed,
                delaySeconds
        );

        log.debug("Recorded comprehensive metrics for project: {}, status: {}, " +
                        "branches: +{}/~{}/­{}, commits: {}",
                projectKey, status, branchesCreated, branchesUpdated,
                branchesDeleted, commitsPushed);
    }

    /**
     * Example 2: Record individual project metrics
     */
    public void recordIndividualMetrics(String projectKey) {
        // Record sync event
        metricsCollector.recordProjectSyncEvent(
                projectKey,
                "webhook",  // category: webhook, scheduled, manual
                "success"    // status: success, failed, pending
        );

        // Record sync task
        metricsCollector.recordProjectSyncTask(
                projectKey,
                "pull",      // task_type: pull, push, mirror_setup
                "success"    // status: success, failed, timeout
        );

        // Record branch changes
        metricsCollector.recordProjectBranchChanges(projectKey, "created", 3);
        metricsCollector.recordProjectBranchChanges(projectKey, "updated", 5);
        metricsCollector.recordProjectBranchChanges(projectKey, "deleted", 1);

        // Record commit changes
        metricsCollector.recordProjectCommitChanges(projectKey, 15);

        // Update project delay
        metricsCollector.updateProjectDelay(projectKey, 120); // 120 seconds delay
    }

    /**
     * Example 3: Record metrics for webhook-triggered sync
     */
    public void onWebhookSync(String projectKey, boolean success,
                               int branchesChanged, int commits) {
        String status = success ? "success" : "failed";

        // Record webhook sync event
        metricsCollector.recordProjectSyncEvent(projectKey, "webhook", status);

        // Record pull task
        metricsCollector.recordProjectSyncTask(projectKey, "pull", status);

        if (success) {
            // Record changes (only on success)
            metricsCollector.recordProjectBranchChanges(projectKey, "updated", branchesChanged);
            metricsCollector.recordProjectCommitChanges(projectKey, commits);
        }
    }

    /**
     * Example 4: Record metrics for scheduled sync
     */
    public void onScheduledSync(String projectKey, String taskType,
                                 boolean success, SyncStatistics stats) {
        String status = success ? "success" : "failed";

        // Record scheduled sync event
        metricsCollector.recordProjectSyncEvent(projectKey, "scheduled", status);

        // Record specific task type
        metricsCollector.recordProjectSyncTask(projectKey, taskType, status);

        if (success && stats != null) {
            // Record branch changes
            if (stats.getBranchesCreated() != null && stats.getBranchesCreated() > 0) {
                metricsCollector.recordProjectBranchChanges(
                        projectKey, "created", stats.getBranchesCreated());
            }
            if (stats.getBranchesUpdated() != null && stats.getBranchesUpdated() > 0) {
                metricsCollector.recordProjectBranchChanges(
                        projectKey, "updated", stats.getBranchesUpdated());
            }
            if (stats.getBranchesDeleted() != null && stats.getBranchesDeleted() > 0) {
                metricsCollector.recordProjectBranchChanges(
                        projectKey, "deleted", stats.getBranchesDeleted());
            }

            // Record commit changes
            if (stats.getCommitsPushed() != null && stats.getCommitsPushed() > 0) {
                metricsCollector.recordProjectCommitChanges(
                        projectKey, stats.getCommitsPushed());
            }
        }
    }

    /**
     * Example 5: Batch update project delays (called by scheduler)
     */
    public void updateProjectDelays(java.util.Map<String, Long> projectDelays) {
        projectDelays.forEach((projectKey, delaySeconds) -> {
            metricsCollector.updateProjectDelay(projectKey, delaySeconds);
        });

        log.debug("Updated delay metrics for {} projects", projectDelays.size());
    }

    // ==================== Helper Methods ====================

    /**
     * Determine sync category from event source
     */
    private String determineSyncCategory(String eventSource) {
        if (eventSource == null) return "unknown";

        if (eventSource.contains("webhook")) {
            return "webhook";
        } else if (eventSource.contains("scheduled") || eventSource.contains("cron")) {
            return "scheduled";
        } else if (eventSource.contains("manual") || eventSource.contains("cli")) {
            return "manual";
        }

        return "unknown";
    }

    /**
     * Determine task type from event type
     */
    private String determineTaskType(String eventType) {
        if (eventType == null) return "unknown";

        if (eventType.contains("pull") || eventType.equals("sync_finished")) {
            return "pull";
        } else if (eventType.contains("push")) {
            return "push";
        } else if (eventType.contains("mirror") || eventType.contains("setup")) {
            return "mirror_setup";
        }

        return "unknown";
    }

    /**
     * Calculate delay in seconds
     */
    private long calculateDelaySeconds(SyncEvent event) {
        // Example implementation - calculate based on event time
        if (event.getEventTime() != null) {
            long eventTimestamp = event.getEventTime()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toEpochSecond();
            long now = System.currentTimeMillis() / 1000;
            return Math.max(0, now - eventTimestamp);
        }
        return 0;
    }
}
