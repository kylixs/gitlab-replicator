package com.gitlab.mirror.server.service;

import com.gitlab.mirror.common.model.GitLabProject;
import com.gitlab.mirror.common.model.RepositoryBranch;
import com.gitlab.mirror.server.client.GitLabApiClient;
import com.gitlab.mirror.server.entity.ProjectBranchSnapshot;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Project Initialization Service
 * <p>
 * Handles initialization of new projects discovered via webhook or project scan.
 * Creates sync_project, source_project_info, pull_sync_config, and sync_task records.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectInitializationService {

    private final SyncProjectMapper syncProjectMapper;
    private final SourceProjectInfoMapper sourceProjectInfoMapper;
    private final PullSyncConfigService pullSyncConfigService;
    private final SyncTaskService syncTaskService;
    private final BranchSnapshotService branchSnapshotService;

    @Qualifier("sourceGitLabApiClient")
    private final GitLabApiClient sourceGitLabApiClient;

    /**
     * Initialize a new project from GitLab project path
     * <p>
     * Fetches project info from GitLab API and creates all necessary records
     *
     * @param projectPath Project path (e.g., "ai/test-rails-5")
     * @return Created sync project ID
     * @throws IllegalArgumentException if project not found or already exists
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long initializeProjectByPath(String projectPath) {
        log.info("Initializing new project from path: {}", projectPath);

        // Check if project already exists
        SyncProject existing = syncProjectMapper.selectByProjectKey(projectPath);
        if (existing != null) {
            log.warn("Project already exists: {}, syncProjectId={}", projectPath, existing.getId());
            throw new IllegalArgumentException("Project already exists: " + projectPath);
        }

        // Fetch project info from source GitLab
        GitLabProject gitLabProject;
        try {
            gitLabProject = sourceGitLabApiClient.getProject(projectPath);
        } catch (Exception e) {
            log.error("Failed to fetch project from GitLab: {}", projectPath, e);
            throw new IllegalArgumentException("Project not found in source GitLab: " + projectPath, e);
        }

        // Fetch branches
        List<RepositoryBranch> branches;
        try {
            branches = sourceGitLabApiClient.getAllBranches(gitLabProject.getId());
        } catch (Exception e) {
            log.warn("Failed to fetch branches for project {}: {}", projectPath, e.getMessage());
            branches = new ArrayList<>();
        }

        // Create all records
        return initializeProject(gitLabProject, branches);
    }

    /**
     * Initialize a new project from GitLab project data
     * <p>
     * Creates sync_project, source_project_info, pull_sync_config, sync_task, and branch snapshot
     *
     * @param project  GitLab project data
     * @param branches List of branches
     * @return Created sync project ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long initializeProject(GitLabProject project, List<RepositoryBranch> branches) {
        String projectKey = project.getPathWithNamespace();
        log.info("Initializing new project: {}", projectKey);

        // Step 1: Create sync_project record
        SyncProject syncProject = new SyncProject();
        syncProject.setProjectKey(projectKey);
        syncProject.setSyncMethod(SyncProject.SyncMethod.PULL_SYNC);
        syncProject.setSyncStatus(SyncProject.SyncStatus.PENDING);
        syncProject.setEnabled(true);
        syncProject.setCreatedAt(LocalDateTime.now());
        syncProject.setUpdatedAt(LocalDateTime.now());

        syncProjectMapper.insert(syncProject);
        log.info("Created sync_project: {} (id={})", projectKey, syncProject.getId());

        // Step 2: Create source_project_info record
        SourceProjectInfo sourceInfo = new SourceProjectInfo();
        sourceInfo.setSyncProjectId(syncProject.getId());
        sourceInfo.setGitlabProjectId(project.getId());
        sourceInfo.setPathWithNamespace(project.getPathWithNamespace());
        sourceInfo.setName(project.getName());
        sourceInfo.setDefaultBranch(project.getDefaultBranch());
        sourceInfo.setVisibility(project.getVisibility());
        sourceInfo.setArchived(project.getArchived());
        sourceInfo.setEmptyRepo(project.getEmptyRepo());
        sourceInfo.setStarCount(project.getStarCount());
        sourceInfo.setForkCount(project.getForksCount());

        // Set commit count and branch count from branches list
        sourceInfo.setCommitCount(0); // Will be updated by sync
        sourceInfo.setBranchCount(branches.size());

        // Extract group path from path_with_namespace
        String groupPath = extractGroupPath(project.getPathWithNamespace());
        sourceInfo.setGroupPath(groupPath);

        // Set latest commit SHA from default branch
        if (project.getDefaultBranch() != null && !branches.isEmpty()) {
            branches.stream()
                    .filter(b -> b.getName().equals(project.getDefaultBranch()))
                    .findFirst()
                    .ifPresent(b -> sourceInfo.setLatestCommitSha(b.getCommit().getId()));
        }

        // Set last activity timestamp
        if (project.getLastActivityAt() != null) {
            sourceInfo.setLastActivityAt(project.getLastActivityAt()
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime());
        }

        sourceInfo.setSyncedAt(LocalDateTime.now());
        sourceInfo.setUpdatedAt(LocalDateTime.now());

        sourceProjectInfoMapper.insert(sourceInfo);
        log.info("Created source_project_info for: {} (id={})", projectKey, sourceInfo.getId());

        // Step 3: Initialize Pull sync config
        pullSyncConfigService.initializeConfig(syncProject.getId(), projectKey);
        log.info("Initialized pull_sync_config for: {}", projectKey);

        // Step 4: Create sync task
        syncTaskService.initializeTask(syncProject.getId(), SyncTask.TaskType.PULL);
        log.info("Initialized sync_task for: {}", projectKey);

        // Step 5: Create branch snapshot
        try {
            branchSnapshotService.updateBranchSnapshot(
                    syncProject.getId(),
                    ProjectBranchSnapshot.ProjectType.SOURCE,
                    branches,
                    project.getDefaultBranch()
            );
            log.info("Created branch snapshot for: {}", projectKey);
        } catch (Exception e) {
            log.warn("Failed to create branch snapshot for {}: {}", projectKey, e.getMessage());
        }

        log.info("Successfully initialized project: {}, syncProjectId={}", projectKey, syncProject.getId());
        return syncProject.getId();
    }

    /**
     * Extract group path from project path
     * <p>
     * Example: "ai/ml/test-project" -> "ai/ml"
     *
     * @param pathWithNamespace Project path with namespace
     * @return Group path or null
     */
    private String extractGroupPath(String pathWithNamespace) {
        if (pathWithNamespace == null || !pathWithNamespace.contains("/")) {
            return null;
        }
        int lastSlash = pathWithNamespace.lastIndexOf('/');
        return pathWithNamespace.substring(0, lastSlash);
    }
}
