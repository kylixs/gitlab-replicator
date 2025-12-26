package com.gitlab.mirror.server.controller.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Sync Event Detail DTO
 * <p>
 * Enhanced event details with branch information for display.
 *
 * @author GitLab Mirror Team
 */
@Data
public class SyncEventDetailDTO {

    // Basic event info
    private Long id;
    private Long syncProjectId;
    private String projectKey;
    private String eventType;
    private String eventSource;
    private String status;
    private LocalDateTime eventTime;
    private Integer durationSeconds;

    // Sync details
    private String commitSha;
    private String ref;
    private String branchName;
    private String errorMessage;
    private Map<String, Object> eventData;

    // Branch summary (for successful syncs)
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
    }
}
