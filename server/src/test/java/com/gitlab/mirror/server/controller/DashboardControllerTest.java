package com.gitlab.mirror.server.controller;

import com.gitlab.mirror.server.entity.SyncEvent;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.SyncEventMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.model.SyncStatistics;
import com.gitlab.mirror.server.service.ProjectListService;
import com.gitlab.mirror.server.controller.dto.ProjectListDTO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DashboardController
 *
 * @author GitLab Mirror Team
 */
@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private SyncProjectMapper syncProjectMapper;

    @Mock
    private SyncEventMapper syncEventMapper;

    @Mock
    private ProjectListService projectListService;

    @InjectMocks
    private DashboardController dashboardController;

    private List<SyncProject> mockProjects;
    private List<SyncEvent> mockEvents;

    @BeforeEach
    void setUp() {
        // Setup mock projects
        mockProjects = Arrays.asList(
            createProject(1L, "project1", "active"),
            createProject(2L, "project2", "active"),
            createProject(3L, "project3", "failed"),
            createProject(4L, "project4", "pending")
        );

        // Setup mock events
        mockEvents = Arrays.asList(
            createEvent(1L, 1L, "sync_finished", "success"),
            createEvent(2L, 2L, "sync_finished", "success"),
            createEvent(3L, 3L, "sync_failed", "failed")
        );
    }

    @Test
    void testGetStats_Success() {
        // Given
        when(syncProjectMapper.selectList(null)).thenReturn(mockProjects);

        // When
        ResponseEntity<DashboardController.ApiResponse<DashboardController.DynamicDashboardStats>> response =
            dashboardController.getStats();

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        DashboardController.DynamicDashboardStats stats = response.getBody().getData();
        assertThat(stats).isNotNull();
        assertThat(stats.getTotalProjects()).isEqualTo(4);
        assertThat(stats.getStatusCounts()).containsEntry("active", 2L);
        assertThat(stats.getStatusCounts()).containsEntry("failed", 1L);
        assertThat(stats.getStatusCounts()).containsEntry("pending", 1L);

        verify(syncProjectMapper, times(1)).selectList(null);
    }

    @Test
    void testGetStats_EmptyProjects() {
        // Given
        when(syncProjectMapper.selectList(null)).thenReturn(Arrays.asList());

        // When
        ResponseEntity<DashboardController.ApiResponse<DashboardController.DynamicDashboardStats>> response =
            dashboardController.getStats();

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        DashboardController.DynamicDashboardStats stats = response.getBody().getData();
        assertThat(stats).isNotNull();
        assertThat(stats.getTotalProjects()).isEqualTo(0);
        assertThat(stats.getStatusCounts()).isEmpty();
    }

    @Test
    void testGetStats_Exception() {
        // Given
        when(syncProjectMapper.selectList(null)).thenThrow(new RuntimeException("Database error"));

        // When
        ResponseEntity<DashboardController.ApiResponse<DashboardController.DynamicDashboardStats>> response =
            dashboardController.getStats();

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).contains("Query failed");
    }

    @Test
    void testGetStatusDistribution_Success() {
        // Given
        when(syncProjectMapper.selectList(null)).thenReturn(mockProjects);

        // When
        ResponseEntity<DashboardController.ApiResponse<List<DashboardController.StatusItem>>> response =
            dashboardController.getStatusDistribution();

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        List<DashboardController.StatusItem> distribution = response.getBody().getData();
        assertThat(distribution).isNotNull();
        assertThat(distribution).hasSize(3);

        // Should be sorted by count descending
        assertThat(distribution.get(0).getStatus()).isEqualTo("active");
        assertThat(distribution.get(0).getCount()).isEqualTo(2);

        verify(syncProjectMapper, times(1)).selectList(null);
    }

    @Test
    void testGetTopDelayedProjects_Success() {
        // Given
        when(syncProjectMapper.selectList(null)).thenReturn(mockProjects);

        ProjectListDTO dto1 = new ProjectListDTO();
        dto1.setId(1L);
        dto1.setProjectKey("project1");
        dto1.setDelaySeconds(3600L);
        dto1.setDelayFormatted("1h");
        dto1.setSyncStatus("active");

        ProjectListDTO dto2 = new ProjectListDTO();
        dto2.setId(2L);
        dto2.setProjectKey("project2");
        dto2.setDelaySeconds(1800L);
        dto2.setDelayFormatted("30m");
        dto2.setSyncStatus("active");

        when(projectListService.buildProjectListDTO(any())).thenReturn(dto1, dto2, dto2, dto2);

        // When
        ResponseEntity<DashboardController.ApiResponse<List<DashboardController.DelayedProject>>> response =
            dashboardController.getTopDelayedProjects(10);

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        List<DashboardController.DelayedProject> delayed = response.getBody().getData();
        assertThat(delayed).isNotNull();
        assertThat(delayed).isNotEmpty();

        // Should be sorted by delay descending
        assertThat(delayed.get(0).getDelaySeconds()).isGreaterThanOrEqualTo(delayed.get(delayed.size() - 1).getDelaySeconds());

        verify(syncProjectMapper, times(1)).selectList(null);
    }

    @Test
    void testGetRecentEvents_Success() {
        // Given
        when(syncEventMapper.selectList(any(QueryWrapper.class))).thenReturn(mockEvents);
        when(syncProjectMapper.selectById(1L)).thenReturn(createProject(1L, "project1", "active"));
        when(syncProjectMapper.selectById(2L)).thenReturn(createProject(2L, "project2", "active"));
        when(syncProjectMapper.selectById(3L)).thenReturn(createProject(3L, "project3", "failed"));

        // When
        ResponseEntity<DashboardController.ApiResponse<List<DashboardController.RecentEvent>>> response =
            dashboardController.getRecentEvents(20);

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        List<DashboardController.RecentEvent> events = response.getBody().getData();
        assertThat(events).isNotNull();
        assertThat(events).hasSize(3);

        // Verify project keys were populated
        assertThat(events.get(0).getProjectKey()).isEqualTo("project1");
        assertThat(events.get(1).getProjectKey()).isEqualTo("project2");
        assertThat(events.get(2).getProjectKey()).isEqualTo("project3");

        verify(syncEventMapper, times(1)).selectList(any(QueryWrapper.class));
        verify(syncProjectMapper, times(3)).selectById(any(Long.class));
    }

    @Test
    void testGetRecentEvents_NullProjectId() {
        // Given
        SyncEvent eventWithoutProject = createEvent(1L, null, "sync_started", "running");
        when(syncEventMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(eventWithoutProject));

        // When
        ResponseEntity<DashboardController.ApiResponse<List<DashboardController.RecentEvent>>> response =
            dashboardController.getRecentEvents(20);

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        List<DashboardController.RecentEvent> events = response.getBody().getData();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getProjectKey()).isNull();

        verify(syncEventMapper, times(1)).selectList(any(QueryWrapper.class));
        verify(syncProjectMapper, never()).selectById(any(Long.class));
    }

    @Test
    void testGetRecentEvents_ProjectNotFound() {
        // Given
        when(syncEventMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(mockEvents.get(0)));
        when(syncProjectMapper.selectById(1L)).thenReturn(null);

        // When
        ResponseEntity<DashboardController.ApiResponse<List<DashboardController.RecentEvent>>> response =
            dashboardController.getRecentEvents(20);

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        List<DashboardController.RecentEvent> events = response.getBody().getData();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getProjectKey()).isNull();

        verify(syncEventMapper, times(1)).selectList(any(QueryWrapper.class));
        verify(syncProjectMapper, times(1)).selectById(1L);
    }

    @Test
    void testGetRecentEvents_Exception() {
        // Given
        when(syncEventMapper.selectList(any(QueryWrapper.class))).thenThrow(new RuntimeException("Database error"));

        // When
        ResponseEntity<DashboardController.ApiResponse<List<DashboardController.RecentEvent>>> response =
            dashboardController.getRecentEvents(20);

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).contains("Query failed");
    }

    // Helper methods
    private SyncProject createProject(Long id, String projectKey, String syncStatus) {
        SyncProject project = new SyncProject();
        project.setId(id);
        project.setProjectKey(projectKey);
        project.setSyncStatus(syncStatus);
        project.setEnabled(true);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        return project;
    }

    private SyncEvent createEvent(Long id, Long syncProjectId, String eventType, String status) {
        SyncEvent event = new SyncEvent();
        event.setId(id);
        event.setSyncProjectId(syncProjectId);
        event.setEventType(eventType);
        event.setEventSource("test");
        event.setStatus(status);
        event.setEventTime(LocalDateTime.now());
        event.setDurationSeconds(10);
        return event;
    }
}
