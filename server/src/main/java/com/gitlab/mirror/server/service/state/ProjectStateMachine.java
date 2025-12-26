package com.gitlab.mirror.server.service.state;

import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.SyncTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Project State Machine
 * <p>
 * Manages state transitions for SyncProject based on sync results and errors
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class ProjectStateMachine {

    /**
     * Project Status Constants
     */
    public static class Status {
        public static final String DISCOVERED = "discovered";       // 新发现，等待初始化
        public static final String INITIALIZING = "initializing";   // 初始化中（创建目标项目）
        public static final String ACTIVE = "active";               // 正常运行
        public static final String ERROR = "error";                 // 错误，需人工介入
        public static final String SOURCE_MISSING = "source_missing"; // 源项目不存在
        public static final String DISABLED = "disabled";           // 用户禁用
        public static final String DELETED = "deleted";             // 已删除（逻辑删除）
    }

    /**
     * Transition to INITIALIZING
     * Called when target project creation starts
     */
    public void toInitializing(SyncProject project) {
        if (!Status.DISCOVERED.equals(project.getSyncStatus())) {
            log.warn("Invalid transition to INITIALIZING from {}", project.getSyncStatus());
            return;
        }

        project.setSyncStatus(Status.INITIALIZING);
        project.setErrorMessage(null);
        log.info("Project {} -> INITIALIZING", project.getProjectKey());
    }

    /**
     * Transition to ACTIVE
     * Called when first sync succeeds or project is re-enabled
     */
    public void toActive(SyncProject project, String fromState) {
        String validStates = String.join(",", Status.INITIALIZING, Status.ERROR,
                                         Status.SOURCE_MISSING, Status.DISABLED);

        if (!validStates.contains(project.getSyncStatus())) {
            log.warn("Invalid transition to ACTIVE from {}", project.getSyncStatus());
        }

        project.setSyncStatus(Status.ACTIVE);
        project.setErrorMessage(null);
        log.info("Project {} -> ACTIVE (from: {})", project.getProjectKey(), fromState);
    }

    /**
     * Handle sync success
     * ACTIVE remains ACTIVE on success
     */
    public void onSyncSuccess(SyncProject project) {
        if (Status.INITIALIZING.equals(project.getSyncStatus())) {
            // First sync success
            toActive(project, Status.INITIALIZING);
        } else if (Status.ACTIVE.equals(project.getSyncStatus())) {
            // Keep ACTIVE
            project.setErrorMessage(null);
            log.debug("Project {} remains ACTIVE (sync success)", project.getProjectKey());
        } else {
            log.warn("Unexpected sync success in state: {}", project.getSyncStatus());
        }
    }

    /**
     * Handle sync failure
     * Determines if project should transition to ERROR or SOURCE_MISSING
     */
    public void onSyncFailure(SyncProject project, SyncTask task, String errorType, String errorMessage) {
        // Check if source project is missing
        if ("not_found".equals(errorType)) {
            toSourceMissing(project, errorMessage);
            return;
        }

        // Check if should transition to ERROR (consecutive failures >= 5)
        if (task.getConsecutiveFailures() >= 5) {
            toError(project, errorMessage);
            return;
        }

        // Otherwise, stay in current state (ACTIVE or INITIALIZING)
        // The task status will reflect the failure
        project.setErrorMessage(errorMessage);
        log.warn("Project {} sync failed (attempts: {}), status remains {}",
                 project.getProjectKey(), task.getConsecutiveFailures(), project.getSyncStatus());
    }

    /**
     * Transition to ERROR
     * Called when consecutive failures >= 5 or critical error
     */
    private void toError(SyncProject project, String errorMessage) {
        project.setSyncStatus(Status.ERROR);
        project.setErrorMessage(errorMessage);
        log.error("Project {} -> ERROR: {}", project.getProjectKey(), errorMessage);
    }

    /**
     * Transition to SOURCE_MISSING
     * Called when source project is not found
     */
    private void toSourceMissing(SyncProject project, String errorMessage) {
        project.setSyncStatus(Status.SOURCE_MISSING);
        project.setErrorMessage(errorMessage);
        log.warn("Project {} -> SOURCE_MISSING: {}", project.getProjectKey(), errorMessage);
    }

    /**
     * Transition to DISABLED
     * Called when user manually disables the project
     */
    public void toDisabled(SyncProject project) {
        String previousStatus = project.getSyncStatus();
        project.setSyncStatus(Status.DISABLED);
        log.info("Project {} -> DISABLED (from: {})", project.getProjectKey(), previousStatus);
    }

    /**
     * Transition to DELETED
     * Called when user deletes the project (logical delete)
     */
    public void toDeleted(SyncProject project) {
        String previousStatus = project.getSyncStatus();
        project.setSyncStatus(Status.DELETED);
        log.info("Project {} -> DELETED (from: {})", project.getProjectKey(), previousStatus);
    }

    /**
     * Check if project can be synced
     */
    public boolean canSync(SyncProject project) {
        return Status.ACTIVE.equals(project.getSyncStatus()) ||
               Status.INITIALIZING.equals(project.getSyncStatus());
    }

    /**
     * Check if project is in error state
     */
    public boolean isError(SyncProject project) {
        return Status.ERROR.equals(project.getSyncStatus()) ||
               Status.SOURCE_MISSING.equals(project.getSyncStatus());
    }
}
