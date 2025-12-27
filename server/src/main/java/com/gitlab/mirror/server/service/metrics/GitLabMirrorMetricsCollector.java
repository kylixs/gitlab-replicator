package com.gitlab.mirror.server.service.metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GitLab Mirror Prometheus Metrics Collector
 * <p>
 * Collects and exposes metrics for monitoring sync operations, project health,
 * and system performance.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class GitLabMirrorMetricsCollector {

    private final MeterRegistry registry;

    // Counter metrics
    private final Counter.Builder syncEventsCounter;
    private final Counter.Builder branchChangesCounter;
    private final Counter.Builder apiCallsCounter;

    // Project-level counter metrics
    private final Counter.Builder projectSyncEventsCounter;
    private final Counter.Builder projectSyncTasksCounter;
    private final Counter.Builder projectBranchChangesCounter;
    private final Counter.Builder projectCommitChangesCounter;

    // Gauge metrics - use ConcurrentHashMap to store current values
    private final Map<String, Double> projectsByStatusGauges = new ConcurrentHashMap<>();
    private final Map<String, Double> delayedProjectsGauges = new ConcurrentHashMap<>();
    private final Map<String, ProjectMetrics> projectMetricsMap = new ConcurrentHashMap<>();

    // Project-level delay gauge - track delay for each project
    private final Map<String, Double> projectDelayGauges = new ConcurrentHashMap<>();

    // Histogram metrics
    private final Timer syncDurationTimer;
    private final DistributionSummary projectBranchesDistribution;

    // Summary metrics
    private final DistributionSummary syncDelaySummary;

    public GitLabMirrorMetricsCollector(MeterRegistry registry) {
        this.registry = registry;

        // Initialize Counter builders
        this.syncEventsCounter = Counter.builder("gitlab_mirror_sync_events_total")
                .description("Total number of sync events by type and status");

        this.branchChangesCounter = Counter.builder("gitlab_mirror_branch_changes_total")
                .description("Total number of branch changes by change type");

        this.apiCallsCounter = Counter.builder("gitlab_mirror_api_calls_total")
                .description("Total number of GitLab API calls");

        // Initialize project-level counters
        this.projectSyncEventsCounter = Counter.builder("gitlab_mirror_project_sync_events_total")
                .description("Total number of sync events per project by category and status");

        this.projectSyncTasksCounter = Counter.builder("gitlab_mirror_project_sync_tasks_total")
                .description("Total number of sync tasks executed per project by type and status");

        this.projectBranchChangesCounter = Counter.builder("gitlab_mirror_project_branch_changes_total")
                .description("Total number of branch changes per project by operation type");

        this.projectCommitChangesCounter = Counter.builder("gitlab_mirror_project_commit_changes_total")
                .description("Total number of commit changes per project");

        // Initialize Histogram/Timer (simplified - custom buckets to reduce cardinality)
        this.syncDurationTimer = Timer.builder("gitlab_mirror_sync_duration_seconds")
                .description("Sync operation duration distribution")
                .serviceLevelObjectives(
                    java.time.Duration.ofSeconds(1),
                    java.time.Duration.ofSeconds(5),
                    java.time.Duration.ofSeconds(10),
                    java.time.Duration.ofSeconds(30),
                    java.time.Duration.ofSeconds(60),
                    java.time.Duration.ofSeconds(300),
                    java.time.Duration.ofSeconds(600)
                )  // Only 7 buckets instead of default ~20
                .register(registry);

        this.projectBranchesDistribution = DistributionSummary.builder("gitlab_mirror_project_branches")
                .description("Distribution of branch counts across projects")
                .baseUnit("branches")
                .serviceLevelObjectives(10, 50, 100, 500, 1000)  // Only 5 buckets
                .register(registry);

        // Initialize Summary
        this.syncDelaySummary = DistributionSummary.builder("gitlab_mirror_sync_delay_seconds")
                .description("Sync delay distribution")
                .baseUnit("seconds")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(registry);

        // Initialize Gauges for projects by status
        for (String status : new String[]{"active", "pending", "missing", "failed", "deleted", "warning", "target_created", "mirror_configured"}) {
            Gauge.builder("gitlab_mirror_projects_by_status", projectsByStatusGauges,
                            map -> map.getOrDefault(status, 0.0))
                    .tag("status", status)
                    .description("Number of projects in each status")
                    .register(registry);
        }

        // Initialize Gauges for delayed projects
        for (String level : new String[]{"1h", "6h", "1d", "3d", "7d"}) {
            Gauge.builder("gitlab_mirror_delayed_projects", delayedProjectsGauges,
                            map -> map.getOrDefault(level, 0.0))
                    .tag("delay_level", level)
                    .description("Number of projects delayed by time level")
                    .register(registry);
        }

        log.info("GitLab Mirror Metrics Collector initialized");
    }

    // ==================== Counter Methods ====================

    /**
     * Increment sync event counter
     *
     * @param eventType Event type (sync_finished, sync_failed, task_blocked, etc.)
     * @param status    Event status (success, failed)
     * @param projectKey Project key
     */
    public void incrementSyncEvent(String eventType, String status, String projectKey) {
        syncEventsCounter
                .tag("event_type", eventType)
                .tag("status", status)
                .tag("project_key", projectKey)
                .register(registry)
                .increment();
    }

    /**
     * Increment branch change counter
     *
     * @param changeType Change type (created, updated, deleted)
     * @param projectKey Project key
     * @param count      Number of changes
     */
    public void incrementBranchChange(String changeType, String projectKey, int count) {
        if (count <= 0) return;

        branchChangesCounter
                .tag("change_type", changeType)
                .tag("project_key", projectKey)
                .register(registry)
                .increment(count);
    }

    /**
     * Increment API call counter
     *
     * @param apiType API type (source, target)
     * @param status  Call status (success, failed)
     */
    public void incrementApiCall(String apiType, String status) {
        apiCallsCounter
                .tag("api_type", apiType)
                .tag("status", status)
                .register(registry)
                .increment();
    }

    // ==================== Gauge Methods ====================

    /**
     * Update projects by status counts
     *
     * @param statusCounts Map of status to count
     */
    public void updateProjectsByStatus(Map<String, Long> statusCounts) {
        statusCounts.forEach((status, count) ->
                projectsByStatusGauges.put(status, count.doubleValue()));

        log.debug("Updated projects by status: {}", statusCounts);
    }

    /**
     * Update delayed projects counts
     *
     * @param delayLevelCounts Map of delay level to count
     */
    public void updateDelayedProjects(Map<String, Integer> delayLevelCounts) {
        delayLevelCounts.forEach((level, count) ->
                delayedProjectsGauges.put(level, count.doubleValue()));

        log.debug("Updated delayed projects: {}", delayLevelCounts);
    }

    /**
     * Update project-specific metrics
     *
     * @param projectKey         Project key
     * @param delaySeconds       Current delay in seconds
     * @param lastSyncTimestamp  Last sync timestamp (Unix time)
     * @param consecutiveFailures Number of consecutive failures
     */
    public void updateProjectMetrics(String projectKey, long delaySeconds,
                                      long lastSyncTimestamp, int consecutiveFailures) {
        ProjectMetrics metrics = projectMetricsMap.computeIfAbsent(projectKey, k -> {
            ProjectMetrics pm = new ProjectMetrics();

            // Register gauges for this project
            Gauge.builder("gitlab_mirror_project_delay_seconds", pm, ProjectMetrics::getDelaySeconds)
                    .tag("project_key", projectKey)
                    .description("Current sync delay for project in seconds")
                    .register(registry);

            Gauge.builder("gitlab_mirror_last_sync_timestamp", pm, ProjectMetrics::getLastSyncTimestamp)
                    .tag("project_key", projectKey)
                    .description("Last sync timestamp for project")
                    .register(registry);

            Gauge.builder("gitlab_mirror_consecutive_failures", pm, ProjectMetrics::getConsecutiveFailures)
                    .tag("project_key", projectKey)
                    .description("Consecutive failure count for project")
                    .register(registry);

            return pm;
        });

        metrics.setDelaySeconds(delaySeconds);
        metrics.setLastSyncTimestamp(lastSyncTimestamp);
        metrics.setConsecutiveFailures(consecutiveFailures);
    }

    /**
     * Remove project metrics (when project is deleted or not tracked)
     *
     * @param projectKey Project key
     */
    public void removeProjectMetrics(String projectKey) {
        projectMetricsMap.remove(projectKey);
        // Note: Micrometer meters cannot be removed, they will expire after TTL
        log.debug("Removed project metrics for: {}", projectKey);
    }

    // ==================== Histogram/Distribution Methods ====================

    /**
     * Record sync duration
     *
     * @param seconds Duration in seconds
     */
    public void recordSyncDuration(double seconds) {
        syncDurationTimer.record(java.time.Duration.ofMillis((long) (seconds * 1000)));
    }

    /**
     * Record project branch count
     *
     * @param branchCount Number of branches
     */
    public void recordProjectBranches(int branchCount) {
        projectBranchesDistribution.record(branchCount);
    }

    /**
     * Record sync delay
     *
     * @param seconds Delay in seconds
     */
    public void recordSyncDelay(double seconds) {
        syncDelaySummary.record(seconds);
    }

    // ==================== Project-Level Metrics Methods ====================

    /**
     * Record project-level sync event
     *
     * @param projectKey Project key
     * @param category   Event category (webhook, scheduled, manual, etc.)
     * @param status     Event status (success, failed, pending)
     */
    public void recordProjectSyncEvent(String projectKey, String category, String status) {
        projectSyncEventsCounter
                .tag("project_key", projectKey)
                .tag("category", category)
                .tag("status", status)
                .register(registry)
                .increment();
    }

    /**
     * Record project-level sync task execution
     *
     * @param projectKey Project key
     * @param taskType   Task type (pull, push, mirror_setup, etc.)
     * @param status     Task status (success, failed, timeout, etc.)
     */
    public void recordProjectSyncTask(String projectKey, String taskType, String status) {
        projectSyncTasksCounter
                .tag("project_key", projectKey)
                .tag("task_type", taskType)
                .tag("status", status)
                .register(registry)
                .increment();
    }

    /**
     * Record project-level branch changes
     *
     * @param projectKey     Project key
     * @param operationType  Operation type (created, updated, deleted)
     * @param count          Number of branches changed
     */
    public void recordProjectBranchChanges(String projectKey, String operationType, int count) {
        if (count <= 0) return;

        projectBranchChangesCounter
                .tag("project_key", projectKey)
                .tag("operation", operationType)
                .register(registry)
                .increment(count);
    }

    /**
     * Record project-level commit changes
     *
     * @param projectKey Project key
     * @param count      Number of commits pushed
     */
    public void recordProjectCommitChanges(String projectKey, int count) {
        if (count <= 0) return;

        projectCommitChangesCounter
                .tag("project_key", projectKey)
                .register(registry)
                .increment(count);
    }

    /**
     * Update project delay gauge
     * Creates a gauge for the project if it doesn't exist
     *
     * @param projectKey   Project key
     * @param delaySeconds Current delay in seconds
     */
    public void updateProjectDelay(String projectKey, long delaySeconds) {
        projectDelayGauges.put(projectKey, (double) delaySeconds);

        // Register gauge if not exists (gauges are registered lazily)
        Gauge.builder("gitlab_mirror_project_delay_seconds", projectDelayGauges,
                        map -> map.getOrDefault(projectKey, 0.0))
                .tag("project_key", projectKey)
                .description("Current sync delay for project in seconds")
                .register(registry);
    }

    /**
     * Record comprehensive sync event with statistics
     * This method records multiple metrics from a single sync event
     *
     * @param projectKey  Project key
     * @param eventType   Event type (sync_finished, sync_failed, etc.)
     * @param status      Event status (success, failed)
     * @param category    Event category (webhook, scheduled, manual)
     * @param taskType    Task type (pull, push, etc.)
     * @param branchesCreated  Number of branches created
     * @param branchesUpdated  Number of branches updated
     * @param branchesDeleted  Number of branches deleted
     * @param commitsPushed    Number of commits pushed
     * @param delaySeconds     Current delay in seconds
     */
    public void recordComprehensiveSyncMetrics(String projectKey, String eventType, String status,
                                                String category, String taskType,
                                                int branchesCreated, int branchesUpdated,
                                                int branchesDeleted, int commitsPushed,
                                                long delaySeconds) {
        // Record sync event
        recordProjectSyncEvent(projectKey, category, status);

        // Record sync task
        if (taskType != null && !taskType.isEmpty()) {
            recordProjectSyncTask(projectKey, taskType, status);
        }

        // Record branch changes
        recordProjectBranchChanges(projectKey, "created", branchesCreated);
        recordProjectBranchChanges(projectKey, "updated", branchesUpdated);
        recordProjectBranchChanges(projectKey, "deleted", branchesDeleted);

        // Record commit changes
        recordProjectCommitChanges(projectKey, commitsPushed);

        // Update delay
        updateProjectDelay(projectKey, delaySeconds);
    }

    // ==================== Helper Classes ====================

    /**
     * Project-specific metrics holder
     */
    private static class ProjectMetrics {
        private volatile long delaySeconds = 0;
        private volatile long lastSyncTimestamp = 0;
        private volatile int consecutiveFailures = 0;

        public double getDelaySeconds() {
            return delaySeconds;
        }

        public void setDelaySeconds(long delaySeconds) {
            this.delaySeconds = delaySeconds;
        }

        public double getLastSyncTimestamp() {
            return lastSyncTimestamp;
        }

        public void setLastSyncTimestamp(long lastSyncTimestamp) {
            this.lastSyncTimestamp = lastSyncTimestamp;
        }

        public double getConsecutiveFailures() {
            return consecutiveFailures;
        }

        public void setConsecutiveFailures(int consecutiveFailures) {
            this.consecutiveFailures = consecutiveFailures;
        }
    }

    /**
     * Get current metric values for debugging/testing
     */
    public Map<String, Double> getProjectsByStatus() {
        return Map.copyOf(projectsByStatusGauges);
    }

    public Map<String, Double> getDelayedProjects() {
        return Map.copyOf(delayedProjectsGauges);
    }
}
