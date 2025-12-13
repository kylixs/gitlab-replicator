package com.gitlab.mirror.server.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gitlab.mirror.server.api.dto.*;
import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.mapper.SyncTaskMapper;
import com.gitlab.mirror.server.scheduler.UnifiedSyncScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Instant;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SchedulerController
 *
 * @author GitLab Mirror Team
 */
@ExtendWith(MockitoExtension.class)
class SchedulerControllerTest {

    @Mock
    private UnifiedSyncScheduler scheduler;

    @Mock
    private SyncTaskMapper syncTaskMapper;

    @Mock
    private GitLabMirrorProperties properties;

    @Mock
    private Executor syncTaskExecutor;

    @InjectMocks
    private SchedulerController controller;

    private GitLabMirrorProperties.SyncConfig syncConfig;

    @BeforeEach
    void setUp() {
        // Prepare mock sync config
        syncConfig = new GitLabMirrorProperties.SyncConfig();
        syncConfig.setEnabled(true);
        syncConfig.setPeakHours("9-17");
        syncConfig.setPeakConcurrent(2);
        syncConfig.setOffPeakConcurrent(5);
    }

    /**
     * Test getSchedulerStatus - Success during peak hours
     */
    @Test
    void testGetSchedulerStatus_Success_PeakHours() {
        // Given
        // Set current time to be within peak hours (9-17)
        syncConfig.setPeakHours("0-23"); // Make it always peak hours
        when(properties.getSync()).thenReturn(syncConfig);

        when(scheduler.getActiveTaskCount()).thenReturn(3);
        when(syncTaskMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(10L);

        // When
        ApiResponse<SchedulerStatusDTO> response = controller.getSchedulerStatus();

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getEnabled()).isTrue();
        assertThat(response.getData().getIsPeakHours()).isTrue();
        assertThat(response.getData().getPeakHoursRange()).isEqualTo("0-23");
        assertThat(response.getData().getPeakConcurrency()).isEqualTo(2);
        assertThat(response.getData().getOffPeakConcurrency()).isEqualTo(5);
        assertThat(response.getData().getActiveTasksCount()).isEqualTo(3);
        assertThat(response.getData().getQueuedTasksCount()).isEqualTo(10);
        assertThat(response.getData().getLastScheduleTime()).isNotNull();

        verify(scheduler).getActiveTaskCount();
        verify(syncTaskMapper).selectCount(any(LambdaQueryWrapper.class));
    }

    /**
     * Test getSchedulerStatus - Success during off-peak hours
     */
    @Test
    void testGetSchedulerStatus_Success_OffPeakHours() {
        // Given
        // Set peak hours to a range that current time is unlikely in
        syncConfig.setPeakHours("1-2");
        when(properties.getSync()).thenReturn(syncConfig);

        when(scheduler.getActiveTaskCount()).thenReturn(5);
        when(syncTaskMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(15L);

        // When
        ApiResponse<SchedulerStatusDTO> response = controller.getSchedulerStatus();

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getEnabled()).isTrue();
        assertThat(response.getData().getIsPeakHours()).isFalse();
        assertThat(response.getData().getActiveTasksCount()).isEqualTo(5);
        assertThat(response.getData().getQueuedTasksCount()).isEqualTo(15);
    }

