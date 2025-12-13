package com.gitlab.mirror.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncEvent;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.SyncEventMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.common.model.GitLabProject;
import com.gitlab.mirror.server.client.GitLabApiClient;
import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Project Discovery Service
 *
 * 负责从源GitLab发现项目并保存到数据库
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class ProjectDiscoveryService {

    private final GitLabApiClient sourceGitLabApiClient;
    private final SyncProjectMapper syncProjectMapper;
    private final SourceProjectInfoMapper sourceProjectInfoMapper;
    private final SyncEventMapper syncEventMapper;
    private final GitLabMirrorProperties properties;
    private final PullSyncConfigService pullSyncConfigService;
    private final SyncTaskService syncTaskService;

    public ProjectDiscoveryService(
            @Qualifier("sourceGitLabApiClient") GitLabApiClient sourceGitLabApiClient,
            SyncProjectMapper syncProjectMapper,
            SourceProjectInfoMapper sourceProjectInfoMapper,
            SyncEventMapper syncEventMapper,
            GitLabMirrorProperties properties,
            PullSyncConfigService pullSyncConfigService,
            SyncTaskService syncTaskService) {
        this.sourceGitLabApiClient = sourceGitLabApiClient;
        this.syncProjectMapper = syncProjectMapper;
        this.sourceProjectInfoMapper = sourceProjectInfoMapper;
        this.syncEventMapper = syncEventMapper;
        this.properties = properties;
        this.pullSyncConfigService = pullSyncConfigService;
        this.syncTaskService = syncTaskService;
    }

    /**
     * 定时执行项目发现任务
     * 每30分钟执行一次
     */
    @Scheduled(fixedDelayString = "${gitlab.mirror.sync.discovery-interval:1800000}")
    public void scheduleDiscovery() {
        if (!properties.getSync().getEnabled()) {
            log.debug("Sync is disabled, skipping project discovery");
            return;
        }

        log.info("Starting scheduled project discovery...");
        try {
            int discovered = discoverProjects(null);
            log.info("Scheduled project discovery completed: discovered {} projects", discovered);
        } catch (Exception e) {
            log.error("Scheduled project discovery failed", e);
        }
    }

    /**
     * 执行项目发现 (使用配置的默认同步方式)
     *
     * @param groupPath 要发现的分组路径，null表示发现所有项目
     * @return 发现的项目数量
     */
    @Transactional
    public int discoverProjects(String groupPath) {
        String defaultMethod = properties.getSync().getDefaultSyncMethod();
        if (defaultMethod == null || defaultMethod.isBlank()) {
            defaultMethod = "pull_sync";  // Use string literal instead of constant
        }
        return discoverProjects(groupPath, defaultMethod);
    }

    /**
     * 执行项目发现 (指定同步方式)
     *
     * @param groupPath  要发现的分组路径，null表示发现所有项目
     * @param syncMethod 同步方式 (push_mirror/pull_sync)
     * @return 发现的项目数量
     */
    @Transactional
    public int discoverProjects(String groupPath, String syncMethod) {
        log.info("Discovering projects from source GitLab, groupPath={}, syncMethod={}", groupPath, syncMethod);

        // 1. 从GitLab获取项目列表
        List<GitLabProject> gitlabProjects = sourceGitLabApiClient.getAllProjects(groupPath);
        log.info("Found {} projects from GitLab", gitlabProjects.size());

        // 2. 应用过滤规则
        List<GitLabProject> filteredProjects = applyFilters(gitlabProjects);
        log.info("After filtering: {} projects", filteredProjects.size());

        // 3. 处理每个项目
        int newCount = 0;
        int updatedCount = 0;

        for (GitLabProject gitlabProject : filteredProjects) {
            try {
                boolean isNew = processProject(gitlabProject, syncMethod);
                if (isNew) {
                    newCount++;
                } else {
                    updatedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to process project: {}", gitlabProject.getPathWithNamespace(), e);
            }
        }

        log.info("Project discovery completed: new={}, updated={}, syncMethod={}", newCount, updatedCount, syncMethod);
        return filteredProjects.size();
    }

    /**
     * 应用过滤规则
     */
    private List<GitLabProject> applyFilters(List<GitLabProject> projects) {
        List<GitLabProject> filtered = new ArrayList<>();

        for (GitLabProject project : projects) {
            // 过滤归档项目
            if (properties.getSync().getExcludeArchived() &&
                project.getArchived() != null && project.getArchived()) {
                log.debug("Excluding archived project: {}", project.getPathWithNamespace());
                continue;
            }

            // 过滤空仓库
            if (properties.getSync().getExcludeEmpty() &&
                project.getEmptyRepo() != null && project.getEmptyRepo()) {
                log.debug("Excluding empty project: {}", project.getPathWithNamespace());
                continue;
            }

            filtered.add(project);
        }

        return filtered;
    }

    /**
     * 处理单个项目
     *
     * @param gitlabProject GitLab项目信息
     * @param syncMethod    同步方式
     * @return true表示新创建，false表示更新
     */
    private boolean processProject(GitLabProject gitlabProject, String syncMethod) {
        String projectPath = gitlabProject.getPathWithNamespace();

        // 1. 查找或创建SyncProject记录
        QueryWrapper<SyncProject> wrapper = new QueryWrapper<>();
        wrapper.eq("project_key", projectPath);
        SyncProject syncProject = syncProjectMapper.selectOne(wrapper);

        boolean isNew = (syncProject == null);

        if (isNew) {
            syncProject = new SyncProject();
            syncProject.setProjectKey(projectPath);
            syncProject.setSyncMethod(syncMethod);
            syncProject.setSyncStatus(SyncProject.SyncStatus.PENDING);
            syncProject.setEnabled(true);
            syncProjectMapper.insert(syncProject);
            log.info("Created new sync project: {}, syncMethod={}", projectPath, syncMethod);

            // Create configuration and task based on sync method
            if ("pull_sync".equals(syncMethod)) {
                // Create Pull sync config
                pullSyncConfigService.initializeConfig(syncProject.getId(), projectPath);

                // Create Pull sync task
                syncTaskService.initializeTask(syncProject.getId(), SyncTask.TaskType.PULL);

                log.info("Initialized Pull sync for project: {}", projectPath);
            }
        } else {
            syncProject.setUpdatedAt(LocalDateTime.now());
            syncProjectMapper.updateById(syncProject);
            log.debug("Updated sync project: {}", projectPath);
        }

        // 2. 更新或创建SourceProjectInfo记录
        updateSourceProjectInfo(syncProject.getId(), gitlabProject);

        // 3. 记录发现事件
        recordDiscoveryEvent(syncProject, isNew);

        return isNew;
    }

    /**
     * 更新源项目信息
     */
    private void updateSourceProjectInfo(Long syncProjectId, GitLabProject gitlabProject) {
        QueryWrapper<SourceProjectInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("sync_project_id", syncProjectId);
        SourceProjectInfo info = sourceProjectInfoMapper.selectOne(wrapper);

        if (info == null) {
            info = new SourceProjectInfo();
            info.setSyncProjectId(syncProjectId);
        }

        info.setGitlabProjectId(gitlabProject.getId());
        info.setName(gitlabProject.getName());
        info.setPathWithNamespace(gitlabProject.getPathWithNamespace());
        info.setDefaultBranch(gitlabProject.getDefaultBranch());
        info.setVisibility(gitlabProject.getVisibility());
        info.setArchived(gitlabProject.getArchived());
        info.setEmptyRepo(gitlabProject.getEmptyRepo());
        info.setRepositorySize(gitlabProject.getStatistics() != null ? gitlabProject.getStatistics().getRepositorySize() : null);
        info.setStarCount(gitlabProject.getStarCount());
        info.setForkCount(gitlabProject.getForksCount());
        info.setLastActivityAt(gitlabProject.getLastActivityAt() != null ?
                gitlabProject.getLastActivityAt().toLocalDateTime() : null);

        // Store additional metadata
        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("description", gitlabProject.getDescription());
        metadata.put("webUrl", gitlabProject.getWebUrl());
        metadata.put("sshUrl", gitlabProject.getSshUrlToRepo());
        metadata.put("httpUrl", gitlabProject.getHttpUrlToRepo());
        info.setMetadata(metadata);

        // Extract group path from pathWithNamespace
        String pathWithNamespace = gitlabProject.getPathWithNamespace();
        int lastSlashIndex = pathWithNamespace.lastIndexOf('/');
        if (lastSlashIndex > 0) {
            info.setGroupPath(pathWithNamespace.substring(0, lastSlashIndex));
        }

        if (info.getId() == null) {
            sourceProjectInfoMapper.insert(info);
        } else {
            sourceProjectInfoMapper.updateById(info);
        }
    }

    /**
     * 记录发现事件
     */
    private void recordDiscoveryEvent(SyncProject syncProject, boolean isNew) {
        SyncEvent event = new SyncEvent();
        event.setSyncProjectId(syncProject.getId());
        event.setEventType(isNew ? "project_discovered" : "project_updated");
        event.setEventSource("discovery_service");
        event.setStatus("success");
        event.setEventTime(LocalDateTime.now());

        java.util.Map<String, Object> eventData = new java.util.HashMap<>();
        eventData.put("project_key", syncProject.getProjectKey());
        eventData.put("action", isNew ? "discovered" : "updated");
        event.setEventData(eventData);

        syncEventMapper.insert(event);
    }
}
