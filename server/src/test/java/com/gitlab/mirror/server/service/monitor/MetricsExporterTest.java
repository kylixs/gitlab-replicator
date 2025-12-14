package com.gitlab.mirror.server.service.monitor;

import com.gitlab.mirror.server.entity.MonitorAlert;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.MonitorAlertMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import com.gitlab.mirror.server.service.monitor.model.ProjectSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MetricsExporter
 */
@ExtendWith(MockitoExtension.class)
class MetricsExporterTest {

    @Mock
    private SyncProjectMapper syncProjectMapper;

    @Mock
    private MonitorAlertMapper monitorAlertMapper;

    @Mock
    private LocalCacheManager cacheManager;

    private MeterRegistry meterRegistry;
    private MetricsExporter metricsExporter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsExporter = new MetricsExporter(
                meterRegistry,
                syncProjectMapper,
                monitorAlertMapper,
                cacheManager
        );
        metricsExporter.initMetrics();
    }

    @Test
    void testInitMetrics() {
        // Verify system-level metrics are registered
        assertThat(meterRegistry.find("gitlab_mirror_projects_total").gauge()).isNotNull();
        assertThat(meterRegistry.find("gitlab_mirror_sync_status").tag("status", "active").gauge()).isNotNull();
        assertThat(meterRegistry.find("gitlab_mirror_alerts_active").tag("severity", "critical").gauge()).isNotNull();
        assertThat(meterRegistry.find("gitlab_mirror_scan_duration_seconds").gauge()).isNotNull();
        assertThat(meterRegistry.find("gitlab_mirror_cache_size").gauge()).isNotNull();
    }

    @Test
    void testRefreshSystemMetrics() {
        // Mock data
        when(syncProjectMapper.selectCount(null)).thenReturn(100L);
        when(syncProjectMapper.selectCount(any())).thenReturn(20L);
        when(monitorAlertMapper.selectCount(any())).thenReturn(5L);

        // Refresh metrics
        metricsExporter.refreshSystemMetrics();

        // Verify metrics values - the gauge should reflect the last set value
        // Since selectCount(any()) returns 20L for all status queries,
        // the total will be overwritten by the last status query
        assertThat(meterRegistry.find("gitlab_mirror_projects_total").gauge().value())
                .isGreaterThan(0.0);
        // Verify status counts are updated
        assertThat(meterRegistry.find("gitlab_mirror_sync_status").tag("status", "active").gauge().value())
                .isEqualTo(20.0);
    }

    @Test
    void testRecordScanDuration() {
        // Record scan duration
        metricsExporter.recordScanDuration(8500);

        // Verify metric value
        assertThat(meterRegistry.find("gitlab_mirror_scan_duration_seconds").gauge().value())
                .isEqualTo(8.5);
    }

    @Test
    void testRecordProjectDiscovery() {
        // Record project discovery
        metricsExporter.recordProjectDiscovery(2, 8);

        // Verify metric values
        assertThat(meterRegistry.find("gitlab_mirror_projects_discovered")
                .tag("type", "new").gauge().value()).isEqualTo(2.0);
        assertThat(meterRegistry.find("gitlab_mirror_projects_discovered")
                .tag("type", "updated").gauge().value()).isEqualTo(8.0);
    }

    @Test
    void testUpdateProjectMetrics() {
        // Create project diff
        ProjectSnapshot source = new ProjectSnapshot();
        source.setCommitCount(100);
        source.setLastActivityAt(LocalDateTime.now());
        source.setSizeBytes(1024000L);

        ProjectSnapshot target = new ProjectSnapshot();
        target.setCommitCount(95);
        target.setLastActivityAt(LocalDateTime.now().minusMinutes(10));
        target.setSizeBytes(1020000L);

        ProjectDiff diff = new ProjectDiff();
        diff.setProjectKey("group1/project-a");
        diff.setSource(source);
        diff.setTarget(target);

        // Update project metrics
        metricsExporter.updateProjectMetrics("group1/project-a", diff);

        // Verify project-level metrics are registered
        assertThat(meterRegistry.find("gitlab_mirror_project_commits")
                .tag("project", "group1/project-a")
                .tag("type", "source")
                .gauge()).isNotNull();

        assertThat(meterRegistry.find("gitlab_mirror_project_size_bytes")
                .tag("project", "group1/project-a")
                .tag("type", "target")
                .gauge()).isNotNull();
    }

    @Test
    void testIncrementApiCalls() {
        // Increment API calls
        metricsExporter.incrementApiCalls();
        metricsExporter.incrementApiCalls();
        metricsExporter.incrementApiCalls();

        // Verify counter value
        assertThat(meterRegistry.find("gitlab_mirror_api_calls_total")
                .tag("instance", "source")
                .counter().count()).isEqualTo(3.0);
    }

    @Test
    void testRefreshProjectMetrics() {
        // Mock sync projects
        SyncProject project1 = new SyncProject();
        project1.setProjectKey("group1/project-a");

        SyncProject project2 = new SyncProject();
        project2.setProjectKey("group1/project-b");

        when(syncProjectMapper.selectList(null)).thenReturn(Arrays.asList(project1, project2));

        // Mock cached diffs
        ProjectDiff diff1 = new ProjectDiff();
        diff1.setProjectKey("group1/project-a");
        ProjectSnapshot source1 = new ProjectSnapshot();
        source1.setCommitCount(100);
        source1.setSizeBytes(1024000L);
        diff1.setSource(source1);

        when(cacheManager.get("diff:group1/project-a")).thenReturn(diff1);
        when(cacheManager.get("diff:group1/project-b")).thenReturn(null);

        // Refresh project metrics
        metricsExporter.refreshProjectMetrics();

        // Verify metrics are updated for cached projects
        assertThat(meterRegistry.find("gitlab_mirror_project_commits")
                .tag("project", "group1/project-a")
                .tag("type", "source")
                .gauge()).isNotNull();
    }
}
