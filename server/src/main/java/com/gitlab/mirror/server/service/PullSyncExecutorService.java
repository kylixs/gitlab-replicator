package com.gitlab.mirror.server.service;

import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import com.gitlab.mirror.server.entity.*;
import com.gitlab.mirror.server.executor.GitCommandExecutor;
import com.gitlab.mirror.server.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class PullSyncExecutorService {

    private final GitCommandExecutor gitCommandExecutor;
    private final TargetProjectManagementService targetProjectManagementService;
    private final SyncTaskMapper syncTaskMapper;
    private final SyncProjectMapper syncProjectMapper;
    private final PullSyncConfigMapper pullSyncConfigMapper;
    private final SourceProjectInfoMapper sourceProjectInfoMapper;
    private final TargetProjectInfoMapper targetProjectInfoMapper;
    private final SyncEventMapper syncEventMapper;
    private final GitLabMirrorProperties properties;

    /**
     * Execute Pull sync task
     *
     * @param task Sync task
     */
    @Transactional
    public void executeSync(SyncTask task) {
        log.info("Executing Pull sync task, taskId={}, projectId={}",
            task.getId(), task.getSyncProjectId());

        try {
            // Update task status: waiting → running
            updateTaskStatus(task, "running", Instant.now());

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
                updateTaskStatusToWaiting(task, null, "Disabled");
                return;
            }

            // Determine if first sync or incremental
            boolean isFirstSync = config.getLocalRepoPath() == null ||
                !gitCommandExecutor.isValidRepository(config.getLocalRepoPath());

            if (isFirstSync) {
                executeFirstSync(task, project, config);
            } else {
                executeIncrementalSync(task, project, config);
            }

        } catch (Exception e) {
            log.error("Pull sync failed, taskId={}", task.getId(), e);
            handleSyncFailure(task, e);
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

        // 2. Prepare local repository path
        String localRepoPath = prepareLocalRepoPath(project, config);

        // 3. Get source and target repository URLs
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

        // 4. Execute git sync-first (clone + push)
        GitCommandExecutor.GitResult result = gitCommandExecutor.syncFirst(
            sourceUrl, targetUrl, localRepoPath
        );

        if (!result.isSuccess()) {
            throw new RuntimeException("First sync failed: " + result.getError());
        }

        // 5. Update config with local repo path
        config.setLocalRepoPath(localRepoPath);
        pullSyncConfigMapper.updateById(config);

        // 6. Update task with sync result
        String finalSha = result.getParsedValue("FINAL_SHA");
        updateTaskAfterSuccess(task, true, finalSha, finalSha);

        // 7. Record event
        recordSyncEvent(project, "first_sync_completed", "success",
            String.format("First sync completed, SHA: %s", finalSha));

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

        // This will be implemented in T3.3
        throw new UnsupportedOperationException("Incremental sync not yet implemented");
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
     * Update task status
     *
     * @param task      Task
     * @param status    New status
     * @param startedAt Started timestamp
     */
    private void updateTaskStatus(SyncTask task, String status, Instant startedAt) {
        task.setTaskStatus(status);
        if (startedAt != null) {
            task.setStartedAt(startedAt);
        }
        task.setUpdatedAt(LocalDateTime.now());
        syncTaskMapper.updateById(task);
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
        task.setErrorType(null);
        task.setErrorMessage(null);
        task.setConsecutiveFailures(0);  // Reset failure count

        // Calculate next run time based on priority
        task.setNextRunAt(calculateNextRunTime(task));
        task.setUpdatedAt(LocalDateTime.now());

        syncTaskMapper.updateById(task);
    }

    /**
     * Update task status to waiting
     *
     * @param task   Task
     * @param reason Reason (optional)
     * @param status Last sync status
     */
    private void updateTaskStatusToWaiting(SyncTask task, String reason, String status) {
        task.setTaskStatus("waiting");
        task.setLastSyncStatus(status);
        task.setNextRunAt(calculateNextRunTime(task));
        task.setUpdatedAt(LocalDateTime.now());
        syncTaskMapper.updateById(task);
    }

    /**
     * Handle sync failure
     *
     * @param task Sync task
     * @param e    Exception
     */
    private void handleSyncFailure(SyncTask task, Exception e) {
        Instant now = Instant.now();
        Instant completedAt = now;
        long durationSeconds = task.getStartedAt() != null ?
            ChronoUnit.SECONDS.between(task.getStartedAt(), completedAt) : 0;

        task.setTaskStatus("waiting");
        task.setCompletedAt(completedAt);
        task.setDurationSeconds((int) durationSeconds);
        task.setLastSyncStatus("failed");
        task.setLastRunAt(completedAt);
        task.setErrorType(classifyError(e));
        task.setErrorMessage(e.getMessage());
        task.setConsecutiveFailures(task.getConsecutiveFailures() + 1);

        // Calculate retry time with exponential backoff
        task.setNextRunAt(calculateRetryTime(task.getConsecutiveFailures()));
        task.setUpdatedAt(LocalDateTime.now());

        syncTaskMapper.updateById(task);

        // Record failure event
        SyncProject project = syncProjectMapper.selectById(task.getSyncProjectId());
        if (project != null) {
            recordSyncEvent(project, "sync_failed", "failed",
                String.format("Error: %s, Failures: %d", e.getMessage(), task.getConsecutiveFailures()));
        }
    }

    /**
     * Classify error type
     *
     * @param e Exception
     * @return Error type
     */
    private String classifyError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "unknown";
        }

        message = message.toLowerCase();
        if (message.contains("authentication") || message.contains("unauthorized")) {
            return "auth_failed";
        } else if (message.contains("not found") || message.contains("404")) {
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
            return Instant.now().plus(120, ChronoUnit.MINUTES);  // Default: 2 hours
        }

        String priority = config.getPriority();
        long intervalMinutes = switch (priority.toLowerCase()) {
            case "critical" -> 10;
            case "high" -> 30;
            case "normal" -> 120;
            case "low" -> 360;
            default -> 120;
        };

        return Instant.now().plus(intervalMinutes, ChronoUnit.MINUTES);
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
}
