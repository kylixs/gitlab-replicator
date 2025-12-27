package com.gitlab.mirror.server.controller;

import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.mapper.SyncTaskMapper;
import com.gitlab.mirror.server.service.ProjectInitializationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Webhook Controller
 * <p>
 * Receives GitLab webhook events and triggers fast sync
 * <p>
 * **No authentication required** - webhook endpoint is public
 * **No event storage** - events are processed immediately
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final SyncProjectMapper syncProjectMapper;
    private final SyncTaskMapper syncTaskMapper;
    private final ProjectInitializationService projectInitializationService;

    /**
     * Handle GitLab webhook events (Push/Tag Push)
     * <p>
     * Endpoint: POST /api/webhooks/gitlab
     * <p>
     * Supported events:
     * - Push Hook (commits, branch create/delete)
     * - Tag Push Hook (tag create/delete)
     *
     * @param eventType GitLab event type from X-Gitlab-Event header
     * @param payload   Webhook payload (raw Map)
     * @return 200 OK with processing result
     */
    @PostMapping("/gitlab")
    @Transactional
    public ResponseEntity<?> handleGitLabWebhook(
            @RequestHeader(value = "X-Gitlab-Event", required = false) String eventType,
            @RequestBody Map<String, Object> payload
    ) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("📥 Webhook received: event={}", eventType);
            log.debug("Webhook payload keys: {}", payload.keySet());

            // Extract project information
            @SuppressWarnings("unchecked")
            Map<String, Object> project = (Map<String, Object>) payload.get("project");
            if (project == null) {
                log.warn("⚠️ Webhook missing project field, ignoring");
                return ResponseEntity.ok(Map.of("status", "ignored", "reason", "no_project"));
            }

            String projectPath = (String) project.get("path_with_namespace");
            String ref = (String) payload.get("ref");
            String beforeSha = (String) payload.get("before");
            String afterSha = (String) payload.get("after");

            log.info("📦 Project: {}, Ref: {}, Before: {}, After: {}",
                    projectPath,
                    ref,
                    beforeSha != null ? beforeSha.substring(0, Math.min(8, beforeSha.length())) : "null",
                    afterSha != null ? afterSha.substring(0, Math.min(8, afterSha.length())) : "null"
            );

            // Find or create sync project
            SyncProject syncProject = syncProjectMapper.selectByProjectKey(projectPath);
            boolean isNewProject = false;
            if (syncProject == null) {
                log.info("🆕 New project discovered via webhook: {}", projectPath);
                try {
                    Long syncProjectId = projectInitializationService.initializeProjectByPath(projectPath);
                    syncProject = syncProjectMapper.selectById(syncProjectId);
                    isNewProject = true;
                    log.info("✅ Project initialized successfully: {}, syncProjectId={}", projectPath, syncProjectId);
                } catch (Exception e) {
                    log.error("❌ Failed to initialize new project {}: {}", projectPath, e.getMessage(), e);
                    return ResponseEntity.ok(Map.of(
                            "status", "error",
                            "reason", "initialization_failed",
                            "message", e.getMessage(),
                            "project", projectPath
                    ));
                }
            }

            // Detect event type
            String changeType = detectChangeType(eventType, beforeSha, afterSha);
            log.info("🔄 Change type: {} for project: {}", changeType, projectPath);

            // Trigger fast sync (for new projects, initialization already created WAITING task)
            boolean triggered = isNewProject || triggerFastSync(syncProject.getId(), projectPath);

            long duration = System.currentTimeMillis() - startTime;
            log.info("✅ Webhook processed: project={}, triggered={}, duration={}ms",
                    projectPath, triggered, duration);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "triggered", triggered,
                    "project", projectPath,
                    "change_type", changeType,
                    "duration_ms", duration
            ));

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("❌ Webhook processing error: duration={}ms", duration, e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", e.getMessage(),
                    "duration_ms", duration
            ));
        }
    }

    /**
     * Trigger fast sync for project
     * <p>
     * Updates sync_task: next_run_at=NOW, trigger_source='webhook'
     * Scheduler will pick it up within 5 seconds
     *
     * @param syncProjectId Sync project ID
     * @param projectPath   Project path (for logging)
     * @return true if triggered, false if already running
     */
    private boolean triggerFastSync(Long syncProjectId, String projectPath) {
        // Find sync task
        SyncTask task = syncTaskMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SyncTask>()
                        .eq("sync_project_id", syncProjectId)
        );

        if (task == null) {
            log.warn("⚠️ No sync task found for project {}", projectPath);
            return false;
        }

        // Skip if already running
        if (SyncTask.TaskStatus.RUNNING.equals(task.getTaskStatus())) {
            log.info("⏳ Task already running for project {}, skipping trigger", projectPath);
            return false;
        }

        // Update task to run immediately (even if already WAITING, prioritize webhook trigger)
        task.setTriggerSource(SyncTask.TriggerSource.WEBHOOK);
        task.setNextRunAt(Instant.now()); // Run NOW
        task.setTaskStatus(SyncTask.TaskStatus.WAITING);

        syncTaskMapper.updateById(task);

        log.info("🚀 Fast sync triggered: taskId={}, project={}, nextRunAt=NOW",
                task.getId(), projectPath);

        return true;
    }

    /**
     * Detect change type from webhook event
     */
    private String detectChangeType(String eventType, String beforeSha, String afterSha) {
        if ("Tag Push Hook".equals(eventType)) {
            if (afterSha != null && afterSha.matches("0+")) {
                return "tag_delete";
            } else {
                return "tag_push";
            }
        }

        // Push Hook
        if (beforeSha != null && beforeSha.matches("0+")) {
            return "branch_create";
        } else if (afterSha != null && afterSha.matches("0+")) {
            return "branch_delete";
        } else {
            return "commit_push";
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "webhook",
                "version", "1.0"
        ));
    }
}
