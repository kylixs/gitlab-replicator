package com.gitlab.mirror.server.service.monitor;

import com.gitlab.mirror.common.model.GitLabProject;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import com.gitlab.mirror.server.service.monitor.model.ScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * UnifiedProjectMonitor Test
 *
 * @author GitLab Mirror Team
 */
class UnifiedProjectMonitorTest {

    @Mock
    private BatchQueryExecutor batchQueryExecutor;

    @Mock
    private UpdateProjectDataService updateProjectDataService;

    @Mock
    private DiffCalculator diffCalculator;

    @Mock
    private LocalCacheManager cacheManager;

    @Mock
    private SyncProjectMapper syncProjectMapper;

    @Mock
    private MetricsExporter metricsExporter;

    @Mock
    private com.gitlab.mirror.server.service.ProjectDiscoveryService projectDiscoveryService;

    private UnifiedProjectMonitor unifiedProjectMonitor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        unifiedProjectMonitor = new UnifiedProjectMonitor(
                batchQueryExecutor,
                updateProjectDataService,
                diffCalculator,
                cacheManager,
                syncProjectMapper,
                metricsExporter,
                projectDiscoveryService
        );
    }

    @Test
    void testScan_incremental_success() {
        // Mock source projects
        List<GitLabProject> sourceProjects = new ArrayList<>();
        GitLabProject project = new GitLabProject();
        project.setId(1L);
        sourceProjects.add(project);

        // Mock project details
        List<BatchQueryExecutor.ProjectDetails> details = new ArrayList<>();
        BatchQueryExecutor.ProjectDetails detail = new BatchQueryExecutor.ProjectDetails();
        detail.setProjectId(1L);
        details.add(detail);

        // Mock update result
        UpdateProjectDataService.UpdateResult updateResult = new UpdateProjectDataService.UpdateResult();
        updateResult.setSuccessCount(1);
        updateResult.setFailedCount(0);

        // Mock sync projects
        List<SyncProject> syncProjects = new ArrayList<>();
        SyncProject syncProject = new SyncProject();
        syncProject.setId(1L);
        syncProjects.add(syncProject);

        // Mock diffs
        List<ProjectDiff> diffs = new ArrayList<>();
        ProjectDiff diff = ProjectDiff.builder()
                .projectKey("test/project")
                .status(ProjectDiff.SyncStatus.SYNCED)
                .build();
        diffs.add(diff);

        when(batchQueryExecutor.querySourceProjects(any(), anyInt())).thenReturn(sourceProjects);
        when(batchQueryExecutor.getProjectDetailsBatch(anyList(), any())).thenReturn(details);
        when(batchQueryExecutor.queryTargetProjects(any(), anyInt())).thenReturn(new ArrayList<>());
        when(updateProjectDataService.updateSourceProjects(anyList(), any())).thenReturn(updateResult);
        when(updateProjectDataService.updateTargetProjects(anyList(), any())).thenReturn(updateResult);
        when(syncProjectMapper.selectList(any())).thenReturn(syncProjects);
        when(diffCalculator.calculateDiffBatch(anyList())).thenReturn(diffs);

        // Execute
        ScanResult result = unifiedProjectMonitor.scan("incremental");

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(result.getProjectsScanned()).isEqualTo(1);
        assertThat(result.getChangesDetected()).isEqualTo(0);

        verify(batchQueryExecutor).querySourceProjects(any(), anyInt());
        verify(diffCalculator).calculateDiffBatch(anyList());
        verify(cacheManager, atLeastOnce()).put(anyString(), any(), anyLong());
    }

    @Test
    void testScan_full_success() {
        // Mock empty projects
        when(batchQueryExecutor.querySourceProjects(any(), anyInt())).thenReturn(new ArrayList<>());

        // Execute
        ScanResult result = unifiedProjectMonitor.scan("full");

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(result.getProjectsScanned()).isEqualTo(0);
    }

    @Test
    void testScan_failure() {
        // Mock exception
        when(batchQueryExecutor.querySourceProjects(any(), anyInt()))
                .thenThrow(new RuntimeException("API error"));

        // Execute
        ScanResult result = unifiedProjectMonitor.scan("incremental");

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getErrorMessage()).contains("API error");
    }
}
