package com.gitlab.mirror.server.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gitlab.mirror.server.api.dto.*;
import com.gitlab.mirror.server.api.exception.ResourceNotFoundException;
import com.gitlab.mirror.server.entity.PullSyncConfig;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.mapper.PullSyncConfigMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.mapper.SyncTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TaskController
 *
 * @author GitLab Mirror Team
 */
@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    @Mock
    private SyncTaskMapper syncTaskMapper;

    @Mock
    private SyncProjectMapper syncProjectMapper;

    @Mock
    private PullSyncConfigMapper pullSyncConfigMapper;

    @InjectMocks
    private TaskController controller;

    private SyncTask mockPullTask;
    private SyncTask mockPushTask;
    private SyncProject mockProject;
    private PullSyncConfig mockConfig;
    private Page<SyncTask> mockPage;

    @BeforeEach
    void setUp() {
        // Prepare mock Pull task
        mockPullTask = new SyncTask();
        mockPullTask.setId(1L);
        mockPullTask.setSyncProjectId(100L);
        mockPullTask.setTaskType("pull");
        mockPullTask.setTaskStatus("waiting");
        mockPullTask.setNextRunAt(Instant.now().plusSeconds(3600));
        mockPullTask.setLastRunAt(Instant.now().minusSeconds(7200));
        mockPullTask.setLastSyncStatus("success");
        mockPullTask.setConsecutiveFailures(0);
        mockPullTask.setCreatedAt(LocalDateTime.now().minusDays(1));
        mockPullTask.setUpdatedAt(LocalDateTime.now());

        // Prepare mock Push task
        mockPushTask = new SyncTask();
        mockPushTask.setId(2L);
        mockPushTask.setSyncProjectId(101L);
        mockPushTask.setTaskType("push");
        mockPushTask.setTaskStatus("running");
        mockPushTask.setNextRunAt(Instant.now().plusSeconds(1800));
        mockPushTask.setLastRunAt(Instant.now().minusSeconds(3600));
        mockPushTask.setLastSyncStatus("success");
        mockPushTask.setConsecutiveFailures(0);
        mockPushTask.setCreatedAt(LocalDateTime.now().minusDays(2));
        mockPushTask.setUpdatedAt(LocalDateTime.now().minusMinutes(5));

        // Prepare mock project
        mockProject = new SyncProject();
        mockProject.setId(100L);
        mockProject.setProjectKey("devops/project1");
        mockProject.setSyncMethod("pull");
        mockProject.setEnabled(true);

        // Prepare mock config
        mockConfig = new PullSyncConfig();
        mockConfig.setId(1L);
        mockConfig.setSyncProjectId(100L);
        mockConfig.setPriority("high");
        mockConfig.setEnabled(true);

        // Prepare mock page
        List<SyncTask> tasks = new ArrayList<>();
        tasks.add(mockPullTask);
        tasks.add(mockPushTask);

        mockPage = new Page<>(1, 20);
        mockPage.setRecords(tasks);
        mockPage.setTotal(2);
        mockPage.setPages(1);
    }

    /**
     * Test listTasks - Success with no filters
     */
    @Test
    void testListTasks_Success() {
        // Given
        when(syncTaskMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mockPage);
        when(syncProjectMapper.selectById(100L))
                .thenReturn(mockProject);
        when(syncProjectMapper.selectById(101L))
                .thenReturn(mockProject);
        when(pullSyncConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockConfig);

        // When
        ApiResponse<PageResponse<TaskDTO>> response = controller.listTasks(
                null, null, null, null, 1, 20);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getItems()).hasSize(2);
        assertThat(response.getData().getTotal()).isEqualTo(2);
        assertThat(response.getData().getPage()).isEqualTo(1);

        TaskDTO dto1 = response.getData().getItems().get(0);
        assertThat(dto1.getId()).isEqualTo(1L);
        assertThat(dto1.getTaskType()).isEqualTo("pull");
        assertThat(dto1.getProjectKey()).isEqualTo("devops/project1");
        assertThat(dto1.getPriority()).isEqualTo("high");

        verify(syncTaskMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    /**
     * Test listTasks - With type filter
     */
    @Test
    void testListTasks_WithTypeFilter() {
        // Given
        Page<SyncTask> pullTaskPage = new Page<>(1, 20);
        pullTaskPage.setRecords(List.of(mockPullTask));
        pullTaskPage.setTotal(1);

        when(syncTaskMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pullTaskPage);
        when(syncProjectMapper.selectById(100L))
                .thenReturn(mockProject);
        when(pullSyncConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockConfig);

        // When
        ApiResponse<PageResponse<TaskDTO>> response = controller.listTasks(
                "pull", null, null, null, 1, 20);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getItems()).hasSize(1);
        assertThat(response.getData().getItems().get(0).getTaskType()).isEqualTo("pull");

        verify(syncTaskMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    /**
     * Test listTasks - With status filter
     */
    @Test
    void testListTasks_WithStatusFilter() {
        // Given
        Page<SyncTask> waitingTaskPage = new Page<>(1, 20);
        waitingTaskPage.setRecords(List.of(mockPullTask));
        waitingTaskPage.setTotal(1);

        when(syncTaskMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(waitingTaskPage);
        when(syncProjectMapper.selectById(100L))
                .thenReturn(mockProject);
        when(pullSyncConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockConfig);

        // When
        ApiResponse<PageResponse<TaskDTO>> response = controller.listTasks(
                null, "waiting", null, null, 1, 20);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getItems()).hasSize(1);
        assertThat(response.getData().getItems().get(0).getTaskStatus()).isEqualTo("waiting");
    }

    /**
     * Test listTasks - With priority filter
     */
    @Test
    void testListTasks_WithPriorityFilter() {
        // Given
        when(syncTaskMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mockPage);
        when(syncProjectMapper.selectById(anyLong()))
                .thenReturn(mockProject);
        when(pullSyncConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockConfig);

        // When
        ApiResponse<PageResponse<TaskDTO>> response = controller.listTasks(
                null, null, "high", null, 1, 20);

        // Then
        assertThat(response.getSuccess()).isTrue();
        // Only Pull task has priority "high", Push task is filtered out
        assertThat(response.getData().getItems()).hasSize(1);
        assertThat(response.getData().getItems().get(0).getPriority()).isEqualTo("high");
    }

    /**
     * Test listTasks - With enabled filter
     */
    @Test
    void testListTasks_WithEnabledFilter() {
        // Given
        when(syncTaskMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mockPage);
        when(syncProjectMapper.selectById(anyLong()))
                .thenReturn(mockProject);
        when(pullSyncConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockConfig);

        // When
        ApiResponse<PageResponse<TaskDTO>> response = controller.listTasks(
                null, null, null, true, 1, 20);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getItems()).allMatch(TaskDTO::getEnabled);
    }

    /**
     * Test listTasks - Empty result
     */
    @Test
    void testListTasks_EmptyResult() {
        // Given
        Page<SyncTask> emptyPage = new Page<>(1, 20);
        emptyPage.setRecords(new ArrayList<>());
        emptyPage.setTotal(0);
        emptyPage.setPages(0);

        when(syncTaskMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(emptyPage);

        // When
        ApiResponse<PageResponse<TaskDTO>> response = controller.listTasks(
                null, null, null, null, 1, 20);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getItems()).isEmpty();
        assertThat(response.getData().getTotal()).isEqualTo(0);
    }

    /**
     * Test listTasks - Pagination
     */
    @Test
    void testListTasks_Pagination() {
        // Given
        Page<SyncTask> page2 = new Page<>(2, 10);
        page2.setRecords(new ArrayList<>());
        page2.setTotal(25);
        page2.setPages(3);

        when(syncTaskMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page2);

        // When
        ApiResponse<PageResponse<TaskDTO>> response = controller.listTasks(
                null, null, null, null, 2, 10);

        // Then
        assertThat(response.getData().getPage()).isEqualTo(2);
        assertThat(response.getData().getPageSize()).isEqualTo(10);

        verify(syncTaskMapper).selectPage(argThat(page ->
                page.getCurrent() == 2 && page.getSize() == 10
        ), any(LambdaQueryWrapper.class));
    }

    /**
     * Test getTask - Success
     */
    @Test
    void testGetTask_Success() {
        // Given
        when(syncTaskMapper.selectById(1L))
                .thenReturn(mockPullTask);
        when(syncProjectMapper.selectById(100L))
                .thenReturn(mockProject);
        when(pullSyncConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockConfig);

        // When
        ApiResponse<TaskDTO> response = controller.getTask(1L);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getId()).isEqualTo(1L);
        assertThat(response.getData().getTaskType()).isEqualTo("pull");
        assertThat(response.getData().getProjectKey()).isEqualTo("devops/project1");
        assertThat(response.getData().getPriority()).isEqualTo("high");

        verify(syncTaskMapper).selectById(1L);
    }

    /**
     * Test getTask - Task not found
     */
    @Test
    void testGetTask_NotFound() {
        // Given
        when(syncTaskMapper.selectById(999L))
                .thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> controller.getTask(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Task not found: 999");

        verify(syncTaskMapper).selectById(999L);
    }

    /**
     * Test retryTask - Success
     */
    @Test
    void testRetryTask_Success() {
        // Given
        when(syncTaskMapper.selectById(1L))
                .thenReturn(mockPullTask);
        when(syncTaskMapper.updateById(any(SyncTask.class)))
                .thenReturn(1);
        when(syncProjectMapper.selectById(100L))
                .thenReturn(mockProject);
        when(pullSyncConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockConfig);

        // When
        ApiResponse<TaskDTO> response = controller.retryTask(1L);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(mockPullTask.getTaskStatus()).isEqualTo("waiting");
        assertThat(mockPullTask.getNextRunAt()).isNotNull();

        verify(syncTaskMapper).selectById(1L);
        verify(syncTaskMapper).updateById(argThat(task ->
                task.getTaskStatus().equals("waiting") &&
                        task.getNextRunAt() != null &&
                        task.getUpdatedAt() != null
        ));
    }

    /**
     * Test retryTask - Task not found
     */
    @Test
    void testRetryTask_NotFound() {
        // Given
        when(syncTaskMapper.selectById(999L))
                .thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> controller.retryTask(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Task not found: 999");

        verify(syncTaskMapper).selectById(999L);
        verify(syncTaskMapper, never()).updateById(any());
    }

    /**
     * Test resetFailures - Success
     */
    @Test
    void testResetFailures_Success() {
        // Given
        mockPullTask.setConsecutiveFailures(3);
        mockPullTask.setErrorMessage("Some error");
        mockProject.setEnabled(false);
        mockConfig.setEnabled(false);

        when(syncTaskMapper.selectById(1L))
                .thenReturn(mockPullTask);
        when(syncTaskMapper.updateById(any(SyncTask.class)))
                .thenReturn(1);
        when(syncProjectMapper.selectById(100L))
                .thenReturn(mockProject);
        when(syncProjectMapper.updateById(any(SyncProject.class)))
                .thenReturn(1);
        when(pullSyncConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockConfig);
        when(pullSyncConfigMapper.updateById(any(PullSyncConfig.class)))
                .thenReturn(1);

        // When
        ApiResponse<TaskDTO> response = controller.resetFailures(1L, null);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(mockPullTask.getConsecutiveFailures()).isEqualTo(0);
        assertThat(mockPullTask.getErrorMessage()).isNull();
        assertThat(mockPullTask.getTaskStatus()).isEqualTo("waiting");
        assertThat(mockProject.getEnabled()).isTrue();
        assertThat(mockConfig.getEnabled()).isTrue();

        verify(syncTaskMapper).updateById(any(SyncTask.class));
        verify(syncProjectMapper).updateById(any(SyncProject.class));
        verify(pullSyncConfigMapper).updateById(any(PullSyncConfig.class));
    }

    /**
     * Test resetFailures - Task not found
     */
    @Test
    void testResetFailures_NotFound() {
        // Given
        when(syncTaskMapper.selectById(999L))
                .thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> controller.resetFailures(999L, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Task not found: 999");

        verify(syncTaskMapper).selectById(999L);
        verify(syncTaskMapper, never()).updateById(any());
    }

    /**
     * Test getTaskStats - Success
     */
    @Test
    void testGetTaskStats_Success() {
        // Given
        when(syncTaskMapper.selectCount(isNull()))
                .thenReturn(10L);
        when(syncTaskMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    // Return different counts based on query
                    return 5L;
                });
        when(syncTaskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(mockPullTask));
        when(pullSyncConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockConfig);

        // When
        ApiResponse<TaskStatsDTO> response = controller.getTaskStats();

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getTotalTasks()).isEqualTo(10L);
        assertThat(response.getData().getHighPriorityTasks()).isEqualTo(1L);

        verify(syncTaskMapper).selectCount(isNull());
        verify(syncTaskMapper, atLeastOnce()).selectCount(any(LambdaQueryWrapper.class));
    }

    /**
     * Test listTasks - Project not found (should still work)
     */
    @Test
    void testListTasks_ProjectNotFound() {
        // Given
        when(syncTaskMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mockPage);
        when(syncProjectMapper.selectById(anyLong()))
                .thenReturn(null);

        // When
        ApiResponse<PageResponse<TaskDTO>> response = controller.listTasks(
                null, null, null, null, 1, 20);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getItems()).hasSize(2);

        TaskDTO dto = response.getData().getItems().get(0);
        assertThat(dto.getProjectKey()).isNull();
        assertThat(dto.getSyncMethod()).isNull();
    }

    /**
     * Test getTaskStats - Priority distribution
     */
    @Test
    void testGetTaskStats_PriorityDistribution() {
        // Given
        PullSyncConfig criticalConfig = new PullSyncConfig();
        criticalConfig.setPriority("critical");
        criticalConfig.setSyncProjectId(100L);

        PullSyncConfig normalConfig = new PullSyncConfig();
        normalConfig.setPriority("normal");
        normalConfig.setSyncProjectId(101L);

        SyncTask task2 = new SyncTask();
        task2.setSyncProjectId(101L);
        task2.setTaskType("pull");

        when(syncTaskMapper.selectCount(any()))
                .thenReturn(5L);
        when(syncTaskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(mockPullTask, task2));
        when(pullSyncConfigMapper.selectOne(argThat(wrapper -> {
            // This is a simplified check - in reality LambdaQueryWrapper is harder to inspect
            return true;
        })))
                .thenReturn(criticalConfig, normalConfig);

        // When
        ApiResponse<TaskStatsDTO> response = controller.getTaskStats();

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getCriticalTasks()).isEqualTo(1L);
        assertThat(response.getData().getNormalPriorityTasks()).isEqualTo(1L);
    }
}
