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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Test
    void testGetTrend_Last24Hours_Success() {
        // Given - Mock SQL aggregation result for 24 hours
        List<Map<String, Object>> mockHourlyStats = createMockHourlyStats();
        when(syncEventMapper.getHourlyTrend24h(any(LocalDateTime.class))).thenReturn(mockHourlyStats);

        // When
        ResponseEntity<DashboardController.ApiResponse<DashboardController.TrendData>> response =
            dashboardController.getTrend("24h");

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        DashboardController.TrendData trendData = response.getBody().getData();
        assertThat(trendData).isNotNull();
        assertThat(trendData.getDates()).hasSize(24);
        assertThat(trendData.getTotalSyncs()).hasSize(24);
        assertThat(trendData.getSuccessSyncs()).hasSize(24);
        assertThat(trendData.getFailedSyncs()).hasSize(24);

        // Verify specific hour data
        int hourIndex = findHourIndex(trendData.getDates(), "14");
        assertThat(hourIndex).isGreaterThanOrEqualTo(0);
        assertThat(trendData.getTotalSyncs().get(hourIndex)).isEqualTo(10);
        assertThat(trendData.getSuccessSyncs().get(hourIndex)).isEqualTo(8);
        assertThat(trendData.getFailedSyncs().get(hourIndex)).isEqualTo(2);

        hourIndex = findHourIndex(trendData.getDates(), "19");
        assertThat(hourIndex).isGreaterThanOrEqualTo(0);
        assertThat(trendData.getTotalSyncs().get(hourIndex)).isEqualTo(5);
        assertThat(trendData.getSuccessSyncs().get(hourIndex)).isEqualTo(3);
        assertThat(trendData.getFailedSyncs().get(hourIndex)).isEqualTo(2);

        verify(syncEventMapper, times(1)).getHourlyTrend24h(any(LocalDateTime.class));
    }

    @Test
    void testGetTrend_Last7Days_Success() {
        // Given - Mock events for 7 days
        LocalDateTime now = LocalDateTime.now();
        List<SyncEvent> mockEvents = Arrays.asList(
            createEventAt(1L, 1L, "sync_finished", "success", now.minusDays(1)),
            createEventAt(2L, 2L, "sync_finished", "success", now.minusDays(1)),
            createEventAt(3L, 3L, "sync_finished", "failed", now.minusDays(2)),
            createEventAt(4L, 1L, "sync_finished", "success", now.minusDays(3)),
            createEventAt(5L, 2L, "sync_finished", "success", now.minusDays(3)),
            createEventAt(6L, 3L, "sync_finished", "success", now.minusDays(3))
        );
        when(syncEventMapper.selectList(any(QueryWrapper.class))).thenReturn(mockEvents);

        // When
        ResponseEntity<DashboardController.ApiResponse<DashboardController.TrendData>> response =
            dashboardController.getTrend("7d");

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        DashboardController.TrendData trendData = response.getBody().getData();
        assertThat(trendData).isNotNull();
        assertThat(trendData.getDates()).hasSize(7);
        assertThat(trendData.getTotalSyncs()).hasSize(7);
        assertThat(trendData.getSuccessSyncs()).hasSize(7);
        assertThat(trendData.getFailedSyncs()).hasSize(7);

        // Total should be 6 events across 7 days
        int totalSum = trendData.getTotalSyncs().stream().mapToInt(Integer::intValue).sum();
        assertThat(totalSum).isEqualTo(6);

        int successSum = trendData.getSuccessSyncs().stream().mapToInt(Integer::intValue).sum();
        assertThat(successSum).isEqualTo(5);

        int failedSum = trendData.getFailedSyncs().stream().mapToInt(Integer::intValue).sum();
        assertThat(failedSum).isEqualTo(1);

        verify(syncEventMapper, times(1)).selectList(any(QueryWrapper.class));
    }

    @Test
    void testGetEventTypeTrend_Last24Hours_Success() {
        // Given - Mock SQL aggregation result for event type trend
        List<Map<String, Object>> mockEventTypeStats = createMockEventTypeStats();
        when(syncEventMapper.getHourlyEventTypeTrend24h(any(LocalDateTime.class))).thenReturn(mockEventTypeStats);

        // When
        ResponseEntity<DashboardController.ApiResponse<DashboardController.EventTypeTrend>> response =
            dashboardController.getEventTypeTrend("24h");

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        DashboardController.EventTypeTrend trend = response.getBody().getData();
        assertThat(trend).isNotNull();
        assertThat(trend.getDates()).hasSize(24);
        assertThat(trend.getTypeData()).isNotEmpty();
        assertThat(trend.getTypeData()).containsKeys("sync_finished", "sync_failed");

        // Verify sync_finished data
        List<Integer> syncFinishedData = trend.getTypeData().get("sync_finished");
        assertThat(syncFinishedData).hasSize(24);

        int hourIndex = findHourIndex(trend.getDates(), "14");
        assertThat(hourIndex).isGreaterThanOrEqualTo(0);
        assertThat(syncFinishedData.get(hourIndex)).isEqualTo(8);

        verify(syncEventMapper, times(1)).getHourlyEventTypeTrend24h(any(LocalDateTime.class));
    }

    @Test
    void testGetEventTypeTrend_Last7Days_Success() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        List<SyncEvent> mockEvents = Arrays.asList(
            createEventAt(1L, 1L, "sync_finished", "success", now.minusDays(1)),
            createEventAt(2L, 2L, "sync_finished", "success", now.minusDays(1)),
            createEventAt(3L, 3L, "target_project_created", "success", now.minusDays(2)),
            createEventAt(4L, 1L, "sync_finished", "success", now.minusDays(3)),
            createEventAt(5L, 2L, "sync_failed", "failed", now.minusDays(3))
        );
        when(syncEventMapper.selectList(any(QueryWrapper.class))).thenReturn(mockEvents);

        // When
        ResponseEntity<DashboardController.ApiResponse<DashboardController.EventTypeTrend>> response =
            dashboardController.getEventTypeTrend("7d");

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        DashboardController.EventTypeTrend trend = response.getBody().getData();
        assertThat(trend).isNotNull();
        assertThat(trend.getDates()).hasSize(7);
        assertThat(trend.getTypeData()).isNotEmpty();
        assertThat(trend.getTypeData()).containsKeys("sync_finished", "sync_failed", "target_project_created");

        // Verify total counts
        int syncFinishedTotal = trend.getTypeData().get("sync_finished").stream().mapToInt(Integer::intValue).sum();
        assertThat(syncFinishedTotal).isEqualTo(3);

        int syncFailedTotal = trend.getTypeData().get("sync_failed").stream().mapToInt(Integer::intValue).sum();
        assertThat(syncFailedTotal).isEqualTo(1);

        int targetCreatedTotal = trend.getTypeData().get("target_project_created").stream().mapToInt(Integer::intValue).sum();
        assertThat(targetCreatedTotal).isEqualTo(1);

        verify(syncEventMapper, times(1)).selectList(any(QueryWrapper.class));
    }

    @Test
    void testGetTrend_EmptyData() {
        // Given - No events
        when(syncEventMapper.getHourlyTrend24h(any(LocalDateTime.class))).thenReturn(createEmptyHourlyStats());

        // When
        ResponseEntity<DashboardController.ApiResponse<DashboardController.TrendData>> response =
            dashboardController.getTrend("24h");

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        DashboardController.TrendData trendData = response.getBody().getData();
        assertThat(trendData).isNotNull();
        assertThat(trendData.getDates()).hasSize(24);

        // All counts should be 0
        assertThat(trendData.getTotalSyncs()).allMatch(count -> count == 0);
        assertThat(trendData.getSuccessSyncs()).allMatch(count -> count == 0);
        assertThat(trendData.getFailedSyncs()).allMatch(count -> count == 0);
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

    private SyncEvent createEventAt(Long id, Long syncProjectId, String eventType, String status, LocalDateTime eventTime) {
        SyncEvent event = createEvent(id, syncProjectId, eventType, status);
        event.setEventTime(eventTime);
        return event;
    }

    private List<Map<String, Object>> createMockHourlyStats() {
        List<Map<String, Object>> stats = new java.util.ArrayList<>();

        // Create 24 hours of data, with some hours having events
        for (int hour = 0; hour < 24; hour++) {
            Map<String, Object> stat = new HashMap<>();
            stat.put("hour", hour);

            if (hour == 14) {
                // Hour 14: 10 total (8 success, 2 failed)
                stat.put("total", 10L);
                stat.put("success", 8L);
                stat.put("failed", 2L);
            } else if (hour == 19) {
                // Hour 19: 5 total (3 success, 2 failed)
                stat.put("total", 5L);
                stat.put("success", 3L);
                stat.put("failed", 2L);
            } else if (hour == 23) {
                // Hour 23: 4 total (all success - target_project_created events)
                stat.put("total", 4L);
                stat.put("success", 4L);
                stat.put("failed", 0L);
            } else {
                // Other hours: no events
                stat.put("total", 0L);
                stat.put("success", 0L);
                stat.put("failed", 0L);
            }

            stats.add(stat);
        }

        return stats;
    }

    private List<Map<String, Object>> createEmptyHourlyStats() {
        List<Map<String, Object>> stats = new java.util.ArrayList<>();

        for (int hour = 0; hour < 24; hour++) {
            Map<String, Object> stat = new HashMap<>();
            stat.put("hour", hour);
            stat.put("total", 0L);
            stat.put("success", 0L);
            stat.put("failed", 0L);
            stats.add(stat);
        }

        return stats;
    }

    private List<Map<String, Object>> createMockEventTypeStats() {
        List<Map<String, Object>> stats = new java.util.ArrayList<>();

        // Create event type data for 24 hours
        for (int hour = 0; hour < 24; hour++) {
            if (hour == 14) {
                // Hour 14: 8 sync_finished, 2 sync_failed
                Map<String, Object> syncFinished = new HashMap<>();
                syncFinished.put("hour", hour);
                syncFinished.put("event_type", "sync_finished");
                syncFinished.put("count", 8L);
                stats.add(syncFinished);

                Map<String, Object> syncFailed = new HashMap<>();
                syncFailed.put("hour", hour);
                syncFailed.put("event_type", "sync_failed");
                syncFailed.put("count", 2L);
                stats.add(syncFailed);
            } else if (hour == 19) {
                // Hour 19: 3 sync_finished, 2 sync_failed
                Map<String, Object> syncFinished = new HashMap<>();
                syncFinished.put("hour", hour);
                syncFinished.put("event_type", "sync_finished");
                syncFinished.put("count", 3L);
                stats.add(syncFinished);

                Map<String, Object> syncFailed = new HashMap<>();
                syncFailed.put("hour", hour);
                syncFailed.put("event_type", "sync_failed");
                syncFailed.put("count", 2L);
                stats.add(syncFailed);
            } else if (hour == 23) {
                // Hour 23: 4 target_project_created
                Map<String, Object> targetCreated = new HashMap<>();
                targetCreated.put("hour", hour);
                targetCreated.put("event_type", "target_project_created");
                targetCreated.put("count", 4L);
                stats.add(targetCreated);
            } else {
                // Other hours: no events - still need to include them with count 0
                Map<String, Object> emptyFinished = new HashMap<>();
                emptyFinished.put("hour", hour);
                emptyFinished.put("event_type", "sync_finished");
                emptyFinished.put("count", 0L);
                stats.add(emptyFinished);

                Map<String, Object> emptyFailed = new HashMap<>();
                emptyFailed.put("hour", hour);
                emptyFailed.put("event_type", "sync_failed");
                emptyFailed.put("count", 0L);
                stats.add(emptyFailed);
            }
        }

        return stats;
    }

    private int findHourIndex(List<String> hours, String targetHour) {
        for (int i = 0; i < hours.size(); i++) {
            if (hours.get(i).equals(targetHour)) {
                return i;
            }
        }
        return -1;
    }
}
