package com.gitlab.mirror.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gitlab.mirror.common.model.RemoteMirror;
import com.gitlab.mirror.server.client.GitLabApiClient;
import com.gitlab.mirror.server.client.GitLabClientException;
import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import com.gitlab.mirror.server.entity.PushMirrorConfig;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncEvent;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import com.gitlab.mirror.server.mapper.PushMirrorConfigMapper;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.SyncEventMapper;
import com.gitlab.mirror.server.mapper.TargetProjectInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Push Mirror Management Service
 *
 * 负责配置和监控Push Mirror
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class PushMirrorManagementService {

    private static final int BATCH_SIZE = 5;
    private static final int MAX_RETRY_COUNT = 3;

    private final GitLabApiClient sourceGitLabApiClient;
    private final GitLabApiClient targetGitLabApiClient;
    private final SourceProjectInfoMapper sourceProjectInfoMapper;
    private final TargetProjectInfoMapper targetProjectInfoMapper;
    private final PushMirrorConfigMapper pushMirrorConfigMapper;
    private final SyncEventMapper syncEventMapper;
    private final GitLabMirrorProperties properties;
    private final TargetProjectManagementService targetProjectManagementService;
    private final ExecutorService executorService;

    public PushMirrorManagementService(
            @Qualifier("sourceGitLabApiClient") GitLabApiClient sourceGitLabApiClient,
            @Qualifier("targetGitLabApiClient") GitLabApiClient targetGitLabApiClient,
            SourceProjectInfoMapper sourceProjectInfoMapper,
            TargetProjectInfoMapper targetProjectInfoMapper,
            PushMirrorConfigMapper pushMirrorConfigMapper,
            SyncEventMapper syncEventMapper,
            GitLabMirrorProperties properties,
            TargetProjectManagementService targetProjectManagementService) {
        this.sourceGitLabApiClient = sourceGitLabApiClient;
        this.targetGitLabApiClient = targetGitLabApiClient;
        this.sourceProjectInfoMapper = sourceProjectInfoMapper;
        this.targetProjectInfoMapper = targetProjectInfoMapper;
        this.pushMirrorConfigMapper = pushMirrorConfigMapper;
        this.syncEventMapper = syncEventMapper;
        this.properties = properties;
        this.targetProjectManagementService = targetProjectManagementService;
        this.executorService = Executors.newFixedThreadPool(BATCH_SIZE);
    }

    /**
     * 配置Push Mirror
     *
     * @param syncProjectId 同步项目ID
     * @return Push Mirror配置
     */
    @Transactional
    public PushMirrorConfig configureMirror(Long syncProjectId) {
        log.info("Configuring push mirror for syncProjectId={}", syncProjectId);

        // 1. 获取源项目和目标项目信息
        SourceProjectInfo sourceInfo = getSourceProjectInfo(syncProjectId);
        TargetProjectInfo targetInfo = getTargetProjectInfo(syncProjectId);

        if (sourceInfo == null) {
            throw new IllegalArgumentException("Source project info not found for syncProjectId: " + syncProjectId);
        }
        if (targetInfo == null) {
            throw new IllegalArgumentException("Target project info not found for syncProjectId: " + syncProjectId);
        }

        // 2. 检查是否已存在Mirror配置
        PushMirrorConfig existingConfig = pushMirrorConfigMapper.selectBySyncProjectId(syncProjectId);
        if (existingConfig != null && existingConfig.getGitlabMirrorId() != null) {
            log.info("Push mirror already configured: mirrorId={}", existingConfig.getGitlabMirrorId());
            return existingConfig;
        }

        // 3. 构建Mirror URL
        String mirrorUrl = buildMirrorUrl(targetInfo.getPathWithNamespace());

        // 4. 创建Mirror配置记录
        PushMirrorConfig config = existingConfig != null ? existingConfig : new PushMirrorConfig();
        config.setSyncProjectId(syncProjectId);
        config.setMirrorUrl(mirrorUrl);  // Store URL without token for security
        config.setConsecutiveFailures(0);

        if (config.getId() == null) {
            try {
                pushMirrorConfigMapper.insert(config);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发情况下可能已经被其他线程创建了，重新查询
                log.warn("Duplicate mirror config detected for syncProjectId={}, reloading from database", syncProjectId);
                PushMirrorConfig reloadedConfig = pushMirrorConfigMapper.selectBySyncProjectId(syncProjectId);
                if (reloadedConfig != null && reloadedConfig.getGitlabMirrorId() != null) {
                    // 已经配置完成，直接返回
                    log.info("Mirror already configured by another thread: mirrorId={}", reloadedConfig.getGitlabMirrorId());
                    return reloadedConfig;
                }
                // 如果记录存在但未配置完成，使用已存在的记录继续
                config = reloadedConfig != null ? reloadedConfig : config;
            }
        } else {
            pushMirrorConfigMapper.updateById(config);
        }

        try {
            // 5. 调用GitLab API创建Push Mirror
            String mirrorUrlWithToken = buildMirrorUrlWithToken(targetInfo.getPathWithNamespace());
            RemoteMirror mirror = sourceGitLabApiClient.createMirror(
                    sourceInfo.getGitlabProjectId(),
                    mirrorUrlWithToken,
                    false  // Don't restrict to protected branches only
            );

            // 6. 更新Mirror配置
            config.setGitlabMirrorId(mirror.getId());
            config.setLastUpdateStatus(mirror.getUpdateStatus());
            if (mirror.getLastUpdateAt() != null) {
                config.setLastUpdateAt(mirror.getLastUpdateAt().toLocalDateTime());
            }
            pushMirrorConfigMapper.updateById(config);

            // 7. 触发首次同步
            try {
                sourceGitLabApiClient.triggerMirrorSync(sourceInfo.getGitlabProjectId(), mirror.getId());
                log.info("Triggered initial mirror sync for mirrorId={}", mirror.getId());
            } catch (Exception e) {
                log.warn("Failed to trigger initial sync, will sync on next push", e);
            }

            // 8. 记录配置成功事件
            recordMirrorEvent(syncProjectId, "mirror_configured", "success",
                    Map.of("mirror_id", mirror.getId(),
                           "mirror_url", mirrorUrl));

            log.info("Push mirror configured successfully: mirrorId={}", mirror.getId());
            return config;

        } catch (GitLabClientException e) {
            // 9. 处理配置失败
            handleConfigurationFailure(config, e);
            throw new RuntimeException("Failed to configure push mirror for syncProjectId: " + syncProjectId, e);
        }
    }

    /**
     * 批量配置Push Mirror
     *
     * @param syncProjectIds 同步项目ID列表
     * @return 配置结果列表
     */
    public List<PushMirrorConfig> batchConfigureMirrors(List<Long> syncProjectIds) {
        log.info("Batch configuring {} push mirrors", syncProjectIds.size());

        List<CompletableFuture<PushMirrorConfig>> futures = syncProjectIds.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return configureMirror(id);
                    } catch (Exception e) {
                        log.error("Failed to configure mirror for syncProjectId={}", id, e);
                        return null;
                    }
                }, executorService))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 定时轮询Mirror状态
     * 每2分钟执行一次
     */
    @Scheduled(fixedDelayString = "${gitlab.mirror.sync.status-check-interval:120000}")
    public void scheduledStatusPolling() {
        if (!properties.getSync().getEnabled()) {
            log.debug("Sync is disabled, skipping mirror status polling");
            return;
        }

        log.info("Starting scheduled mirror status polling...");
        try {
            List<PushMirrorConfig> configs = pushMirrorConfigMapper.selectList(null);
            log.info("Polling status for {} mirrors", configs.size());

            int updated = 0;
            for (PushMirrorConfig config : configs) {
                try {
                    boolean statusChanged = pollMirrorStatus(config.getSyncProjectId());
                    if (statusChanged) {
                        updated++;
                    }
                } catch (Exception e) {
                    log.error("Failed to poll mirror status for syncProjectId={}", config.getSyncProjectId(), e);
                }
            }

            log.info("Scheduled mirror status polling completed: {} status changes detected", updated);
        } catch (Exception e) {
            log.error("Scheduled mirror status polling failed", e);
        }
    }

    /**
     * 轮询单个Mirror状态
     *
     * @param syncProjectId 同步项目ID
     * @return true if status changed
     */
    @Transactional
    public boolean pollMirrorStatus(Long syncProjectId) {
        log.debug("Polling mirror status for syncProjectId={}", syncProjectId);

        PushMirrorConfig config = pushMirrorConfigMapper.selectBySyncProjectId(syncProjectId);
        if (config == null || config.getGitlabMirrorId() == null) {
            log.warn("No mirror config found for syncProjectId={}", syncProjectId);
            return false;
        }

        SourceProjectInfo sourceInfo = getSourceProjectInfo(syncProjectId);
        if (sourceInfo == null) {
            log.warn("No source project info found for syncProjectId={}", syncProjectId);
            return false;
        }

        try {
            // 查询Mirror状态
            RemoteMirror mirror = sourceGitLabApiClient.getMirror(
                    sourceInfo.getGitlabProjectId(),
                    config.getGitlabMirrorId()
            );

            String oldStatus = config.getLastUpdateStatus();
            String newStatus = mirror.getUpdateStatus();
            boolean statusChanged = !Objects.equals(oldStatus, newStatus);

            // 更新配置
            config.setLastUpdateStatus(newStatus);

            // 优先使用last_update_at，如果为空则使用last_update_started_at
            if (mirror.getLastUpdateAt() != null) {
                config.setLastUpdateAt(mirror.getLastUpdateAt().toLocalDateTime());
            } else if (mirror.getLastUpdateStartedAt() != null) {
                config.setLastUpdateAt(mirror.getLastUpdateStartedAt().toLocalDateTime());
            }

            if (mirror.getLastSuccessfulUpdateAt() != null) {
                config.setLastSuccessfulUpdateAt(mirror.getLastSuccessfulUpdateAt().toLocalDateTime());
            }

            // 检查错误信息，判断是否需要创建目标项目
            String errorMessage = mirror.getLastError();
            boolean needsTargetProject = false;

            if (errorMessage != null && (
                errorMessage.contains("not found") ||
                errorMessage.contains("could not be found") ||
                errorMessage.contains("you don't have permission"))) {

                log.warn("Mirror sync failed due to missing target project: syncProjectId={}", syncProjectId);
                needsTargetProject = true;

                // 检查目标项目状态
                TargetProjectInfo targetInfo = getTargetProjectInfo(syncProjectId);
                if (targetInfo != null && !TargetProjectInfo.Status.CREATED.equals(targetInfo.getStatus())) {
                    log.info("Target project exists but status is {}, will attempt to create", targetInfo.getStatus());
                    try {
                        // 尝试创建目标项目
                        targetProjectManagementService.createTargetProject(syncProjectId);
                        log.info("Successfully created target project for syncProjectId={}", syncProjectId);

                        // 创建成功后触发一次同步
                        sourceGitLabApiClient.triggerMirrorSync(
                            sourceInfo.getGitlabProjectId(),
                            config.getGitlabMirrorId()
                        );
                        log.info("Triggered mirror sync after creating target project");

                        recordMirrorEvent(syncProjectId, "target_project_created", "success",
                            Map.of("action", "auto_created_on_mirror_error"));
                    } catch (Exception ex) {
                        log.error("Failed to auto-create target project for syncProjectId={}", syncProjectId, ex);
                        recordMirrorEvent(syncProjectId, "target_project_create_failed", "error",
                            Map.of("error", ex.getMessage()));
                    }
                }
            }

            // 更新连续失败计数和错误信息
            if (PushMirrorConfig.UpdateStatus.FAILED.equals(newStatus)) {
                config.setConsecutiveFailures(
                        config.getConsecutiveFailures() != null ? config.getConsecutiveFailures() + 1 : 1
                );
                config.setErrorMessage(errorMessage);
            } else if (PushMirrorConfig.UpdateStatus.FINISHED.equals(newStatus)) {
                config.setConsecutiveFailures(0);
                config.setErrorMessage(null);
            } else {
                // 对于其他状态（to_retry, started等），也保存错误信息（如果有）
                if (errorMessage != null && !errorMessage.isEmpty()) {
                    config.setErrorMessage(errorMessage);
                }
            }

            pushMirrorConfigMapper.updateById(config);

            // 记录状态变化事件
            if (statusChanged) {
                recordMirrorEvent(syncProjectId, "mirror_status_changed", "info",
                        Map.of("old_status", oldStatus != null ? oldStatus : "null",
                               "new_status", newStatus,
                               "consecutive_failures", config.getConsecutiveFailures()));
                log.info("Mirror status changed: syncProjectId={}, {} -> {}", syncProjectId, oldStatus, newStatus);
            }

            return statusChanged;

        } catch (Exception e) {
            log.error("Failed to poll mirror status for syncProjectId={}", syncProjectId, e);
            return false;
        }
    }

    /**
     * 批量轮询Mirror状态
     *
     * @param syncProjectIds 同步项目ID列表
     * @return 状态变化的数量
     */
    public int batchPollMirrorStatus(List<Long> syncProjectIds) {
        log.info("Batch polling {} mirror statuses", syncProjectIds.size());

        List<CompletableFuture<Boolean>> futures = syncProjectIds.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return pollMirrorStatus(id);
                    } catch (Exception e) {
                        log.error("Failed to poll mirror status for syncProjectId={}", id, e);
                        return false;
                    }
                }, executorService))
                .collect(Collectors.toList());

        return (int) futures.stream()
                .map(CompletableFuture::join)
                .filter(changed -> changed)
                .count();
    }

    /**
     * 构建Mirror URL（不包含Token，用于数据库存储）
     */
    private String buildMirrorUrl(String pathWithNamespace) {
        // Use mirrorUrl if configured, otherwise fall back to url
        String targetUrl = properties.getTarget().getMirrorUrl() != null
            ? properties.getTarget().getMirrorUrl()
            : properties.getTarget().getUrl();

        // Remove trailing slash if present
        if (targetUrl.endsWith("/")) {
            targetUrl = targetUrl.substring(0, targetUrl.length() - 1);
        }
        return String.format("%s/%s.git", targetUrl, pathWithNamespace);
    }

    /**
     * 构建Mirror URL（包含Token，用于API调用）
     */
    private String buildMirrorUrlWithToken(String pathWithNamespace) {
        // Use mirrorUrl if configured, otherwise fall back to url
        String targetUrl = properties.getTarget().getMirrorUrl() != null
            ? properties.getTarget().getMirrorUrl()
            : properties.getTarget().getUrl();
        String token = properties.getTarget().getToken();

        // Remove trailing slash
        if (targetUrl.endsWith("/")) {
            targetUrl = targetUrl.substring(0, targetUrl.length() - 1);
        }

        // Extract protocol and host
        String protocol;
        String host;
        if (targetUrl.startsWith("https://")) {
            protocol = "https://";
            host = targetUrl.substring(8);
        } else if (targetUrl.startsWith("http://")) {
            protocol = "http://";
            host = targetUrl.substring(7);
        } else {
            throw new IllegalArgumentException("Invalid target URL: " + targetUrl);
        }

        // Build URL with token: https://oauth2:TOKEN@gitlab.example.com/group/project.git
        return String.format("%soauth2:%s@%s/%s.git",
                protocol,
                URLEncoder.encode(token, StandardCharsets.UTF_8),
                host,
                pathWithNamespace);
    }

    /**
     * 处理配置失败
     */
    private void handleConfigurationFailure(PushMirrorConfig config, Exception e) {
        config.setErrorMessage(e.getMessage());
        config.setConsecutiveFailures(
                config.getConsecutiveFailures() != null ? config.getConsecutiveFailures() + 1 : 1
        );
        pushMirrorConfigMapper.updateById(config);

        recordMirrorEvent(config.getSyncProjectId(), "mirror_configuration_failed", "error",
                Map.of("error", e.getMessage(),
                       "consecutive_failures", config.getConsecutiveFailures()));

        log.error("Mirror configuration failed: syncProjectId={}, consecutiveFailures={}",
                config.getSyncProjectId(), config.getConsecutiveFailures(), e);
    }

    /**
     * 手动触发Mirror同步
     *
     * @param syncProjectId 同步项目ID
     */
    public void triggerSync(Long syncProjectId) {
        log.info("Manually triggering mirror sync for syncProjectId={}", syncProjectId);

        PushMirrorConfig config = pushMirrorConfigMapper.selectBySyncProjectId(syncProjectId);
        if (config == null || config.getGitlabMirrorId() == null) {
            throw new IllegalArgumentException("Mirror not configured for syncProjectId: " + syncProjectId);
        }

        SourceProjectInfo sourceInfo = getSourceProjectInfo(syncProjectId);
        if (sourceInfo == null) {
            throw new IllegalArgumentException("Source project info not found for syncProjectId: " + syncProjectId);
        }

        try {
            sourceGitLabApiClient.triggerMirrorSync(sourceInfo.getGitlabProjectId(), config.getGitlabMirrorId());
            log.info("Successfully triggered mirror sync: syncProjectId={}, mirrorId={}",
                    syncProjectId, config.getGitlabMirrorId());

            recordMirrorEvent(syncProjectId, "mirror_sync_triggered", "success",
                    Map.of("mirror_id", config.getGitlabMirrorId()));
        } catch (Exception e) {
            log.error("Failed to trigger mirror sync: syncProjectId={}", syncProjectId, e);
            recordMirrorEvent(syncProjectId, "mirror_sync_failed", "error",
                    Map.of("error", e.getMessage()));
            throw new RuntimeException("Failed to trigger mirror sync: " + e.getMessage(), e);
        }
    }

    /**
     * 获取源项目信息
     */
    private SourceProjectInfo getSourceProjectInfo(Long syncProjectId) {
        QueryWrapper<SourceProjectInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("sync_project_id", syncProjectId);
        return sourceProjectInfoMapper.selectOne(wrapper);
    }

    /**
     * 获取目标项目信息
     */
    private TargetProjectInfo getTargetProjectInfo(Long syncProjectId) {
        QueryWrapper<TargetProjectInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("sync_project_id", syncProjectId);
        return targetProjectInfoMapper.selectOne(wrapper);
    }

    /**
     * 记录Mirror事件
     */
    private void recordMirrorEvent(Long syncProjectId, String eventType, String status, Map<String, Object> data) {
        SyncEvent event = new SyncEvent();
        event.setSyncProjectId(syncProjectId);
        event.setEventType(eventType);
        event.setEventSource("mirror_service");
        event.setStatus(status);
        event.setEventTime(LocalDateTime.now());
        event.setEventData(new HashMap<>(data));
        syncEventMapper.insert(event);
    }
}
