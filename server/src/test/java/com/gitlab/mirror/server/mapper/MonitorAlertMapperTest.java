package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gitlab.mirror.server.entity.MonitorAlert;
import com.gitlab.mirror.server.entity.SyncProject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Monitor Alert Mapper Test
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MonitorAlertMapperTest {

    @Autowired
    private MonitorAlertMapper monitorAlertMapper;

    @Autowired
    private SyncProjectMapper syncProjectMapper;

    @Test
    void testInsert() {
        // Create sync project first
        SyncProject syncProject = createSyncProject("group1/test-alert-1");

        // Create monitor alert
        MonitorAlert alert = new MonitorAlert();
        alert.setSyncProjectId(syncProject.getId());
        alert.setAlertType(MonitorAlert.AlertType.SYNC_DELAY);
        alert.setSeverity(MonitorAlert.Severity.HIGH);
        alert.setTitle("Sync delay detected");
        alert.setDescription("Project sync delay is 45 minutes");
        alert.setMetadata("{\"delay_minutes\":45}");
        alert.setStatus(MonitorAlert.Status.ACTIVE);
        alert.setTriggeredAt(LocalDateTime.now());

        // Insert
        int result = monitorAlertMapper.insert(alert);

        // Verify
        assertThat(result).isEqualTo(1);
        assertThat(alert.getId()).isNotNull();
        assertThat(alert.getCreatedAt()).isNotNull();
    }

    @Test
    void testSelectById() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-alert-2");
        MonitorAlert alert = createMonitorAlert(syncProject.getId());

        // Query by ID
        MonitorAlert found = monitorAlertMapper.selectById(alert.getId());

        // Verify
        assertThat(found).isNotNull();
        assertThat(found.getSyncProjectId()).isEqualTo(syncProject.getId());
        assertThat(found.getAlertType()).isEqualTo(MonitorAlert.AlertType.COMMIT_DIFF);
        assertThat(found.getSeverity()).isEqualTo(MonitorAlert.Severity.CRITICAL);
        assertThat(found.getStatus()).isEqualTo(MonitorAlert.Status.ACTIVE);
    }

    @Test
    void testUpdate() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-alert-3");
        MonitorAlert alert = createMonitorAlert(syncProject.getId());

        // Update
        alert.setStatus(MonitorAlert.Status.RESOLVED);
        alert.setResolvedAt(LocalDateTime.now());
        int result = monitorAlertMapper.updateById(alert);

        // Verify
        assertThat(result).isEqualTo(1);
        MonitorAlert updated = monitorAlertMapper.selectById(alert.getId());
        assertThat(updated.getStatus()).isEqualTo(MonitorAlert.Status.RESOLVED);
        assertThat(updated.getResolvedAt()).isNotNull();
    }

    @Test
    void testDelete() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-alert-4");
        MonitorAlert alert = createMonitorAlert(syncProject.getId());

        // Delete
        int result = monitorAlertMapper.deleteById(alert.getId());

        // Verify
        assertThat(result).isEqualTo(1);
        MonitorAlert deleted = monitorAlertMapper.selectById(alert.getId());
        assertThat(deleted).isNull();
    }

    @Test
    void testQueryByStatus() {
        // Create sync project
        SyncProject syncProject = createSyncProject("group1/test-alert-5");

        // Create active alert
        MonitorAlert activeAlert = createMonitorAlert(syncProject.getId());

        // Create resolved alert
        MonitorAlert resolvedAlert = new MonitorAlert();
        resolvedAlert.setSyncProjectId(syncProject.getId());
        resolvedAlert.setAlertType(MonitorAlert.AlertType.SYNC_DELAY);
        resolvedAlert.setSeverity(MonitorAlert.Severity.MEDIUM);
        resolvedAlert.setTitle("Resolved alert");
        resolvedAlert.setDescription("This alert was resolved");
        resolvedAlert.setStatus(MonitorAlert.Status.RESOLVED);
        resolvedAlert.setTriggeredAt(LocalDateTime.now().minusHours(2));
        resolvedAlert.setResolvedAt(LocalDateTime.now().minusHours(1));
        monitorAlertMapper.insert(resolvedAlert);

        // Query active alerts
        QueryWrapper<MonitorAlert> wrapper = new QueryWrapper<>();
        wrapper.eq("status", MonitorAlert.Status.ACTIVE);
        List<MonitorAlert> activeAlerts = monitorAlertMapper.selectList(wrapper);

        // Verify
        assertThat(activeAlerts).hasSizeGreaterThanOrEqualTo(1);
        assertThat(activeAlerts).allMatch(a -> a.getStatus().equals(MonitorAlert.Status.ACTIVE));
    }

    @Test
    void testQueryBySeverity() {
        // Create sync project
        SyncProject syncProject = createSyncProject("group1/test-alert-6");

        // Create critical alert
        MonitorAlert criticalAlert = createMonitorAlert(syncProject.getId());

        // Create low severity alert
        MonitorAlert lowAlert = new MonitorAlert();
        lowAlert.setSyncProjectId(syncProject.getId());
        lowAlert.setAlertType(MonitorAlert.AlertType.SIZE_DIFF);
        lowAlert.setSeverity(MonitorAlert.Severity.LOW);
        lowAlert.setTitle("Minor size difference");
        lowAlert.setDescription("Size diff is 1%");
        lowAlert.setStatus(MonitorAlert.Status.ACTIVE);
        lowAlert.setTriggeredAt(LocalDateTime.now());
        monitorAlertMapper.insert(lowAlert);

        // Query critical alerts
        QueryWrapper<MonitorAlert> wrapper = new QueryWrapper<>();
        wrapper.eq("severity", MonitorAlert.Severity.CRITICAL);
        List<MonitorAlert> criticalAlerts = monitorAlertMapper.selectList(wrapper);

        // Verify
        assertThat(criticalAlerts).hasSizeGreaterThanOrEqualTo(1);
        assertThat(criticalAlerts).allMatch(a -> a.getSeverity().equals(MonitorAlert.Severity.CRITICAL));
    }

    @Test
    void testQueryByAlertType() {
        // Create sync project
        SyncProject syncProject = createSyncProject("group1/test-alert-7");

        // Create different alert types
        MonitorAlert commitDiffAlert = createMonitorAlert(syncProject.getId());

        MonitorAlert branchDiffAlert = new MonitorAlert();
        branchDiffAlert.setSyncProjectId(syncProject.getId());
        branchDiffAlert.setAlertType(MonitorAlert.AlertType.BRANCH_DIFF);
        branchDiffAlert.setSeverity(MonitorAlert.Severity.MEDIUM);
        branchDiffAlert.setTitle("Branch count mismatch");
        branchDiffAlert.setDescription("Source has 5 branches, target has 3");
        branchDiffAlert.setStatus(MonitorAlert.Status.ACTIVE);
        branchDiffAlert.setTriggeredAt(LocalDateTime.now());
        monitorAlertMapper.insert(branchDiffAlert);

        // Query commit_diff alerts
        QueryWrapper<MonitorAlert> wrapper = new QueryWrapper<>();
        wrapper.eq("alert_type", MonitorAlert.AlertType.COMMIT_DIFF);
        List<MonitorAlert> commitDiffAlerts = monitorAlertMapper.selectList(wrapper);

        // Verify
        assertThat(commitDiffAlerts).hasSizeGreaterThanOrEqualTo(1);
        assertThat(commitDiffAlerts).allMatch(a -> a.getAlertType().equals(MonitorAlert.AlertType.COMMIT_DIFF));
    }

    @Test
    void testQueryByProjectId() {
        // Create two sync projects
        SyncProject project1 = createSyncProject("group1/test-alert-8");
        SyncProject project2 = createSyncProject("group1/test-alert-9");

        // Create alerts for both projects
        createMonitorAlert(project1.getId());
        createMonitorAlert(project2.getId());

        // Query alerts for project1
        QueryWrapper<MonitorAlert> wrapper = new QueryWrapper<>();
        wrapper.eq("sync_project_id", project1.getId());
        List<MonitorAlert> project1Alerts = monitorAlertMapper.selectList(wrapper);

        // Verify
        assertThat(project1Alerts).hasSizeGreaterThanOrEqualTo(1);
        assertThat(project1Alerts).allMatch(a -> a.getSyncProjectId().equals(project1.getId()));
    }

    @Test
    void testForeignKeyConstraint() {
        // Test cascade delete: when sync_project is deleted, alerts should be deleted too
        SyncProject syncProject = createSyncProject("group1/test-alert-10");
        MonitorAlert alert = createMonitorAlert(syncProject.getId());

        // Verify alert exists
        assertThat(monitorAlertMapper.selectById(alert.getId())).isNotNull();

        // Delete sync project (should cascade delete alert)
        syncProjectMapper.deleteById(syncProject.getId());

        // Verify alert was deleted
        MonitorAlert deletedAlert = monitorAlertMapper.selectById(alert.getId());
        assertThat(deletedAlert).isNull();
    }

    private SyncProject createSyncProject(String projectKey) {
        SyncProject project = new SyncProject();
        project.setProjectKey(projectKey);
        project.setSyncMethod(SyncProject.SyncMethod.PUSH_MIRROR);
        project.setSyncStatus(SyncProject.SyncStatus.PENDING);
        project.setEnabled(true);
        syncProjectMapper.insert(project);
        return project;
    }

    private MonitorAlert createMonitorAlert(Long syncProjectId) {
        MonitorAlert alert = new MonitorAlert();
        alert.setSyncProjectId(syncProjectId);
        alert.setAlertType(MonitorAlert.AlertType.COMMIT_DIFF);
        alert.setSeverity(MonitorAlert.Severity.CRITICAL);
        alert.setTitle("Commit差异过大");
        alert.setDescription("源和目标commit SHA不一致，差异15个提交");
        alert.setMetadata("{\"source_sha\":\"abc123\",\"target_sha\":\"def456\",\"commit_diff\":15}");
        alert.setStatus(MonitorAlert.Status.ACTIVE);
        alert.setTriggeredAt(LocalDateTime.now());
        monitorAlertMapper.insert(alert);
        return alert;
    }
}
