package com.gitlab.mirror.server.service;

import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.mapper.SyncTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Task Status Update Service
 * <p>
 * Handles status updates in separate transactions to ensure they persist
 * even when the main sync transaction rolls back
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskStatusUpdateService {

    private final SyncTaskMapper syncTaskMapper;

    /**
     * Update task status in new transaction
     *
     * @param task      Task
     * @param status    New status
     * @param startedAt Started timestamp (optional)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(SyncTask task, String status, Instant startedAt) {
        task.setTaskStatus(status);
        if (startedAt != null) {
            task.setStartedAt(startedAt);
        }
        task.setUpdatedAt(LocalDateTime.now());
        syncTaskMapper.updateById(task);
        log.debug("Updated task status in new transaction: taskId={}, status={}", task.getId(), status);
    }

    /**
     * Update task to waiting status with next run time
     *
     * @param task      Task
     * @param nextRunAt Next run time (optional)
     * @param reason    Reason for status change
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateToWaiting(SyncTask task, Instant nextRunAt, String reason) {
        task.setTaskStatus("waiting");
        if (nextRunAt != null) {
            task.setNextRunAt(nextRunAt);
        }
        task.setUpdatedAt(LocalDateTime.now());
        if (reason != null) {
            task.setErrorMessage(reason);
        }
        syncTaskMapper.updateById(task);
        log.debug("Updated task to waiting in new transaction: taskId={}, reason={}", task.getId(), reason);
    }

    /**
     * Update task after failure in new transaction
     * This ensures failure information persists even if main transaction rolls back
     *
     * @param task Sync task with updated failure info
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateAfterFailure(SyncTask task) {
        task.setUpdatedAt(LocalDateTime.now());
        syncTaskMapper.updateById(task);
        log.debug("Updated task after failure in new transaction: taskId={}", task.getId());
    }
}
