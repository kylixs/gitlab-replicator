package com.gitlab.mirror.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gitlab.mirror.common.model.GitLabGroup;
import com.gitlab.mirror.common.model.GitLabProject;
import com.gitlab.mirror.server.client.GitLabApiClient;
import com.gitlab.mirror.server.client.GitLabClientException;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncEvent;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.SyncEventMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.mapper.TargetProjectInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Target Project Management Service
 *
 * 负责在目标GitLab创建和管理项目
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class TargetProjectManagementService {

    private static final int MAX_RETRY_COUNT = 3;
    private static final int BATCH_SIZE = 10;

    private final GitLabApiClient targetGitLabApiClient;
    private final SyncProjectMapper syncProjectMapper;
    private final SourceProjectInfoMapper sourceProjectInfoMapper;
    private final TargetProjectInfoMapper targetProjectInfoMapper;
    private final SyncEventMapper syncEventMapper;
    private final ExecutorService executorService;

    public TargetProjectManagementService(
            @Qualifier("targetGitLabApiClient") GitLabApiClient targetGitLabApiClient,
            SyncProjectMapper syncProjectMapper,
            SourceProjectInfoMapper sourceProjectInfoMapper,
            TargetProjectInfoMapper targetProjectInfoMapper,
            SyncEventMapper syncEventMapper) {
        this.targetGitLabApiClient = targetGitLabApiClient;
        this.syncProjectMapper = syncProjectMapper;
        this.sourceProjectInfoMapper = sourceProjectInfoMapper;
        this.targetProjectInfoMapper = targetProjectInfoMapper;
        this.syncEventMapper = syncEventMapper;
        this.executorService = Executors.newFixedThreadPool(BATCH_SIZE);
    }

    /**
     * 为同步项目创建目标项目
     *
     * @param syncProjectId 同步项目ID
     * @return 创建结果
     */
    @Transactional
    public TargetProjectInfo createTargetProject(Long syncProjectId) {
        log.info("Creating target project for syncProjectId={}", syncProjectId);

        // 1. 获取源项目信息
        SourceProjectInfo sourceInfo = getSourceProjectInfo(syncProjectId);
        if (sourceInfo == null) {
            throw new IllegalArgumentException("Source project info not found for syncProjectId: " + syncProjectId);
        }

        // 2. 检查是否已存在目标项目记录
        TargetProjectInfo existingTarget = getTargetProjectInfo(syncProjectId);
        if (existingTarget != null) {
            if (TargetProjectInfo.Status.CREATED.equals(existingTarget.getStatus()) ||
                TargetProjectInfo.Status.READY.equals(existingTarget.getStatus())) {
                log.info("Target project already exists: {}", existingTarget.getPathWithNamespace());
                return existingTarget;
            }
            // 如果状态是ERROR或CREATING，继续重试
            log.info("Retrying target project creation, current status: {}", existingTarget.getStatus());
        }

        // 3. 创建目标项目记录（状态：creating）
        TargetProjectInfo targetInfo = existingTarget != null ? existingTarget : new TargetProjectInfo();
        targetInfo.setSyncProjectId(syncProjectId);
        targetInfo.setPathWithNamespace(sourceInfo.getPathWithNamespace());
        targetInfo.setName(sourceInfo.getName());
        targetInfo.setVisibility(sourceInfo.getVisibility());
        targetInfo.setStatus(TargetProjectInfo.Status.CREATING);
        targetInfo.setRetryCount(targetInfo.getRetryCount() != null ? targetInfo.getRetryCount() + 1 : 1);

        if (targetInfo.getId() == null) {
            targetProjectInfoMapper.insert(targetInfo);
        } else {
            targetProjectInfoMapper.updateById(targetInfo);
        }

        try {
            // 4. 确保目标分组结构存在
            String groupPath = sourceInfo.getGroupPath();
            if (groupPath != null && !groupPath.isEmpty()) {
                ensureGroupHierarchy(groupPath);
            }

            // 5. 创建目标项目
            String projectPath = extractProjectPath(sourceInfo.getPathWithNamespace());
            GitLabProject createdProject = targetGitLabApiClient.createProject(
                    projectPath,
                    sourceInfo.getName(),
                    groupPath
            );

            // 6. 更新目标项目信息（状态：created）
            targetInfo.setGitlabProjectId(createdProject.getId());
            targetInfo.setStatus(TargetProjectInfo.Status.CREATED);
            targetInfo.setErrorMessage(null);
            targetInfo.setLastCheckedAt(LocalDateTime.now());
            targetProjectInfoMapper.updateById(targetInfo);

            // 7. 记录创建成功事件
            recordTargetProjectEvent(syncProjectId, "target_project_created", "success",
                    Map.of("project_path", targetInfo.getPathWithNamespace(),
                           "gitlab_project_id", createdProject.getId()));

            log.info("Target project created successfully: {}", targetInfo.getPathWithNamespace());
            return targetInfo;

        } catch (GitLabClientException e) {
            // 8. 处理创建失败
            handleCreationFailure(targetInfo, e);
            throw new RuntimeException("Failed to create target project: " + sourceInfo.getPathWithNamespace(), e);
        }
    }

    /**
     * 批量创建目标项目
     *
     * @param syncProjectIds 同步项目ID列表
     * @return 创建结果列表
     */
    public List<TargetProjectInfo> batchCreateTargetProjects(List<Long> syncProjectIds) {
        log.info("Batch creating {} target projects", syncProjectIds.size());

        List<CompletableFuture<TargetProjectInfo>> futures = syncProjectIds.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return createTargetProject(id);
                    } catch (Exception e) {
                        log.error("Failed to create target project for syncProjectId={}", id, e);
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
     * 检查目标项目状态
     *
     * @param syncProjectId 同步项目ID
     * @return 目标项目信息
     */
    @Transactional
    public TargetProjectInfo checkTargetProjectStatus(Long syncProjectId) {
        log.debug("Checking target project status for syncProjectId={}", syncProjectId);

        TargetProjectInfo targetInfo = getTargetProjectInfo(syncProjectId);
        if (targetInfo == null) {
            log.warn("No target project info found for syncProjectId={}", syncProjectId);
            return null;
        }

        try {
            // 检查目标项目是否存在
            boolean exists = targetGitLabApiClient.projectExists(targetInfo.getPathWithNamespace());

            String oldStatus = targetInfo.getStatus();
            if (exists) {
                // 项目存在，更新状态为READY
                if (!TargetProjectInfo.Status.READY.equals(oldStatus)) {
                    targetInfo.setStatus(TargetProjectInfo.Status.READY);
                    targetInfo.setErrorMessage(null);
                }
            } else {
                // 项目不存在，标记为DELETED
                if (!TargetProjectInfo.Status.DELETED.equals(oldStatus)) {
                    targetInfo.setStatus(TargetProjectInfo.Status.DELETED);
                    targetInfo.setErrorMessage("Project was deleted from target GitLab");
                    recordTargetProjectEvent(syncProjectId, "target_project_deleted", "warning",
                            Map.of("project_path", targetInfo.getPathWithNamespace()));
                }
            }

            targetInfo.setLastCheckedAt(LocalDateTime.now());
            targetProjectInfoMapper.updateById(targetInfo);

            return targetInfo;

        } catch (Exception e) {
            log.error("Failed to check target project status for syncProjectId={}", syncProjectId, e);
            targetInfo.setErrorMessage(e.getMessage());
            targetInfo.setLastCheckedAt(LocalDateTime.now());
            targetProjectInfoMapper.updateById(targetInfo);
            return targetInfo;
        }
    }

    /**
     * 批量检查目标项目状态
     *
     * @param syncProjectIds 同步项目ID列表
     * @return 检查结果列表
     */
    public List<TargetProjectInfo> batchCheckTargetProjectStatus(List<Long> syncProjectIds) {
        log.info("Batch checking {} target project statuses", syncProjectIds.size());

        List<CompletableFuture<TargetProjectInfo>> futures = syncProjectIds.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return checkTargetProjectStatus(id);
                    } catch (Exception e) {
                        log.error("Failed to check target project status for syncProjectId={}", id, e);
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
     * 确保分组层级结构存在
     *
     * @param groupPath 完整分组路径 (例如: "group1/subgroup1/subgroup2")
     */
    private void ensureGroupHierarchy(String groupPath) {
        log.debug("Ensuring group hierarchy for: {}", groupPath);

        String[] parts = groupPath.split("/");
        String currentPath = "";

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            String parentPath = currentPath.isEmpty() ? null : currentPath;
            currentPath = currentPath.isEmpty() ? part : currentPath + "/" + part;

            // 检查分组是否存在
            if (!targetGitLabApiClient.groupExists(currentPath)) {
                // 创建分组
                String groupName = part.substring(0, 1).toUpperCase() + part.substring(1);
                try {
                    GitLabGroup createdGroup = targetGitLabApiClient.createGroup(part, groupName, parentPath);
                    log.info("Created target group: path={}, id={}", currentPath, createdGroup.getId());
                } catch (GitLabClientException e) {
                    // 可能是并发创建导致的冲突，再次检查
                    if (targetGitLabApiClient.groupExists(currentPath)) {
                        log.info("Group already exists (created by another process): {}", currentPath);
                    } else {
                        throw e;
                    }
                }
            } else {
                log.debug("Group already exists: {}", currentPath);
            }
        }
    }

    /**
     * 处理创建失败
     */
    private void handleCreationFailure(TargetProjectInfo targetInfo, Exception e) {
        targetInfo.setStatus(TargetProjectInfo.Status.ERROR);
        targetInfo.setErrorMessage(e.getMessage());
        targetInfo.setLastCheckedAt(LocalDateTime.now());
        targetProjectInfoMapper.updateById(targetInfo);

        recordTargetProjectEvent(targetInfo.getSyncProjectId(), "target_project_creation_failed", "error",
                Map.of("project_path", targetInfo.getPathWithNamespace(),
                       "error", e.getMessage(),
                       "retry_count", targetInfo.getRetryCount()));

        log.error("Target project creation failed: path={}, retryCount={}",
                targetInfo.getPathWithNamespace(), targetInfo.getRetryCount(), e);
    }

    /**
     * 从完整路径中提取项目路径
     */
    private String extractProjectPath(String pathWithNamespace) {
        int lastSlashIndex = pathWithNamespace.lastIndexOf('/');
        if (lastSlashIndex > 0) {
            return pathWithNamespace.substring(lastSlashIndex + 1);
        }
        return pathWithNamespace;
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
     * 记录目标项目事件
     */
    private void recordTargetProjectEvent(Long syncProjectId, String eventType, String status, Map<String, Object> data) {
        SyncEvent event = new SyncEvent();
        event.setSyncProjectId(syncProjectId);
        event.setEventType(eventType);
        event.setEventSource("target_project_service");
        event.setStatus(status);
        event.setEventTime(LocalDateTime.now());
        event.setEventData(new HashMap<>(data));
        syncEventMapper.insert(event);
    }
}
