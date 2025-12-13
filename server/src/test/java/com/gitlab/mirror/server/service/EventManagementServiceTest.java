package com.gitlab.mirror.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gitlab.mirror.server.entity.SyncEvent;
import com.gitlab.mirror.server.mapper.SyncEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventManagementService
 *
 * 测试要求：
 * - 测试单个事件记录
 * - 测试批量事件记录
 * - 测试并发记录
 * - 测试多维度查询
 * - 测试统计计算
 * - 测试性能
 *
 * @author GitLab Mirror Team
 */
@ExtendWith(MockitoExtension.class)
class EventManagementServiceTest {

    @Mock
    private SyncEventMapper syncEventMapper;

    @Mock
    private com.gitlab.mirror.server.mapper.SyncProjectMapper syncProjectMapper;

    @InjectMocks
    private EventManagementService eventManagementService;

    private SyncEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = new SyncEvent();
        testEvent.setSyncProjectId(100L);
        testEvent.setEventType(SyncEvent.EventType.SYNC_FINISHED);
        testEvent.setEventSource(SyncEvent.EventSource.SYSTEM);
        testEvent.setStatus(SyncEvent.Status.SUCCESS);
        testEvent.setDurationSeconds(10);
    }

    /**
     * 测试单个事件记录
     */
    @Test
    void testRecordEvent_Success() {
        // Given
        when(syncEventMapper.insert(any(SyncEvent.class)))
                .thenAnswer(invocation -> {
                    SyncEvent event = invocation.getArgument(0);
                    event.setId(1L);
                    return 1;
                });

        // When
        SyncEvent result = eventManagementService.recordEvent(testEvent);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEventTime()).isNotNull(); // 自动填充时间戳

        // 验证mapper被调用
        ArgumentCaptor<SyncEvent> captor = ArgumentCaptor.forClass(SyncEvent.class);
        verify(syncEventMapper).insert(captor.capture());

        SyncEvent captured = captor.getValue();
        assertThat(captured.getEventType()).isEqualTo(SyncEvent.EventType.SYNC_FINISHED);
        assertThat(captured.getEventSource()).isEqualTo(SyncEvent.EventSource.SYSTEM);
        assertThat(captured.getStatus()).isEqualTo(SyncEvent.Status.SUCCESS);
    }

    /**
     * 测试自动填充时间戳
     */
    @Test
    void testRecordEvent_AutoFillTimestamp() {
        // Given: 事件没有时间戳
        testEvent.setEventTime(null);

        when(syncEventMapper.insert(any(SyncEvent.class)))
                .thenAnswer(invocation -> {
                    SyncEvent event = invocation.getArgument(0);
                    event.setId(1L);
                    return 1;
                });

        // When
        SyncEvent result = eventManagementService.recordEvent(testEvent);

        // Then: 时间戳应该被自动填充
        assertThat(result.getEventTime()).isNotNull();
        assertThat(result.getEventTime()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    /**
     * 测试数据验证 - 缺少事件类型
     */
    @Test
    void testRecordEvent_ValidationError_MissingEventType() {
        // Given: 缺少事件类型
        testEvent.setEventType(null);

        // When & Then: 应该抛出异常
        assertThatThrownBy(() -> eventManagementService.recordEvent(testEvent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event type is required");

        verify(syncEventMapper, never()).insert(any());
    }

    /**
     * 测试数据验证 - 缺少事件来源
     */
    @Test
    void testRecordEvent_ValidationError_MissingEventSource() {
        // Given
        testEvent.setEventSource(null);

        // When & Then
        assertThatThrownBy(() -> eventManagementService.recordEvent(testEvent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event source is required");

        verify(syncEventMapper, never()).insert(any());
    }

    /**
     * 测试数据验证 - 缺少状态
     */
    @Test
    void testRecordEvent_ValidationError_MissingStatus() {
        // Given
        testEvent.setStatus(null);

        // When & Then
        assertThatThrownBy(() -> eventManagementService.recordEvent(testEvent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event status is required");

        verify(syncEventMapper, never()).insert(any());
    }

    /**
     * 测试批量事件记录
     */
    @Test
    void testRecordEvents_Batch() {
        // Given: 准备3个事件
        List<SyncEvent> events = Arrays.asList(
                createEvent(100L, SyncEvent.EventType.PUSH_DETECTED),
                createEvent(101L, SyncEvent.EventType.SYNC_STARTED),
                createEvent(102L, SyncEvent.EventType.SYNC_FINISHED)
        );

        when(syncEventMapper.insert(any(SyncEvent.class)))
                .thenReturn(1);

        // When
        int count = eventManagementService.recordEvents(events);

        // Then
        assertThat(count).isEqualTo(3);

        // 验证所有事件都被插入
        verify(syncEventMapper, times(3)).insert(any(SyncEvent.class));

        // 验证所有事件的时间戳都被填充
        ArgumentCaptor<SyncEvent> captor = ArgumentCaptor.forClass(SyncEvent.class);
        verify(syncEventMapper, times(3)).insert(captor.capture());

        List<SyncEvent> capturedEvents = captor.getAllValues();
        assertThat(capturedEvents).allMatch(e -> e.getEventTime() != null);
    }

    /**
     * 测试批量记录空列表
     */
    @Test
    void testRecordEvents_EmptyList() {
        // When
        int count = eventManagementService.recordEvents(new ArrayList<>());

        // Then
        assertThat(count).isEqualTo(0);
        verify(syncEventMapper, never()).insert(any());
    }

    /**
     * 测试批量记录null
     */
    @Test
    void testRecordEvents_Null() {
        // When
        int count = eventManagementService.recordEvents(null);

        // Then
        assertThat(count).isEqualTo(0);
        verify(syncEventMapper, never()).insert(any());
    }

    /**
     * 测试按项目ID查询
     */
    @Test
    void testGetEventsBySyncProject() {
        // Given
        List<SyncEvent> mockEvents = Arrays.asList(
                createEvent(100L, SyncEvent.EventType.PUSH_DETECTED),
                createEvent(100L, SyncEvent.EventType.SYNC_FINISHED)
        );

        when(syncEventMapper.selectBySyncProjectId(100L, 100))
                .thenReturn(mockEvents);

        // When
        List<SyncEvent> result = eventManagementService.getEventsBySyncProject(100L, 100);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(e -> e.getSyncProjectId().equals(100L));

        verify(syncEventMapper).selectBySyncProjectId(100L, 100);
    }

    /**
     * 测试按事件类型查询
     */
    @Test
    void testGetEventsByType() {
        // Given
        List<SyncEvent> mockEvents = Arrays.asList(
                createEvent(100L, SyncEvent.EventType.SYNC_FINISHED),
                createEvent(101L, SyncEvent.EventType.SYNC_FINISHED)
        );

        when(syncEventMapper.selectByEventType(SyncEvent.EventType.SYNC_FINISHED))
                .thenReturn(mockEvents);

        // When
        List<SyncEvent> result = eventManagementService.getEventsByType(SyncEvent.EventType.SYNC_FINISHED);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(e -> e.getEventType().equals(SyncEvent.EventType.SYNC_FINISHED));
    }

    /**
     * 测试按状态查询
     */
    @Test
    void testGetEventsByStatus() {
        // Given
        List<SyncEvent> mockEvents = Arrays.asList(
                createEvent(100L, SyncEvent.EventType.SYNC_FINISHED),
                createEvent(101L, SyncEvent.EventType.PUSH_DETECTED)
        );
        mockEvents.forEach(e -> e.setStatus(SyncEvent.Status.SUCCESS));

        when(syncEventMapper.selectByStatus(SyncEvent.Status.SUCCESS))
                .thenReturn(mockEvents);

        // When
        List<SyncEvent> result = eventManagementService.getEventsByStatus(SyncEvent.Status.SUCCESS);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(e -> e.getStatus().equals(SyncEvent.Status.SUCCESS));
    }

    /**
     * 测试按时间范围查询
     */
    @Test
    void testGetEventsByTimeRange() {
        // Given
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now();

        List<SyncEvent> mockEvents = Arrays.asList(
                createEvent(100L, SyncEvent.EventType.SYNC_FINISHED),
                createEvent(101L, SyncEvent.EventType.SYNC_FINISHED)
        );

        when(syncEventMapper.selectByTimeRange(startTime, endTime))
                .thenReturn(mockEvents);

        // When
        List<SyncEvent> result = eventManagementService.getEventsByTimeRange(startTime, endTime);

        // Then
        assertThat(result).hasSize(2);
        verify(syncEventMapper).selectByTimeRange(startTime, endTime);
    }

    /**
     * 测试多维度分页查询
     */
    @Test
    void testQueryEvents_MultiDimensional() {
        // Given
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now();

        Page<SyncEvent> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Arrays.asList(
                createEvent(100L, SyncEvent.EventType.SYNC_FINISHED),
                createEvent(100L, SyncEvent.EventType.PUSH_DETECTED)
        ));
        mockPage.setTotal(2);

        when(syncEventMapper.selectPageWithFilters(
                any(Page.class), eq(100L), eq(SyncEvent.EventType.SYNC_FINISHED),
                eq(SyncEvent.Status.SUCCESS), eq(startTime), eq(endTime)))
                .thenReturn(mockPage);

        // When
        IPage<SyncEvent> result = eventManagementService.queryEvents(
                1, 10, 100L, SyncEvent.EventType.SYNC_FINISHED,
                SyncEvent.Status.SUCCESS, startTime, endTime);

        // Then
        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(2);

        verify(syncEventMapper).selectPageWithFilters(
                any(Page.class), eq(100L), eq(SyncEvent.EventType.SYNC_FINISHED),
                eq(SyncEvent.Status.SUCCESS), eq(startTime), eq(endTime));
    }

    /**
     * 测试事件统计
     */
    @Test
    void testGetEventStatistics() {
        // Given
        Map<String, Object> mockStats = new HashMap<>();
        mockStats.put("total", 100L);
        mockStats.put("success_count", 80L);
        mockStats.put("failed_count", 20L);
        mockStats.put("avg_duration", 15.5);

        when(syncEventMapper.getEventStatistics()).thenReturn(mockStats);

        // When
        Map<String, Object> result = eventManagementService.getEventStatistics();

        // Then
        assertThat(result.get("total")).isEqualTo(100L);
        assertThat(result.get("success_count")).isEqualTo(80L);
        assertThat(result.get("failed_count")).isEqualTo(20L);
        assertThat(result.get("avg_duration")).isEqualTo(15.5);
    }

    /**
     * 测试按类型统计
     */
    @Test
    void testCountEventsByType() {
        // Given
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> row1 = new HashMap<>();
        row1.put("type", SyncEvent.EventType.SYNC_FINISHED);
        row1.put("count", 50L);
        mockData.add(row1);

        Map<String, Object> row2 = new HashMap<>();
        row2.put("type", SyncEvent.EventType.PUSH_DETECTED);
        row2.put("count", 30L);
        mockData.add(row2);

        when(syncEventMapper.countByEventType()).thenReturn(mockData);

        // When
        Map<String, Long> result = eventManagementService.countEventsByType();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(SyncEvent.EventType.SYNC_FINISHED)).isEqualTo(50L);
        assertThat(result.get(SyncEvent.EventType.PUSH_DETECTED)).isEqualTo(30L);
    }

    /**
     * 测试按状态统计
     */
    @Test
    void testCountEventsByStatus() {
        // Given
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> row1 = new HashMap<>();
        row1.put("status", SyncEvent.Status.SUCCESS);
        row1.put("count", 80L);
        mockData.add(row1);

        Map<String, Object> row2 = new HashMap<>();
        row2.put("status", SyncEvent.Status.FAILED);
        row2.put("count", 20L);
        mockData.add(row2);

        when(syncEventMapper.countByStatus()).thenReturn(mockData);

        // When
        Map<String, Long> result = eventManagementService.countEventsByStatus();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(SyncEvent.Status.SUCCESS)).isEqualTo(80L);
        assertThat(result.get(SyncEvent.Status.FAILED)).isEqualTo(20L);
    }

    /**
     * 测试获取平均同步延迟
     */
    @Test
    void testGetAverageSyncDelay() {
        // Given
        when(syncEventMapper.getAverageSyncDelay()).thenReturn(12.5);

        // When
        Double result = eventManagementService.getAverageSyncDelay();

        // Then
        assertThat(result).isEqualTo(12.5);
    }

    /**
     * 测试获取平均同步延迟 - null处理
     */
    @Test
    void testGetAverageSyncDelay_Null() {
        // Given
        when(syncEventMapper.getAverageSyncDelay()).thenReturn(null);

        // When
        Double result = eventManagementService.getAverageSyncDelay();

        // Then
        assertThat(result).isEqualTo(0.0);
    }

    /**
     * 测试Push到Sync延迟分析
     */
    @Test
    void testAnalyzePushToSyncDelay() {
        // Given
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now();

        Page<SyncEvent> mockPage = new Page<>(1, 1000);

        SyncEvent pushEvent = createEvent(100L, SyncEvent.EventType.PUSH_DETECTED);
        pushEvent.setCommitSha("abc123");
        pushEvent.setEventTime(LocalDateTime.now().minusMinutes(10));

        SyncEvent syncEvent = createEvent(100L, SyncEvent.EventType.SYNC_FINISHED);
        syncEvent.setCommitSha("abc123");
        syncEvent.setEventTime(LocalDateTime.now().minusMinutes(5));

        mockPage.setRecords(Arrays.asList(pushEvent, syncEvent));

        when(syncEventMapper.selectPageWithFilters(
                any(Page.class), eq(100L), isNull(), isNull(), eq(startTime), eq(endTime)))
                .thenReturn(mockPage);

        // When
        Map<String, Object> result = eventManagementService.analyzePushToSyncDelay(100L, startTime, endTime);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("push_count")).isEqualTo(1);
        assertThat(result.get("sync_count")).isEqualTo(1);
        assertThat(result.get("total_pairs")).isEqualTo(1);
        assertThat(result.get("avg_delay_seconds")).isNotNull();
    }

    /**
     * 测试获取单个项目路径
     */
    @Test
    void testGetProjectKey_Success() {
        // Given
        Long syncProjectId = 100L;
        com.gitlab.mirror.server.entity.SyncProject project = new com.gitlab.mirror.server.entity.SyncProject();
        project.setId(syncProjectId);
        project.setProjectKey("devops/gitlab-mirror");

        when(syncProjectMapper.selectById(syncProjectId)).thenReturn(project);

        // When
        String result = eventManagementService.getProjectKey(syncProjectId);

        // Then
        assertThat(result).isEqualTo("devops/gitlab-mirror");
        verify(syncProjectMapper).selectById(syncProjectId);
    }

    /**
     * 测试获取项目路径 - 项目不存在
     */
    @Test
    void testGetProjectKey_ProjectNotFound() {
        // Given
        Long syncProjectId = 999L;
        when(syncProjectMapper.selectById(syncProjectId)).thenReturn(null);

        // When
        String result = eventManagementService.getProjectKey(syncProjectId);

        // Then
        assertThat(result).isNull();
        verify(syncProjectMapper).selectById(syncProjectId);
    }

    /**
     * 测试获取项目路径 - null ID
     */
    @Test
    void testGetProjectKey_NullId() {
        // When
        String result = eventManagementService.getProjectKey(null);

        // Then
        assertThat(result).isNull();
        verify(syncProjectMapper, never()).selectById(any());
    }

    /**
     * 测试批量获取项目路径
     */
    @Test
    void testGetProjectKeys_MultipleProjects() {
        // Given
        List<Long> projectIds = Arrays.asList(100L, 101L, 102L);

        com.gitlab.mirror.server.entity.SyncProject project1 = new com.gitlab.mirror.server.entity.SyncProject();
        project1.setId(100L);
        project1.setProjectKey("devops/project1");

        com.gitlab.mirror.server.entity.SyncProject project2 = new com.gitlab.mirror.server.entity.SyncProject();
        project2.setId(101L);
        project2.setProjectKey("devops/project2");

        com.gitlab.mirror.server.entity.SyncProject project3 = new com.gitlab.mirror.server.entity.SyncProject();
        project3.setId(102L);
        project3.setProjectKey("devops/project3");

        when(syncProjectMapper.selectById(100L)).thenReturn(project1);
        when(syncProjectMapper.selectById(101L)).thenReturn(project2);
        when(syncProjectMapper.selectById(102L)).thenReturn(project3);

        // When
        Map<Long, String> result = eventManagementService.getProjectKeys(projectIds);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(100L)).isEqualTo("devops/project1");
        assertThat(result.get(101L)).isEqualTo("devops/project2");
        assertThat(result.get(102L)).isEqualTo("devops/project3");

        verify(syncProjectMapper, times(3)).selectById(any());
    }

    /**
     * 测试批量获取项目路径 - 包含null和不存在的项目
     */
    @Test
    void testGetProjectKeys_WithNullAndMissingProjects() {
        // Given
        List<Long> projectIds = Arrays.asList(100L, null, 999L);

        com.gitlab.mirror.server.entity.SyncProject project1 = new com.gitlab.mirror.server.entity.SyncProject();
        project1.setId(100L);
        project1.setProjectKey("devops/project1");

        when(syncProjectMapper.selectById(100L)).thenReturn(project1);
        when(syncProjectMapper.selectById(999L)).thenReturn(null);

        // When
        Map<Long, String> result = eventManagementService.getProjectKeys(projectIds);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(100L)).isEqualTo("devops/project1");
        assertThat(result.get(999L)).isNull();

        // null ID should not trigger selectById
        verify(syncProjectMapper, times(2)).selectById(any());
    }

    /**
     * 测试批量获取项目路径 - 空列表
     */
    @Test
    void testGetProjectKeys_EmptyList() {
        // When
        Map<Long, String> result = eventManagementService.getProjectKeys(new ArrayList<>());

        // Then
        assertThat(result).isEmpty();
        verify(syncProjectMapper, never()).selectById(any());
    }

    /**
     * 测试批量获取项目路径 - null列表
     */
    @Test
    void testGetProjectKeys_NullList() {
        // When
        Map<Long, String> result = eventManagementService.getProjectKeys(null);

        // Then
        assertThat(result).isEmpty();
        verify(syncProjectMapper, never()).selectById(any());
    }

    // Helper methods

    private SyncEvent createEvent(Long syncProjectId, String eventType) {
        SyncEvent event = new SyncEvent();
        event.setSyncProjectId(syncProjectId);
        event.setEventType(eventType);
        event.setEventSource(SyncEvent.EventSource.SYSTEM);
        event.setStatus(SyncEvent.Status.SUCCESS);
        event.setEventTime(LocalDateTime.now());
        return event;
    }
}
