package com.gitlab.mirror.server.service.metrics;

import com.gitlab.mirror.server.controller.dto.ProjectListDTO;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.service.ProjectListService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Metrics Collection Scheduler
 * <p>
 * Periodically collects metrics from database and updates Prometheus gauges
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Component
public class MetricsScheduler {

    private final GitLabMirrorMetricsCollector metricsCollector;
    private final SyncProjectMapper syncProjectMapper;
    private final ProjectListService projectListService;

    public MetricsScheduler(GitLabMirrorMetricsCollector metricsCollector,
                            SyncProjectMapper syncProjectMapper,
                            ProjectListService projectListService) {
        this.metricsCollector = metricsCollector;
        this.syncProjectMapper = syncProjectMapper;
        this.projectListService = projectListService;
    }

    /**
     * Collect project metrics every minute
     */
    @Scheduled(fixedRate = 60000, initialDelay = 10000)
    public void collectProjectMetrics() {
        try {
            log.debug("Starting metrics collection");

            // 1. Count projects by status
            List<SyncProject> allProjects = syncProjectMapper.selectList(null);
            Map<String, Long> statusCounts = allProjects.stream()
                    .collect(Collectors.groupingBy(
                            p -> p.getSyncStatus() != null ? p.getSyncStatus() : "unknown",
                            Collectors.counting()
                    ));

            metricsCollector.updateProjectsByStatus(statusCounts);
            log.debug("Updated status counts: {}", statusCounts);

            // 2. Calculate delayed projects by level
            Map<String, Integer> delayedCounts = calculateDelayedProjects(allProjects);
            metricsCollector.updateDelayedProjects(delayedCounts);
            log.debug("Updated delayed counts: {}", delayedCounts);

            // 3. Update project-specific metrics
            for (SyncProject project : allProjects) {
                if (project.getProjectKey() == null) continue;

                // Use ProjectListService to get accurate delay calculation (based on branch snapshots)
                ProjectListDTO dto = projectListService.buildProjectListDTO(project);

                // Get delay from DTO (based on branch commit times, same as API)
                long delaySeconds = dto.getDelaySeconds() != null ? dto.getDelaySeconds() : 0;

                // Get last sync timestamp
                long lastSyncTimestamp = 0;
                if (project.getLastSyncAt() != null) {
                    lastSyncTimestamp = project.getLastSyncAt()
                            .atZone(ZoneId.systemDefault())
                            .toEpochSecond();
                }

                // Get consecutive failures from DTO
                int consecutiveFailures = dto.getConsecutiveFailures() != null ? dto.getConsecutiveFailures() : 0;

                metricsCollector.updateProjectMetrics(
                        project.getProjectKey(),
                        delaySeconds,
                        lastSyncTimestamp,
                        consecutiveFailures
                );

                // Record delay distribution (sample every 10th project to reduce overhead)
                if (delaySeconds > 0 && project.getId() % 10 == 0) {
                    metricsCollector.recordSyncDelay(delaySeconds);
                }
            }

            log.debug("Metrics collection completed");

        } catch (Exception e) {
            log.error("Failed to collect metrics", e);
        }
    }

    /**
     * Calculate number of delayed projects by time level
     *
     * @param projects All projects
     * @return Map of delay level to count
     */
    private Map<String, Integer> calculateDelayedProjects(List<SyncProject> projects) {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("1h", 0);
        counts.put("6h", 0);
        counts.put("1d", 0);
        counts.put("3d", 0);
        counts.put("7d", 0);

        LocalDateTime now = LocalDateTime.now();

        for (SyncProject project : projects) {
            if (project.getLastSyncAt() == null) continue;

            LocalDateTime lastSync = project.getLastSyncAt();
            long hoursDiff = java.time.Duration.between(lastSync, now).toHours();

            if (hoursDiff >= 168) { // 7 days
                counts.put("7d", counts.get("7d") + 1);
            } else if (hoursDiff >= 72) { // 3 days
                counts.put("3d", counts.get("3d") + 1);
            } else if (hoursDiff >= 24) { // 1 day
                counts.put("1d", counts.get("1d") + 1);
            } else if (hoursDiff >= 6) { // 6 hours
                counts.put("6h", counts.get("6h") + 1);
            } else if (hoursDiff >= 1) { // 1 hour
                counts.put("1h", counts.get("1h") + 1);
            }
        }

        return counts;
    }
}
