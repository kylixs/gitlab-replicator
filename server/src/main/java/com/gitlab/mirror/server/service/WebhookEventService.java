package com.gitlab.mirror.server.service;

import com.gitlab.mirror.server.api.dto.webhook.GitLabPushEvent;
import com.gitlab.mirror.server.entity.*;
import com.gitlab.mirror.server.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Webhook Event Service
 * <p>
 * Handles GitLab Push Webhook events with debounce logic and auto-initialization
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookEventService {

    private final SyncProjectMapper syncProjectMapper;
    private final SyncTaskMapper syncTaskMapper;
    private final PullSyncConfigMapper pullSyncConfigMapper;
    private final SourceProjectInfoMapper sourceProjectInfoMapper;
    private final TargetProjectInfoMapper targetProjectInfoMapper;
    private final SyncEventMapper syncEventMapper;

    private static final int DEBOUNCE_SECONDS = 120; // 2 minutes

    /**
     * Handle Push Event asynchronously
     *
     * @param event GitLab Push Event
     */
    @Async
    public void handlePushEventAsync(GitLabPushEvent event) {
        try {
            handlePushEvent(event);
        } catch (Exception e) {
            log.error("Failed to handle push event async", e);
        }
    }

    /**
     * Handle Push Event
     *
     * @param event GitLab Push Event
     */
    @Transactional
    public void handlePushEvent(GitLabPushEvent event) {
        String projectKey = event.getProject().getPathWithNamespace();
        
        log.info("Processing webhook event: projectKey={}, ref={}, commits={}", 
                projectKey, event.getRef(), event.getTotalCommitsCount());

        // 1. Check if project exists
        SyncProject project = findProjectByKey(projectKey);

        if (project == null) {
            // 2. Auto-initialize new project
            log.info("Project not found, auto-initializing: projectKey={}", projectKey);
            project = initializeProject(event);
            
            if (project == null) {
                log.error("Failed to initialize project from webhook: projectKey={}", projectKey);
                return;
            }
        }

        // 3. Get task
        SyncTask task = getTaskByProjectId(project.getId());
        
        if (task == null) {
            log.warn("No task found for project: projectKey={}", projectKey);
            return;
        }

        // 4. Debounce check: skip if recent successful sync < 2 minutes
        if (shouldDebounce(task)) {
            log.debug("Webhook debounced: projectKey={}, lastSuccessfulSync={}", 
                    projectKey, task.getLastRunAt());
            recordWebhookEvent(project, event, "debounced", "Recent sync < 2 minutes");
            return;
        }

        // 5. Update source project info with latest commit from webhook
        updateSourceProjectInfo(project, event);

        // 6. Update next_run_at to NOW for immediate scheduling
        task.setNextRunAt(Instant.now());
        syncTaskMapper.updateById(task);

        log.info("Immediate schedule triggered by webhook: projectKey={}, nextRunAt={}",
                projectKey, task.getNextRunAt());

        // 7. Record webhook event
        recordWebhookEvent(project, event, "accepted", "Scheduled for immediate execution");
    }

    /**
     * Find project by key
     *
     * @param projectKey Project key (path with namespace)
     * @return SyncProject or null
     */
    private SyncProject findProjectByKey(String projectKey) {
        return syncProjectMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SyncProject>()
                .eq(SyncProject::getProjectKey, projectKey)
        ).stream().findFirst().orElse(null);
    }

    /**
     * Get task by project ID
     *
     * @param projectId Project ID
     * @return SyncTask or null
     */
    private SyncTask getTaskByProjectId(Long projectId) {
        return syncTaskMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SyncTask>()
                .eq(SyncTask::getSyncProjectId, projectId)
        ).stream().findFirst().orElse(null);
    }

    /**
     * Check if webhook should be debounced
     *
     * @param task Sync task
     * @return true if should debounce (skip)
     */
    private boolean shouldDebounce(SyncTask task) {
        // Only debounce if last sync was successful
        if (!"success".equals(task.getLastSyncStatus())) {
            return false;
        }

        // Check if last successful sync was within debounce period
        if (task.getLastRunAt() == null) {
            return false;
        }

        Instant debounceThreshold = Instant.now().minus(DEBOUNCE_SECONDS, ChronoUnit.SECONDS);
        return task.getLastRunAt().isAfter(debounceThreshold);
    }

    /**
     * Initialize project from webhook event
     * <p>
     * Creates SYNC_PROJECT, SOURCE_PROJECT_INFO, PULL_SYNC_CONFIG, SYNC_TASK
     *
     * @param event GitLab Push Event
     * @return Created SyncProject or null if failed
     */
    @Transactional
    public SyncProject initializeProject(GitLabPushEvent event) {
        try {
            GitLabPushEvent.ProjectInfo projectInfo = event.getProject();
            String projectKey = projectInfo.getPathWithNamespace();

            log.info("Auto-initializing project from webhook: projectKey={}", projectKey);

            // 1. Create SYNC_PROJECT
            SyncProject project = new SyncProject();
            project.setProjectKey(projectKey);
            project.setSyncMethod("pull_sync");
            project.setSyncStatus("pending");
            project.setEnabled(true);
            project.setCreatedAt(LocalDateTime.now());
            project.setUpdatedAt(LocalDateTime.now());
            syncProjectMapper.insert(project);

            // 2. Create SOURCE_PROJECT_INFO
            SourceProjectInfo sourceInfo = new SourceProjectInfo();
            sourceInfo.setSyncProjectId(project.getId());
            sourceInfo.setGitlabProjectId(projectInfo.getId());
            sourceInfo.setPathWithNamespace(projectKey);
            sourceInfo.setName(projectInfo.getName());
            sourceInfo.setVisibility(projectInfo.getVisibility());
            sourceInfo.setDefaultBranch(projectInfo.getDefaultBranch());
            // Note: Other fields like emptyRepo, archived, URLs will be populated during full discovery
            sourceProjectInfoMapper.insert(sourceInfo);

            // 3. Create PULL_SYNC_CONFIG
            PullSyncConfig config = new PullSyncConfig();
            config.setSyncProjectId(project.getId());
            config.setPriority("normal");
            config.setEnabled(true);
            config.setLocalRepoPath(generateLocalRepoPath(projectKey));
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            pullSyncConfigMapper.insert(config);

            // 4. Create SYNC_TASK
            SyncTask task = new SyncTask();
            task.setSyncProjectId(project.getId());
            task.setTaskType("pull");
            task.setTaskStatus("waiting");
            task.setNextRunAt(Instant.now()); // Immediate execution
            task.setConsecutiveFailures(0);
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            syncTaskMapper.insert(task);

            log.info("Project auto-initialized successfully: projectKey={}, projectId={}, priority={}", 
                    projectKey, project.getId(), config.getPriority());

            return project;

        } catch (Exception e) {
            log.error("Failed to initialize project from webhook", e);
            throw new RuntimeException("Failed to initialize project", e);
        }
    }

    /**
     * Generate local repository path
     *
     * @param projectKey Project key
     * @return Local repo path
     */
    private String generateLocalRepoPath(String projectKey) {
        // Default base path (should come from config in production)
        String basePath = System.getProperty("user.home") + "/.gitlab-sync/repos";
        return basePath + "/" + projectKey;
    }

    /**
     * Update source project info with latest commit from webhook
     *
     * @param project Sync project
     * @param event GitLab push event
     */
    private void updateSourceProjectInfo(SyncProject project, GitLabPushEvent event) {
        try {
            // Find source project info by sync_project_id
            SourceProjectInfo info = sourceProjectInfoMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SourceProjectInfo>()
                            .eq(SourceProjectInfo::getSyncProjectId, project.getId())
            ).stream().findFirst().orElse(null);

            if (info == null) {
                log.warn("[WEBHOOK] Source project info not found for projectKey: {}", project.getProjectKey());
                return;
            }

            // Update latest commit SHA
            if (event.getAfter() != null && !event.getAfter().equals("0000000000000000000000000000000000000000")) {
                info.setLatestCommitSha(event.getAfter());
            }

            // Update last activity time to now
            info.setLastActivityAt(LocalDateTime.now());

            // Update default branch if available
            if (event.getProject() != null && event.getProject().getDefaultBranch() != null) {
                info.setDefaultBranch(event.getProject().getDefaultBranch());
            }

            // Save to database
            sourceProjectInfoMapper.updateById(info);

            log.info("[WEBHOOK] Updated source project info: projectKey={}, latestCommit={}",
                    project.getProjectKey(),
                    event.getAfter() != null ? event.getAfter().substring(0, 8) : "null");

        } catch (Exception e) {
            log.error("[WEBHOOK] Failed to update source project info: {}", e.getMessage(), e);
        }
    }

    /**
     * Update target project info with latest commit from webhook
     *
     * @param project Sync project
     * @param event GitLab push event
     */
    private void updateTargetProjectInfo(SyncProject project, GitLabPushEvent event) {
        try {
            // Find target project info by sync_project_id
            TargetProjectInfo info = targetProjectInfoMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TargetProjectInfo>()
                            .eq(TargetProjectInfo::getSyncProjectId, project.getId())
            ).stream().findFirst().orElse(null);

            if (info == null) {
                log.debug("[WEBHOOK] Target project info not found for projectKey: {}", project.getProjectKey());
                return;
            }

            // Update latest commit SHA
            if (event.getAfter() != null && !event.getAfter().equals("0000000000000000000000000000000000000000")) {
                info.setLatestCommitSha(event.getAfter());
            }

            // Update last activity time to now
            info.setLastActivityAt(LocalDateTime.now());

            // Update default branch if available
            if (event.getProject() != null && event.getProject().getDefaultBranch() != null) {
                info.setDefaultBranch(event.getProject().getDefaultBranch());
            }

            // Save to database
            targetProjectInfoMapper.updateById(info);

            log.info("[WEBHOOK] Updated target project info: projectKey={}, latestCommit={}",
                    project.getProjectKey(),
                    event.getAfter() != null ? event.getAfter().substring(0, 8) : "null");

        } catch (Exception e) {
            log.error("[WEBHOOK] Failed to update target project info: {}", e.getMessage(), e);
        }
    }

    /**
     * Record webhook event
     *
     * @param project   Sync project
     * @param event     GitLab push event
     * @param status    Event status
     * @param message   Event message
     */
    private void recordWebhookEvent(SyncProject project, GitLabPushEvent event,
                                     String status, String message) {
        try {
            SyncEvent syncEvent = new SyncEvent();
            syncEvent.setSyncProjectId(project.getId());
            syncEvent.setEventType("webhook_push");
            syncEvent.setEventSource(SyncEvent.EventSource.WEBHOOK);
            syncEvent.setStatus(status);
            syncEvent.setRef(event.getRef());
            syncEvent.setCommitSha(event.getAfter());
            syncEvent.setErrorMessage(message);
            syncEvent.setEventTime(LocalDateTime.now());

            // Store additional details in eventData
            if (event.getTotalCommitsCount() != null && event.getTotalCommitsCount() > 0) {
                java.util.Map<String, Object> eventData = new java.util.HashMap<>();
                eventData.put("commits_count", event.getTotalCommitsCount());
                eventData.put("user", event.getUserUsername());
                eventData.put("ref", event.getRef());
                syncEvent.setEventData(eventData);
            }

            syncEventMapper.insert(syncEvent);

            log.debug("Webhook event recorded: projectKey={}, status={}",
                    project.getProjectKey(), status);

        } catch (Exception e) {
            log.warn("Failed to record webhook event", e);
        }
    }
}
