package com.gitlab.mirror.server.service.monitor;

import com.gitlab.mirror.server.service.monitor.model.AlertInfo;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Alert Threshold Evaluator
 * <p>
 * Evaluates project differences against configured thresholds
 * and generates alerts for projects that exceed thresholds.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class AlertThresholdEvaluator {

    @Value("${monitor.threshold.sync-delay-minutes:30}")
    private Long syncDelayMinutes;

    @Value("${monitor.threshold.critical-delay-hours:2}")
    private Long criticalDelayHours;

    @Value("${monitor.threshold.commit-diff-alert:10}")
    private Integer commitDiffAlert;

    @Value("${monitor.threshold.size-diff-tolerance:5.0}")
    private Double sizeDiffTolerance;

    /**
     * Evaluate thresholds for a single project
     *
     * @param diff Project diff
     * @return List of alerts (empty if no issues)
     */
    public List<AlertInfo> evaluateThresholds(ProjectDiff diff) {
        List<AlertInfo> alerts = new ArrayList<>();

        if (diff == null) {
            return alerts;
        }

        // Check target missing
        if (diff.getTarget() == null) {
            alerts.add(createAlert(diff, AlertInfo.AlertType.TARGET_MISSING, AlertInfo.Severity.CRITICAL,
                    "Target project does not exist"));
            return alerts;
        }

        // Check sync delay
        Long delayMinutes = diff.getDiff().getSyncDelayMinutes();
        if (delayMinutes != null) {
            if (delayMinutes > criticalDelayHours * 60) {
                alerts.add(createAlert(diff, AlertInfo.AlertType.SYNC_DELAY, AlertInfo.Severity.CRITICAL,
                        String.format("Sync delay %d minutes exceeds critical threshold %d hours",
                                delayMinutes, criticalDelayHours)));
            } else if (delayMinutes > syncDelayMinutes) {
                alerts.add(createAlert(diff, AlertInfo.AlertType.SYNC_DELAY, AlertInfo.Severity.HIGH,
                        String.format("Sync delay %d minutes exceeds warning threshold %d minutes",
                                delayMinutes, syncDelayMinutes)));
            }
        }

        // Check commit diff
        Integer commitBehind = diff.getDiff().getCommitBehind();
        if (commitBehind != null && Math.abs(commitBehind) > commitDiffAlert) {
            AlertInfo.Severity severity = Math.abs(commitBehind) > commitDiffAlert * 2
                    ? AlertInfo.Severity.HIGH
                    : AlertInfo.Severity.MEDIUM;
            alerts.add(createAlert(diff, AlertInfo.AlertType.COMMIT_DIFF, severity,
                    String.format("Commit difference %d exceeds threshold %d", commitBehind, commitDiffAlert)));
        }

        // Check branch diff
        Integer branchDiff = diff.getDiff().getBranchDiff();
        if (branchDiff != null && branchDiff != 0) {
            alerts.add(createAlert(diff, AlertInfo.AlertType.BRANCH_DIFF, AlertInfo.Severity.MEDIUM,
                    String.format("Branch count mismatch: source=%d, target=%d",
                            diff.getSource().getBranchCount(),
                            diff.getTarget().getBranchCount())));
        }

        // Check size diff
        Double sizeDiffPercent = diff.getDiff().getSizeDiffPercent();
        if (sizeDiffPercent != null && sizeDiffPercent > sizeDiffTolerance) {
            AlertInfo.Severity severity = sizeDiffPercent > sizeDiffTolerance * 2
                    ? AlertInfo.Severity.MEDIUM
                    : AlertInfo.Severity.LOW;
            alerts.add(createAlert(diff, AlertInfo.AlertType.SIZE_DIFF, severity,
                    String.format("Repository size difference %.2f%% exceeds tolerance %.2f%%",
                            sizeDiffPercent, sizeDiffTolerance)));
        }

        return alerts;
    }

    /**
     * Evaluate thresholds for multiple projects
     *
     * @param diffs List of project diffs
     * @return List of all alerts
     */
    public List<AlertInfo> evaluateThresholdsBatch(List<ProjectDiff> diffs) {
        log.info("Evaluating thresholds for {} projects", diffs.size());

        List<AlertInfo> allAlerts = new ArrayList<>();
        for (ProjectDiff diff : diffs) {
            List<AlertInfo> alerts = evaluateThresholds(diff);
            allAlerts.addAll(alerts);
        }

        log.info("Generated {} alerts from {} projects", allAlerts.size(), diffs.size());
        return allAlerts;
    }

    /**
     * Create alert info
     */
    private AlertInfo createAlert(ProjectDiff diff, AlertInfo.AlertType type,
                                   AlertInfo.Severity severity, String message) {
        return AlertInfo.builder()
                .projectKey(diff.getProjectKey())
                .syncProjectId(diff.getSyncProjectId())
                .alertType(type)
                .severity(severity)
                .message(message)
                .syncStatus(diff.getStatus())
                .build();
    }
}
