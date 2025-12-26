package com.gitlab.mirror.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.mapper.SyncTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Sync Task Service
 * <p>
 * Manages unified sync tasks for both Push and Pull methods
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncTaskService {

    private final SyncTaskMapper syncTaskMapper;

    /**
     * Initialize task for a project
     *
     * @param syncProjectId Sync project ID
     * @param taskType      Task type (push/pull)
     * @return Created task
     */
    @Transactional
    public SyncTask initializeTask(Long syncProjectId, String taskType) {
        log.info("Initializing sync task for syncProjectId={}, taskType={}", syncProjectId, taskType);

        // Check if task already exists
        LambdaQueryWrapper<SyncTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncTask::getSyncProjectId, syncProjectId);
        SyncTask existing = syncTaskMapper.selectOne(queryWrapper);

        if (existing != null) {
            log.debug("Sync task already exists for syncProjectId={}, id={}", syncProjectId, existing.getId());
            return existing;
        }

        // Create new task
        SyncTask task = new SyncTask();
        task.setSyncProjectId(syncProjectId);
        task.setTaskType(taskType);
        task.setTaskStatus(SyncTask.TaskStatus.WAITING);
        task.setNextRunAt(Instant.now()); // Schedule for immediate execution
        task.setConsecutiveFailures(0);

        syncTaskMapper.insert(task);

        log.info("Created sync task for syncProjectId={}, id={}, taskType={}, nextRunAt={}",
                syncProjectId, task.getId(), taskType, task.getNextRunAt());

        return task;
    }

    /**
     * Get task by sync project ID
     *
     * @param syncProjectId Sync project ID
     * @return Task or null if not found
     */
    public SyncTask getTaskBySyncProjectId(Long syncProjectId) {
        LambdaQueryWrapper<SyncTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SyncTask::getSyncProjectId, syncProjectId);
        return syncTaskMapper.selectOne(queryWrapper);
    }

    /**
     * Update task status
     *
     * @param taskId    Task ID
     * @param newStatus New status
     */
    @Transactional
    public void updateTaskStatus(Long taskId, String newStatus) {
        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }

        task.setTaskStatus(newStatus);
        syncTaskMapper.updateById(task);

        log.debug("Updated task status: id={}, status={}", taskId, newStatus);
    }

    /**
     * Update next run time
     *
     * @param taskId     Task ID
     * @param nextRunAt  Next run time
     */
    @Transactional
    public void updateNextRunAt(Long taskId, Instant nextRunAt) {
        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }

        task.setNextRunAt(nextRunAt);
        syncTaskMapper.updateById(task);

        log.debug("Updated next run time: id={}, nextRunAt={}", taskId, nextRunAt);
    }

    /**
     * Update sync task
     *
     * @param task Task to update
     */
    @Transactional
    public void updateTask(SyncTask task) {
        if (task == null || task.getId() == null) {
            throw new IllegalArgumentException("Task or task ID cannot be null");
        }

        syncTaskMapper.updateById(task);
        log.debug("Updated task: id={}, status={}, nextRunAt={}",
                 task.getId(), task.getTaskStatus(), task.getNextRunAt());
    }
}
