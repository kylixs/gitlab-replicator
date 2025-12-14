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

    public UnifiedProjectMonitor(
            BatchQueryExecutor batchQueryExecutor,
            UpdateProjectDataService updateProjectDataService,
            DiffCalculator diffCalculator,
            LocalCacheManager cacheManager,
            SyncProjectMapper syncProjectMapper) {
        this.batchQueryExecutor = batchQueryExecutor;
        this.updateProjectDataService = updateProjectDataService;
        this.diffCalculator = diffCalculator;
        this.cacheManager = cacheManager;
        this.syncProjectMapper = syncProjectMapper;
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
            LocalDateTime updatedAfter = "incremental".equals(scanType) ? getLastScanTime() : null;
            List<GitLabProject> sourceProjects = batchQueryExecutor.querySourceProjects(updatedAfter, 100);
            log.info("Queried {} source projects", sourceProjects.size());

            if (sourceProjects.isEmpty()) {
                log.info("No projects to scan");
                return buildEmptyResult(resultBuilder, startTime);
            }

            // Step 2: Get project details (branches, commits)
            List<Long> projectIds = sourceProjects.stream()
                    .map(GitLabProject::getId)
                    .collect(Collectors.toList());
            List<BatchQueryExecutor.ProjectDetails> projectDetails =
                    batchQueryExecutor.getProjectDetailsBatch(projectIds, batchQueryExecutor.getSourceClient());

            // Convert to map for easy lookup
            Map<Long, BatchQueryExecutor.ProjectDetails> detailsMap = projectDetails.stream()
                    .collect(Collectors.toMap(
                            BatchQueryExecutor.ProjectDetails::getProjectId,
                            d -> d
                    ));

            // Step 3: Update project data in database
            UpdateProjectDataService.UpdateResult updateResult =
                    updateProjectDataService.updateSourceProjects(sourceProjects, detailsMap);
            log.info("Updated {} source projects", updateResult.getSuccessCount());

            // Query target projects
            List<GitLabProject> targetProjects = batchQueryExecutor.queryTargetProjects(updatedAfter, 100);
            List<Long> targetProjectIds = targetProjects.stream()
                    .map(GitLabProject::getId)
                    .collect(Collectors.toList());
            List<BatchQueryExecutor.ProjectDetails> targetDetails =
                    batchQueryExecutor.getProjectDetailsBatch(targetProjectIds, batchQueryExecutor.getTargetClient());
            Map<Long, BatchQueryExecutor.ProjectDetails> targetDetailsMap = targetDetails.stream()
                    .collect(Collectors.toMap(
                            BatchQueryExecutor.ProjectDetails::getProjectId,
                            d -> d
                    ));
            UpdateProjectDataService.UpdateResult targetUpdateResult =
                    updateProjectDataService.updateTargetProjects(targetProjects, targetDetailsMap);
            log.info("Updated {} target projects", targetUpdateResult.getSuccessCount());

            // Step 4: Calculate differences for all sync projects
            List<SyncProject> syncProjects = syncProjectMapper.selectList(null);
            List<Long> syncProjectIds = syncProjects.stream()
                    .map(SyncProject::getId)
                    .collect(Collectors.toList());

            List<ProjectDiff> diffs = diffCalculator.calculateDiffBatch(syncProjectIds);
            log.info("Calculated {} project diffs", diffs.size());

            // Step 5: Cache diff results
            for (ProjectDiff diff : diffs) {
                cacheManager.put("diff:" + diff.getProjectKey(), diff, 15);
            }

            // Step 6: Count changes
            int changesDetected = (int) diffs.stream()
                    .filter(d -> d.getStatus() != ProjectDiff.SyncStatus.SYNCED)
                    .count();

            // Update last scan time
            updateLastScanTime(LocalDateTime.now());

            // Build result
            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = Duration.between(startTime, endTime).toMillis();

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
