package com.gitlab.mirror.server.service.monitor;

import com.gitlab.mirror.server.entity.MonitorAlert;
import com.gitlab.mirror.server.mapper.MonitorAlertMapper;
import com.gitlab.mirror.server.service.monitor.model.AlertInfo;
import com.gitlab.mirror.server.service.monitor.model.DiffDetails;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SyncMonitorService
 */
@ExtendWith(MockitoExtension.class)
class SyncMonitorServiceTest {

    @Mock
    private AlertThresholdEvaluator alertThresholdEvaluator;

    @Mock
    private MonitorAlertMapper monitorAlertMapper;

    @Mock
    private LocalCacheManager cacheManager;

    private SyncMonitorService syncMonitorService;

    @BeforeEach
    void setUp() {
        syncMonitorService = new SyncMonitorService(
                alertThresholdEvaluator,
                monitorAlertMapper,
                cacheManager
        );
    }

    @Test
    void testEvaluateProjects_createsAlerts() {
        // Mock project diffs
        List<ProjectDiff> diffs = createTestDiffs();

        // Mock alert evaluation
        List<AlertInfo> alertInfos = Arrays.asList(
                createAlertInfo(1L, "group1/project-a", AlertInfo.AlertType.SYNC_DELAY, AlertInfo.Severity.HIGH),
                createAlertInfo(2L, "group1/project-b", AlertInfo.AlertType.COMMIT_DIFF, AlertInfo.Severity.MEDIUM)
        );
        when(alertThresholdEvaluator.evaluateThresholdsBatch(diffs)).thenReturn(alertInfos);

        // Mock no existing alerts
        when(monitorAlertMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // Execute
        int result = syncMonitorService.evaluateProjects(diffs);

        // Verify
        assertThat(result).isEqualTo(2);
        verify(monitorAlertMapper, times(2)).insert(any(MonitorAlert.class));
    }

    @Test
    void testCreateAlert_withDeduplication() {
        // Create alert info
        AlertInfo alertInfo = createAlertInfo(1L, "group1/project-a",
                AlertInfo.AlertType.SYNC_DELAY, AlertInfo.Severity.HIGH);

        // Mock existing alert (within 60 minutes)
        MonitorAlert existingAlert = new MonitorAlert();
        existingAlert.setId(1L);
        existingAlert.setStatus(MonitorAlert.Status.ACTIVE);
        when(monitorAlertMapper.selectOne(any(QueryWrapper.class))).thenReturn(existingAlert);

        // Execute
        boolean result = syncMonitorService.createAlert(alertInfo);

        // Verify - should be deduplicated
        assertThat(result).isFalse();
        verify(monitorAlertMapper, never()).insert(any(MonitorAlert.class));
    }

    @Test
    void testCreateAlert_newAlert() {
        // Create alert info
        AlertInfo alertInfo = createAlertInfo(1L, "group1/project-a",
                AlertInfo.AlertType.SYNC_DELAY, AlertInfo.Severity.HIGH);

        // Mock no existing alert
        when(monitorAlertMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // Execute
        boolean result = syncMonitorService.createAlert(alertInfo);

        // Verify
        assertThat(result).isTrue();

        ArgumentCaptor<MonitorAlert> captor = ArgumentCaptor.forClass(MonitorAlert.class);
        verify(monitorAlertMapper).insert(captor.capture());

        MonitorAlert alert = captor.getValue();
        assertThat(alert.getSyncProjectId()).isEqualTo(1L);
        assertThat(alert.getAlertType()).isEqualTo("sync_delay");
        assertThat(alert.getSeverity()).isEqualTo("high");
        assertThat(alert.getStatus()).isEqualTo(MonitorAlert.Status.ACTIVE);
    }

    @Test
    void testResolveAlert() {
        // Mock existing alert
        MonitorAlert alert = new MonitorAlert();
        alert.setId(1L);
        alert.setStatus(MonitorAlert.Status.ACTIVE);
        when(monitorAlertMapper.selectById(1L)).thenReturn(alert);

        // Execute
        boolean result = syncMonitorService.resolveAlert(1L);

        // Verify
        assertThat(result).isTrue();
        verify(monitorAlertMapper).updateById(any(MonitorAlert.class));
        assertThat(alert.getStatus()).isEqualTo(MonitorAlert.Status.RESOLVED);
        assertThat(alert.getResolvedAt()).isNotNull();
    }

    @Test
    void testResolveAlert_notFound() {
        // Mock no alert found
        when(monitorAlertMapper.selectById(1L)).thenReturn(null);

        // Execute
        boolean result = syncMonitorService.resolveAlert(1L);

        // Verify
        assertThat(result).isFalse();
        verify(monitorAlertMapper, never()).updateById(any(MonitorAlert.class));
    }

    @Test
    void testAutoResolveAlerts() {
        // Mock active alerts
        MonitorAlert alert1 = new MonitorAlert();
        alert1.setId(1L);
        alert1.setSyncProjectId(1L);
        alert1.setAlertType("sync_delay");
        alert1.setStatus(MonitorAlert.Status.ACTIVE);

        MonitorAlert alert2 = new MonitorAlert();
        alert2.setId(2L);
        alert2.setSyncProjectId(2L);
        alert2.setAlertType("commit_diff");
        alert2.setStatus(MonitorAlert.Status.ACTIVE);

        when(monitorAlertMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(Arrays.asList(alert1, alert2));

        // Mock project diffs with fixed issues
        List<ProjectDiff> diffs = new ArrayList<>();
        ProjectDiff diff1 = new ProjectDiff();
        diff1.setSyncProjectId(1L);
        DiffDetails details1 = new DiffDetails();
        details1.setSyncDelayMinutes(3L); // Fixed - below threshold
        diff1.setDiff(details1);
        diffs.add(diff1);

        ProjectDiff diff2 = new ProjectDiff();
        diff2.setSyncProjectId(2L);
        DiffDetails details2 = new DiffDetails();
        details2.setCommitBehind(2); // Fixed - below threshold
        diff2.setDiff(details2);
        diffs.add(diff2);

        when(monitorAlertMapper.selectById(1L)).thenReturn(alert1);
        when(monitorAlertMapper.selectById(2L)).thenReturn(alert2);

        // Execute
        int result = syncMonitorService.autoResolveAlerts(diffs);

        // Verify
        assertThat(result).isEqualTo(2);
        verify(monitorAlertMapper, times(2)).updateById(any(MonitorAlert.class));
    }

    @Test
    void testMuteAlert() {
        // Mock existing alert
        MonitorAlert alert = new MonitorAlert();
        alert.setId(1L);
        alert.setStatus(MonitorAlert.Status.ACTIVE);
        when(monitorAlertMapper.selectById(1L)).thenReturn(alert);

        // Execute
        boolean result = syncMonitorService.muteAlert(1L, 60);

        // Verify
        assertThat(result).isTrue();
        verify(monitorAlertMapper).updateById(any(MonitorAlert.class));
        assertThat(alert.getStatus()).isEqualTo(MonitorAlert.Status.MUTED);
    }

    private List<ProjectDiff> createTestDiffs() {
        List<ProjectDiff> diffs = new ArrayList<>();

        ProjectDiff diff1 = new ProjectDiff();
        diff1.setSyncProjectId(1L);
        diff1.setProjectKey("group1/project-a");
        diff1.setStatus(ProjectDiff.SyncStatus.OUTDATED);
        diffs.add(diff1);

        ProjectDiff diff2 = new ProjectDiff();
        diff2.setSyncProjectId(2L);
        diff2.setProjectKey("group1/project-b");
        diff2.setStatus(ProjectDiff.SyncStatus.SYNCED);
        diffs.add(diff2);

        return diffs;
    }

    private AlertInfo createAlertInfo(Long syncProjectId, String projectKey,
                                      AlertInfo.AlertType type, AlertInfo.Severity severity) {
        AlertInfo info = new AlertInfo();
        info.setSyncProjectId(syncProjectId);
        info.setProjectKey(projectKey);
        info.setAlertType(type);
        info.setSeverity(severity);
        info.setMessage("Test alert message");
        info.setSyncStatus(ProjectDiff.SyncStatus.OUTDATED);
        return info;
    }
}
