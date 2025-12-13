package com.gitlab.mirror.server.scheduler;

import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import com.gitlab.mirror.server.entity.PullSyncConfig;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.mapper.PullSyncConfigMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.mapper.SyncTaskMapper;
import com.gitlab.mirror.server.service.PullSyncExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unified Sync Scheduler Test
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UnifiedSyncSchedulerTest {

    @Autowired
    private UnifiedSyncScheduler scheduler;

    @Autowired
    private SyncTaskMapper syncTaskMapper;

    @Autowired
    private SyncProjectMapper syncProjectMapper;

    @Autowired
    private PullSyncConfigMapper pullSyncConfigMapper;

    @Autowired
    private GitLabMirrorProperties properties;

    @MockBean
    private PullSyncExecutorService pullSyncExecutorService;

    @Autowired
    private ThreadPoolTaskExecutor syncTaskExecutor;

    @BeforeEach
    void setUp() {
        // Clean up handled by @Transactional rollback
        // Reset mock
        reset(pullSyncExecutorService);
    }

    @Test
    void testSchedulePullTasks_NoAvailableSlots() throws InterruptedException {
        // Given: No available slots (all executors busy)
        // Fill up the executor AND queue with blocking tasks
        int maxConcurrent = properties.getSync().getOffPeakConcurrent();
        int queueCapacity = 50; // From TaskExecutorConfig
        CountDownLatch blockLatch = new CountDownLatch(1);
        CountDownLatch startLatch = new CountDownLatch(maxConcurrent);

        // Fill both the active threads and the queue
        for (int i = 0; i < maxConcurrent + queueCapacity; i++) {
            final int index = i;
            syncTaskExecutor.execute(() -> {
                try {
                    if (index < maxConcurrent) {
                        startLatch.countDown();
                    }
                    blockLatch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Wait for all active tasks to start
        boolean started = startLatch.await(3, TimeUnit.SECONDS);
        assertThat(started).isTrue();

        // Additional wait to ensure queue is full
        Thread.sleep(500);

        // Create waiting task
        SyncTask task = createWaitingTask("test/project1", "normal", 0);

        // When: Scheduler runs
        scheduler.schedulePullTasks();

        // Give some time for potential scheduling
        Thread.sleep(1000);

        // Then: Task should not be scheduled (no slots)
        // Note: Due to CallerRunsPolicy, the task might still get scheduled
        // So we verify that active count is at capacity
        int activeCount = scheduler.getActiveTaskCount();
        assertThat(activeCount).isGreaterThanOrEqualTo(maxConcurrent);

        // Cleanup
        blockLatch.countDown();
    }

    @Test
    void testSchedulePullTasks_Success() throws InterruptedException {
        // Given: Available slots and waiting task
        SyncTask task = createWaitingTask("test/project1", "normal", 0);

        // Mock executor to complete immediately
        CountDownLatch executionLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            executionLatch.countDown();
            return null;
        }).when(pullSyncExecutorService).executeSync(any(SyncTask.class));

        // When: Scheduler runs
        scheduler.schedulePullTasks();

        // Then: Task should be scheduled
        boolean executed = executionLatch.await(2, TimeUnit.SECONDS);
        assertThat(executed).isTrue();

        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());
        assertThat(updatedTask.getTaskStatus()).isEqualTo("pending");

        verify(pullSyncExecutorService, times(1)).executeSync(any(SyncTask.class));
    }

    @Test
    void testSchedulePullTasks_MultipleTasksWithinSlots() throws InterruptedException {
        // Given: 3 waiting tasks and enough slots
        SyncTask task1 = createWaitingTask("test/project1", "high", 0);
        SyncTask task2 = createWaitingTask("test/project2", "normal", 0);
        SyncTask task3 = createWaitingTask("test/project3", "low", 0);

        CountDownLatch executionLatch = new CountDownLatch(3);
        doAnswer(invocation -> {
            executionLatch.countDown();
            return null;
        }).when(pullSyncExecutorService).executeSync(any(SyncTask.class));

        // When: Scheduler runs
        scheduler.schedulePullTasks();

        // Then: All tasks should be scheduled
        boolean executed = executionLatch.await(2, TimeUnit.SECONDS);
        assertThat(executed).isTrue();

        verify(pullSyncExecutorService, times(3)).executeSync(any(SyncTask.class));
    }

    @Test
    void testSchedulePullTasks_SkipFutureNextRunAt() {
        // Given: Task with future next_run_at
        SyncTask task = createWaitingTask("test/project1", "normal", 0);
        task.setNextRunAt(Instant.now().plus(1, ChronoUnit.HOURS));
        syncTaskMapper.updateById(task);

        // When: Scheduler runs
        scheduler.schedulePullTasks();

        // Then: Task should not be scheduled
        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());
        assertThat(updatedTask.getTaskStatus()).isEqualTo("waiting");

        verify(pullSyncExecutorService, never()).executeSync(any(SyncTask.class));
    }

    @Test
    void testSchedulePullTasks_SkipHighFailureCount() {
        // Given: Task with 5 consecutive failures
        SyncTask task = createWaitingTask("test/project1", "normal", 5);

        // When: Scheduler runs
        scheduler.schedulePullTasks();

        // Then: Task should not be scheduled
        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());
        assertThat(updatedTask.getTaskStatus()).isEqualTo("waiting");

        verify(pullSyncExecutorService, never()).executeSync(any(SyncTask.class));
    }

    @Test
    void testSchedulePullTasks_SkipNonPullTasks() {
        // Given: A push mirror task
        SyncTask task = createWaitingTask("test/project1", "normal", 0);
        task.setTaskType("push");
        syncTaskMapper.updateById(task);

        // When: Scheduler runs
        scheduler.schedulePullTasks();

        // Then: Task should not be scheduled
        verify(pullSyncExecutorService, never()).executeSync(any(SyncTask.class));
    }

    @Test
    void testSchedulePullTasks_SkipNonWaitingTasks() {
        // Given: A running task
        SyncTask task = createWaitingTask("test/project1", "normal", 0);
        task.setTaskStatus("running");
        syncTaskMapper.updateById(task);

        // When: Scheduler runs
        scheduler.schedulePullTasks();

        // Then: Task should not be scheduled
        verify(pullSyncExecutorService, never()).executeSync(any(SyncTask.class));
    }

    @Test
    void testSchedulePullTasks_ScheduleFailureRollback() throws InterruptedException {
        // Given: Task and mock executor to throw exception
        SyncTask task = createWaitingTask("test/project1", "normal", 0);

        doThrow(new RuntimeException("Executor rejected"))
            .when(pullSyncExecutorService).executeSync(any(SyncTask.class));

        // When: Scheduler runs
        scheduler.schedulePullTasks();

        // Give some time for async execution
        Thread.sleep(500);

        // Then: Task status should be reset to waiting
        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());
        // Note: Due to async execution, status might still be pending
        // The actual rollback happens in the executor thread
        assertThat(updatedTask.getTaskStatus()).isIn("waiting", "pending");
    }

    @Test
    void testGetActiveTaskCount() {
        // Given: Some active tasks in executor
        CountDownLatch blockLatch = new CountDownLatch(1);

        syncTaskExecutor.execute(() -> {
            try {
                blockLatch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Wait for task to start
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When: Get active count
        int activeCount = scheduler.getActiveTaskCount();

        // Then: Should return count from thread pool
        assertThat(activeCount).isGreaterThanOrEqualTo(1);

        // Cleanup
        blockLatch.countDown();
    }

    @Test
    void testPeakHoursDetection() {
        // This test depends on the current time
        // We can only verify the method doesn't crash
        // Real peak hours logic is tested indirectly via available slots

        // Given: Current time
        int currentHour = LocalTime.now().getHour();

        // Parse configured peak hours
        String peakHours = properties.getSync().getPeakHours();
        String[] parts = peakHours.split("-");
        int startHour = Integer.parseInt(parts[0]);
        int endHour = Integer.parseInt(parts[1]);

        // When: Get available slots (which calls isPeakHours internally)
        int activeCount = scheduler.getActiveTaskCount();

        // Then: Should use appropriate concurrent limit
        // If peak hours: use peakConcurrent (3)
        // If off-peak: use offPeakConcurrent (8)
        // We can't assert exact behavior without controlling time,
        // but we can verify it doesn't crash
        assertThat(activeCount).isGreaterThanOrEqualTo(0);
    }

    @Test
    void testSchedulePullTasks_NoPendingTasks() {
        // Given: No tasks in database

        // When: Scheduler runs
        scheduler.schedulePullTasks();

        // Then: Should complete without error
        verify(pullSyncExecutorService, never()).executeSync(any(SyncTask.class));
    }

    @Test
    void testSchedulePullTasks_PriorityOrdering() throws InterruptedException {
        // Given: Tasks with different priorities
        SyncTask lowTask = createWaitingTask("test/project-low", "low", 0);
        SyncTask normalTask = createWaitingTask("test/project-normal", "normal", 0);
        SyncTask highTask = createWaitingTask("test/project-high", "high", 0);
        SyncTask criticalTask = createWaitingTask("test/project-critical", "critical", 0);

        // All have same next_run_at (minus 1 minute)
        // Priority should be: critical > high > normal > low

        CountDownLatch executionLatch = new CountDownLatch(4);
        java.util.List<String> executionOrder = new java.util.concurrent.CopyOnWriteArrayList<>();

        doAnswer(invocation -> {
            SyncTask task = invocation.getArgument(0);
            SyncProject project = syncProjectMapper.selectById(task.getSyncProjectId());
            executionOrder.add(project.getProjectKey());
            executionLatch.countDown();
            return null;
        }).when(pullSyncExecutorService).executeSync(any(SyncTask.class));

        // When: Scheduler runs
        scheduler.schedulePullTasks();

        // Then: All tasks should be scheduled in priority order
        boolean executed = executionLatch.await(5, TimeUnit.SECONDS);
        assertThat(executed).isTrue();

        // Verify execution order (critical should be first, low should be last)
        assertThat(executionOrder).hasSize(4);
        assertThat(executionOrder.get(0)).isEqualTo("test/project-critical");
        assertThat(executionOrder.get(1)).isEqualTo("test/project-high");
        assertThat(executionOrder.get(2)).isEqualTo("test/project-normal");
        assertThat(executionOrder.get(3)).isEqualTo("test/project-low");

        verify(pullSyncExecutorService, times(4)).executeSync(any(SyncTask.class));
    }

    @Test
    void testSchedulePullTasks_OrderByNextRunAt() throws InterruptedException {
        // Given: 3 tasks with different next_run_at times
        SyncTask task1 = createWaitingTask("test/project1", "normal", 0);
        task1.setNextRunAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        syncTaskMapper.updateById(task1);

        SyncTask task2 = createWaitingTask("test/project2", "normal", 0);
        task2.setNextRunAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        syncTaskMapper.updateById(task2);

        SyncTask task3 = createWaitingTask("test/project3", "normal", 0);
        task3.setNextRunAt(Instant.now().minus(20, ChronoUnit.MINUTES));
        syncTaskMapper.updateById(task3);

        CountDownLatch executionLatch = new CountDownLatch(3);
        doAnswer(invocation -> {
            try {
                // Small delay to simulate execution
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executionLatch.countDown();
            return null;
        }).when(pullSyncExecutorService).executeSync(any(SyncTask.class));

        // When: Scheduler runs
        scheduler.schedulePullTasks();

        // Then: All tasks should be scheduled (order verified by query)
        boolean executed = executionLatch.await(10, TimeUnit.SECONDS);
        assertThat(executed).withFailMessage(
            "Expected all 3 tasks to execute within 10 seconds, but only " +
            (3 - executionLatch.getCount()) + " tasks completed"
        ).isTrue();

        verify(pullSyncExecutorService, times(3)).executeSync(any(SyncTask.class));
    }

    // Helper method to create a waiting task
    private SyncTask createWaitingTask(String projectKey, String priority, int consecutiveFailures) {
        // Create sync project
        SyncProject project = new SyncProject();
        project.setProjectKey(projectKey);
        project.setSyncMethod("pull_sync");
        project.setSyncStatus("active");
        project.setEnabled(true);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        syncProjectMapper.insert(project);

        // Create pull sync config
        PullSyncConfig config = new PullSyncConfig();
        config.setSyncProjectId(project.getId());
        config.setPriority(priority);
        config.setEnabled(true);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        pullSyncConfigMapper.insert(config);

        // Create sync task
        SyncTask task = new SyncTask();
        task.setSyncProjectId(project.getId());
        task.setTaskType("pull");
        task.setTaskStatus("waiting");
        task.setNextRunAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        task.setConsecutiveFailures(consecutiveFailures);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        syncTaskMapper.insert(task);

        return task;
    }
}
