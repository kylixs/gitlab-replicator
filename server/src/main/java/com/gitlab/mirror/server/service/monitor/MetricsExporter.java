package com.gitlab.mirror.server.service.monitor;

import com.gitlab.mirror.server.entity.MonitorAlert;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.MonitorAlertMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics Exporter Service
 * <p>
 * Exports system-level and project-level metrics to Prometheus via Micrometer.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class MetricsExporter {

    private final MeterRegistry meterRegistry;
    private final SyncProjectMapper syncProjectMapper;
    private final MonitorAlertMapper monitorAlertMapper;
    private final LocalCacheManager cacheManager;

    // System-level metrics
    private final AtomicInteger totalProjects = new AtomicInteger(0);
    private final Map<String, AtomicInteger> syncStatusCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> alertSeverityCounts = new ConcurrentHashMap<>();
    private final AtomicLong lastScanDurationMs = new AtomicLong(0);
    private final AtomicInteger projectsDiscoveredNew = new AtomicInteger(0);
    private final AtomicInteger projectsDiscoveredUpdated = new AtomicInteger(0);

    // Project-level metrics cache
    private final Map<String, ProjectMetrics> projectMetricsCache = new ConcurrentHashMap<>();

    // Counters
    private Counter apiCallsCounter;

    public MetricsExporter(
            MeterRegistry meterRegistry,
            SyncProjectMapper syncProjectMapper,
            MonitorAlertMapper monitorAlertMapper,
            LocalCacheManager cacheManager) {
        this.meterRegistry = meterRegistry;
        this.syncProjectMapper = syncProjectMapper;
        this.monitorAlertMapper = monitorAlertMapper;
        this.cacheManager = cacheManager;
    }

    /**
     * Initialize metrics
     */
    @PostConstruct
    public void initMetrics() {
        log.info("Initializing Prometheus metrics");

        // System-level gauges
        Gauge.builder("gitlab_mirror_projects_total", totalProjects, AtomicInteger::get)
                .description("Total number of projects being monitored")
                .register(meterRegistry);

        // Sync status gauges
        for (String status : Arrays.asList("pending", "target_created", "mirror_configured", "active", "failed")) {
            syncStatusCounts.putIfAbsent(status, new AtomicInteger(0));
            Gauge.builder("gitlab_mirror_sync_status", syncStatusCounts.get(status), AtomicInteger::get)
                    .description("Number of projects by sync status")
                    .tag("status", status)
                    .register(meterRegistry);
        }

        // Alert severity gauges
        for (String severity : Arrays.asList("critical", "high", "medium", "low")) {
            alertSeverityCounts.putIfAbsent(severity, new AtomicInteger(0));
            Gauge.builder("gitlab_mirror_alerts_active", alertSeverityCounts.get(severity), AtomicInteger::get)
                    .description("Number of active alerts by severity")
                    .tag("severity", severity)
                    .register(meterRegistry);
        }

        // Scan duration gauge
        Gauge.builder("gitlab_mirror_scan_duration_seconds", lastScanDurationMs, val -> val.get() / 1000.0)
                .description("Last scan duration in seconds")
                .register(meterRegistry);

        // Project discovery gauges
        Gauge.builder("gitlab_mirror_projects_discovered", projectsDiscoveredNew, AtomicInteger::get)
                .description("Number of projects discovered")
                .tag("type", "new")
                .register(meterRegistry);

        Gauge.builder("gitlab_mirror_projects_discovered", projectsDiscoveredUpdated, AtomicInteger::get)
                .description("Number of projects discovered")
                .tag("type", "updated")
                .register(meterRegistry);

        // API calls counter
        apiCallsCounter = Counter.builder("gitlab_mirror_api_calls_total")
                .description("Total number of GitLab API calls")
                .tag("instance", "source")
                .register(meterRegistry);

        // Cache metrics
        Gauge.builder("gitlab_mirror_cache_size", cacheManager, LocalCacheManager::size)
                .description("Number of entries in local cache")
                .register(meterRegistry);

        Gauge.builder("gitlab_mirror_cache_hit_rate", cacheManager,
                cm -> cm.getStats().getHitRate())
                .description("Cache hit rate percentage")
                .register(meterRegistry);

        log.info("Prometheus metrics initialized successfully");
    }

    /**
     * Refresh system-level metrics from database
     */
    public void refreshSystemMetrics() {
        log.debug("Refreshing system-level metrics");

        try {
            // Total projects
            Long total = syncProjectMapper.selectCount(null);
            totalProjects.set(total.intValue());

            // Sync status counts
            for (String status : syncStatusCounts.keySet()) {
                QueryWrapper<SyncProject> wrapper = new QueryWrapper<>();
                wrapper.eq("sync_status", status);
                Long count = syncProjectMapper.selectCount(wrapper);
                syncStatusCounts.get(status).set(count.intValue());
            }

            // Alert severity counts
            for (String severity : alertSeverityCounts.keySet()) {
                QueryWrapper<MonitorAlert> wrapper = new QueryWrapper<>();
                wrapper.eq("severity", severity)
                        .eq("status", MonitorAlert.Status.ACTIVE);
                Long count = monitorAlertMapper.selectCount(wrapper);
                alertSeverityCounts.get(severity).set(count.intValue());
            }

            log.debug("System metrics refreshed: total={}, active_alerts={}",
                    totalProjects.get(),
                    alertSeverityCounts.values().stream().mapToInt(AtomicInteger::get).sum());

        } catch (Exception e) {
            log.error("Failed to refresh system metrics", e);
        }
    }

    /**
     * Refresh project-level metrics from cache
     */
    public void refreshProjectMetrics() {
        log.debug("Refreshing project-level metrics");

        try {
            // Get all project diffs from cache
            List<SyncProject> projects = syncProjectMapper.selectList(null);

            for (SyncProject project : projects) {
                String cacheKey = "diff:" + project.getProjectKey();
                ProjectDiff diff = cacheManager.get(cacheKey);

                if (diff != null) {
                    updateProjectMetrics(project.getProjectKey(), diff);
                }
            }

            log.debug("Project metrics refreshed for {} projects", projectMetricsCache.size());

        } catch (Exception e) {
            log.error("Failed to refresh project metrics", e);
        }
    }

    /**
     * Update project-level metrics
     */
    public void updateProjectMetrics(String projectKey, ProjectDiff diff) {
        ProjectMetrics metrics = projectMetricsCache.computeIfAbsent(projectKey,
                key -> new ProjectMetrics(key, meterRegistry));

        if (diff.getSource() != null) {
            metrics.updateSourceMetrics(
                    diff.getSource().getCommitCount(),
                    diff.getSource().getLastActivityAt() != null ?
                            diff.getSource().getLastActivityAt().toEpochSecond(java.time.ZoneOffset.UTC) : 0,
                    diff.getSource().getSizeBytes()
            );
        }

        if (diff.getTarget() != null) {
            metrics.updateTargetMetrics(
                    diff.getTarget().getCommitCount(),
                    diff.getTarget().getLastActivityAt() != null ?
                            diff.getTarget().getLastActivityAt().toEpochSecond(java.time.ZoneOffset.UTC) : 0,
                    diff.getTarget().getSizeBytes()
            );
        }
    }

    /**
     * Record scan duration
     */
    public void recordScanDuration(long durationMs) {
        lastScanDurationMs.set(durationMs);
        log.debug("Recorded scan duration: {}ms", durationMs);
    }

    /**
     * Record project discovery
     */
    public void recordProjectDiscovery(int newCount, int updatedCount) {
        projectsDiscoveredNew.set(newCount);
        projectsDiscoveredUpdated.set(updatedCount);
        log.debug("Recorded project discovery: new={}, updated={}", newCount, updatedCount);
    }

    /**
     * Increment API call counter
     */
    public void incrementApiCalls() {
        apiCallsCounter.increment();
    }

    /**
     * Project-level metrics holder
     */
    private static class ProjectMetrics {
        private final String projectKey;
        private final AtomicInteger sourceCommits = new AtomicInteger(0);
        private final AtomicLong sourceLastCommitTime = new AtomicLong(0);
        private final AtomicLong sourceSizeBytes = new AtomicLong(0);
        private final AtomicInteger targetCommits = new AtomicInteger(0);
        private final AtomicLong targetLastCommitTime = new AtomicLong(0);
        private final AtomicLong targetSizeBytes = new AtomicLong(0);

        public ProjectMetrics(String projectKey, MeterRegistry registry) {
            this.projectKey = projectKey;

            // Register gauges for this project
            Gauge.builder("gitlab_mirror_project_commits", sourceCommits, AtomicInteger::get)
                    .description("Number of commits in project")
                    .tags("project", projectKey, "type", "source")
                    .register(registry);

            Gauge.builder("gitlab_mirror_project_commits", targetCommits, AtomicInteger::get)
                    .description("Number of commits in project")
                    .tags("project", projectKey, "type", "target")
                    .register(registry);

            Gauge.builder("gitlab_mirror_project_last_commit_time", sourceLastCommitTime, AtomicLong::get)
                    .description("Last commit timestamp (Unix epoch)")
                    .tags("project", projectKey, "type", "source")
                    .register(registry);

            Gauge.builder("gitlab_mirror_project_last_commit_time", targetLastCommitTime, AtomicLong::get)
                    .description("Last commit timestamp (Unix epoch)")
                    .tags("project", projectKey, "type", "target")
                    .register(registry);

            Gauge.builder("gitlab_mirror_project_size_bytes", sourceSizeBytes, AtomicLong::get)
                    .description("Repository size in bytes")
                    .tags("project", projectKey, "type", "source")
                    .register(registry);

            Gauge.builder("gitlab_mirror_project_size_bytes", targetSizeBytes, AtomicLong::get)
                    .description("Repository size in bytes")
                    .tags("project", projectKey, "type", "target")
                    .register(registry);
        }

        public void updateSourceMetrics(Integer commits, Long lastCommitTime, Long sizeBytes) {
            if (commits != null) sourceCommits.set(commits);
            if (lastCommitTime != null) sourceLastCommitTime.set(lastCommitTime);
            if (sizeBytes != null) sourceSizeBytes.set(sizeBytes);
        }

        public void updateTargetMetrics(Integer commits, Long lastCommitTime, Long sizeBytes) {
            if (commits != null) targetCommits.set(commits);
            if (lastCommitTime != null) targetLastCommitTime.set(lastCommitTime);
            if (sizeBytes != null) targetSizeBytes.set(sizeBytes);
        }
    }
}
