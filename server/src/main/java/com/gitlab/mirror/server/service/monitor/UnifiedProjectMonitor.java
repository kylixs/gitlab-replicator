package com.gitlab.mirror.server.service.monitor;

import com.gitlab.mirror.common.model.GitLabProject;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import com.gitlab.mirror.server.service.monitor.model.ScanResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Unified Project Monitor Service
 * <p>
 * Orchestrates the project scanning and monitoring workflow.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class UnifiedProjectMonitor {

    private final BatchQueryExecutor batchQueryExecutor;
    private final UpdateProjectDataService updateProjectDataService;
    private final DiffCalculator diffCalculator;
    private final LocalCacheManager cacheManager;
    private final SyncProjectMapper syncProjectMapper;
    private final MetricsExporter metricsExporter;

    public UnifiedProjectMonitor(
            BatchQueryExecutor batchQueryExecutor,
            UpdateProjectDataService updateProjectDataService,
            DiffCalculator diffCalculator,
            LocalCacheManager cacheManager,
            SyncProjectMapper syncProjectMapper,
            MetricsExporter metricsExporter) {
        this.batchQueryExecutor = batchQueryExecutor;
        this.updateProjectDataService = updateProjectDataService;
        this.diffCalculator = diffCalculator;
        this.cacheManager = cacheManager;
        this.syncProjectMapper = syncProjectMapper;
        this.metricsExporter = metricsExporter;
    }

    /**
     * Scan projects and calculate differences
     *
     * @param scanType Scan type (incremental or full)
     * @return Scan result statistics
     */
    @Transactional(rollbackFor = Exception.class)
    public ScanResult scan(String scanType) {
        log.info("Starting {} scan", scanType);
        LocalDateTime startTime = LocalDateTime.now();

        ScanResult.ScanResultBuilder resultBuilder = ScanResult.builder()
                .scanType(scanType)
                .startTime(startTime)
                .status("success");

        try {
            // Step 1: Query source projects
            long step1Start = System.currentTimeMillis();
            LocalDateTime updatedAfter = "incremental".equals(scanType) ? getLastScanTime() : null;
            List<GitLabProject> sourceProjects = batchQueryExecutor.querySourceProjects(updatedAfter, 100);
            long step1Duration = System.currentTimeMillis() - step1Start;
            log.info("[SCAN-PERF] Step 1: Query {} source projects - {}ms", sourceProjects.size(), step1Duration);

            if (sourceProjects.isEmpty()) {
                log.info("No projects to scan");
                return buildEmptyResult(resultBuilder, startTime);
            }

            // Step 2: Get project details using GraphQL batch query (Two-stage optimization - Stage 1)
            long step2Start = System.currentTimeMillis();
            List<com.gitlab.mirror.server.client.graphql.GraphQLProjectInfo> graphQLInfos =
                    batchQueryExecutor.getProjectDetailsBatchGraphQL(sourceProjects);
            long step2Duration = System.currentTimeMillis() - step2Start;
            log.info("[SCAN-PERF] Step 2: GraphQL batch query {} source projects - {}ms", sourceProjects.size(), step2Duration);

            // Convert to map for easy lookup
            Map<Long, com.gitlab.mirror.server.client.graphql.GraphQLProjectInfo> graphQLMap = graphQLInfos.stream()
                    .collect(Collectors.toMap(
                            com.gitlab.mirror.server.client.graphql.GraphQLProjectInfo::getProjectId,
                            info -> info
                    ));

            // Step 3: Update project data in database using GraphQL data
            long step3Start = System.currentTimeMillis();
            UpdateProjectDataService.UpdateResult updateResult =
                    updateProjectDataService.updateSourceProjectsFromGraphQL(sourceProjects, graphQLMap);
            long step3Duration = System.currentTimeMillis() - step3Start;
            log.info("[SCAN-PERF] Step 3: Update {} source projects to DB - {}ms", updateResult.getSuccessCount(), step3Duration);

            // Step 4: Query target projects - optimized version
            long step4Start = System.currentTimeMillis();
            List<GitLabProject> targetProjects = batchQueryExecutor.queryTargetProjects(updatedAfter, 100);
            long step4Duration = System.currentTimeMillis() - step4Start;
            log.info("[SCAN-PERF] Step 4: Query {} target projects - {}ms", targetProjects.size(), step4Duration);

            // Step 5: Get target project details
            long step5Start = System.currentTimeMillis();
            List<BatchQueryExecutor.ProjectDetails> targetDetails =
                    batchQueryExecutor.getProjectDetailsBatchOptimized(targetProjects, batchQueryExecutor.getTargetClient());
            long step5Duration = System.currentTimeMillis() - step5Start;
            log.info("[SCAN-PERF] Step 5: Get {} target project details - {}ms", targetProjects.size(), step5Duration);

            Map<Long, BatchQueryExecutor.ProjectDetails> targetDetailsMap = targetDetails.stream()
                    .collect(Collectors.toMap(
                            BatchQueryExecutor.ProjectDetails::getProjectId,
                            d -> d
                    ));

            // Step 6: Update target projects
            long step6Start = System.currentTimeMillis();
            UpdateProjectDataService.UpdateResult targetUpdateResult =
                    updateProjectDataService.updateTargetProjects(targetProjects, targetDetailsMap);
            long step6Duration = System.currentTimeMillis() - step6Start;
            log.info("[SCAN-PERF] Step 6: Update {} target projects to DB - {}ms", targetUpdateResult.getSuccessCount(), step6Duration);

            // Step 7: Calculate differences for all sync projects
            long step7Start = System.currentTimeMillis();
            List<SyncProject> syncProjects = syncProjectMapper.selectList(null);
            List<Long> syncProjectIds = syncProjects.stream()
                    .map(SyncProject::getId)
                    .collect(Collectors.toList());

            List<ProjectDiff> diffs = diffCalculator.calculateDiffBatch(syncProjectIds);
            long step7Duration = System.currentTimeMillis() - step7Start;
            log.info("[SCAN-PERF] Step 7: Calculate {} project diffs - {}ms", diffs.size(), step7Duration);

            // Step 8: Cache diff results
            long step8Start = System.currentTimeMillis();
            for (ProjectDiff diff : diffs) {
                cacheManager.put("diff:" + diff.getProjectKey(), diff, 15);
            }
            long step8Duration = System.currentTimeMillis() - step8Start;
            log.info("[SCAN-PERF] Step 8: Cache {} diff results - {}ms", diffs.size(), step8Duration);

            // Count changes
            int changesDetected = (int) diffs.stream()
                    .filter(d -> d.getStatus() != ProjectDiff.SyncStatus.SYNCED)
                    .count();

            // Update last scan time
            updateLastScanTime(LocalDateTime.now());

            // Build result
            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = Duration.between(startTime, endTime).toMillis();

            // Step 9: Update metrics
            long step9Start = System.currentTimeMillis();
            metricsExporter.recordScanDuration(durationMs);
            metricsExporter.refreshSystemMetrics();
            metricsExporter.refreshProjectMetrics();
            long step9Duration = System.currentTimeMillis() - step9Start;
            log.info("[SCAN-PERF] Step 9: Update metrics - {}ms", step9Duration);

            // Performance summary
            log.info("[SCAN-PERF] === SCAN PERFORMANCE SUMMARY ===");
            log.info("[SCAN-PERF] Total Duration: {}ms", durationMs);
            log.info("[SCAN-PERF] Step 1 (Query Source):      {}ms ({} %)", step1Duration, String.format("%.1f", step1Duration * 100.0 / durationMs));
            log.info("[SCAN-PERF] Step 2 (Source Details):    {}ms ({} %)", step2Duration, String.format("%.1f", step2Duration * 100.0 / durationMs));
            log.info("[SCAN-PERF] Step 3 (Update Source):     {}ms ({} %)", step3Duration, String.format("%.1f", step3Duration * 100.0 / durationMs));
            log.info("[SCAN-PERF] Step 4 (Query Target):      {}ms ({} %)", step4Duration, String.format("%.1f", step4Duration * 100.0 / durationMs));
            log.info("[SCAN-PERF] Step 5 (Target Details):    {}ms ({} %)", step5Duration, String.format("%.1f", step5Duration * 100.0 / durationMs));
            log.info("[SCAN-PERF] Step 6 (Update Target):     {}ms ({} %)", step6Duration, String.format("%.1f", step6Duration * 100.0 / durationMs));
            log.info("[SCAN-PERF] Step 7 (Calculate Diff):    {}ms ({} %)", step7Duration, String.format("%.1f", step7Duration * 100.0 / durationMs));
            log.info("[SCAN-PERF] Step 8 (Cache Results):     {}ms ({} %)", step8Duration, String.format("%.1f", step8Duration * 100.0 / durationMs));
            log.info("[SCAN-PERF] Step 9 (Metrics):           {}ms ({} %)", step9Duration, String.format("%.1f", step9Duration * 100.0 / durationMs));
            log.info("[SCAN-PERF] ================================");

            return resultBuilder
                    .durationMs(durationMs)
                    .projectsScanned(sourceProjects.size())
                    .projectsUpdated(updateResult.getSuccessCount() + targetUpdateResult.getSuccessCount())
                    .newProjects(0) // Will be updated by ProjectDiscoveryService
                    .changesDetected(changesDetected)
                    .endTime(endTime)
                    .build();

        } catch (Exception e) {
            log.error("Scan failed", e);
            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = Duration.between(startTime, endTime).toMillis();

            return resultBuilder
                    .status("failed")
                    .errorMessage(e.getMessage())
                    .durationMs(durationMs)
                    .endTime(endTime)
                    .build();
        }
    }

    /**
     * Get last scan time from cache
     */
    private LocalDateTime getLastScanTime() {
        LocalDateTime lastScan = cacheManager.get("last_scan_time");
        return lastScan != null ? lastScan : LocalDateTime.now().minusHours(1);
    }

    /**
     * Update last scan time in cache
     */
    private void updateLastScanTime(LocalDateTime time) {
        cacheManager.put("last_scan_time", time, 60 * 24); // 24 hours TTL
    }

    /**
     * Build empty result when no projects to scan
     */
    private ScanResult buildEmptyResult(ScanResult.ScanResultBuilder builder, LocalDateTime startTime) {
        LocalDateTime endTime = LocalDateTime.now();
        long durationMs = Duration.between(startTime, endTime).toMillis();

        return builder
                .durationMs(durationMs)
                .projectsScanned(0)
                .projectsUpdated(0)
                .newProjects(0)
                .changesDetected(0)
                .endTime(endTime)
                .build();
    }
}
