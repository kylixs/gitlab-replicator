package com.gitlab.mirror.server.service;

import com.gitlab.mirror.common.model.RepositoryBranch;
import com.gitlab.mirror.server.client.GitLabApiClient;
import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import com.gitlab.mirror.server.entity.*;
import com.gitlab.mirror.server.executor.GitCommandExecutor;
import com.gitlab.mirror.server.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Pull Sync Executor Service
 * <p>
 * Executes Pull sync tasks (first sync and incremental sync)
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class PullSyncExecutorService {

    private final GitCommandExecutor gitCommandExecutor;
    private final GitLabApiClient sourceGitLabApiClient;
    private final TargetProjectManagementService targetProjectManagementService;
    private final DiskManagementService diskManagementService;
    private final SyncTaskMapper syncTaskMapper;
    private final SyncProjectMapper syncProjectMapper;
    private final PullSyncConfigMapper pullSyncConfigMapper;
    private final SourceProjectInfoMapper sourceProjectInfoMapper;
    private final TargetProjectInfoMapper targetProjectInfoMapper;
    private final SyncEventMapper syncEventMapper;
    private final SyncResultMapper syncResultMapper;
    private final GitLabMirrorProperties properties;
    private final TaskStatusUpdateService taskStatusUpdateService;
    private final BranchSnapshotService branchSnapshotService;

    public PullSyncExecutorService(
            GitCommandExecutor gitCommandExecutor,
            @Qualifier("sourceGitLabApiClient") GitLabApiClient sourceGitLabApiClient,
            TargetProjectManagementService targetProjectManagementService,
            DiskManagementService diskManagementService,
            SyncTaskMapper syncTaskMapper,
            SyncProjectMapper syncProjectMapper,
            PullSyncConfigMapper pullSyncConfigMapper,
            SourceProjectInfoMapper sourceProjectInfoMapper,
            TargetProjectInfoMapper targetProjectInfoMapper,
            SyncEventMapper syncEventMapper,
            SyncResultMapper syncResultMapper,
            GitLabMirrorProperties properties,
            TaskStatusUpdateService taskStatusUpdateService,
            BranchSnapshotService branchSnapshotService) {
        this.gitCommandExecutor = gitCommandExecutor;
        this.sourceGitLabApiClient = sourceGitLabApiClient;
        this.targetProjectManagementService = targetProjectManagementService;
        this.diskManagementService = diskManagementService;
        this.syncTaskMapper = syncTaskMapper;
        this.syncProjectMapper = syncProjectMapper;
        this.pullSyncConfigMapper = pullSyncConfigMapper;
        this.sourceProjectInfoMapper = sourceProjectInfoMapper;
        this.targetProjectInfoMapper = targetProjectInfoMapper;
        this.syncEventMapper = syncEventMapper;
        this.syncResultMapper = syncResultMapper;
        this.properties = properties;
        this.taskStatusUpdateService = taskStatusUpdateService;
        this.branchSnapshotService = branchSnapshotService;
    }

    /**
     * Execute Pull sync task
     *
     * @param task Sync task
     */
    public void executeSync(SyncTask task) {
        log.info("Executing Pull sync task, taskId={}, projectId={}",
            task.getId(), task.getSyncProjectId());

        Instant startTime = Instant.now();
        boolean success = false;
        Throwable exception = null;

        try {
            // Update task status: pending → running (in separate transaction to ensure it persists)
            taskStatusUpdateService.updateStatus(task, "running", startTime);

            // Execute sync in transaction
            executeSyncInternal(task);

            // Mark as successful if no exception thrown
            success = true;

        } catch (Throwable e) {
            log.error("Pull sync failed, taskId={}", task.getId(), e);
            exception = e;
        } finally {
            // Always record completion time and result
            Instant completedAt = Instant.now();

            if (success) {
                // Success case: completion time should already be set in recordSyncResult
                // But ensure it's set in case of early returns
                if (task.getCompletedAt() == null) {
                    task.setCompletedAt(completedAt);
                    if (task.getStartedAt() != null) {
                        long duration = ChronoUnit.SECONDS.between(task.getStartedAt(), completedAt);
                        task.setDurationSeconds((int) duration);
                    }
                    syncTaskMapper.updateById(task);
                    log.warn("CompletedAt was not set during sync execution, set it in finally block");
                }
                log.info("Pull sync completed successfully, taskId={}, duration={}s",
                    task.getId(),
                    task.getStartedAt() != null ? ChronoUnit.SECONDS.between(task.getStartedAt(), completedAt) : 0);
            } else {
                // Failure case: ensure completion time is recorded
                handleSyncFailure(task, exception != null ? exception : new RuntimeException("Unknown error"));
            }
        }
    }

    /**
     * Execute sync internal logic (transactional)
     *
     * @param task Sync task
     */
    @Transactional
    private void executeSyncInternal(SyncTask task) {
        // Get project and config
        SyncProject project = syncProjectMapper.selectById(task.getSyncProjectId());
        if (project == null) {
            throw new IllegalStateException("Sync project not found: " + task.getSyncProjectId());
        }

        PullSyncConfig config = pullSyncConfigMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PullSyncConfig>()
                .eq("sync_project_id", project.getId())
        );
        if (config == null) {
            throw new IllegalStateException("Pull sync config not found: " + project.getId());
        }

        // Check project status - only sync if in syncable states
        if (!isSyncable(project.getSyncStatus())) {
            log.warn("Project {} is not in syncable status: {}", project.getProjectKey(), project.getSyncStatus());
            // Set task status to 'blocked' instead of 'waiting'
            // This prevents scheduler from picking it up
            task.setTaskStatus("blocked");
            task.setErrorMessage("Project status is " + project.getSyncStatus());
            task.setNextRunAt(null); // No next run for blocked tasks
            syncTaskMapper.updateById(task);
            log.info("Task {} blocked due to project status: {}", task.getId(), project.getSyncStatus());

            // Record task blocked event
            recordTaskBlockedEvent(project, task, "Project in non-syncable status: " + project.getSyncStatus());
            return;
        }

        // Record sync start time in task
        task.setStartedAt(java.time.Instant.now());
        syncTaskMapper.updateById(task);
        log.info("Sync started for project: {} at {}", project.getProjectKey(), task.getStartedAt());

        // Determine if first sync or incremental
        boolean isFirstSync = config.getLocalRepoPath() == null ||
            !gitCommandExecutor.isValidRepository(config.getLocalRepoPath());

        if (isFirstSync) {
            executeFirstSync(task, project, config);
        } else {
            executeIncrementalSync(task, project, config);
        }
    }

    /**
     * Execute first sync (clone + push)
     *
     * @param task    Sync task
     * @param project Sync project
     * @param config  Pull sync config
     */
    private void executeFirstSync(SyncTask task, SyncProject project, PullSyncConfig config) {
        log.info("Executing first sync for project: {}", project.getProjectKey());

        // 1. Ensure target project exists
        ensureTargetProjectExists(project);

        // 2. Check disk space availability
        long requiredSpace = diskManagementService.estimateRequiredSpace(project.getId());
        if (!diskManagementService.checkAvailableSpace(requiredSpace)) {
            throw new RuntimeException(String.format(
                "Insufficient disk space: required=%d bytes, project=%s",
                requiredSpace, project.getProjectKey()));
        }

        // 3. Prepare local repository path
        String localRepoPath = prepareLocalRepoPath(project, config);

        // 4. Get source and target repository URLs
        SourceProjectInfo sourceInfo = sourceProjectInfoMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SourceProjectInfo>()
                .eq("sync_project_id", project.getId())
        );
        TargetProjectInfo targetInfo = targetProjectInfoMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TargetProjectInfo>()
                .eq("sync_project_id", project.getId())
        );

        if (sourceInfo == null || targetInfo == null) {
            throw new IllegalStateException("Source or target project info not found");
        }

        String sourceUrl = buildGitUrl(properties.getSource().getUrl(),
            properties.getSource().getToken(), sourceInfo.getPathWithNamespace());
        String targetUrl = buildGitUrl(properties.getTarget().getUrl(),
            properties.getTarget().getToken(), targetInfo.getPathWithNamespace());

        // 5. Execute git sync-first (clone + push)
        GitCommandExecutor.GitResult result = gitCommandExecutor.syncFirst(
            sourceUrl, targetUrl, localRepoPath
        );

        if (!result.isSuccess()) {
            throw new RuntimeException("First sync failed: " + result.getError());
        }

        // 6. Update config with local repo path
        config.setLocalRepoPath(localRepoPath);
        pullSyncConfigMapper.updateById(config);

        // 7. Update task with sync result
        String finalSha = result.getParsedValue("FINAL_SHA");
        updateTaskAfterSuccess(task, true, finalSha, finalSha);

        // 8. Update branch snapshots after successful sync
        try {
            branchSnapshotService.updateSourceBranchSnapshot(
                project.getId(), sourceInfo.getGitlabProjectId(), sourceInfo.getDefaultBranch());
            branchSnapshotService.updateTargetBranchSnapshot(
                project.getId(), targetInfo.getGitlabProjectId(), targetInfo.getDefaultBranch());
            log.info("Updated branch snapshots after first sync");

            // Update target_project_info branch count from snapshot
            int targetBranchCount = branchSnapshotService.countBranches(project.getId(), "target");
            targetInfo.setBranchCount(targetBranchCount);
            targetProjectInfoMapper.updateById(targetInfo);
            log.info("Updated target project info: branch_count={}", targetBranchCount);
        } catch (Exception e) {
            log.warn("Failed to update branch snapshots after sync: {}", e.getMessage());
        }

        // 9. Record sync finished event
        recordSyncFinishedEvent(project, task, finalSha, "First sync completed");

        log.info("First sync completed successfully for project: {}", project.getProjectKey());
    }

    /**
     * Execute incremental sync (update + push)
     *
     * @param task    Sync task
     * @param project Sync project
     * @param config  Pull sync config
     */
    private void executeIncrementalSync(SyncTask task, SyncProject project, PullSyncConfig config) {
        log.info("Executing incremental sync for project: {}", project.getProjectKey());

        // 1. Ensure target project exists
        ensureTargetProjectExists(project);

        // 2. Get local repository path
        String localRepoPath = config.getLocalRepoPath();
        if (localRepoPath == null || localRepoPath.isEmpty()) {
            log.warn("Local repo path is empty, falling back to first sync");
            executeFirstSync(task, project, config);
            return;
        }

        // 3. Validate local repository exists and is valid
        if (!gitCommandExecutor.isValidRepository(localRepoPath)) {
            log.warn("Local repository is invalid or missing at: {}, falling back to first sync", localRepoPath);
            // Clear local repo path and trigger first sync
            config.setLocalRepoPath(null);
            pullSyncConfigMapper.updateById(config);
            executeFirstSync(task, project, config);
            return;
        }

        // 4. Get source project info
        SourceProjectInfo sourceInfo = sourceProjectInfoMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SourceProjectInfo>()
                .eq("sync_project_id", project.getId())
        );
        TargetProjectInfo targetInfo = targetProjectInfoMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TargetProjectInfo>()
                .eq("sync_project_id", project.getId())
        );

        if (sourceInfo == null || targetInfo == null) {
            throw new IllegalStateException("Source or target project info not found");
        }

        // 4. Build URLs
        String sourceUrl = buildGitUrl(properties.getSource().getUrl(),
            properties.getSource().getToken(), sourceInfo.getPathWithNamespace());
        String targetUrl = buildGitUrl(properties.getTarget().getUrl(),
            properties.getTarget().getToken(), targetInfo.getPathWithNamespace());

        // 5. Check for branch-level changes using branch snapshot comparison
        boolean hasChanges = checkForBranchChanges(project.getId(),
            sourceInfo.getGitlabProjectId(), targetInfo.getGitlabProjectId());

        // 6. If no changes and not forced, skip sync but still update snapshots to ensure accuracy
        if (!hasChanges && !Boolean.TRUE.equals(task.getForceSync())) {
            log.info("No branch changes detected for project: {}, skipping sync", project.getProjectKey());
            String currentHeadSha = task.getSourceCommitSha();
            updateTaskAfterSuccess(task, false, currentHeadSha, currentHeadSha);

            // Update snapshots even when skipping sync to keep database accurate
            try {
                branchSnapshotService.updateSourceBranchSnapshot(
                    project.getId(), sourceInfo.getGitlabProjectId(), sourceInfo.getDefaultBranch());
                branchSnapshotService.updateTargetBranchSnapshot(
                    project.getId(), targetInfo.getGitlabProjectId(), targetInfo.getDefaultBranch());
                log.info("Updated branch snapshots (no changes detected)");

                // Update target_project_info branch count from snapshot
                int targetBranchCount = branchSnapshotService.countBranches(project.getId(), "target");
                targetInfo.setBranchCount(targetBranchCount);
                targetProjectInfoMapper.updateById(targetInfo);
                log.info("Updated target project info: branch_count={}", targetBranchCount);
            } catch (Exception e) {
                log.warn("Failed to update branch snapshots: {}", e.getMessage());
            }

            // Record to sync_result table but not to sync_event (no changes)
            recordSyncResult(project, task, SyncResult.Status.SKIPPED,
                "No branch changes detected, sync skipped");
            return;
        }

        // Log reason for sync
        if (Boolean.TRUE.equals(task.getForceSync())) {
            log.info("Force sync enabled for project: {}, bypassing change detection", project.getProjectKey());
        } else {
            log.info("Branch changes detected for project: {}, proceeding with sync", project.getProjectKey());
        }

        // 7. Execute git sync-incremental (remote update + push)
        String lastSyncedSha = task.getSourceCommitSha();
        GitCommandExecutor.GitResult result = gitCommandExecutor.syncIncremental(
            sourceUrl, targetUrl, localRepoPath
        );

        if (!result.isSuccess()) {
            throw new RuntimeException("Incremental sync failed: " + result.getError());
        }

        // 8. Update task with sync result
        String finalSha = result.getParsedValue("FINAL_SHA");
        updateTaskAfterSuccess(task, true, finalSha, finalSha);

        // 9. Update branch snapshots after successful sync
        try {
            branchSnapshotService.updateSourceBranchSnapshot(
                project.getId(), sourceInfo.getGitlabProjectId(), sourceInfo.getDefaultBranch());
            branchSnapshotService.updateTargetBranchSnapshot(
                project.getId(), targetInfo.getGitlabProjectId(), targetInfo.getDefaultBranch());
            log.info("Updated branch snapshots after incremental sync");

            // Update target_project_info branch count from snapshot
            int targetBranchCount = branchSnapshotService.countBranches(project.getId(), "target");
            targetInfo.setBranchCount(targetBranchCount);
            targetProjectInfoMapper.updateById(targetInfo);
            log.info("Updated target project info: branch_count={}", targetBranchCount);
        } catch (Exception e) {
            log.warn("Failed to update branch snapshots after sync: {}", e.getMessage());
        }

        // 10. Record sync finished event
        recordSyncFinishedEvent(project, task, finalSha,
            String.format("Incremental sync completed, SHA: %s → %s", lastSyncedSha, finalSha));

        log.info("Incremental sync completed successfully for project: {}", project.getProjectKey());
    }

    /**
     * Ensure target project exists (create if not)
     *
     * @param project Sync project
     */
    private void ensureTargetProjectExists(SyncProject project) {
        TargetProjectInfo targetInfo = targetProjectInfoMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TargetProjectInfo>()
                .eq("sync_project_id", project.getId())
        );

        if (targetInfo == null || targetInfo.getGitlabProjectId() == null) {
            log.info("Target project not created, creating now: {}", project.getProjectKey());
            targetProjectManagementService.createTargetProject(project.getId());
        }
    }

    /**
     * Prepare local repository path
     *
     * @param project Sync project
     * @param config  Pull sync config
     * @return Local repository path
     */
    private String prepareLocalRepoPath(SyncProject project, PullSyncConfig config) {
        if (config.getLocalRepoPath() != null && !config.getLocalRepoPath().isEmpty()) {
            return config.getLocalRepoPath();
        }

        // Generate default path: ~/.gitlab-sync/repos/<project-key>
        String homeDir = System.getProperty("user.home");
        String basePath = homeDir + "/.gitlab-sync/repos";
        String localPath = basePath + "/" + project.getProjectKey();

        // Create parent directory
        try {
            Path path = Paths.get(localPath).getParent();
            if (path != null && !Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create local repo directory: " + localPath, e);
        }

        return localPath;
    }

    /**
     * Build Git URL with authentication token
     *
     * @param baseUrl         Base URL (e.g., http://localhost:8000)
     * @param token          Access token
     * @param projectPath    Project path (e.g., group/project)
     * @return Authenticated Git URL
     */
    private String buildGitUrl(String baseUrl, String token, String projectPath) {
        if (projectPath == null || projectPath.isEmpty()) {
            throw new IllegalArgumentException("Project path is null or empty");
        }

        // Build URL: http://gitlab-ci-token:<token>@localhost:8000/group/project.git
        String url = baseUrl;

        // Remove trailing slash
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        // Insert token into URL
        if (url.startsWith("http://")) {
            url = url.replace("http://", "http://gitlab-ci-token:" + token + "@");
        } else if (url.startsWith("https://")) {
            url = url.replace("https://", "https://gitlab-ci-token:" + token + "@");
        }

        // Append project path with .git extension
        return url + "/" + projectPath + ".git";
    }


    /**
     * Update task after successful sync
     *
     * @param task      Task
     * @param hasChanges Whether has changes
     * @param sourceSha Source commit SHA
     * @param targetSha Target commit SHA
     */
    private void updateTaskAfterSuccess(SyncTask task, boolean hasChanges,
                                        String sourceSha, String targetSha) {
        Instant now = Instant.now();
        Instant completedAt = now;
        long durationSeconds = ChronoUnit.SECONDS.between(task.getStartedAt(), completedAt);

        task.setTaskStatus("waiting");
        task.setCompletedAt(completedAt);
        task.setDurationSeconds((int) durationSeconds);
        task.setHasChanges(hasChanges);
        task.setSourceCommitSha(sourceSha);
        task.setTargetCommitSha(targetSha);
        task.setLastSyncStatus("success");
        task.setLastRunAt(completedAt);
        task.setConsecutiveFailures(0);  // Reset failure count
        task.setForceSync(false);  // Clear force sync flag after execution

        // Calculate next run time based on priority
        task.setNextRunAt(calculateNextRunTime(task));
        task.setUpdatedAt(LocalDateTime.now());

        // Clear error fields on success
        task.setErrorType("");
        task.setErrorMessage("");

        syncTaskMapper.updateById(task);

        // Reset SyncProject status to active on success
        SyncProject project = syncProjectMapper.selectById(task.getSyncProjectId());
        if (project != null) {
            String previousStatus = project.getSyncStatus();
            // Always reset to active on success (from warning or error states)
            if (!SyncProject.SyncStatus.ACTIVE.equals(previousStatus)) {
                project.setSyncStatus(SyncProject.SyncStatus.ACTIVE);
                project.setErrorMessage(null);
                project.setLastSyncAt(LocalDateTime.now());
                syncProjectMapper.updateById(project);
                log.info("Reset project status to active after success: {} (was: {})",
                    project.getProjectKey(), previousStatus);
            } else {
                // Even if already active, update last sync time and clear error
                project.setErrorMessage(null);
                project.setLastSyncAt(LocalDateTime.now());
                syncProjectMapper.updateById(project);
            }

            // Record sync result to sync_result table
            String message = hasChanges ?
                String.format("Sync completed with changes (source: %s, target: %s)", sourceSha, targetSha) :
                "Sync completed without changes";
            recordSyncResult(project, task, SyncResult.Status.SUCCESS, message);
        }
    }


    /**
     * Handle sync failure (uses separate transaction service to ensure status update persists)
     *
     * @param task Sync task
     * @param e    Exception
     */
    private void handleSyncFailure(SyncTask task, Throwable e) {
        Instant now = Instant.now();
        Instant completedAt = now;
        long durationSeconds = task.getStartedAt() != null ?
            ChronoUnit.SECONDS.between(task.getStartedAt(), completedAt) : 0;

        task.setCompletedAt(completedAt);
        task.setDurationSeconds((int) durationSeconds);
        task.setLastSyncStatus("failed");
        task.setLastRunAt(completedAt);

        String errorType = classifyError(e);
        task.setErrorType(errorType);
        task.setErrorMessage(e.getMessage());
        task.setConsecutiveFailures(task.getConsecutiveFailures() + 1);

        // Check if should block task
        SyncProject project = syncProjectMapper.selectById(task.getSyncProjectId());

        boolean shouldBlock = false;
        String blockReason = null;

        // Block immediately for non-retryable errors
        if (isNonRetryableError(errorType)) {
            shouldBlock = true;
            blockReason = "Non-retryable error: " + errorType;
        }
        // Block after 5 consecutive failures
        else if (task.getConsecutiveFailures() >= 5) {
            shouldBlock = true;
            blockReason = "Too many consecutive failures (≥5)";
        }

        // Update SyncProject status based on error type and failure count
        if (project != null) {
            String previousStatus = project.getSyncStatus();

            if ("not_found".equals(errorType)) {
                // Source project missing
                project.setSyncStatus(SyncProject.SyncStatus.MISSING);
                log.info("Updated project status to missing: {} (was: {})",
                    project.getProjectKey(), previousStatus);
            } else if (shouldBlock) {
                // Non-retryable error or too many failures (≥5)
                project.setSyncStatus(SyncProject.SyncStatus.FAILED);
                log.info("Updated project status to failed: {} (was: {}, failures: {})",
                    project.getProjectKey(), previousStatus, task.getConsecutiveFailures());
            } else {
                // Retryable error with failures < 5 - set to WARNING
                if (SyncProject.SyncStatus.ACTIVE.equals(previousStatus) ||
                    SyncProject.SyncStatus.WARNING.equals(previousStatus)) {
                    project.setSyncStatus(SyncProject.SyncStatus.WARNING);
                    log.info("Set project status to warning: {} (was: {}, failures: {})",
                        project.getProjectKey(), previousStatus, task.getConsecutiveFailures());
                }
                // If already WARNING, keep it as WARNING
            }
            project.setErrorMessage(e.getMessage());
            syncProjectMapper.updateById(project);
        }

        // Set task status based on whether it should be blocked
        if (shouldBlock) {
            task.setTaskStatus("blocked");
            task.setNextRunAt(null); // No next run for blocked tasks
            log.warn("Task {} blocked due to: {}, failures: {}",
                task.getId(), blockReason, task.getConsecutiveFailures());

            // Record task blocked event
            if (project != null) {
                recordTaskBlockedEvent(project, task, blockReason);
            }
        } else {
            task.setTaskStatus("waiting");
            // Calculate retry time with exponential backoff for retryable errors
            task.setNextRunAt(calculateRetryTime(task.getConsecutiveFailures()));
        }

        // Update task status in new transaction to ensure it persists
        taskStatusUpdateService.updateAfterFailure(task);

        // Record sync result to sync_result table
        if (project != null) {
            String failureMessage = String.format("Sync failed: %s (failures: %d, error: %s)",
                e.getMessage(), task.getConsecutiveFailures(), errorType);
            recordSyncResult(project, task, SyncResult.Status.FAILED, failureMessage);

            // Record sync failed event with details
            SyncEvent event = new SyncEvent();
            event.setSyncProjectId(project.getId());
            event.setEventType(SyncEvent.EventType.SYNC_FAILED);
            event.setEventSource("pull_sync_executor");
            event.setStatus(SyncEvent.Status.FAILED);
            event.setErrorMessage(e.getMessage());
            event.setDurationSeconds(task.getDurationSeconds());
            event.setEventData(java.util.Map.of(
                "errorType", errorType,
                "consecutiveFailures", task.getConsecutiveFailures(),
                "isBlocked", shouldBlock
            ));
            event.setEventTime(LocalDateTime.now());
            syncEventMapper.insert(event);
        }
    }

    /**
     * Check if error is non-retryable
     *
     * @param errorType Error type
     * @return true if non-retryable
     */
    private boolean isNonRetryableError(String errorType) {
        return "auth_failed".equals(errorType) || "not_found".equals(errorType);
    }

    /**
     * Classify error type
     *
     * @param e Exception
     * @return Error type
     */
    private String classifyError(Throwable e) {
        String message = e.getMessage();
        if (message == null) {
            return "unknown";
        }

        message = message.toLowerCase();
        if (message.contains("authentication") || message.contains("unauthorized")) {
            return "auth_failed";
        } else if (message.contains("not found") || message.contains("404") ||
                   message.contains("source project") || message.contains("source or target project info not found")) {
            return "not_found";
        } else if (message.contains("timeout")) {
            return "timeout";
        } else if (message.contains("network") || message.contains("connection")) {
            return "network_error";
        } else if (message.contains("disk") || message.contains("space")) {
            return "disk_error";
        } else {
            return "unknown";
        }
    }

    /**
     * Check if project status allows sync execution
     *
     * @param syncStatus Project sync status
     * @return true if syncable
     */
    private boolean isSyncable(String syncStatus) {
        // Syncable states: active, warning, pending (for backward compatibility)
        // Not syncable: missing, deleted, failed
        return SyncProject.SyncStatus.ACTIVE.equals(syncStatus) ||
               SyncProject.SyncStatus.WARNING.equals(syncStatus) ||
               SyncProject.SyncStatus.PENDING.equals(syncStatus) ||
               SyncProject.SyncStatus.TARGET_CREATED.equals(syncStatus) ||
               SyncProject.SyncStatus.MIRROR_CONFIGURED.equals(syncStatus);
    }

    /**
     * Calculate next run time based on priority
     *
     * @param task Sync task
     * @return Next run time
     */
    private Instant calculateNextRunTime(SyncTask task) {
        // Get priority from config
        PullSyncConfig config = pullSyncConfigMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PullSyncConfig>()
                .eq("sync_project_id", task.getSyncProjectId())
        );

        if (config == null) {
            // Default: use normal priority interval
            int defaultInterval = properties.getSync().getPullInterval().getNormalSeconds();
            return Instant.now().plus(defaultInterval, ChronoUnit.SECONDS);
        }

        // Get interval from configuration based on priority
        GitLabMirrorProperties.PullSyncIntervalConfig intervalConfig =
            properties.getSync().getPullInterval();

        String priority = config.getPriority();
        int intervalSeconds = switch (priority.toLowerCase()) {
            case "critical" -> intervalConfig.getCriticalSeconds();
            case "high" -> intervalConfig.getHighSeconds();
            case "normal" -> intervalConfig.getNormalSeconds();
            case "low" -> intervalConfig.getLowSeconds();
            default -> intervalConfig.getNormalSeconds();  // Default: normal priority
        };

        return Instant.now().plus(intervalSeconds, ChronoUnit.SECONDS);
    }

    /**
     * Calculate retry time with exponential backoff
     *
     * @param retryCount Retry count
     * @return Retry time
     */
    private Instant calculateRetryTime(int retryCount) {
        // Exponential backoff: delay = 5min × 2^retry_count
        long delayMinutes = 5 * (long) Math.pow(2, Math.min(retryCount, 5));
        return Instant.now().plus(delayMinutes, ChronoUnit.MINUTES);
    }

    /**
     * Record sync event
     *
     * @param project    Sync project
     * @param eventType  Event type
     * @param status     Status
     * @param eventData  Event data
     */
    /**
     * Record or update sync result
     * <p>
     * Always updates sync_result table (one record per project)
     * Only records to sync_event table if there are changes or failures
     *
     * @param project Sync project
     * @param task    Sync task
     * @param status  Sync status: success/failed/skipped
     * @param message Result message
     */
    private void recordSyncResult(SyncProject project, SyncTask task, String status, String message) {
        // Calculate completion time and duration
        java.time.Instant completedAt = java.time.Instant.now();
        if (task.getStartedAt() != null) {
            long duration = java.time.Duration.between(task.getStartedAt(), completedAt).getSeconds();
            task.setDurationSeconds((int) duration);
        }
        task.setCompletedAt(completedAt);

        // Note: Project status updates are handled in updateTaskAfterSuccess() and handleSyncFailure()

        // Always update sync_result table
        SyncResult syncResult = syncResultMapper.selectBySyncProjectId(project.getId());
        if (syncResult == null) {
            syncResult = new SyncResult();
            syncResult.setSyncProjectId(project.getId());
        }

        syncResult.setLastSyncAt(LocalDateTime.now());
        syncResult.setStartedAt(task.getStartedAt() != null ?
            LocalDateTime.ofInstant(task.getStartedAt(), java.time.ZoneId.systemDefault()) : null);
        syncResult.setCompletedAt(LocalDateTime.ofInstant(completedAt, java.time.ZoneId.systemDefault()));
        syncResult.setSyncStatus(status);
        syncResult.setHasChanges(task.getHasChanges());
        syncResult.setChangesCount(task.getChangesCount());
        syncResult.setSourceCommitSha(task.getSourceCommitSha());
        syncResult.setTargetCommitSha(task.getTargetCommitSha());
        syncResult.setDurationSeconds(task.getDurationSeconds());
        syncResult.setSummary(message);

        // Set error_message only for failures, use empty string for success/skipped
        // Empty string triggers MyBatis-Plus to update the field (null values are skipped)
        if (SyncResult.Status.FAILED.equals(status)) {
            syncResult.setErrorMessage(task.getErrorMessage());
        } else {
            syncResult.setErrorMessage("");  // Empty string to clear error message
        }

        if (syncResult.getId() == null) {
            syncResultMapper.insert(syncResult);
        } else {
            syncResultMapper.updateById(syncResult);
        }

        // Only record to sync_event if there are changes or it's a failure
        boolean hasChanges = Boolean.TRUE.equals(task.getHasChanges());
        boolean isFailure = SyncResult.Status.FAILED.equals(status);

        if (hasChanges || isFailure) {
            SyncEvent event = new SyncEvent();
            event.setSyncProjectId(project.getId());
            event.setEventType(SyncEvent.EventType.SYNC_FINISHED);
            event.setEventSource("pull_sync_executor");
            event.setStatus(isFailure ? SyncEvent.Status.FAILED : SyncEvent.Status.SUCCESS);
            event.setCommitSha(task.getSourceCommitSha());
            event.setDurationSeconds(task.getDurationSeconds());
            event.setEventTime(LocalDateTime.now());

            // Set start/end times
            event.setStartedAt(task.getStartedAt() != null ?
                LocalDateTime.ofInstant(task.getStartedAt(), java.time.ZoneId.systemDefault()) : null);
            event.setCompletedAt(LocalDateTime.ofInstant(completedAt, java.time.ZoneId.systemDefault()));

            event.setEventData(java.util.Map.of(
                "message", message,
                "hasChanges", hasChanges,
                "sourceSha", task.getSourceCommitSha() != null ? task.getSourceCommitSha() : "",
                "targetSha", task.getTargetCommitSha() != null ? task.getTargetCommitSha() : ""
            ));

            // Set error message for failed events
            if (isFailure) {
                event.setErrorMessage(task.getErrorMessage());
            }

            syncEventMapper.insert(event);
            log.info("Recorded sync event for project {} - status: {}, hasChanges: {}",
                project.getProjectKey(), status, hasChanges);
        } else {
            log.info("Skipped event recording for project {} - no changes and not a failure",
                project.getProjectKey());
        }
    }

    /**
     * Recover blocked task (manually or automatically)
     *
     * @param task Sync task
     * @param reason Recovery reason
     */
    public void recoverBlockedTask(SyncTask task, String reason) {
        if (!"blocked".equals(task.getTaskStatus())) {
            log.warn("Task {} is not blocked, current status: {}", task.getId(), task.getTaskStatus());
            return;
        }

        SyncProject project = syncProjectMapper.selectById(task.getSyncProjectId());
        if (project == null) {
            log.error("Project not found for task {}", task.getId());
            return;
        }

        // Check if project is now in syncable state
        if (!isSyncable(project.getSyncStatus())) {
            log.warn("Cannot recover task {} - project {} still in non-syncable status: {}",
                task.getId(), project.getProjectKey(), project.getSyncStatus());
            return;
        }

        // Reset task to waiting status
        task.setTaskStatus("waiting");
        task.setErrorMessage(null);
        task.setConsecutiveFailures(0); // Reset failure count
        task.setNextRunAt(Instant.now().plusSeconds(10)); // Schedule to run soon
        syncTaskMapper.updateById(task);

        // Record task recovered event
        SyncEvent event = new SyncEvent();
        event.setSyncProjectId(project.getId());
        event.setEventType(SyncEvent.EventType.TASK_RECOVERED);
        event.setEventSource("pull_sync_executor");
        event.setStatus(SyncEvent.Status.SUCCESS);
        event.setErrorMessage(null);
        event.setEventData(java.util.Map.of(
            "taskId", task.getId(),
            "previousStatus", "blocked",
            "newStatus", "waiting",
            "recoveryReason", reason,
            "projectStatus", project.getSyncStatus()
        ));
        event.setEventTime(LocalDateTime.now());
        syncEventMapper.insert(event);

        log.info("Recovered blocked task {} for project {}: {}", task.getId(), project.getProjectKey(), reason);
    }

    /**
     * Record task blocked event
     *
     * @param project Sync project
     * @param task Sync task
     * @param reason Block reason
     */
    private void recordTaskBlockedEvent(SyncProject project, SyncTask task, String reason) {
        SyncEvent event = new SyncEvent();
        event.setSyncProjectId(project.getId());
        event.setEventType(SyncEvent.EventType.TASK_BLOCKED);
        event.setEventSource("pull_sync_executor");
        event.setStatus(SyncEvent.Status.FAILED);
        event.setErrorMessage(reason);
        event.setEventData(java.util.Map.of(
            "taskId", task.getId(),
            "taskStatus", task.getTaskStatus(),
            "projectStatus", project.getSyncStatus(),
            "blockReason", reason,
            "consecutiveFailures", task.getConsecutiveFailures(),
            "errorType", task.getErrorType() != null ? task.getErrorType() : "unknown"
        ));
        event.setEventTime(LocalDateTime.now());
        syncEventMapper.insert(event);
        log.info("Recorded task blocked event for project {}: {}", project.getProjectKey(), reason);
    }

    /**
     * Record sync finished event with detailed information
     * @deprecated Use recordSyncResult instead
     */
    @Deprecated
    private void recordSyncFinishedEvent(SyncProject project, SyncTask task, String commitSha, String message) {
        recordSyncResult(project, task, SyncResult.Status.SUCCESS, message);
    }

    /**
     * Get remote HEAD SHA using GitLab API
     * <p>
     * This method uses GitLab API instead of git ls-remote to avoid network hang issues
     *
     * @param projectPath Source project path
     * @return Commit SHA or null if failed
     */
    private String getRemoteHeadShaViaApi(String projectPath) {
        try {
            log.debug("Getting remote HEAD SHA via GitLab API for: {}", projectPath);

            // Get default branch info via API
            RepositoryBranch defaultBranch = sourceGitLabApiClient.getDefaultBranch(projectPath);

            if (defaultBranch == null || defaultBranch.getCommit() == null) {
                log.warn("Failed to get default branch info via API for: {}", projectPath);
                return null;
            }

            String commitSha = defaultBranch.getCommit().getId();
            log.debug("Got remote HEAD SHA via API: {} for {}", commitSha, projectPath);
            return commitSha;

        } catch (Exception e) {
            log.error("Failed to get remote HEAD SHA via API for: {}", projectPath, e);
            return null;
        }
    }

    /**
     * Check for branch-level changes by comparing source branches with target branches
     * <p>
     * Detects:
     * - New branches in source (not in target)
     * - Updated branches (different commit SHA between source and target)
     * - Branches in source that need to be synced to target
     *
     * @param syncProjectId Sync project ID
     * @param sourceProjectId Source GitLab project ID
     * @param targetProjectId Target GitLab project ID
     * @return true if any branch differences detected
     */
    private boolean checkForBranchChanges(Long syncProjectId, Long sourceProjectId, Long targetProjectId) {
        try {
            log.debug("Checking for branch changes, syncProjectId={}, sourceProjectId={}, targetProjectId={}",
                syncProjectId, sourceProjectId, targetProjectId);

            // Get source and target branch snapshots from database (updated by scan)
            java.util.List<ProjectBranchSnapshot> sourceSnapshot =
                branchSnapshotService.getBranchSnapshots(syncProjectId, "source");
            java.util.List<ProjectBranchSnapshot> targetSnapshot =
                branchSnapshotService.getBranchSnapshots(syncProjectId, "target");

            if (sourceSnapshot == null || sourceSnapshot.isEmpty()) {
                log.info("No source branch snapshot found, will proceed with sync");
                return true;
            }

            if (targetSnapshot == null || targetSnapshot.isEmpty()) {
                log.info("No target branch snapshot found, will proceed with sync");
                return true;
            }

            // Build maps for comparison
            java.util.Map<String, String> sourceBranchMap = new java.util.HashMap<>();
            for (ProjectBranchSnapshot snapshot : sourceSnapshot) {
                sourceBranchMap.put(snapshot.getBranchName(), snapshot.getCommitSha());
            }

            java.util.Map<String, String> targetBranchMap = new java.util.HashMap<>();
            for (ProjectBranchSnapshot snapshot : targetSnapshot) {
                targetBranchMap.put(snapshot.getBranchName(), snapshot.getCommitSha());
            }

            // Check for new or updated branches in source
            for (java.util.Map.Entry<String, String> entry : sourceBranchMap.entrySet()) {
                String branchName = entry.getKey();
                String sourceSha = entry.getValue();
                String targetSha = targetBranchMap.get(branchName);

                if (targetSha == null) {
                    log.info("New branch in source (missing in target): {}", branchName);
                    return true;
                }

                if (!sourceSha.equals(targetSha)) {
                    log.info("Branch differs: {}, source={}, target={}", branchName, sourceSha, targetSha);
                    return true;
                }
            }

            // Check for deleted branches in source (exist in target but not in source)
            for (String branchName : targetBranchMap.keySet()) {
                if (!sourceBranchMap.containsKey(branchName)) {
                    log.info("Branch deleted in source (still in target): {}", branchName);
                    return true;
                }
            }

            log.debug("No branch differences between source and target");
            return false;

        } catch (Exception e) {
            log.error("Failed to check branch changes, will proceed with sync", e);
            return true;  // On error, proceed with sync to be safe
        }
    }
}
