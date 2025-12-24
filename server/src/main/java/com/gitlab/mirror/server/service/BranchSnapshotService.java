package com.gitlab.mirror.server.service;

import com.gitlab.mirror.common.model.RepositoryBranch;
import com.gitlab.mirror.server.client.GitLabApiClient;
import com.gitlab.mirror.server.entity.ProjectBranchSnapshot;
import com.gitlab.mirror.server.mapper.ProjectBranchSnapshotMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Branch Snapshot Service
 * <p>
 * Manages storage and retrieval of project branch snapshots for accurate comparison.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class BranchSnapshotService {

    private final ProjectBranchSnapshotMapper branchSnapshotMapper;
    private final GitLabApiClient sourceGitLabApiClient;
    private final GitLabApiClient targetGitLabApiClient;

    public BranchSnapshotService(
            ProjectBranchSnapshotMapper branchSnapshotMapper,
            @Qualifier("sourceGitLabApiClient") GitLabApiClient sourceGitLabApiClient,
            @Qualifier("targetGitLabApiClient") GitLabApiClient targetGitLabApiClient) {
        this.branchSnapshotMapper = branchSnapshotMapper;
        this.sourceGitLabApiClient = sourceGitLabApiClient;
        this.targetGitLabApiClient = targetGitLabApiClient;
    }

    /**
     * Update source project branch snapshot
     *
     * @param syncProjectId    Sync project ID
     * @param gitlabProjectId  GitLab project ID
     * @param defaultBranch    Default branch name
     */
    @Transactional
    public void updateSourceBranchSnapshot(Long syncProjectId, Long gitlabProjectId, String defaultBranch) {
        log.info("Updating source branch snapshot: syncProjectId={}, gitlabProjectId={}", syncProjectId, gitlabProjectId);

        try {
            // Fetch ALL branches from source GitLab (with pagination)
            List<RepositoryBranch> branches = sourceGitLabApiClient.getAllBranches(gitlabProjectId);

            // Update snapshot
            updateBranchSnapshot(syncProjectId, ProjectBranchSnapshot.ProjectType.SOURCE, branches, defaultBranch);

            log.info("Updated source branch snapshot: {} branches", branches.size());
        } catch (Exception e) {
            log.error("Failed to update source branch snapshot: syncProjectId={}", syncProjectId, e);
            throw new RuntimeException("Failed to update source branch snapshot", e);
        }
    }

    /**
     * Update target project branch snapshot
     *
     * @param syncProjectId    Sync project ID
     * @param gitlabProjectId  GitLab project ID
     * @param defaultBranch    Default branch name
     */
    @Transactional
    public void updateTargetBranchSnapshot(Long syncProjectId, Long gitlabProjectId, String defaultBranch) {
        log.info("Updating target branch snapshot: syncProjectId={}, gitlabProjectId={}", syncProjectId, gitlabProjectId);

        try {
            // Fetch ALL branches from target GitLab (with pagination)
            List<RepositoryBranch> branches = targetGitLabApiClient.getAllBranches(gitlabProjectId);

            // Update snapshot
            updateBranchSnapshot(syncProjectId, ProjectBranchSnapshot.ProjectType.TARGET, branches, defaultBranch);

            log.info("Updated target branch snapshot: {} branches", branches.size());
        } catch (Exception e) {
            log.error("Failed to update target branch snapshot: syncProjectId={}", syncProjectId, e);
            throw new RuntimeException("Failed to update target branch snapshot", e);
        }
    }

    /**
     * Update branch snapshot (common logic)
     *
     * @param syncProjectId Sync project ID
     * @param projectType   Project type (source/target)
     * @param branches      List of branches from GitLab API
     * @param defaultBranch Default branch name
     */
    @Transactional
    public void updateBranchSnapshot(
            Long syncProjectId,
            String projectType,
            List<RepositoryBranch> branches,
            String defaultBranch) {

        log.debug("Updating branch snapshot: syncProjectId={}, type={}, branches={}",
            syncProjectId, projectType, branches.size());

        // Delete existing snapshots for this project
        int deleted = branchSnapshotMapper.deleteByProject(syncProjectId, projectType);
        log.debug("Deleted {} existing branch snapshots", deleted);

        // Insert new snapshots
        LocalDateTime snapshotTime = LocalDateTime.now();
        int inserted = 0;

        for (RepositoryBranch branch : branches) {
            ProjectBranchSnapshot snapshot = new ProjectBranchSnapshot();
            snapshot.setSyncProjectId(syncProjectId);
            snapshot.setProjectType(projectType);
            snapshot.setBranchName(branch.getName());
            snapshot.setCommitSha(branch.getCommit() != null ? branch.getCommit().getId() : null);
            snapshot.setCommitMessage(extractCommitTitle(branch.getCommit() != null ? branch.getCommit().getMessage() : null));
            snapshot.setCommitAuthor(branch.getCommit() != null && branch.getCommit().getAuthorName() != null
                ? branch.getCommit().getAuthorName() : null);

            // Convert OffsetDateTime to LocalDateTime
            if (branch.getCommit() != null && branch.getCommit().getCommittedDate() != null) {
                snapshot.setCommittedAt(branch.getCommit().getCommittedDate().toLocalDateTime());
            }

            snapshot.setIsDefault(Boolean.TRUE.equals(branch.getDefault()));
            snapshot.setIsProtected(Boolean.TRUE.equals(branch.getProtected()));
            snapshot.setSnapshotAt(snapshotTime);

            branchSnapshotMapper.insert(snapshot);
            inserted++;
        }

        log.info("Inserted {} new branch snapshots for project {} (type: {})", inserted, syncProjectId, projectType);
    }

    /**
     * Get branch snapshots for a project
     *
     * @param syncProjectId Sync project ID
     * @param projectType   Project type (source/target)
     * @return List of branch snapshots
     */
    public List<ProjectBranchSnapshot> getBranchSnapshots(Long syncProjectId, String projectType) {
        return branchSnapshotMapper.selectByProject(syncProjectId, projectType);
    }

    /**
     * Get specific branch snapshot
     *
     * @param syncProjectId Sync project ID
     * @param projectType   Project type (source/target)
     * @param branchName    Branch name
     * @return Branch snapshot or null if not found
     */
    public ProjectBranchSnapshot getBranchSnapshot(Long syncProjectId, String projectType, String branchName) {
        return branchSnapshotMapper.selectByBranch(syncProjectId, projectType, branchName);
    }

    /**
     * Count branches for a project
     *
     * @param syncProjectId Sync project ID
     * @param projectType   Project type (source/target)
     * @return Branch count
     */
    public int countBranches(Long syncProjectId, String projectType) {
        return branchSnapshotMapper.countByProject(syncProjectId, projectType);
    }

    /**
     * Extract commit title (first line) from commit message and truncate if too long
     *
     * @param commitMessage Full commit message
     * @return Commit title (first line, max 200 chars)
     */
    private String extractCommitTitle(String commitMessage) {
        if (commitMessage == null || commitMessage.isEmpty()) {
            return null;
        }

        // Extract first line
        String firstLine = commitMessage.split("\n")[0].trim();

        // Truncate if too long (max 200 chars)
        if (firstLine.length() > 200) {
            return firstLine.substring(0, 197) + "...";
        }

        return firstLine;
    }
}