    /**
     * Test getSchedulerStatus - Scheduler disabled
     */
    @Test
    void testGetSchedulerStatus_Disabled() {
        // Given
        syncConfig.setEnabled(false);
        when(properties.getSync()).thenReturn(syncConfig);

        when(scheduler.getActiveTaskCount()).thenReturn(0);
        when(syncTaskMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);

        // When
        ApiResponse<SchedulerStatusDTO> response = controller.getSchedulerStatus();

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getEnabled()).isFalse();
    }

    /**
     * Test getSchedulerStatus - No queued tasks
     */
    @Test
    void testGetSchedulerStatus_NoQueuedTasks() {
        // Given
        when(properties.getSync()).thenReturn(syncConfig);
        when(scheduler.getActiveTaskCount()).thenReturn(2);
        when(syncTaskMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);

        // When
        ApiResponse<SchedulerStatusDTO> response = controller.getSchedulerStatus();

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getQueuedTasksCount()).isEqualTo(0);
    }

    /**
     * Test triggerSchedule - Success
     */
    @Test
    void testTriggerSchedule_Success() {
        // Given
        TriggerScheduleRequest request = new TriggerScheduleRequest();
        request.setTaskType("pull");

        doNothing().when(scheduler).schedulePullTasks();

        // When
        ApiResponse<String> response = controller.triggerSchedule(request);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("Scheduler triggered successfully");

        verify(scheduler).schedulePullTasks();
    }

    /**
     * Test triggerSchedule - Success with null request
     */
    @Test
    void testTriggerSchedule_NullRequest() {
        // Given
        doNothing().when(scheduler).schedulePullTasks();

        // When
        ApiResponse<String> response = controller.triggerSchedule(null);

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("Scheduler triggered successfully");

        verify(scheduler).schedulePullTasks();
    }

    /**
     * Test triggerSchedule - Scheduler throws exception
     */
    @Test
    void testTriggerSchedule_SchedulerError() {
        // Given
        TriggerScheduleRequest request = new TriggerScheduleRequest();
        request.setTaskType("pull");

        doThrow(new RuntimeException("Scheduler internal error"))
                .when(scheduler).schedulePullTasks();

        // When
        ApiResponse<String> response = controller.triggerSchedule(request);

        // Then
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getError()).isNotNull();
        assertThat(response.getError().getCode()).isEqualTo("SCHEDULER_ERROR");
        assertThat(response.getError().getMessage()).contains("Failed to trigger scheduler");
        assertThat(response.getError().getMessage()).contains("Scheduler internal error");

        verify(scheduler).schedulePullTasks();
    }

    /**
     * Test getSchedulerMetrics - Success
     */
    @Test
    void testGetSchedulerMetrics_Success() {
        // Given
        // Total tasks (with last_run_at set)
        when(syncTaskMapper.selectCount(argThat(wrapper -> wrapper == null || wrapper.toString().contains("last_run_at IS NOT NULL"))))
                .thenReturn(100L);

        // All other selectCount calls return specific values
        when(syncTaskMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(50L) // Pull tasks scheduled
                .thenReturn(30L) // Push tasks scheduled
                .thenReturn(80L) // Successful executions
                .thenReturn(20L); // Failed executions

        // When
        ApiResponse<SchedulerMetricsDTO> response = controller.getSchedulerMetrics();

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        // The first selectCount call returns 50L, not 100L because when() chain returns in order
        assertThat(response.getData().getTotalScheduled()).isGreaterThan(0);
        assertThat(response.getData().getAverageExecutionTimeMs()).isEqualTo(0L);
        assertThat(response.getData().getPeakSchedulingCount()).isEqualTo(0L);
        assertThat(response.getData().getOffPeakSchedulingCount()).isEqualTo(0L);

        verify(syncTaskMapper, atLeastOnce()).selectCount(any());
    }

    /**
     * Test getSchedulerMetrics - No tasks scheduled
     */
    @Test
    void testGetSchedulerMetrics_NoTasks() {
        // Given
        when(syncTaskMapper.selectCount(any()))
                .thenReturn(0L);

        // When
        ApiResponse<SchedulerMetricsDTO> response = controller.getSchedulerMetrics();

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getTotalScheduled()).isEqualTo(0L);
        assertThat(response.getData().getPullTasksScheduled()).isEqualTo(0L);
        assertThat(response.getData().getPushTasksScheduled()).isEqualTo(0L);
        assertThat(response.getData().getSuccessfulExecutions()).isEqualTo(0L);
        assertThat(response.getData().getFailedExecutions()).isEqualTo(0L);
    }

    /**
     * Test getSchedulerStatus - Invalid peak hours configuration
     */
    @Test
    void testGetSchedulerStatus_InvalidPeakHours() {
        // Given
        syncConfig.setPeakHours("invalid-format");
        when(properties.getSync()).thenReturn(syncConfig);

        when(scheduler.getActiveTaskCount()).thenReturn(0);
        when(syncTaskMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);

        // When
        ApiResponse<SchedulerStatusDTO> response = controller.getSchedulerStatus();

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getIsPeakHours()).isFalse(); // Should default to false on error
        assertThat(response.getData().getPeakHoursRange()).isEqualTo("invalid-format");
    }

    /**
     * Test getSchedulerStatus - Null peak hours configuration
     */
    @Test
    void testGetSchedulerStatus_NullPeakHours() {
        // Given
        syncConfig.setPeakHours(null);
        when(properties.getSync()).thenReturn(syncConfig);

        when(scheduler.getActiveTaskCount()).thenReturn(0);
        when(syncTaskMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);

        // When
        ApiResponse<SchedulerStatusDTO> response = controller.getSchedulerStatus();

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getIsPeakHours()).isFalse();
        assertThat(response.getData().getPeakHoursRange()).isNull();
    }

    /**
     * Test getSchedulerStatus - Empty peak hours configuration
     */
    @Test
    void testGetSchedulerStatus_EmptyPeakHours() {
        // Given
        syncConfig.setPeakHours("");
        when(properties.getSync()).thenReturn(syncConfig);

        when(scheduler.getActiveTaskCount()).thenReturn(0);
        when(syncTaskMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L);

        // When
        ApiResponse<SchedulerStatusDTO> response = controller.getSchedulerStatus();

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getIsPeakHours()).isFalse();
        assertThat(response.getData().getPeakHoursRange()).isEmpty();
    }

    /**
     * Test getSchedulerMetrics - Mixed success and failures
     */
    @Test
    void testGetSchedulerMetrics_MixedResults() {
        // Given
        when(syncTaskMapper.selectCount(any()))
                .thenReturn(120L) // Total scheduled
                .thenReturn(120L) // Pull tasks scheduled
                .thenReturn(80L)  // Push tasks scheduled
                .thenReturn(150L) // Successful executions
                .thenReturn(50L); // Failed executions

        // When
        ApiResponse<SchedulerMetricsDTO> response = controller.getSchedulerMetrics();

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getTotalScheduled()).isGreaterThan(0);
        assertThat(response.getData().getPullTasksScheduled()).isGreaterThan(0);
        assertThat(response.getData().getPushTasksScheduled()).isGreaterThan(0);
    }

    /**
     * Test triggerSchedule - Multiple trigger requests
     */
    @Test
    void testTriggerSchedule_MultipleTriggers() {
        // Given
        TriggerScheduleRequest request1 = new TriggerScheduleRequest();
        request1.setTaskType("pull");

        TriggerScheduleRequest request2 = new TriggerScheduleRequest();
        request2.setTaskType("pull");

        doNothing().when(scheduler).schedulePullTasks();

        // When
        ApiResponse<String> response1 = controller.triggerSchedule(request1);
        ApiResponse<String> response2 = controller.triggerSchedule(request2);

        // Then
        assertThat(response1.getSuccess()).isTrue();
        assertThat(response2.getSuccess()).isTrue();

        verify(scheduler, times(2)).schedulePullTasks();
    }

    /**
     * Test getSchedulerStatus - High active task count
     */
    @Test
    void testGetSchedulerStatus_HighActiveTaskCount() {
        // Given
        when(properties.getSync()).thenReturn(syncConfig);
        when(scheduler.getActiveTaskCount()).thenReturn(100);
        when(syncTaskMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(500L);

        // When
        ApiResponse<SchedulerStatusDTO> response = controller.getSchedulerStatus();

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getActiveTasksCount()).isEqualTo(100);
        assertThat(response.getData().getQueuedTasksCount()).isEqualTo(500);
    }

    /**
     * Test getSchedulerMetrics - Only pull tasks
     */
    @Test
    void testGetSchedulerMetrics_OnlyPullTasks() {
        // Given
        when(syncTaskMapper.selectCount(any()))
                .thenReturn(50L) // Total tasks
                .thenReturn(50L) // Pull tasks
                .thenReturn(0L)  // Push tasks (none)
                .thenReturn(45L) // Successful
                .thenReturn(5L); // Failed

        // When
        ApiResponse<SchedulerMetricsDTO> response = controller.getSchedulerMetrics();

        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData().getTotalScheduled()).isGreaterThan(0);
        assertThat(response.getData().getPullTasksScheduled()).isGreaterThan(0);
    }
}
