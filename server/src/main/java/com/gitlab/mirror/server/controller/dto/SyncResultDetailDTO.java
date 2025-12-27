package com.gitlab.mirror.server.controller.dto;

import com.gitlab.mirror.server.model.SyncStatistics;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sync Result Detail DTO
 * <p>
 * Used for displaying detailed sync result with branch information.
 *
 * @author GitLab Mirror Team
 */
@Data
public class SyncResultDetailDTO {

    private Long id;
    private Long syncProjectId;
    private String projectKey;
    private String syncMethod;
    private LocalDateTime lastSyncAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String syncStatus;
    private Boolean hasChanges;
    private Integer changesCount;
    private String sourceCommitSha;
    private String targetCommitSha;
    private Integer durationSeconds;
    private String errorMessage;
    private String summary;

    // Sync statistics
    private SyncStatistics statistics;

    // Branch summary
    private Integer totalBranches;
    private List<BranchInfo> recentBranches;

    @Data
    public static class BranchInfo {
        private String branchName;
        private String commitSha;
        private String commitMessage;
        private String commitAuthor;
        private LocalDateTime committedAt;
        private Boolean isDefault;
        private Boolean isProtected;
        private Boolean isRecentlyUpdated;  // 标记最近更新的分支（24小时内）
    }
}
