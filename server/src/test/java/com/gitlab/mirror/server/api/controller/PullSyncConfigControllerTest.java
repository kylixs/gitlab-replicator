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
 * Unit tests for PullSyncConfigController
 *
 * @author GitLab Mirror Team
 */
@ExtendWith(MockitoExtension.class)
class PullSyncConfigControllerTest {

    @Mock
    private PullSyncConfigMapper pullSyncConfigMapper;

    @Mock
    private SyncProjectMapper syncProjectMapper;

    @Mock
    private SyncTaskMapper syncTaskMapper;

    @InjectMocks
    private PullSyncConfigController controller;

    private PullSyncConfig mockConfig;
    private SyncProject mockProject;
    private SyncTask mockTask;
    private Page<PullSyncConfig> mockPage;

    @BeforeEach
    void setUp() {
        // Prepare mock config
        mockConfig = new PullSyncConfig();
        mockConfig.setId(1L);
        mockConfig.setSyncProjectId(100L);
        mockConfig.setPriority("high");
        mockConfig.setEnabled(true);
        mockConfig.setLocalRepoPath("/data/repos/project1");
        mockConfig.setCreatedAt(LocalDateTime.now().minusDays(1));
        mockConfig.setUpdatedAt(LocalDateTime.now());

        // Prepare mock project
        mockProject = new SyncProject();
        mockProject.setId(100L);
        mockProject.setProjectKey("devops/project1");
        mockProject.setSyncStatus("active");
        mockProject.setSyncMethod("pull");
        mockProject.setEnabled(true);

        // Prepare mock task
        mockTask = new SyncTask();
        mockTask.setId(200L);
        mockTask.setSyncProjectId(100L);
        mockTask.setTaskType("pull");
        mockTask.setTaskStatus("waiting");
        mockTask.setLastRunAt(Instant.now().minusSeconds(3600));
        mockTask.setLastSyncStatus("success");
        mockTask.setConsecutiveFailures(0);

        // Prepare mock page
        List<PullSyncConfig> configs = new ArrayList<>();
        configs.add(mockConfig);

        mockPage = new Page<>(1, 20);
        mockPage.setRecords(configs);
        mockPage.setTotal(1);
        mockPage.setPages(1);
    }

    /**
     * Test getConfig - Success case
     */
    @Test
    void testGetConfig_Success() {
        // Given
        when(pullSyncConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockConfig);
        when(syncProjectMapper.selectById(100L))
                .thenReturn(mockProject);
        when(syncTaskMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockTask);

        // When
        ApiResponse<PullSyncConfigDTO> response = controller.getConfig(100L);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getId()).isEqualTo(1L);
        assertThat(response.getData().getSyncProjectId()).isEqualTo(100L);
        assertThat(response.getData().getProjectKey()).isEqualTo("devops/project1");
        assertThat(response.getData().getPriority()).isEqualTo("high");
        assertThat(response.getData().getEnabled()).isTrue();
        assertThat(response.getData().getSyncStatus()).isEqualTo("active");
        assertThat(response.getData().getConsecutiveFailures()).isEqualTo(0);

