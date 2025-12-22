package com.gitlab.mirror.server.controller;

import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.service.monitor.DiffCalculator;
import com.gitlab.mirror.server.service.monitor.LocalCacheManager;
import com.gitlab.mirror.server.service.monitor.UnifiedProjectMonitor;
import com.gitlab.mirror.server.service.monitor.model.DiffDetails;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import com.gitlab.mirror.server.service.monitor.model.ProjectSnapshot;
import com.gitlab.mirror.server.service.monitor.model.ScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Sync Controller Test
 *
 * @author GitLab Mirror Team
 */
@ExtendWith(MockitoExtension.class)
class SyncControllerTest {

    @Mock
    private UnifiedProjectMonitor unifiedProjectMonitor;

    @Mock
    private SyncProjectMapper syncProjectMapper;

    @Mock
    private DiffCalculator diffCalculator;

    @Mock
    private LocalCacheManager cacheManager;

    @InjectMocks
    private SyncController syncController;

    private SyncProject testProject;
    private ProjectDiff testDiff;

    @BeforeEach
    void setUp() {
        testProject = new SyncProject();
        testProject.setId(1L);
        testProject.setProjectKey("test/project");
        testProject.setSyncStatus("active");

        ProjectSnapshot sourceSnapshot = ProjectSnapshot.builder()
                .commitSha("abc123")
                .commitCount(100)
                .branchCount(5)
                .sizeBytes(1024000L)
                .lastActivityAt(LocalDateTime.now())
                .defaultBranch("main")
                .build();

        ProjectSnapshot targetSnapshot = ProjectSnapshot.builder()
                .commitSha("abc123")
                .commitCount(100)
                .branchCount(5)
                .sizeBytes(1024000L)
                .lastActivityAt(LocalDateTime.now())
                .defaultBranch("main")
                .build();

        DiffDetails diffDetails = DiffDetails.builder()
                .commitShaMatches(true)
                .commitBehind(0)
                .branchDiff(0)
                .sizeDiffPercent(0.0)
                .syncDelayMinutes(0L)
                .build();

        testDiff = ProjectDiff.builder()
                .projectKey("test/project")
                .syncProjectId(1L)
                .source(sourceSnapshot)
                .target(targetSnapshot)
                .diff(diffDetails)
                .status(ProjectDiff.SyncStatus.SYNCED)
                .checkedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testTriggerScan_Success() {
        // Given
        ScanResult scanResult = new ScanResult();
        scanResult.setScanType("incremental");
        scanResult.setProjectsScanned(10);
        scanResult.setProjectsUpdated(5);
        scanResult.setChangesDetected(3);
        scanResult.setDurationMs(1000L);

        when(unifiedProjectMonitor.scan("incremental")).thenReturn(scanResult);

        // When
        ResponseEntity<SyncController.ApiResponse<ScanResult>> response =
                syncController.triggerScan("incremental");

        // Then
        assertNotNull(response);
        assertTrue(response.getBody().isSuccess());
        assertEquals("incremental", response.getBody().getData().getScanType());
        assertEquals(10, response.getBody().getData().getProjectsScanned());
        verify(unifiedProjectMonitor, times(1)).scan("incremental");
    }

    @Test
    void testTriggerScan_Failure() {
        // Given
        when(unifiedProjectMonitor.scan(anyString()))
                .thenThrow(new RuntimeException("Scan failed"));

        // When
        ResponseEntity<SyncController.ApiResponse<ScanResult>> response =
                syncController.triggerScan("incremental");

        // Then
        assertNotNull(response);
        assertTrue(response.getBody().isSuccess()); // Still returns 200 with error message
        assertNull(response.getBody().getData());
        assertNotNull(response.getBody().getMessage());
        assertTrue(response.getBody().getMessage().contains("Scan failed"));
    }

    @Test
    void testGetProjectDiff_FromCache() {
        // Given
        when(cacheManager.get("diff:test/project")).thenReturn(testDiff);

        // When
        ResponseEntity<SyncController.ApiResponse<ProjectDiff>> response =
                syncController.getProjectDiff("test/project", null);

        // Then
        assertNotNull(response);
        assertTrue(response.getBody().isSuccess());
        assertEquals("test/project", response.getBody().getData().getProjectKey());
        verify(cacheManager, times(1)).get("diff:test/project");
        verify(diffCalculator, never()).calculateDiff(anyLong());
    }

    @Test
    void testGetProjectDiff_CalculateNew() {
        // Given
        when(cacheManager.get("diff:test/project")).thenReturn(null);
        when(syncProjectMapper.selectOne(any())).thenReturn(testProject);
        when(diffCalculator.calculateDiff(1L)).thenReturn(testDiff);

        // When
        ResponseEntity<SyncController.ApiResponse<ProjectDiff>> response =
                syncController.getProjectDiff("test/project", null);

        // Then
        assertNotNull(response);
        assertTrue(response.getBody().isSuccess());
        assertEquals("test/project", response.getBody().getData().getProjectKey());
        verify(diffCalculator, times(1)).calculateDiff(1L);
        verify(cacheManager, times(1)).put(eq("diff:test/project"), any(ProjectDiff.class), eq(15));
    }

    @Test
    void testGetProjectDiff_ProjectNotFound() {
        // Given
        when(cacheManager.get("diff:test/project")).thenReturn(null);
        when(syncProjectMapper.selectOne(any())).thenReturn(null);

        // When
        ResponseEntity<SyncController.ApiResponse<ProjectDiff>> response =
                syncController.getProjectDiff("test/project", null);

        // Then
        assertNotNull(response);
        assertTrue(response.getBody().isSuccess()); // Returns 200 with error message
        assertNull(response.getBody().getData());
        assertEquals("Project not found", response.getBody().getMessage());
    }

    @Test
    void testGetProjectDiffs_Success() {
        // Given
        List<SyncProject> projects = Arrays.asList(testProject);
        when(syncProjectMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<SyncProject> page =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
            page.setRecords(projects);
            page.setTotal(1);
            return page;
        });
        when(cacheManager.get("diff:test/project")).thenReturn(null);
        when(diffCalculator.calculateDiff(1L)).thenReturn(testDiff);

        // When
        ResponseEntity<SyncController.ApiResponse<SyncController.PageResult<ProjectDiff>>> response =
                syncController.getProjectDiffs(null, 1, 20);

        // Then
        assertNotNull(response);
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().getItems().size());
        assertEquals("test/project", response.getBody().getData().getItems().get(0).getProjectKey());
    }

    @Test
    void testGetProjectDiffs_WithStatusFilter() {
        // Given
        List<SyncProject> projects = Arrays.asList(testProject);
        when(syncProjectMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<SyncProject> page =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
            page.setRecords(projects);
            page.setTotal(1);
            return page;
        });
        when(cacheManager.get("diff:test/project")).thenReturn(testDiff);

        // When
        ResponseEntity<SyncController.ApiResponse<SyncController.PageResult<ProjectDiff>>> response =
                syncController.getProjectDiffs("active", 1, 20);

        // Then
        assertNotNull(response);
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().getItems().size());
    }

    @Test
    void testGetProjects_Success() {
        // Given
        List<SyncProject> projects = Arrays.asList(testProject);
        when(syncProjectMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<SyncProject> page =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
            page.setRecords(projects);
            page.setTotal(1);
            return page;
        });

        // When
        ResponseEntity<SyncController.ApiResponse<SyncController.PageResult<SyncProject>>> response =
                syncController.getProjects(null, 1, 20);

        // Then
        assertNotNull(response);
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().getTotal());
    }

    @Test
    void testGetProjectDetails_Success() {
        // Given
        when(syncProjectMapper.selectOne(any())).thenReturn(testProject);

        // When
        ResponseEntity<SyncController.ApiResponse<SyncProject>> response =
                syncController.getProjectDetails("test/project");

        // Then
        assertNotNull(response);
        assertTrue(response.getBody().isSuccess());
        assertEquals("test/project", response.getBody().getData().getProjectKey());
    }

    @Test
    void testGetProjectDetails_NotFound() {
        // Given
        when(syncProjectMapper.selectOne(any())).thenReturn(null);

        // When
        ResponseEntity<SyncController.ApiResponse<SyncProject>> response =
                syncController.getProjectDetails("nonexistent/project");

        // Then
        assertNotNull(response);
        assertTrue(response.getBody().isSuccess()); // Returns 200 with error message
        assertNull(response.getBody().getData());
        assertEquals("Project not found", response.getBody().getMessage());
    }
}
