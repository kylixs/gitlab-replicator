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

        try {
            // Update task status: pending → running (in separate transaction to ensure it persists)
            taskStatusUpdateService.updateStatus(task, "running", Instant.now());

            // Execute sync in transaction
            executeSyncInternal(task);

        } catch (Throwable e) {
            log.error("Pull sync failed, taskId={}", task.getId(), e);
            handleSyncFailure(task, e);
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

        // Check if enabled
        if (!config.getEnabled()) {
            log.warn("Pull sync disabled for project: {}", project.getProjectKey());
            taskStatusUpdateService.updateToWaiting(task, null, "Disabled");
            return;
        }

        // Record sync started event
        recordSyncEvent(project, SyncEvent.EventType.SYNC_STARTED, SyncEvent.Status.RUNNING,
            String.format("Starting sync for project: %s", project.getProjectKey()));

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

        // 3. Get source project info
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

            recordSyncFinishedEvent(project, task, currentHeadSha,
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

        // Update SyncProject status to active on success
        SyncProject project = syncProjectMapper.selectById(task.getSyncProjectId());
        if (project != null && !SyncProject.SyncStatus.ACTIVE.equals(project.getSyncStatus())) {
            project.setSyncStatus(SyncProject.SyncStatus.ACTIVE);
            project.setErrorMessage(null);
            project.setLastSyncAt(LocalDateTime.now());
            syncProjectMapper.updateById(project);
            log.info("Updated project status to active: {}", project.getProjectKey());
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

        task.setTaskStatus("waiting");
        task.setCompletedAt(completedAt);
        task.setDurationSeconds((int) durationSeconds);
        task.setLastSyncStatus("failed");
        task.setLastRunAt(completedAt);

        String errorType = classifyError(e);
        task.setErrorType(errorType);
        task.setErrorMessage(e.getMessage());
        task.setConsecutiveFailures(task.getConsecutiveFailures() + 1);

        // Check if auto-disable is needed
        SyncProject project = syncProjectMapper.selectById(task.getSyncProjectId());
        PullSyncConfig config = pullSyncConfigMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PullSyncConfig>()
                .eq("sync_project_id", task.getSyncProjectId())
        );

        boolean shouldDisable = false;
        String disableReason = null;

        // Disable immediately for non-retryable errors
        if (isNonRetryableError(errorType)) {
            shouldDisable = true;
            disableReason = "Non-retryable error: " + errorType;
        }
        // Disable after 5 consecutive failures
        else if (task.getConsecutiveFailures() >= 5) {
            shouldDisable = true;
            disableReason = "Too many consecutive failures (≥5)";
        }

        // Update SyncProject status based on error type
        if (project != null) {
            if ("not_found".equals(errorType)) {
                project.setSyncStatus(SyncProject.SyncStatus.SOURCE_NOT_FOUND);
                log.info("Updated project status to source_not_found: {}", project.getProjectKey());
            } else {
                project.setSyncStatus(SyncProject.SyncStatus.FAILED);
                log.info("Updated project status to failed: {}", project.getProjectKey());
            }
            project.setErrorMessage(e.getMessage());
            syncProjectMapper.updateById(project);
        }

        if (shouldDisable && config != null) {
            config.setEnabled(false);
            pullSyncConfigMapper.updateById(config);
            log.warn("Auto-disabled pull sync for project: {}, reason: {}",
                project != null ? project.getProjectKey() : task.getSyncProjectId(), disableReason);

            if (project != null) {
                recordSyncEvent(project, "auto_disabled", "warning",
                    String.format("%s, Failures: %d", disableReason, task.getConsecutiveFailures()));
            }
        }

        // Calculate retry time with exponential backoff
        task.setNextRunAt(calculateRetryTime(task.getConsecutiveFailures()));

        // Update task status in new transaction to ensure it persists
        taskStatusUpdateService.updateAfterFailure(task);

        // Record sync failed event with details
        if (project != null) {
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
                "shouldDisable", shouldDisable
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
    private void recordSyncEvent(SyncProject project, String eventType, String status, String eventData) {
        SyncEvent event = new SyncEvent();
        event.setSyncProjectId(project.getId());
        event.setEventType(eventType);
        event.setEventSource("pull_sync_executor");
        event.setStatus(status);
        event.setEventData(java.util.Map.of("message", eventData));
        event.setEventTime(LocalDateTime.now());
        syncEventMapper.insert(event);
    }

    /**
     * Record sync finished event with detailed information
     *
     * @param project Sync project
     * @param task    Sync task
     * @param commitSha Final commit SHA
     * @param message Event message
     */
    private void recordSyncFinishedEvent(SyncProject project, SyncTask task, String commitSha, String message) {
        SyncEvent event = new SyncEvent();
        event.setSyncProjectId(project.getId());
        event.setEventType(SyncEvent.EventType.SYNC_FINISHED);
        event.setEventSource("pull_sync_executor");
        event.setStatus(SyncEvent.Status.SUCCESS);
        event.setCommitSha(commitSha);
        event.setDurationSeconds(task.getDurationSeconds());
        event.setEventData(java.util.Map.of(
            "message", message,
            "hasChanges", task.getHasChanges() != null ? task.getHasChanges() : false,
            "sourceSha", task.getSourceCommitSha() != null ? task.getSourceCommitSha() : "",
            "targetSha", task.getTargetCommitSha() != null ? task.getTargetCommitSha() : ""
        ));
        event.setEventTime(LocalDateTime.now());
        syncEventMapper.insert(event);
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
