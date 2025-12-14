package com.gitlab.mirror.server.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gitlab.mirror.server.entity.MonitorAlert;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.MonitorAlertMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.service.monitor.LocalCacheManager;
import com.gitlab.mirror.server.service.monitor.SyncMonitorService;
import com.gitlab.mirror.server.service.monitor.UnifiedProjectMonitor;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import com.gitlab.mirror.server.service.monitor.model.ScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MonitorScheduler
 */
@ExtendWith(MockitoExtension.class)
class MonitorSchedulerTest {

    @Mock
    private UnifiedProjectMonitor unifiedProjectMonitor;

    @Mock
    private SyncMonitorService syncMonitorService;

    @Mock
    private LocalCacheManager cacheManager;

    @Mock
    private MonitorAlertMapper monitorAlertMapper;

    @Mock
    private SyncProjectMapper syncProjectMapper;

    private MonitorScheduler monitorScheduler;

    @BeforeEach
    void setUp() {
        monitorScheduler = new MonitorScheduler(
                unifiedProjectMonitor,
                syncMonitorService,
                cacheManager,
                monitorAlertMapper,
                syncProjectMapper
        );
    }

    @Test
    void testIncrementalScan_success() {
        // Mock scan result
        ScanResult scanResult = ScanResult.builder()
                .scanType("incremental")
                .projectsScanned(50)
                .projectsUpdated(10)
                .changesDetected(5)
                .durationMs(3000L)
                .status("success")
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .build();

        when(unifiedProjectMonitor.scan("incremental")).thenReturn(scanResult);
        when(monitorAlertMapper.selectCount(any(QueryWrapper.class))).thenReturn(3L);
        when(cacheManager.get(anyString())).thenReturn(null); // Lock not held

        // Execute
        monitorScheduler.incrementalScan();

        // Verify
        verify(unifiedProjectMonitor).scan("incremental");
        verify(monitorAlertMapper).selectCount(any(QueryWrapper.class));
        verify(cacheManager, times(2)).put(anyString(), any(), anyLong()); // Lock + stats
        verify(cacheManager).remove(anyString()); // Release lock
    }

    @Test
    void testIncrementalScan_lockAcquireFails() {
        // Mock lock already held
        when(cacheManager.get("scheduler:incremental:lock")).thenReturn("locked");

        // Execute
        monitorScheduler.incrementalScan();

        // Verify - should skip
        verify(unifiedProjectMonitor, never()).scan(anyString());
    }

    @Test
    void testFullScan_success() {
        // Mock scan result
        ScanResult scanResult = ScanResult.builder()
                .scanType("full")
                .projectsScanned(100)
                .projectsUpdated(20)
                .newProjects(5)
                .changesDetected(15)
                .durationMs(8000L)
                .status("success")
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .build();

        when(unifiedProjectMonitor.scan("full")).thenReturn(scanResult);
        when(monitorAlertMapper.selectCount(any(QueryWrapper.class))).thenReturn(8L);
        when(cacheManager.get(eq("scheduler:full:lock"))).thenReturn(null); // Lock not held

        // Execute
        monitorScheduler.fullScan();

        // Verify
        verify(unifiedProjectMonitor).scan("full");
        verify(monitorAlertMapper).selectCount(any(QueryWrapper.class));
        verify(cacheManager, times(3)).put(anyString(), any(), anyLong()); // Lock + stats + report
        verify(cacheManager).remove("scheduler:full:lock"); // Release lock
    }

