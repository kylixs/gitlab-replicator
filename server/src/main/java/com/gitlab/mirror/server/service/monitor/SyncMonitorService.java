package com.gitlab.mirror.server.service.monitor;

import com.gitlab.mirror.server.entity.MonitorAlert;
import com.gitlab.mirror.server.mapper.MonitorAlertMapper;
import com.gitlab.mirror.server.service.monitor.model.AlertInfo;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sync Monitor Service
 * <p>
 * Manages alert creation, resolution, and evaluation.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class SyncMonitorService {

    private final AlertThresholdEvaluator alertThresholdEvaluator;
    private final MonitorAlertMapper monitorAlertMapper;
    private final LocalCacheManager cacheManager;

    private static final int ALERT_DEDUP_MINUTES = 60;

    public SyncMonitorService(
            AlertThresholdEvaluator alertThresholdEvaluator,
            MonitorAlertMapper monitorAlertMapper,
            LocalCacheManager cacheManager) {
        this.alertThresholdEvaluator = alertThresholdEvaluator;
        this.monitorAlertMapper = monitorAlertMapper;
        this.cacheManager = cacheManager;
    }

    /**
     * Evaluate projects and create alerts
     *
     * @param diffs List of project diffs
     * @return Number of alerts created
     */
    @Transactional(rollbackFor = Exception.class)
    public int evaluateProjects(List<ProjectDiff> diffs) {
        log.info("Evaluating {} projects for alerts", diffs.size());

        List<AlertInfo> alerts = alertThresholdEvaluator.evaluateThresholdsBatch(diffs);
        log.info("Generated {} alerts from evaluation", alerts.size());

        int createdCount = 0;
        for (AlertInfo alertInfo : alerts) {
            if (createAlert(alertInfo)) {
                createdCount++;
            }
        }

        log.info("Created {} new alerts", createdCount);
        return createdCount;
    }

    /**
     * Create alert with deduplication
     *
     * @param alertInfo Alert information
     * @return true if alert was created, false if deduplicated
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean createAlert(AlertInfo alertInfo) {
        // Check for duplicate active alerts (same project + type within 60 minutes)
        QueryWrapper<MonitorAlert> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("sync_project_id", alertInfo.getSyncProjectId())
                .eq("alert_type", alertInfo.getAlertType().name().toLowerCase())
                .eq("status", MonitorAlert.Status.ACTIVE)
                .ge("triggered_at", LocalDateTime.now().minusMinutes(ALERT_DEDUP_MINUTES));

        MonitorAlert existing = monitorAlertMapper.selectOne(queryWrapper);
        if (existing != null) {
            log.debug("Alert already exists for project {} type {}, skipping",
                    alertInfo.getProjectKey(), alertInfo.getAlertType());
            return false;
        }

        // Create new alert
        MonitorAlert alert = new MonitorAlert();
        alert.setSyncProjectId(alertInfo.getSyncProjectId());
        alert.setAlertType(alertInfo.getAlertType().name().toLowerCase());
        alert.setSeverity(alertInfo.getSeverity().name().toLowerCase());
        alert.setTitle(generateAlertTitle(alertInfo));
        alert.setDescription(alertInfo.getMessage());
        alert.setMetadata(generateMetadata(alertInfo));
        alert.setStatus(MonitorAlert.Status.ACTIVE);
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setCreatedAt(LocalDateTime.now());
        alert.setUpdatedAt(LocalDateTime.now());

        monitorAlertMapper.insert(alert);
        log.info("Created alert: {} for project {}", alert.getAlertType(), alertInfo.getProjectKey());

        return true;
    }

    /**
     * Resolve alert
     *
     * @param alertId Alert ID
     * @return true if resolved successfully
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean resolveAlert(Long alertId) {
        MonitorAlert alert = monitorAlertMapper.selectById(alertId);
        if (alert == null) {
            log.warn("Alert not found: {}", alertId);
            return false;
        }

        if (MonitorAlert.Status.RESOLVED.equals(alert.getStatus())) {
            log.debug("Alert already resolved: {}", alertId);
            return true;
        }

        alert.setStatus(MonitorAlert.Status.RESOLVED);
        alert.setResolvedAt(LocalDateTime.now());
        alert.setUpdatedAt(LocalDateTime.now());

        monitorAlertMapper.updateById(alert);
        log.info("Resolved alert: {}", alertId);

        return true;
    }

    /**
     * Auto-resolve alerts based on current project status
     *
     * @param diffs Current project diffs
     * @return Number of alerts resolved
     */
    @Transactional(rollbackFor = Exception.class)
    public int autoResolveAlerts(List<ProjectDiff> diffs) {
        log.info("Auto-resolving alerts for {} projects", diffs.size());

        // Get all active alerts
        QueryWrapper<MonitorAlert> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", MonitorAlert.Status.ACTIVE);
        List<MonitorAlert> activeAlerts = monitorAlertMapper.selectList(queryWrapper);

        int resolvedCount = 0;
        for (MonitorAlert alert : activeAlerts) {
            // Find corresponding diff
            ProjectDiff diff = diffs.stream()
                    .filter(d -> d.getSyncProjectId().equals(alert.getSyncProjectId()))
                    .findFirst()
                    .orElse(null);

            if (diff != null && shouldAutoResolve(alert, diff)) {
                resolveAlert(alert.getId());
                resolvedCount++;
            }
        }

        log.info("Auto-resolved {} alerts", resolvedCount);
        return resolvedCount;
    }

    /**
     * Mute alert
     *
     * @param alertId Alert ID
     * @param durationMinutes Mute duration in minutes
     * @return true if muted successfully
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean muteAlert(Long alertId, Integer durationMinutes) {
        MonitorAlert alert = monitorAlertMapper.selectById(alertId);
        if (alert == null) {
            log.warn("Alert not found: {}", alertId);
            return false;
        }

        alert.setStatus(MonitorAlert.Status.MUTED);
        alert.setUpdatedAt(LocalDateTime.now());

        monitorAlertMapper.updateById(alert);
        log.info("Muted alert: {} for {} minutes", alertId, durationMinutes);

        return true;
    }

    /**
     * Check if alert should be auto-resolved
     */
    private boolean shouldAutoResolve(MonitorAlert alert, ProjectDiff diff) {
        String alertType = alert.getAlertType();

        // Check if the issue is fixed based on alert type
        switch (alertType) {
            case "sync_delay":
                return diff.getDiff().getSyncDelayMinutes() != null &&
                       diff.getDiff().getSyncDelayMinutes() < 5;

            case "commit_diff":
                return diff.getDiff().getCommitBehind() != null &&
                       Math.abs(diff.getDiff().getCommitBehind()) < 5;

            case "branch_diff":
                return diff.getDiff().getBranchDiff() != null &&
                       diff.getDiff().getBranchDiff() == 0;

            case "size_diff":
                return diff.getDiff().getSizeDiffPercent() != null &&
                       diff.getDiff().getSizeDiffPercent() < 5.0;

            case "target_missing":
                return diff.getTarget() != null;

            default:
                return false;
        }
    }

    /**
     * Generate alert title
     */
    private String generateAlertTitle(AlertInfo alertInfo) {
        switch (alertInfo.getAlertType()) {
            case SYNC_DELAY:
                return "Sync Delay Detected";
            case COMMIT_DIFF:
                return "Commit Difference Detected";
            case BRANCH_DIFF:
                return "Branch Count Mismatch";
            case SIZE_DIFF:
                return "Repository Size Difference";
            case TARGET_MISSING:
                return "Target Project Missing";
            default:
                return "Alert";
        }
    }

    /**
     * Generate alert metadata JSON
     */
    private String generateMetadata(AlertInfo alertInfo) {
        return String.format("{\"project_key\":\"%s\",\"sync_status\":\"%s\"}",
                alertInfo.getProjectKey(),
                alertInfo.getSyncStatus());
    }
}
