package com.gitlab.mirror.server.service.monitor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gitlab.mirror.common.model.GitLabProject;
import com.gitlab.mirror.server.client.graphql.GraphQLProjectInfo;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.TargetProjectInfoMapper;
import com.gitlab.mirror.server.service.monitor.model.ProjectChange;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Update Project Data Service
 * <p>
 * Handles batch updates of monitoring fields in SOURCE_PROJECT_INFO and TARGET_PROJECT_INFO tables.
 * Provides transaction support and update result statistics.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class UpdateProjectDataService {

    private final SourceProjectInfoMapper sourceProjectInfoMapper;
    private final TargetProjectInfoMapper targetProjectInfoMapper;
    private final BatchQueryExecutor batchQueryExecutor;

    public UpdateProjectDataService(
            SourceProjectInfoMapper sourceProjectInfoMapper,
            TargetProjectInfoMapper targetProjectInfoMapper,
            BatchQueryExecutor batchQueryExecutor) {
        this.sourceProjectInfoMapper = sourceProjectInfoMapper;
        this.targetProjectInfoMapper = targetProjectInfoMapper;
        this.batchQueryExecutor = batchQueryExecutor;
    }

    /**
     * Update source projects monitoring fields from GitLab API data
     *
     * @param projects List of GitLab projects from API
     * @param projectDetails Map of project ID to detailed information
     * @return Update result statistics
     */
    @Transactional(rollbackFor = Exception.class)
    public UpdateResult updateSourceProjects(
            List<GitLabProject> projects,
            Map<Long, BatchQueryExecutor.ProjectDetails> projectDetails) {

        log.info("Updating {} source projects monitoring fields", projects.size());

        UpdateResult result = new UpdateResult();
        result.setTotalCount(projects.size());

        List<String> errors = new ArrayList<>();

        for (GitLabProject project : projects) {
            try {
                QueryWrapper<SourceProjectInfo> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("gitlab_project_id", project.getId());
                SourceProjectInfo info = sourceProjectInfoMapper.selectOne(queryWrapper);

                if (info == null) {
                    log.warn("Source project not found for GitLab project ID: {}", project.getId());
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    continue;
                }

                // Update fields from project data
                updateSourceProjectFields(info, project, projectDetails.get(project.getId()));

                // Update record
                int updated = sourceProjectInfoMapper.updateById(info);
                if (updated > 0) {
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } else {
                    result.setFailedCount(result.getFailedCount() + 1);
                    errors.add("Failed to update source project: " + project.getPathWithNamespace());
                }

            } catch (Exception e) {
                log.error("Error updating source project {}: {}", project.getPathWithNamespace(), e.getMessage(), e);
                result.setFailedCount(result.getFailedCount() + 1);
                errors.add(project.getPathWithNamespace() + ": " + e.getMessage());
            }
        }

        result.setErrors(errors);
        log.info("Source projects update completed - success: {}, failed: {}, skipped: {}",
                result.getSuccessCount(), result.getFailedCount(), result.getSkippedCount());

        return result;
    }

    /**
     * Update source projects monitoring fields from GraphQL batch query (Two-stage optimization)
     * Only updates projects with actual changes
     *
     * @param projects List of GitLab projects from API
     * @param graphQLInfos Map of project ID to GraphQL info
     * @return Update result statistics
     */
    @Transactional(rollbackFor = Exception.class)
    public UpdateResult updateSourceProjectsFromGraphQL(
            List<GitLabProject> projects,
            Map<Long, GraphQLProjectInfo> graphQLInfos) {

        log.info("Updating {} source projects from GraphQL data (only changed projects)", projects.size());

        UpdateResult result = new UpdateResult();
        result.setTotalCount(projects.size());

        List<String> errors = new ArrayList<>();

        for (GitLabProject project : projects) {
            try {
                QueryWrapper<SourceProjectInfo> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("gitlab_project_id", project.getId());
                SourceProjectInfo info = sourceProjectInfoMapper.selectOne(queryWrapper);

                if (info == null) {
                    log.warn("Source project not found for GitLab project ID: {}", project.getId());
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    continue;
                }

                // Update fields from GraphQL data and check for changes
                GraphQLProjectInfo graphQLInfo = graphQLInfos.get(project.getId());
                ProjectChange change = updateSourceProjectFieldsFromGraphQL(info, project, graphQLInfo);

                // Only update if there are changes
                if (change != null) {
                    int updated = sourceProjectInfoMapper.updateById(info);
                    if (updated > 0) {
                        result.setSuccessCount(result.getSuccessCount() + 1);
                        result.getProjectChanges().add(change);
                        log.debug("Updated source project {} with {} changes",
                                project.getPathWithNamespace(), change.getFieldChanges().size());
                    } else {
                        result.setFailedCount(result.getFailedCount() + 1);
                        errors.add("Failed to update source project: " + project.getPathWithNamespace());
                    }
                } else {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    log.debug("No changes for source project {}", project.getPathWithNamespace());
                }

            } catch (Exception e) {
                log.error("Error updating source project {}: {}", project.getPathWithNamespace(), e.getMessage(), e);
                result.setFailedCount(result.getFailedCount() + 1);
                errors.add(project.getPathWithNamespace() + ": " + e.getMessage());
            }
        }

        result.setErrors(errors);
        log.info("Source projects GraphQL update completed - updated: {}, unchanged: {}, failed: {}, skipped: {}",
                result.getSuccessCount(), result.getSkippedCount(), result.getFailedCount(),
                result.getTotalCount() - result.getSuccessCount() - result.getSkippedCount() - result.getFailedCount());

        return result;
    }

    /**
     * Update source projects branch count only
     *
     * @param projects List of GitLab projects from API
     * @param projectDetails Map of project ID to detailed information (contains branch count)
     * @return Update result statistics
     */
    @Transactional(rollbackFor = Exception.class)
    public UpdateResult updateSourceProjectsBranchCount(
            List<GitLabProject> projects,
            Map<Long, BatchQueryExecutor.ProjectDetails> projectDetails) {

        log.info("Updating {} source projects branch count", projects.size());

        UpdateResult result = new UpdateResult();
        result.setTotalCount(projects.size());

        List<String> errors = new ArrayList<>();

        for (GitLabProject project : projects) {
            try {
                QueryWrapper<SourceProjectInfo> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("gitlab_project_id", project.getId());
                SourceProjectInfo info = sourceProjectInfoMapper.selectOne(queryWrapper);

                if (info == null) {
                    log.warn("Source project not found for GitLab project ID: {}", project.getId());
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    continue;
                }

                // Update branch count from project details
                BatchQueryExecutor.ProjectDetails details = projectDetails.get(project.getId());
                if (details != null && details.getBranchCount() != null) {
                    info.setBranchCount(details.getBranchCount());
                }

                // Update record
                int updated = sourceProjectInfoMapper.updateById(info);
                if (updated > 0) {
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } else {
                    result.setFailedCount(result.getFailedCount() + 1);
                    errors.add("Failed to update source project branch count: " + project.getPathWithNamespace());
                }

            } catch (Exception e) {
                log.error("Error updating source project branch count {}: {}", project.getPathWithNamespace(), e.getMessage(), e);
                result.setFailedCount(result.getFailedCount() + 1);
                errors.add(project.getPathWithNamespace() + ": " + e.getMessage());
            }
        }

        result.setErrors(errors);
        log.info("Source projects branch count update completed - success: {}, failed: {}, skipped: {}",
                result.getSuccessCount(), result.getFailedCount(), result.getSkippedCount());

        return result;
    }

    /**
     * Update target projects monitoring fields from GitLab API data
     *
     * @param projects List of GitLab projects from API
     * @param projectDetails Map of project ID to detailed information
     * @return Update result statistics
     */
    @Transactional(rollbackFor = Exception.class)
    public UpdateResult updateTargetProjects(
            List<GitLabProject> projects,
            Map<Long, BatchQueryExecutor.ProjectDetails> projectDetails) {

        log.info("Updating {} target projects monitoring fields", projects.size());

        UpdateResult result = new UpdateResult();
        result.setTotalCount(projects.size());

        List<String> errors = new ArrayList<>();

        for (GitLabProject project : projects) {
            try {
                QueryWrapper<TargetProjectInfo> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("gitlab_project_id", project.getId());
                TargetProjectInfo info = targetProjectInfoMapper.selectOne(queryWrapper);

                if (info == null) {
                    log.warn("Target project not found for GitLab project ID: {}", project.getId());
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    continue;
                }

                // Update fields from project data
                updateTargetProjectFields(info, project, projectDetails.get(project.getId()));

                // Update record
                int updated = targetProjectInfoMapper.updateById(info);
                if (updated > 0) {
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } else {
                    result.setFailedCount(result.getFailedCount() + 1);
                    errors.add("Failed to update target project: " + project.getPathWithNamespace());
                }

            } catch (Exception e) {
                log.error("Error updating target project {}: {}", project.getPathWithNamespace(), e.getMessage(), e);
                result.setFailedCount(result.getFailedCount() + 1);
                errors.add(project.getPathWithNamespace() + ": " + e.getMessage());
            }
        }

        result.setErrors(errors);
        log.info("Target projects update completed - success: {}, failed: {}, skipped: {}",
                result.getSuccessCount(), result.getFailedCount(), result.getSkippedCount());

        return result;
    }

    /**
     * Update target projects from GraphQL data (OPTIMIZED - much faster than REST API)
     * Only updates projects with actual changes
     *
     * @param projects List of GitLab projects from API
     * @param graphQLInfos Map of project ID to GraphQL information
     * @return Update result statistics
     */
    @Transactional(rollbackFor = Exception.class)
    public UpdateResult updateTargetProjectsFromGraphQL(
            List<GitLabProject> projects,
            Map<Long, GraphQLProjectInfo> graphQLInfos) {

        log.info("Updating {} target projects from GraphQL data (only changed projects)", projects.size());

        UpdateResult result = new UpdateResult();
        result.setTotalCount(projects.size());

        List<String> errors = new ArrayList<>();

        for (GitLabProject project : projects) {
            try {
                QueryWrapper<TargetProjectInfo> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("gitlab_project_id", project.getId());
                TargetProjectInfo info = targetProjectInfoMapper.selectOne(queryWrapper);

                if (info == null) {
                    log.warn("Target project not found for GitLab project ID: {}", project.getId());
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    continue;
                }

                // Update fields from GraphQL data and check for changes
                GraphQLProjectInfo graphQLInfo = graphQLInfos.get(project.getId());
                ProjectChange change = updateTargetProjectFieldsFromGraphQL(info, project, graphQLInfo);

                // Only update if there are changes
                if (change != null) {
                    int updated = targetProjectInfoMapper.updateById(info);
                    if (updated > 0) {
                        result.setSuccessCount(result.getSuccessCount() + 1);
                        result.getProjectChanges().add(change);
                        log.debug("Updated target project {} with {} changes",
                                project.getPathWithNamespace(), change.getFieldChanges().size());
                    } else {
                        result.setFailedCount(result.getFailedCount() + 1);
                        errors.add("Failed to update target project: " + project.getPathWithNamespace());
                    }
                } else {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    log.debug("No changes for target project {}", project.getPathWithNamespace());
                }

            } catch (Exception e) {
                log.error("Error updating target project {}: {}", project.getPathWithNamespace(), e.getMessage(), e);
                result.setFailedCount(result.getFailedCount() + 1);
                errors.add(project.getPathWithNamespace() + ": " + e.getMessage());
            }
        }

        result.setErrors(errors);
        log.info("Target projects GraphQL update completed - updated: {}, unchanged: {}, failed: {}, skipped: {}",
                result.getSuccessCount(), result.getSkippedCount(), result.getFailedCount(),
                result.getTotalCount() - result.getSuccessCount() - result.getSkippedCount() - result.getFailedCount());

        return result;
    }

    /**
     * Update source project info fields
     */
    private void updateSourceProjectFields(
            SourceProjectInfo info,
            GitLabProject project,
            BatchQueryExecutor.ProjectDetails details) {

        // Update from project details
        if (details != null) {
            info.setLatestCommitSha(details.getLatestCommitSha());
            info.setCommitCount(details.getCommitCount());
            info.setBranchCount(details.getBranchCount());
        }

        // Update from project statistics
        if (project.getStatistics() != null && project.getStatistics().getRepositorySize() != null) {
            info.setRepositorySize(project.getStatistics().getRepositorySize());
        }

        // Update last activity time
        if (project.getLastActivityAt() != null) {
            info.setLastActivityAt(convertToLocalDateTime(project.getLastActivityAt()));
        }

        // Update default branch
        if (project.getDefaultBranch() != null) {
            info.setDefaultBranch(project.getDefaultBranch());
        }

        // Updated at is automatically set by MyBatis-Plus
    }

    /**
     * Update source project info fields from GraphQL data (Two-stage optimization)
     * Returns ProjectChange if there are changes, null otherwise
     */
    private ProjectChange updateSourceProjectFieldsFromGraphQL(
            SourceProjectInfo info,
            GitLabProject project,
            GraphQLProjectInfo graphQLInfo) {

        ProjectChange change = ProjectChange.builder()
                .projectKey(info.getPathWithNamespace())
                .projectType("source")
                .build();

        // Update from GraphQL data
        if (graphQLInfo != null) {
            // Commit SHA
            if (graphQLInfo.getLastCommitSha() != null &&
                !Objects.equals(info.getLatestCommitSha(), graphQLInfo.getLastCommitSha())) {
                change.addChange("latestCommitSha", info.getLatestCommitSha(), graphQLInfo.getLastCommitSha());
                info.setLatestCommitSha(graphQLInfo.getLastCommitSha());
            }

            // Commit count
            if (graphQLInfo.getCommitCount() != null &&
                !Objects.equals(info.getCommitCount(), graphQLInfo.getCommitCount())) {
                change.addChange("commitCount", info.getCommitCount(), graphQLInfo.getCommitCount());
                info.setCommitCount(graphQLInfo.getCommitCount());
            }

            // Branch count
            if (graphQLInfo.getBranchCount() != null &&
                !Objects.equals(info.getBranchCount(), graphQLInfo.getBranchCount())) {
                change.addChange("branchCount", info.getBranchCount(), graphQLInfo.getBranchCount());
                info.setBranchCount(graphQLInfo.getBranchCount());
            }

            // Repository size
            if (graphQLInfo.getRepositorySize() != null &&
                !Objects.equals(info.getRepositorySize(), graphQLInfo.getRepositorySize())) {
                change.addChange("repositorySize", info.getRepositorySize(), graphQLInfo.getRepositorySize());
                info.setRepositorySize(graphQLInfo.getRepositorySize());
            }

            // Last activity time
            if (graphQLInfo.getLastActivityAt() != null) {
                LocalDateTime newActivityTime = convertToLocalDateTime(graphQLInfo.getLastActivityAt());
                if (!Objects.equals(info.getLastActivityAt(), newActivityTime)) {
                    change.addChange("lastActivityAt", info.getLastActivityAt(), newActivityTime);
                    info.setLastActivityAt(newActivityTime);
                }
            }

            // Default branch
            if (graphQLInfo.getRepository() != null && graphQLInfo.getRepository().getRootRef() != null &&
                !Objects.equals(info.getDefaultBranch(), graphQLInfo.getRepository().getRootRef())) {
                change.addChange("defaultBranch", info.getDefaultBranch(), graphQLInfo.getRepository().getRootRef());
                info.setDefaultBranch(graphQLInfo.getRepository().getRootRef());
            }
        }

        // Also update from project basic data if available
        if (project.getDefaultBranch() != null &&
            !Objects.equals(info.getDefaultBranch(), project.getDefaultBranch())) {
            change.addChange("defaultBranch", info.getDefaultBranch(), project.getDefaultBranch());
            info.setDefaultBranch(project.getDefaultBranch());
        }

        // Return change only if there are actual changes
        return change.hasChanges() ? change : null;
    }

    /**
     * Update target project info fields from GraphQL data
     * Returns ProjectChange if there are changes, null otherwise
     */
    private ProjectChange updateTargetProjectFieldsFromGraphQL(
            TargetProjectInfo info,
            GitLabProject project,
            GraphQLProjectInfo graphQLInfo) {

        ProjectChange change = ProjectChange.builder()
                .projectKey(info.getPathWithNamespace())
                .projectType("target")
                .build();

        // Update from GraphQL data
        if (graphQLInfo != null) {
            // Commit SHA
            if (graphQLInfo.getLastCommitSha() != null &&
                !Objects.equals(info.getLatestCommitSha(), graphQLInfo.getLastCommitSha())) {
                change.addChange("latestCommitSha", info.getLatestCommitSha(), graphQLInfo.getLastCommitSha());
                info.setLatestCommitSha(graphQLInfo.getLastCommitSha());
            }

            // Branch count
            if (graphQLInfo.getBranchCount() != null &&
                !Objects.equals(info.getBranchCount(), graphQLInfo.getBranchCount())) {
                change.addChange("branchCount", info.getBranchCount(), graphQLInfo.getBranchCount());
                info.setBranchCount(graphQLInfo.getBranchCount());
            }

            // Commit count
            if (graphQLInfo.getCommitCount() != null &&
                !Objects.equals(info.getCommitCount(), graphQLInfo.getCommitCount())) {
                change.addChange("commitCount", info.getCommitCount(), graphQLInfo.getCommitCount());
                info.setCommitCount(graphQLInfo.getCommitCount());
            }

            // Repository size
            if (graphQLInfo.getRepositorySize() != null &&
                !Objects.equals(info.getRepositorySize(), graphQLInfo.getRepositorySize())) {
                change.addChange("repositorySize", info.getRepositorySize(), graphQLInfo.getRepositorySize());
                info.setRepositorySize(graphQLInfo.getRepositorySize());
            }
        }

        // Update from project statistics (if not in GraphQL)
        if (project.getStatistics() != null && project.getStatistics().getRepositorySize() != null &&
            !Objects.equals(info.getRepositorySize(), project.getStatistics().getRepositorySize())) {
            change.addChange("repositorySize", info.getRepositorySize(), project.getStatistics().getRepositorySize());
            info.setRepositorySize(project.getStatistics().getRepositorySize());
        }

        // Last activity time
        if (project.getLastActivityAt() != null) {
            LocalDateTime newActivityTime = convertToLocalDateTime(project.getLastActivityAt());
            if (!Objects.equals(info.getLastActivityAt(), newActivityTime)) {
                change.addChange("lastActivityAt", info.getLastActivityAt(), newActivityTime);
                info.setLastActivityAt(newActivityTime);
            }
        }

        // Default branch
        if (project.getDefaultBranch() != null &&
            !Objects.equals(info.getDefaultBranch(), project.getDefaultBranch())) {
            change.addChange("defaultBranch", info.getDefaultBranch(), project.getDefaultBranch());
            info.setDefaultBranch(project.getDefaultBranch());
        }

        // Return change only if there are actual changes
        return change.hasChanges() ? change : null;
    }

    /**
     * Update target project info fields
     */
    private void updateTargetProjectFields(
            TargetProjectInfo info,
            GitLabProject project,
            BatchQueryExecutor.ProjectDetails details) {

        // Update latest commit SHA and branch count from details
        if (details != null) {
            info.setLatestCommitSha(details.getLatestCommitSha());
            info.setBranchCount(details.getBranchCount());
        }

        // Update commit count from project statistics (fallback if details doesn't have it)
        if (project.getStatistics() != null && project.getStatistics().getCommitCount() != null) {
            info.setCommitCount(project.getStatistics().getCommitCount());
            log.debug("[TARGET-UPDATE] Updating target project {} commitCount from statistics: {}",
                    info.getPathWithNamespace(), project.getStatistics().getCommitCount());
        } else if (details != null && details.getCommitCount() != null) {
            info.setCommitCount(details.getCommitCount());
            log.debug("[TARGET-UPDATE] Updating target project {} commitCount from details: {}",
                    info.getPathWithNamespace(), details.getCommitCount());
        } else {
            log.warn("[TARGET-UPDATE] No commit count available for target project {}", info.getPathWithNamespace());
        }

        // Update from project statistics
        if (project.getStatistics() != null && project.getStatistics().getRepositorySize() != null) {
            info.setRepositorySize(project.getStatistics().getRepositorySize());
        }

        // Update last activity time
        if (project.getLastActivityAt() != null) {
            info.setLastActivityAt(convertToLocalDateTime(project.getLastActivityAt()));
        }

        // Update default branch
        if (project.getDefaultBranch() != null) {
            info.setDefaultBranch(project.getDefaultBranch());
        }

        // Updated at is automatically set by MyBatis-Plus
    }

    /**
     * Convert OffsetDateTime to LocalDateTime
     */
    private LocalDateTime convertToLocalDateTime(OffsetDateTime offsetDateTime) {
        return offsetDateTime != null ? offsetDateTime.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() : null;
    }

    /**
     * Update result statistics
     */
    @Data
    public static class UpdateResult {
        private int totalCount;
        private int successCount;
        private int failedCount;
        private int skippedCount;
        private List<String> errors = new ArrayList<>();
        private List<ProjectChange> projectChanges = new ArrayList<>();

        public boolean isSuccess() {
            return failedCount == 0;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }
}
