package com.gitlab.mirror.server.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gitlab.mirror.server.api.dto.ApiResponse;
import com.gitlab.mirror.server.api.dto.EventDTO;
import com.gitlab.mirror.server.api.dto.PageResponse;
import com.gitlab.mirror.server.entity.SyncEvent;
import com.gitlab.mirror.server.service.EventManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventController
 *
 * @author GitLab Mirror Team
 */
@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    @Mock
    private EventManagementService eventManagementService;

    @InjectMocks
    private EventController eventController;

    private Page<SyncEvent> mockPage;
    private List<SyncEvent> mockEvents;
    private Map<Long, String> mockProjectKeys;

    @BeforeEach
    void setUp() {
        // Prepare mock events
        mockEvents = new ArrayList<>();

        SyncEvent event1 = new SyncEvent();
        event1.setId(1L);
        event1.setSyncProjectId(100L);
        event1.setEventType("project_updated");
        event1.setEventSource("discovery_service");
        event1.setStatus("success");
        event1.setEventTime(LocalDateTime.now().minusHours(1));
        mockEvents.add(event1);

        SyncEvent event2 = new SyncEvent();
        event2.setId(2L);
        event2.setSyncProjectId(101L);
        event2.setEventType("project_discovered");
        event2.setEventSource("discovery_service");
        event2.setStatus("success");
        event2.setEventTime(LocalDateTime.now().minusHours(2));
        mockEvents.add(event2);

        // Prepare mock page
        mockPage = new Page<>(1, 10);
        mockPage.setRecords(mockEvents);
        mockPage.setTotal(2);
        mockPage.setPages(1);

        // Prepare mock project keys
        mockProjectKeys = new HashMap<>();
        mockProjectKeys.put(100L, "devops/project1");
        mockProjectKeys.put(101L, "devops/project2");
    }

    /**
     * 测试查询事件 - 成功返回EventDTO列表
     */
    @Test
    void testGetEvents_Success() {
        // Given
        when(eventManagementService.queryEvents(
                eq(1), eq(10), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(mockPage);

        when(eventManagementService.getProjectKeys(anyList()))
                .thenReturn(mockProjectKeys);

        // When
        ApiResponse<PageResponse<EventDTO>> response = eventController.getEvents(
                null, null, null, null, null, 1, 10);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getItems()).hasSize(2);
        assertThat(response.getData().getTotal()).isEqualTo(2);
        assertThat(response.getData().getPage()).isEqualTo(1);
        assertThat(response.getData().getTotalPages()).isEqualTo(1);

        // Verify EventDTO contains project keys
        EventDTO dto1 = response.getData().getItems().get(0);
        assertThat(dto1.getId()).isEqualTo(1L);
        assertThat(dto1.getSyncProjectId()).isEqualTo(100L);
        assertThat(dto1.getProjectKey()).isEqualTo("devops/project1");
        assertThat(dto1.getEventType()).isEqualTo("project_updated");

        EventDTO dto2 = response.getData().getItems().get(1);
        assertThat(dto2.getId()).isEqualTo(2L);
        assertThat(dto2.getSyncProjectId()).isEqualTo(101L);
        assertThat(dto2.getProjectKey()).isEqualTo("devops/project2");

        // Verify service methods were called
        verify(eventManagementService).queryEvents(
                eq(1), eq(10), isNull(), isNull(), isNull(), isNull(), isNull());
        verify(eventManagementService).getProjectKeys(argThat(list ->
                list.size() == 2 && list.contains(100L) && list.contains(101L)));
    }

    /**
     * 测试查询事件 - 使用过滤条件
     */
    @Test
    void testGetEvents_WithFilters() {
        // Given
        Long projectId = 100L;
        String eventType = "project_updated";
        String status = "success";
        LocalDateTime startTime = LocalDateTime.now().minusDays(1);
        LocalDateTime endTime = LocalDateTime.now();

        Page<SyncEvent> filteredPage = new Page<>(1, 10);
        filteredPage.setRecords(Collections.singletonList(mockEvents.get(0)));
        filteredPage.setTotal(1);
        filteredPage.setPages(1);

        when(eventManagementService.queryEvents(
                eq(1), eq(10), eq(projectId), eq(eventType), eq(status),
                eq(startTime), eq(endTime)))
                .thenReturn(filteredPage);

        when(eventManagementService.getProjectKeys(anyList()))
                .thenReturn(mockProjectKeys);

        // When
        ApiResponse<PageResponse<EventDTO>> response = eventController.getEvents(
                projectId, eventType, status, startTime, endTime, 1, 10);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getItems()).hasSize(1);
        assertThat(response.getData().getTotal()).isEqualTo(1);

        verify(eventManagementService).queryEvents(
                eq(1), eq(10), eq(projectId), eq(eventType), eq(status),
                eq(startTime), eq(endTime));
    }

    /**
     * 测试查询事件 - 空结果
     */
    @Test
    void testGetEvents_EmptyResult() {
        // Given
        Page<SyncEvent> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(Collections.emptyList());
        emptyPage.setTotal(0);
        emptyPage.setPages(0);

        when(eventManagementService.queryEvents(
                anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(emptyPage);

        when(eventManagementService.getProjectKeys(anyList()))
                .thenReturn(Collections.emptyMap());

        // When
        ApiResponse<PageResponse<EventDTO>> response = eventController.getEvents(
                null, null, null, null, null, 1, 10);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getItems()).isEmpty();
        assertThat(response.getData().getTotal()).isEqualTo(0);
        assertThat(response.getData().getTotalPages()).isEqualTo(0);
    }

    /**
     * 测试查询事件 - 项目不存在时projectKey为null
     */
    @Test
    void testGetEvents_ProjectNotFound() {
        // Given
        when(eventManagementService.queryEvents(
                anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(mockPage);

        // Return empty map - no project keys found
        when(eventManagementService.getProjectKeys(anyList()))
                .thenReturn(Collections.emptyMap());

        // When
        ApiResponse<PageResponse<EventDTO>> response = eventController.getEvents(
                null, null, null, null, null, 1, 10);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getItems()).hasSize(2);

        // Project keys should be null when not found
        EventDTO dto1 = response.getData().getItems().get(0);
        assertThat(dto1.getProjectKey()).isNull();

        EventDTO dto2 = response.getData().getItems().get(1);
        assertThat(dto2.getProjectKey()).isNull();
    }

    /**
     * 测试查询事件 - 分页参数
     */
    @Test
    void testGetEvents_Pagination() {
        // Given
        Page<SyncEvent> page2 = new Page<>(2, 5);
        page2.setRecords(mockEvents);
        page2.setTotal(15);
        page2.setPages(3);

        when(eventManagementService.queryEvents(
                eq(2), eq(5), any(), any(), any(), any(), any()))
                .thenReturn(page2);

        when(eventManagementService.getProjectKeys(anyList()))
                .thenReturn(mockProjectKeys);

        // When
        ApiResponse<PageResponse<EventDTO>> response = eventController.getEvents(
                null, null, null, null, null, 2, 5);

        // Then
        assertThat(response.getData().getPage()).isEqualTo(2);
        assertThat(response.getData().getPageSize()).isEqualTo(5);
        assertThat(response.getData().getTotal()).isEqualTo(15);
        assertThat(response.getData().getTotalPages()).isEqualTo(3);

        verify(eventManagementService).queryEvents(
                eq(2), eq(5), any(), any(), any(), any(), any());
    }

    /**
     * 测试查询事件 - 默认分页参数
     */
    @Test
    void testGetEvents_DefaultPagination() {
        // Given
        when(eventManagementService.queryEvents(
                eq(1), eq(50), any(), any(), any(), any(), any()))
                .thenReturn(mockPage);

        when(eventManagementService.getProjectKeys(anyList()))
                .thenReturn(mockProjectKeys);

        // When - using default values
        ApiResponse<PageResponse<EventDTO>> response = eventController.getEvents(
                null, null, null, null, null, 1, 50);

        // Then
        verify(eventManagementService).queryEvents(
                eq(1), eq(50), any(), any(), any(), any(), any());
    }

    /**
     * 测试查询事件 - 去重项目ID
     */
    @Test
    void testGetEvents_DeduplicateProjectIds() {
        // Given - multiple events with same project ID
        SyncEvent event3 = new SyncEvent();
        event3.setId(3L);
        event3.setSyncProjectId(100L); // Same as event1
        event3.setEventType("project_updated");
        event3.setEventSource("system");
        event3.setStatus("success");
        event3.setEventTime(LocalDateTime.now());

        List<SyncEvent> eventsWithDup = new ArrayList<>(mockEvents);
        eventsWithDup.add(event3);

        Page<SyncEvent> pageWithDup = new Page<>(1, 10);
        pageWithDup.setRecords(eventsWithDup);
        pageWithDup.setTotal(3);

        when(eventManagementService.queryEvents(
                anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(pageWithDup);

        when(eventManagementService.getProjectKeys(anyList()))
                .thenReturn(mockProjectKeys);

        // When
        eventController.getEvents(null, null, null, null, null, 1, 10);

        // Then - getProjectKeys should be called with deduplicated list
        verify(eventManagementService).getProjectKeys(argThat(list ->
                list.size() == 2 && list.contains(100L) && list.contains(101L)));
    }
}
