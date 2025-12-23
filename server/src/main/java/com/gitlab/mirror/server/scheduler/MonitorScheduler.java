package com.gitlab.mirror.server.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gitlab.mirror.server.entity.MonitorAlert;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.MonitorAlertMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.service.monitor.DiffCalculator;
import com.gitlab.mirror.server.service.monitor.LocalCacheManager;
import com.gitlab.mirror.server.service.monitor.SyncMonitorService;
import com.gitlab.mirror.server.service.monitor.UnifiedProjectMonitor;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import com.gitlab.mirror.server.service.monitor.model.ScanResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monitor Scheduler
 * <p>
 * Schedules incremental scans, full reconciliation, and auto-resolve alerts
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "gitlab.mirror.monitor.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonitorScheduler {

    private static final String INCREMENTAL_LOCK_KEY = "scheduler:incremental:lock";
    private static final String FULL_SCAN_LOCK_KEY = "scheduler:full:lock";
    private static final String AUTO_RESOLVE_LOCK_KEY = "scheduler:auto_resolve:lock";
    private static final long LOCK_TTL_MINUTES = 10;
    private static final long FULL_SCAN_LOCK_TTL_MINUTES = 60; // Full scan may take longer

    private final UnifiedProjectMonitor unifiedProjectMonitor;
    private final SyncMonitorService syncMonitorService;
    private final LocalCacheManager cacheManager;
    private final MonitorAlertMapper monitorAlertMapper;
    private final SyncProjectMapper syncProjectMapper;
    private final DiffCalculator diffCalculator;

    public MonitorScheduler(
            UnifiedProjectMonitor unifiedProjectMonitor,
            SyncMonitorService syncMonitorService,
            LocalCacheManager cacheManager,
            MonitorAlertMapper monitorAlertMapper,
            SyncProjectMapper syncProjectMapper,
            DiffCalculator diffCalculator) {
        this.unifiedProjectMonitor = unifiedProjectMonitor;
        this.syncMonitorService = syncMonitorService;
        this.cacheManager = cacheManager;
        this.monitorAlertMapper = monitorAlertMapper;
        this.syncProjectMapper = syncProjectMapper;
        this.diffCalculator = diffCalculator;
    }

    /**
     * Incremental scan every 5 minutes
     * <p>
     * Scans projects incrementally to detect sync status changes
     */
    @Scheduled(fixedDelayString = "${gitlab.mirror.monitor.incremental-interval:300000}")
    @ConditionalOnProperty(prefix = "gitlab.mirror.monitor.scheduler", name = "incremental-enabled", havingValue = "true", matchIfMissing = true)
    public void incrementalScan() {
        // Acquire distributed lock
        if (!tryAcquireLock(INCREMENTAL_LOCK_KEY)) {
            log.debug("Incremental scan already running, skipping");
            return;
        }

        LocalDateTime startTime = LocalDateTime.now();
        log.info("=== Incremental Scan Started ===");

        try {
            // Execute incremental scan
            ScanResult result = unifiedProjectMonitor.scan("incremental");

            // Query active alerts
            QueryWrapper<MonitorAlert> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("status", MonitorAlert.Status.ACTIVE);
            long activeAlerts = monitorAlertMapper.selectCount(queryWrapper);

            // Log scan results
            long durationMs = result.getDurationMs();
            log.info("=== Incremental Scan Completed ===");
            log.info("Projects scanned: {}", result.getProjectsScanned());
            log.info("Projects updated: {}", result.getProjectsUpdated());
            log.info("Changes detected: {}", result.getChangesDetected());
            log.info("Active alerts: {}", activeAlerts);
            log.info("Duration: {}ms", durationMs);

            // Record metrics
            recordScanMetrics("incremental", result);

        } catch (Exception e) {
            log.error("Incremental scan failed", e);
        } finally {
            releaseLock(INCREMENTAL_LOCK_KEY);
        }
    }

    /**
     * Full scan and reconciliation daily at 2:00 AM
     * <p>
     * Performs comprehensive scan to detect new/deleted projects and data inconsistencies
     */
    @Scheduled(cron = "${gitlab.mirror.monitor.full-scan-cron:0 0 2 * * ?}")
    @ConditionalOnProperty(prefix = "gitlab.mirror.monitor.scheduler", name = "full-scan-enabled", havingValue = "true", matchIfMissing = true)
    public void fullScan() {
        // Acquire distributed lock
        if (!tryAcquireLock(FULL_SCAN_LOCK_KEY, FULL_SCAN_LOCK_TTL_MINUTES)) {
            log.debug("Full scan already running, skipping");
            return;
        }

        LocalDateTime startTime = LocalDateTime.now();
        log.info("=== Full Scan Started ===");

        try {
            // Execute full scan
            ScanResult result = unifiedProjectMonitor.scan("full");

            // Query active alerts
            QueryWrapper<MonitorAlert> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("status", MonitorAlert.Status.ACTIVE);
            long activeAlerts = monitorAlertMapper.selectCount(queryWrapper);

            // Log scan results
            long durationMs = result.getDurationMs();
            log.info("=== Full Scan Completed ===");
            log.info("Projects scanned: {}", result.getProjectsScanned());
            log.info("Projects updated: {}", result.getProjectsUpdated());
            log.info("New projects discovered: {}", result.getNewProjects());
            log.info("Changes detected: {}", result.getChangesDetected());
            log.info("Active alerts: {}", activeAlerts);
            log.info("Duration: {}ms", durationMs);

            // Record metrics
            recordScanMetrics("full", result);

            // Generate reconciliation report
            generateReconciliationReport(result, activeAlerts);

        } catch (Exception e) {
            log.error("Full scan failed", e);
        } finally {
            releaseLock(FULL_SCAN_LOCK_KEY);
        }
    }

    /**
     * Auto-resolve alerts every 10 minutes
     * <p>
     * Checks active alerts and auto-resolves if issues are fixed
     */
    @Scheduled(fixedDelayString = "${gitlab.mirror.monitor.auto-resolve-interval:600000}")
    @ConditionalOnProperty(prefix = "gitlab.mirror.monitor.scheduler", name = "auto-resolve-enabled", havingValue = "true", matchIfMissing = true)
    public void autoResolveAlerts() {
        // Acquire distributed lock
        if (!tryAcquireLock(AUTO_RESOLVE_LOCK_KEY)) {
            log.debug("Auto-resolve already running, skipping");
            return;
        }

        log.info("=== Auto-Resolve Alerts Started ===");
        int resolvedCount = 0;

        try {
            // Query all sync projects and calculate diffs
            List<SyncProject> syncProjects = syncProjectMapper.selectList(null);

            // Calculate diffs for all projects
            List<ProjectDiff> diffs = syncProjects.stream()
                .map(project -> diffCalculator.calculateDiff(project.getId()))
                .filter(diff -> diff != null)
                .toList();

            // Auto-resolve alerts
            resolvedCount = syncMonitorService.autoResolveAlerts(diffs);

            log.info("=== Auto-Resolve Alerts Completed ===");
            log.info("Alerts resolved: {}", resolvedCount);

        } catch (Exception e) {
            log.error("Auto-resolve alerts failed", e);
        } finally {
            releaseLock(AUTO_RESOLVE_LOCK_KEY);
        }
    }

    /**
     * Cleanup expired alerts weekly
     * <p>
     * Removes resolved alerts older than 30 days
     */
    @Scheduled(cron = "0 0 3 * * MON")
    @ConditionalOnProperty(prefix = "gitlab.mirror.monitor.scheduler", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
    public void cleanupExpiredAlerts() {
        log.info("=== Cleanup Expired Alerts Started ===");

        try {
            // Delete alerts resolved more than 30 days ago
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);

            QueryWrapper<MonitorAlert> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("status", MonitorAlert.Status.RESOLVED);
            queryWrapper.lt("resolved_at", cutoffDate);

            int deleted = monitorAlertMapper.delete(queryWrapper);

            log.info("=== Cleanup Expired Alerts Completed ===");
            log.info("Alerts deleted: {}", deleted);

        } catch (Exception e) {
            log.error("Cleanup expired alerts failed", e);
        }
    }

    /**
     * Try to acquire distributed lock
     *
     * @param lockKey Lock key
     * @return true if lock acquired successfully
     */
    private boolean tryAcquireLock(String lockKey) {
        return tryAcquireLock(lockKey, LOCK_TTL_MINUTES);
    }

    /**
     * Try to acquire distributed lock with custom TTL
     *
     * @param lockKey Lock key
     * @param ttlMinutes Lock TTL in minutes
     * @return true if lock acquired successfully
     */
    private boolean tryAcquireLock(String lockKey, long ttlMinutes) {
        String existingLock = cacheManager.get(lockKey);
        if (existingLock != null) {
            return false; // Lock already held
        }

        // Set lock with TTL
        String lockValue = Thread.currentThread().getName() + ":" + System.currentTimeMillis();
        cacheManager.put(lockKey, lockValue, ttlMinutes);
        return true;
    }

    /**
     * Release distributed lock
     *
     * @param lockKey Lock key
     */
    private void releaseLock(String lockKey) {
        cacheManager.remove(lockKey);
    }

    /**
     * Record scan metrics
     *
     * @param scanType Scan type (incremental/full)
     * @param result Scan result
     */
    private void recordScanMetrics(String scanType, ScanResult result) {
        // Cache scan statistics
        String statsKey = "scan:stats:" + scanType;
        cacheManager.put(statsKey, result, 60); // Cache for 60 minutes

        log.debug("Scan metrics recorded: type={}, duration={}ms", scanType, result.getDurationMs());
    }

    /**
     * Generate reconciliation report
     *
     * @param result Scan result
     * @param activeAlerts Active alert count
     */
    private void generateReconciliationReport(ScanResult result, long activeAlerts) {
        StringBuilder report = new StringBuilder();
        report.append("\n");
        report.append("╔════════════════════════════════════════════════════════════╗\n");
        report.append("║           Daily Reconciliation Report                     ║\n");
        report.append("╠════════════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ Scan Type:          %-38s ║\n", result.getScanType()));
        report.append(String.format("║ Start Time:         %-38s ║\n", result.getStartTime()));
        report.append(String.format("║ End Time:           %-38s ║\n", result.getEndTime()));
        report.append(String.format("║ Duration:           %-38s ║\n", result.getDurationMs() + "ms"));
        report.append("╠════════════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ Projects Scanned:   %-38s ║\n", result.getProjectsScanned()));
        report.append(String.format("║ Projects Updated:   %-38s ║\n", result.getProjectsUpdated()));
        report.append(String.format("║ New Projects:       %-38s ║\n", result.getNewProjects()));
        report.append(String.format("║ Changes Detected:   %-38s ║\n", result.getChangesDetected()));
        report.append(String.format("║ Active Alerts:      %-38s ║\n", activeAlerts));
        report.append("╠════════════════════════════════════════════════════════════╣\n");
        report.append(String.format("║ Status:             %-38s ║\n", result.getStatus()));
        if (result.getErrorMessage() != null) {
            String errorMsg = result.getErrorMessage();
            if (errorMsg.length() > 38) {
                errorMsg = errorMsg.substring(0, 35) + "...";
            }
            report.append(String.format("║ Error:              %-38s ║\n", errorMsg));
        }
        report.append("╚════════════════════════════════════════════════════════════╝\n");

        log.info(report.toString());

        // Cache the report
        cacheManager.put("reconciliation:report:latest", report.toString(), 24 * 60); // 24 hours
    }
}
