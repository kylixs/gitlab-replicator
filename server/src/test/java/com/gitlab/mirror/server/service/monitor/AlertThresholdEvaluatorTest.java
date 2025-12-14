package com.gitlab.mirror.server.service.monitor;

import com.gitlab.mirror.server.service.monitor.model.AlertInfo;
import com.gitlab.mirror.server.service.monitor.model.DiffDetails;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import com.gitlab.mirror.server.service.monitor.model.ProjectSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AlertThresholdEvaluator Test
 *
 * @author GitLab Mirror Team
 */
class AlertThresholdEvaluatorTest {

    private AlertThresholdEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new AlertThresholdEvaluator();

        // Set threshold values using reflection
        ReflectionTestUtils.setField(evaluator, "syncDelayMinutes", 30L);
        ReflectionTestUtils.setField(evaluator, "criticalDelayHours", 2L);
        ReflectionTestUtils.setField(evaluator, "commitDiffAlert", 10);
        ReflectionTestUtils.setField(evaluator, "sizeDiffTolerance", 5.0);
    }

    @Test
    void testEvaluateThresholds_noAlerts() {
        ProjectDiff diff = createProjectDiff("group/project1",
                createSnapshot("abc123", 100, 5),
                createSnapshot("abc123", 100, 5),
                DiffDetails.builder()
                        .commitBehind(0)
                        .syncDelayMinutes(2L)
                        .sizeDiffPercent(1.0)
                        .branchDiff(0)
                        .commitShaMatches(true)
                        .build()
        );

        List<AlertInfo> alerts = evaluator.evaluateThresholds(diff);

        assertThat(alerts).isEmpty();
    }

    @Test
    void testEvaluateThresholds_targetMissing() {
        ProjectDiff diff = createProjectDiff("group/project1",
                createSnapshot("abc123", 100, 5),
                null,
                DiffDetails.builder().build()
        );

        List<AlertInfo> alerts = evaluator.evaluateThresholds(diff);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getAlertType()).isEqualTo(AlertInfo.AlertType.TARGET_MISSING);
        assertThat(alerts.get(0).getSeverity()).isEqualTo(AlertInfo.Severity.CRITICAL);
    }

    @Test
    void testEvaluateThresholds_syncDelayWarning() {
        ProjectDiff diff = createProjectDiff("group/project2",
                createSnapshot("abc123", 100, 5),
                createSnapshot("abc123", 100, 5),
                DiffDetails.builder()
                        .commitBehind(0)
                        .syncDelayMinutes(45L) // 45 > 30 (warning threshold)
                        .sizeDiffPercent(1.0)
                        .branchDiff(0)
                        .commitShaMatches(true)
                        .build()
        );

        List<AlertInfo> alerts = evaluator.evaluateThresholds(diff);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getAlertType()).isEqualTo(AlertInfo.AlertType.SYNC_DELAY);
        assertThat(alerts.get(0).getSeverity()).isEqualTo(AlertInfo.Severity.HIGH);
    }

    @Test
    void testEvaluateThresholds_syncDelayCritical() {
        ProjectDiff diff = createProjectDiff("group/project3",
                createSnapshot("abc123", 100, 5),
                createSnapshot("abc123", 100, 5),
                DiffDetails.builder()
                        .commitBehind(0)
                        .syncDelayMinutes(150L) // 150 min = 2.5 hours > 2 hours (critical)
                        .sizeDiffPercent(1.0)
                        .branchDiff(0)
                        .commitShaMatches(true)
                        .build()
        );

        List<AlertInfo> alerts = evaluator.evaluateThresholds(diff);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getAlertType()).isEqualTo(AlertInfo.AlertType.SYNC_DELAY);
        assertThat(alerts.get(0).getSeverity()).isEqualTo(AlertInfo.Severity.CRITICAL);
    }

    @Test
    void testEvaluateThresholds_commitDiff() {
        ProjectDiff diff = createProjectDiff("group/project4",
                createSnapshot("abc123", 125, 5),
                createSnapshot("def456", 100, 5),
                DiffDetails.builder()
                        .commitBehind(25) // 25 > 20 (commit diff threshold * 2)
                        .syncDelayMinutes(5L)
                        .sizeDiffPercent(1.0)
                        .branchDiff(0)
                        .commitShaMatches(false)
                        .build()
        );

        List<AlertInfo> alerts = evaluator.evaluateThresholds(diff);

        assertThat(alerts).hasSizeGreaterThanOrEqualTo(1);
        assertThat(alerts).anyMatch(a -> a.getAlertType() == AlertInfo.AlertType.COMMIT_DIFF);
        assertThat(alerts.stream()
                .filter(a -> a.getAlertType() == AlertInfo.AlertType.COMMIT_DIFF)
                .findFirst()
                .get()
                .getSeverity()).isEqualTo(AlertInfo.Severity.HIGH);
    }

    @Test
    void testEvaluateThresholds_branchDiff() {
        ProjectDiff diff = createProjectDiff("group/project5",
                createSnapshot("abc123", 100, 10),
                createSnapshot("abc123", 100, 5),
                DiffDetails.builder()
                        .commitBehind(0)
                        .syncDelayMinutes(2L)
                        .sizeDiffPercent(1.0)
                        .branchDiff(-5) // Branch count mismatch
                        .commitShaMatches(true)
                        .build()
        );

        List<AlertInfo> alerts = evaluator.evaluateThresholds(diff);

        assertThat(alerts).hasSizeGreaterThanOrEqualTo(1);
        assertThat(alerts).anyMatch(a -> a.getAlertType() == AlertInfo.AlertType.BRANCH_DIFF);
        assertThat(alerts.stream()
                .filter(a -> a.getAlertType() == AlertInfo.AlertType.BRANCH_DIFF)
                .findFirst()
                .get()
                .getSeverity()).isEqualTo(AlertInfo.Severity.MEDIUM);
    }

    @Test
    void testEvaluateThresholds_sizeDiff() {
        ProjectDiff diff = createProjectDiff("group/project6",
                createSnapshot("abc123", 100, 5),
                createSnapshot("abc123", 100, 5),
                DiffDetails.builder()
                        .commitBehind(0)
                        .syncDelayMinutes(2L)
                        .sizeDiffPercent(8.0) // 8% > 5% tolerance
                        .branchDiff(0)
                        .commitShaMatches(true)
                        .build()
        );

        List<AlertInfo> alerts = evaluator.evaluateThresholds(diff);

        assertThat(alerts).hasSizeGreaterThanOrEqualTo(1);
        assertThat(alerts).anyMatch(a -> a.getAlertType() == AlertInfo.AlertType.SIZE_DIFF);
        assertThat(alerts.stream()
                .filter(a -> a.getAlertType() == AlertInfo.AlertType.SIZE_DIFF)
                .findFirst()
                .get()
                .getSeverity()).isEqualTo(AlertInfo.Severity.LOW);
    }

    @Test
    void testEvaluateThresholds_multipleAlerts() {
        ProjectDiff diff = createProjectDiff("group/project7",
                createSnapshot("abc123", 120, 10),
                createSnapshot("def456", 100, 5),
                DiffDetails.builder()
                        .commitBehind(20) // Exceeds threshold
                        .syncDelayMinutes(45L) // Exceeds threshold
                        .sizeDiffPercent(8.0) // Exceeds tolerance
                        .branchDiff(-5) // Mismatch
                        .commitShaMatches(false)
                        .build()
        );

        List<AlertInfo> alerts = evaluator.evaluateThresholds(diff);

        // Should have multiple alerts
        assertThat(alerts).hasSizeGreaterThanOrEqualTo(3);
        assertThat(alerts).anyMatch(a -> a.getAlertType() == AlertInfo.AlertType.SYNC_DELAY);
        assertThat(alerts).anyMatch(a -> a.getAlertType() == AlertInfo.AlertType.COMMIT_DIFF);
        assertThat(alerts).anyMatch(a -> a.getAlertType() == AlertInfo.AlertType.BRANCH_DIFF);
        assertThat(alerts).anyMatch(a -> a.getAlertType() == AlertInfo.AlertType.SIZE_DIFF);
    }

    @Test
    void testEvaluateThresholdsBatch_multipleProjects() {
        List<ProjectDiff> diffs = List.of(
                createProjectDiff("project1", null, null, DiffDetails.builder().build()), // Target missing
                createProjectDiff("project2",
                        createSnapshot("abc123", 100, 5),
                        createSnapshot("abc123", 100, 5),
                        DiffDetails.builder()
                                .syncDelayMinutes(2L)
                                .commitBehind(0)
                                .branchDiff(0)
                                .sizeDiffPercent(1.0)
                                .commitShaMatches(true)
                                .build()), // No alerts
                createProjectDiff("project3",
                        createSnapshot("abc123", 120, 5),
                        createSnapshot("def456", 100, 5),
                        DiffDetails.builder()
                                .syncDelayMinutes(45L)
                                .commitBehind(20)
                                .branchDiff(0)
                                .sizeDiffPercent(1.0)
                                .commitShaMatches(false)
                                .build()) // Multiple alerts
        );

        List<AlertInfo> alerts = evaluator.evaluateThresholdsBatch(diffs);

        assertThat(alerts).hasSizeGreaterThanOrEqualTo(3); // At least 1 + 0 + 2
    }

    @Test
    void testEvaluateThresholds_nullDiff() {
        List<AlertInfo> alerts = evaluator.evaluateThresholds(null);

        assertThat(alerts).isEmpty();
    }

    private ProjectDiff createProjectDiff(String projectKey, ProjectSnapshot source,
                                           ProjectSnapshot target, DiffDetails diffDetails) {
        return ProjectDiff.builder()
                .projectKey(projectKey)
                .syncProjectId(1L)
                .source(source)
                .target(target)
                .diff(diffDetails)
                .status(ProjectDiff.SyncStatus.SYNCED)
                .checkedAt(LocalDateTime.now())
                .build();
    }

    private ProjectSnapshot createSnapshot(String commitSha, int commitCount, int branchCount) {
        return ProjectSnapshot.builder()
                .commitSha(commitSha)
                .commitCount(commitCount)
                .branchCount(branchCount)
                .sizeBytes(1024L * 1024)
                .lastActivityAt(LocalDateTime.now())
                .defaultBranch("main")
                .build();
    }
}