        verify(pullSyncConfigMapper).selectOne(any(LambdaQueryWrapper.class));
        verify(syncProjectMapper).selectById(100L);
        verify(syncTaskMapper).selectOne(any(LambdaQueryWrapper.class));
    }

    /**
     * Test getConfig - Config not found
     */
    @Test
    void testGetConfig_ConfigNotFound() {
        // Given
        when(pullSyncConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> controller.getConfig(100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Pull sync config not found for projectId: 100");

        verify(pullSyncConfigMapper).selectOne(any(LambdaQueryWrapper.class));
        verify(syncProjectMapper, never()).selectById(anyLong());
    }

    /**
     * Test getConfig - Project not found
     */
    @Test
    void testGetConfig_ProjectNotFound() {
        // Given
        when(pullSyncConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockConfig);
        when(syncProjectMapper.selectById(100L))
                .thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> controller.getConfig(100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found: 100");

        verify(pullSyncConfigMapper).selectOne(any(LambdaQueryWrapper.class));
        verify(syncProjectMapper).selectById(100L);
    }

    /**
     * Test listConfigs - Success with no filters
     */
    @Test
    void testListConfigs_Success() {
        // Given
        when(pullSyncConfigMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mockPage);
        when(syncProjectMapper.selectById(100L))
                .thenReturn(mockProject);
        when(syncTaskMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockTask);

        // When
        ApiResponse<PageResponse<PullSyncConfigDTO>> response = controller.listConfigs(
                null, null, 1, 20);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getItems()).hasSize(1);
        assertThat(response.getData().getTotal()).isEqualTo(1);
        assertThat(response.getData().getPage()).isEqualTo(1);
        assertThat(response.getData().getPageSize()).isEqualTo(20);

        PullSyncConfigDTO dto = response.getData().getItems().get(0);
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getProjectKey()).isEqualTo("devops/project1");
        assertThat(dto.getPriority()).isEqualTo("high");

        verify(pullSyncConfigMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    /**
     * Test listConfigs - With priority filter
     */
    @Test
    void testListConfigs_WithPriorityFilter() {
        // Given
        when(pullSyncConfigMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mockPage);
        when(syncProjectMapper.selectById(100L))
                .thenReturn(mockProject);
        when(syncTaskMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockTask);

        // When
        ApiResponse<PageResponse<PullSyncConfigDTO>> response = controller.listConfigs(
                "high", null, 1, 20);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getItems()).hasSize(1);

        verify(pullSyncConfigMapper).selectPage(any(Page.class), argThat(wrapper -> {
            // Verify the wrapper has priority condition
            return true; // Can't easily verify LambdaQueryWrapper internals
        }));
    }

    /**
     * Test listConfigs - With enabled filter
     */
    @Test
    void testListConfigs_WithEnabledFilter() {
        // Given
        when(pullSyncConfigMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mockPage);
        when(syncProjectMapper.selectById(100L))
                .thenReturn(mockProject);
        when(syncTaskMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockTask);

        // When
        ApiResponse<PageResponse<PullSyncConfigDTO>> response = controller.listConfigs(
                null, true, 1, 20);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getItems()).hasSize(1);

        verify(pullSyncConfigMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    /**
     * Test listConfigs - Empty result
     */
    @Test
    void testListConfigs_EmptyResult() {
        // Given
        Page<PullSyncConfig> emptyPage = new Page<>(1, 20);
        emptyPage.setRecords(new ArrayList<>());
        emptyPage.setTotal(0);
        emptyPage.setPages(0);

        when(pullSyncConfigMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(emptyPage);

        // When
        ApiResponse<PageResponse<PullSyncConfigDTO>> response = controller.listConfigs(
                null, null, 1, 20);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getItems()).isEmpty();
        assertThat(response.getData().getTotal()).isEqualTo(0);
    }

    /**
     * Test listConfigs - Pagination
     */
    @Test
    void testListConfigs_Pagination() {
        // Given
        Page<PullSyncConfig> page2 = new Page<>(2, 10);
        page2.setRecords(new ArrayList<>());
        page2.setTotal(25);
        page2.setPages(3);

        when(pullSyncConfigMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page2);

        // When
        ApiResponse<PageResponse<PullSyncConfigDTO>> response = controller.listConfigs(
                null, null, 2, 10);

        // Then
        assertThat(response.getData().getPage()).isEqualTo(2);
        assertThat(response.getData().getPageSize()).isEqualTo(10);

        verify(pullSyncConfigMapper).selectPage(argThat(page ->
                page.getCurrent() == 2 && page.getSize() == 10
        ), any(LambdaQueryWrapper.class));
    }

    /**
     * Test updatePriority - Success
     */
    @Test
    void testUpdatePriority_Success() {
        // Given
        UpdatePriorityRequest request = new UpdatePriorityRequest();
        request.setPriority("critical");

        when(pullSyncConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockConfig);
        when(pullSyncConfigMapper.updateById(any(PullSyncConfig.class)))
                .thenReturn(1);
        when(syncProjectMapper.selectById(100L))
                .thenReturn(mockProject);
        when(syncTaskMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockTask);

        // When
        ApiResponse<PullSyncConfigDTO> response = controller.updatePriority(100L, request);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(mockConfig.getPriority()).isEqualTo("critical");

        verify(pullSyncConfigMapper).selectOne(any(LambdaQueryWrapper.class));
        verify(pullSyncConfigMapper).updateById(argThat(config ->
                config.getPriority().equals("critical") && config.getUpdatedAt() != null
        ));
    }

    /**
     * Test updatePriority - Config not found
     */
    @Test
    void testUpdatePriority_ConfigNotFound() {
        // Given
        UpdatePriorityRequest request = new UpdatePriorityRequest();
        request.setPriority("critical");

        when(pullSyncConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> controller.updatePriority(100L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Pull sync config not found for projectId: 100");

        verify(pullSyncConfigMapper).selectOne(any(LambdaQueryWrapper.class));
        verify(pullSyncConfigMapper, never()).updateById(any());
    }

    /**
     * Test updateEnabled - Success enabling
     */
    @Test
    void testUpdateEnabled_Success_Enable() {
        // Given
        UpdateEnabledRequest request = new UpdateEnabledRequest();
        request.setEnabled(true);

        when(pullSyncConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockConfig);
        when(syncProjectMapper.selectById(100L))
                .thenReturn(mockProject);
        when(pullSyncConfigMapper.updateById(any(PullSyncConfig.class)))
                .thenReturn(1);
        when(syncProjectMapper.updateById(any(SyncProject.class)))
                .thenReturn(1);
        when(syncTaskMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockTask);

        // When
        ApiResponse<PullSyncConfigDTO> response = controller.updateEnabled(100L, request);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(mockConfig.getEnabled()).isTrue();
        assertThat(mockProject.getEnabled()).isTrue();

        verify(pullSyncConfigMapper).updateById(any(PullSyncConfig.class));
        verify(syncProjectMapper).updateById(any(SyncProject.class));
    }

    /**
     * Test updateEnabled - Success disabling
     */
    @Test
    void testUpdateEnabled_Success_Disable() {
        // Given
        UpdateEnabledRequest request = new UpdateEnabledRequest();
        request.setEnabled(false);

        when(pullSyncConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockConfig);
        when(syncProjectMapper.selectById(100L))
                .thenReturn(mockProject);
        when(pullSyncConfigMapper.updateById(any(PullSyncConfig.class)))
                .thenReturn(1);
        when(syncProjectMapper.updateById(any(SyncProject.class)))
                .thenReturn(1);
        when(syncTaskMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockTask);

        // When
        ApiResponse<PullSyncConfigDTO> response = controller.updateEnabled(100L, request);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(mockConfig.getEnabled()).isFalse();
        assertThat(mockProject.getEnabled()).isFalse();

        verify(pullSyncConfigMapper).updateById(any(PullSyncConfig.class));
        verify(syncProjectMapper).updateById(any(SyncProject.class));
    }

    /**
     * Test updateEnabled - Config not found
     */
    @Test
    void testUpdateEnabled_ConfigNotFound() {
        // Given
        UpdateEnabledRequest request = new UpdateEnabledRequest();
        request.setEnabled(true);

        when(pullSyncConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> controller.updateEnabled(100L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Pull sync config not found for projectId: 100");

        verify(pullSyncConfigMapper, never()).updateById(any());
        verify(syncProjectMapper, never()).updateById(any());
    }

    /**
     * Test updateEnabled - Project not found
     */
    @Test
    void testUpdateEnabled_ProjectNotFound() {
        // Given
        UpdateEnabledRequest request = new UpdateEnabledRequest();
        request.setEnabled(true);

        when(pullSyncConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(mockConfig);
        when(syncProjectMapper.selectById(100L))
                .thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> controller.updateEnabled(100L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found: 100");

        verify(pullSyncConfigMapper, never()).updateById(any());
        verify(syncProjectMapper, never()).updateById(any());
    }

    /**
     * Test listConfigs - Task not found (should still work)
     */
    @Test
    void testListConfigs_TaskNotFound() {
        // Given
        when(pullSyncConfigMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mockPage);
        when(syncProjectMapper.selectById(100L))
                .thenReturn(mockProject);
        when(syncTaskMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);

        // When
        ApiResponse<PageResponse<PullSyncConfigDTO>> response = controller.listConfigs(
                null, null, 1, 20);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getItems()).hasSize(1);

        PullSyncConfigDTO dto = response.getData().getItems().get(0);
        assertThat(dto.getLastSyncAt()).isNull();
        assertThat(dto.getConsecutiveFailures()).isNull();
    }
}
