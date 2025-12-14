package com.gitlab.mirror.server.service.monitor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gitlab.mirror.common.model.GitLabProject;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.TargetProjectInfoMapper;
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

    public UpdateProjectDataService(
            SourceProjectInfoMapper sourceProjectInfoMapper,
            TargetProjectInfoMapper targetProjectInfoMapper) {
        this.sourceProjectInfoMapper = sourceProjectInfoMapper;
        this.targetProjectInfoMapper = targetProjectInfoMapper;
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
     * Update target project info fields
     */
    private void updateTargetProjectFields(
            TargetProjectInfo info,
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

        public boolean isSuccess() {
            return failedCount == 0;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }
}
