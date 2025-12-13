package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gitlab.mirror.server.entity.PullSyncConfig;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.SyncTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sync Task Mapper Test
 * <p>
 * MUST strictly perform unit testing for all CRUD operations and queries
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SyncTaskMapperTest {

    @Autowired
    private SyncTaskMapper syncTaskMapper;

    @Autowired
    private SyncProjectMapper syncProjectMapper;

    @Autowired
    private PullSyncConfigMapper pullSyncConfigMapper;

    @Test
    void testInsert() {
        // Create sync project first
        SyncProject syncProject = createSyncProject("group1/test-task-1");

        // Create sync task
        SyncTask task = new SyncTask();
        task.setSyncProjectId(syncProject.getId());
        task.setTaskType(SyncTask.TaskType.PULL);
        task.setTaskStatus(SyncTask.TaskStatus.WAITING);
        task.setNextRunAt(Instant.now());
        task.setConsecutiveFailures(0);

        // Insert
        int result = syncTaskMapper.insert(task);

        // Verify
        assertThat(result).isEqualTo(1);
        assertThat(task.getId()).isNotNull();
        assertThat(task.getCreatedAt()).isNotNull();
        assertThat(task.getUpdatedAt()).isNotNull();
    }

    @Test
    void testSelectById() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-task-2");
        SyncTask task = createSyncTask(syncProject.getId(), SyncTask.TaskType.PULL);

        // Query by ID
        SyncTask found = syncTaskMapper.selectById(task.getId());

        // Verify
        assertThat(found).isNotNull();
        assertThat(found.getTaskType()).isEqualTo(SyncTask.TaskType.PULL);
        assertThat(found.getTaskStatus()).isEqualTo(SyncTask.TaskStatus.WAITING);
    }

    @Test
    void testSelectBySyncProjectId() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-task-3");
        SyncTask task = createSyncTask(syncProject.getId(), SyncTask.TaskType.PULL);

        // Query by sync_project_id
        LambdaQueryWrapper<SyncTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncTask::getSyncProjectId, syncProject.getId());
        SyncTask found = syncTaskMapper.selectOne(queryWrapper);

        // Verify
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(task.getId());
    }

    @Test
    void testUpdate() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-task-4");
        SyncTask task = createSyncTask(syncProject.getId(), SyncTask.TaskType.PULL);

        // Update to running status
        task.setTaskStatus(SyncTask.TaskStatus.RUNNING);
        task.setStartedAt(Instant.now());
        int result = syncTaskMapper.updateById(task);

        // Verify
        assertThat(result).isEqualTo(1);
        SyncTask updated = syncTaskMapper.selectById(task.getId());
        assertThat(updated.getTaskStatus()).isEqualTo(SyncTask.TaskStatus.RUNNING);
        assertThat(updated.getStartedAt()).isNotNull();
    }

    @Test
    void testUpdateSyncResult() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-task-5");
        SyncTask task = createSyncTask(syncProject.getId(), SyncTask.TaskType.PULL);

        // Update with sync result
        task.setTaskStatus(SyncTask.TaskStatus.WAITING);
        task.setLastSyncStatus(SyncTask.SyncStatus.SUCCESS);
        task.setLastRunAt(Instant.now());
        task.setCompletedAt(Instant.now());
        task.setDurationSeconds(120);
        task.setHasChanges(true);
        task.setChangesCount(5);
        task.setSourceCommitSha("abc123");
        task.setTargetCommitSha("def456");
        task.setConsecutiveFailures(0);
        task.setNextRunAt(Instant.now().plusSeconds(600));

        int result = syncTaskMapper.updateById(task);

        // Verify
        assertThat(result).isEqualTo(1);
        SyncTask updated = syncTaskMapper.selectById(task.getId());
        assertThat(updated.getLastSyncStatus()).isEqualTo(SyncTask.SyncStatus.SUCCESS);
        assertThat(updated.getHasChanges()).isTrue();
        assertThat(updated.getChangesCount()).isEqualTo(5);
        assertThat(updated.getSourceCommitSha()).isEqualTo("abc123");
        assertThat(updated.getTargetCommitSha()).isEqualTo("def456");
    }

    @Test
    void testUpdateFailure() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-task-6");
        SyncTask task = createSyncTask(syncProject.getId(), SyncTask.TaskType.PULL);

        // Update with failure
        task.setTaskStatus(SyncTask.TaskStatus.WAITING);
        task.setLastSyncStatus(SyncTask.SyncStatus.FAILED);
        task.setErrorType("NETWORK_TIMEOUT");
        task.setErrorMessage("Connection timeout after 30s");
        task.setConsecutiveFailures(1);
        task.setNextRunAt(Instant.now().plusSeconds(300)); // 5 min retry

        int result = syncTaskMapper.updateById(task);

        // Verify
        assertThat(result).isEqualTo(1);
        SyncTask updated = syncTaskMapper.selectById(task.getId());
        assertThat(updated.getLastSyncStatus()).isEqualTo(SyncTask.SyncStatus.FAILED);
        assertThat(updated.getErrorType()).isEqualTo("NETWORK_TIMEOUT");
        assertThat(updated.getConsecutiveFailures()).isEqualTo(1);
    }

    @Test
    void testDelete() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-task-7");
        SyncTask task = createSyncTask(syncProject.getId(), SyncTask.TaskType.PULL);

        // Delete
        int result = syncTaskMapper.deleteById(task.getId());

        // Verify
        assertThat(result).isEqualTo(1);
        SyncTask deleted = syncTaskMapper.selectById(task.getId());
        assertThat(deleted).isNull();
    }

    @Test
    void testUniqueSyncProjectId() {
        // Create sync project
        SyncProject syncProject = createSyncProject("group1/test-task-unique");

        // Create first task
        SyncTask task1 = new SyncTask();
        task1.setSyncProjectId(syncProject.getId());
        task1.setTaskType(SyncTask.TaskType.PULL);
        task1.setTaskStatus(SyncTask.TaskStatus.WAITING);
        task1.setNextRunAt(Instant.now());
        syncTaskMapper.insert(task1);

        // Try to create second task with same sync_project_id (should fail)
        SyncTask task2 = new SyncTask();
        task2.setSyncProjectId(syncProject.getId());
        task2.setTaskType(SyncTask.TaskType.PUSH);
        task2.setTaskStatus(SyncTask.TaskStatus.WAITING);
        task2.setNextRunAt(Instant.now());

        try {
            syncTaskMapper.insert(task2);
            // If we reach here, the unique constraint is not working
            assertThat(false).as("Should throw exception for duplicate sync_project_id").isTrue();
        } catch (Exception e) {
            // Expected - unique constraint violation
            assertThat(e.getMessage()).containsAnyOf("Duplicate", "duplicate", "unique", "UNIQUE");
        }
    }

    @Test
    void testCascadeDelete() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-task-cascade");
        SyncTask task = createSyncTask(syncProject.getId(), SyncTask.TaskType.PULL);

        // Delete sync project (should cascade delete task)
        syncProjectMapper.deleteById(syncProject.getId());

        // Verify task is also deleted
        SyncTask found = syncTaskMapper.selectById(task.getId());
        assertThat(found).isNull();
    }

    @Test
    void testSelectTasksForScheduling() {
        // Create multiple tasks
        SyncProject project1 = createSyncProject("group1/task-sched-1");
        SyncProject project2 = createSyncProject("group1/task-sched-2");
        SyncProject project3 = createSyncProject("group1/task-sched-3");

        // Task 1: ready for scheduling (next_run_at in the past)
        SyncTask task1 = createSyncTask(project1.getId(), SyncTask.TaskType.PULL);
        task1.setNextRunAt(Instant.now().minusSeconds(600));
        syncTaskMapper.updateById(task1);

        // Task 2: ready for scheduling (next_run_at = now)
        SyncTask task2 = createSyncTask(project2.getId(), SyncTask.TaskType.PULL);
        task2.setNextRunAt(Instant.now());
        syncTaskMapper.updateById(task2);

        // Task 3: not ready (next_run_at in the future)
        SyncTask task3 = createSyncTask(project3.getId(), SyncTask.TaskType.PULL);
        task3.setNextRunAt(Instant.now().plusSeconds(600));
        syncTaskMapper.updateById(task3);

        // Query tasks for scheduling
        List<SyncTask> tasks = syncTaskMapper.selectTasksForScheduling(
                SyncTask.TaskType.PULL,
                SyncTask.TaskStatus.WAITING,
                Instant.now(),
                5,
                10
        );

        // Verify - should return task1 and task2, not task3
        assertThat(tasks).hasSize(2);
        assertThat(tasks).extracting(SyncTask::getId)
                .containsExactlyInAnyOrder(task1.getId(), task2.getId());
    }

    @Test
    void testSelectTasksForSchedulingWithFailureFilter() {
        // Create tasks with different failure counts
        SyncProject project1 = createSyncProject("group1/task-fail-1");
        SyncProject project2 = createSyncProject("group1/task-fail-2");

        // Task 1: few failures (should be included)
        SyncTask task1 = createSyncTask(project1.getId(), SyncTask.TaskType.PULL);
        task1.setConsecutiveFailures(2);
        task1.setNextRunAt(Instant.now());
        syncTaskMapper.updateById(task1);

        // Task 2: too many failures (should be excluded)
        SyncTask task2 = createSyncTask(project2.getId(), SyncTask.TaskType.PULL);
        task2.setConsecutiveFailures(5);
        task2.setNextRunAt(Instant.now());
        syncTaskMapper.updateById(task2);

        // Query with maxFailures = 5
        List<SyncTask> tasks = syncTaskMapper.selectTasksForScheduling(
                SyncTask.TaskType.PULL,
                SyncTask.TaskStatus.WAITING,
                Instant.now(),
                5,
                10
        );

        // Verify - should only return task1
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getId()).isEqualTo(task1.getId());
    }

    @Test
    void testSelectPullTasksWithPriority() {
        // Create tasks with different priorities
        SyncProject project1 = createSyncProject("group1/priority-critical");
        SyncProject project2 = createSyncProject("group1/priority-normal");
        SyncProject project3 = createSyncProject("group1/priority-low");

        SyncTask task1 = createSyncTask(project1.getId(), SyncTask.TaskType.PULL);
        task1.setNextRunAt(Instant.now());
        syncTaskMapper.updateById(task1);

        SyncTask task2 = createSyncTask(project2.getId(), SyncTask.TaskType.PULL);
        task2.setNextRunAt(Instant.now());
        syncTaskMapper.updateById(task2);

        SyncTask task3 = createSyncTask(project3.getId(), SyncTask.TaskType.PULL);
        task3.setNextRunAt(Instant.now());
        syncTaskMapper.updateById(task3);

        // Create configs with different priorities
        createPullSyncConfig(project1.getId(), PullSyncConfig.Priority.CRITICAL);
        createPullSyncConfig(project2.getId(), PullSyncConfig.Priority.NORMAL);
        createPullSyncConfig(project3.getId(), PullSyncConfig.Priority.LOW);

        // Query with priority ordering
        List<SyncTask> tasks = syncTaskMapper.selectPullTasksWithPriority(
                Instant.now(),
                5,
                10
        );

        // Verify - should be ordered: critical, normal, low
        assertThat(tasks).hasSize(3);
        assertThat(tasks.get(0).getId()).isEqualTo(task1.getId()); // critical
        assertThat(tasks.get(1).getId()).isEqualTo(task2.getId()); // normal
        assertThat(tasks.get(2).getId()).isEqualTo(task3.getId()); // low
    }

    @Test
    void testSelectPullTasksWithPriorityExcludesDisabled() {
        // Create task with disabled config
        SyncProject project = createSyncProject("group1/disabled-task");
        SyncTask task = createSyncTask(project.getId(), SyncTask.TaskType.PULL);
        task.setNextRunAt(Instant.now());
        syncTaskMapper.updateById(task);

        PullSyncConfig config = createPullSyncConfig(project.getId(), PullSyncConfig.Priority.HIGH);
        config.setEnabled(false);
        pullSyncConfigMapper.updateById(config);

        // Query
        List<SyncTask> tasks = syncTaskMapper.selectPullTasksWithPriority(
                Instant.now(),
                5,
                10
        );

        // Verify - should not include disabled task
        assertThat(tasks).isEmpty();
    }

    private SyncProject createSyncProject(String projectKey) {
        SyncProject project = new SyncProject();
        project.setProjectKey(projectKey);
        project.setSyncMethod("pull_sync");
        project.setSyncStatus(SyncProject.SyncStatus.PENDING);
        project.setEnabled(true);
        syncProjectMapper.insert(project);
        return project;
    }

    private SyncTask createSyncTask(Long syncProjectId, String taskType) {
        SyncTask task = new SyncTask();
        task.setSyncProjectId(syncProjectId);
        task.setTaskType(taskType);
        task.setTaskStatus(SyncTask.TaskStatus.WAITING);
        task.setNextRunAt(Instant.now().plusSeconds(3600));
        task.setConsecutiveFailures(0);
        syncTaskMapper.insert(task);
        return task;
    }

    private PullSyncConfig createPullSyncConfig(Long syncProjectId, String priority) {
        PullSyncConfig config = new PullSyncConfig();
        config.setSyncProjectId(syncProjectId);
        config.setPriority(priority);
        config.setEnabled(true);
        config.setLocalRepoPath("/data/repos/test");
        pullSyncConfigMapper.insert(config);
        return config;
    }
}