    @Test
    void testFullScan_generatesReport() {
        // Mock scan result
        ScanResult scanResult = ScanResult.builder()
                .scanType("full")
                .projectsScanned(100)
                .projectsUpdated(20)
                .newProjects(5)
                .changesDetected(15)
                .durationMs(8000L)
                .status("success")
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .build();

        when(unifiedProjectMonitor.scan("full")).thenReturn(scanResult);
        when(monitorAlertMapper.selectCount(any(QueryWrapper.class))).thenReturn(8L);
        when(cacheManager.get(eq("scheduler:full:lock"))).thenReturn(null);

        // Execute
        monitorScheduler.fullScan();

        // Verify report is cached
        ArgumentCaptor<String> reportCaptor = ArgumentCaptor.forClass(String.class);
        verify(cacheManager, atLeastOnce()).put(eq("reconciliation:report:latest"), reportCaptor.capture(), eq(24 * 60L));

        String report = reportCaptor.getValue();
        assertThat(report).contains("Daily Reconciliation Report");
        assertThat(report).contains("Projects Scanned");
    }

    @Test
    void testAutoResolveAlerts_success() {
        // Mock sync projects
        SyncProject project1 = new SyncProject();
        project1.setProjectKey("group1/project-a");

        SyncProject project2 = new SyncProject();
        project2.setProjectKey("group1/project-b");

        when(syncProjectMapper.selectList(null)).thenReturn(Arrays.asList(project1, project2));

        // Mock cached diffs
        ProjectDiff diff1 = new ProjectDiff();
        diff1.setProjectKey("group1/project-a");

        ProjectDiff diff2 = new ProjectDiff();
        diff2.setProjectKey("group1/project-b");

        when(cacheManager.get("diff:group1/project-a")).thenReturn(diff1);
        when(cacheManager.get("diff:group1/project-b")).thenReturn(diff2);
        when(cacheManager.get(eq("scheduler:auto_resolve:lock"))).thenReturn(null); // Lock not held

        // Mock auto-resolve result
        when(syncMonitorService.autoResolveAlerts(any(List.class))).thenReturn(2);

        // Execute
        monitorScheduler.autoResolveAlerts();

        // Verify
        verify(syncProjectMapper).selectList(null);
        verify(syncMonitorService).autoResolveAlerts(any(List.class));
        verify(cacheManager).put(eq("scheduler:auto_resolve:lock"), any(), anyLong());
        verify(cacheManager).remove("scheduler:auto_resolve:lock");
    }

    @Test
    void testAutoResolveAlerts_noCachedDiffs() {
        // Mock sync projects but no cached diffs
        SyncProject project1 = new SyncProject();
        project1.setProjectKey("group1/project-a");

        when(syncProjectMapper.selectList(null)).thenReturn(Arrays.asList(project1));
        when(cacheManager.get("diff:group1/project-a")).thenReturn(null);
        when(cacheManager.get(eq("scheduler:auto_resolve:lock"))).thenReturn(null);
        when(syncMonitorService.autoResolveAlerts(any(List.class))).thenReturn(0);

        // Execute
        monitorScheduler.autoResolveAlerts();

        // Verify - should still call but with empty list
        ArgumentCaptor<List<ProjectDiff>> diffsCaptor = ArgumentCaptor.forClass(List.class);
        verify(syncMonitorService).autoResolveAlerts(diffsCaptor.capture());
        assertThat(diffsCaptor.getValue()).isEmpty();
    }

    @Test
    void testCleanupExpiredAlerts() {
        // Mock deletion result
        when(monitorAlertMapper.delete(any(QueryWrapper.class))).thenReturn(15);

        // Execute
        monitorScheduler.cleanupExpiredAlerts();

        // Verify
        ArgumentCaptor<QueryWrapper<MonitorAlert>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(monitorAlertMapper).delete(queryCaptor.capture());
    }

    @Test
    void testScanFailure_handledGracefully() {
        // Mock scan failure
        when(cacheManager.get(eq("scheduler:incremental:lock"))).thenReturn(null);
        when(unifiedProjectMonitor.scan("incremental")).thenThrow(new RuntimeException("Scan failed"));

        // Execute - should not throw
        monitorScheduler.incrementalScan();

        // Verify lock is released even on failure
        verify(cacheManager).remove("scheduler:incremental:lock");
    }
}
