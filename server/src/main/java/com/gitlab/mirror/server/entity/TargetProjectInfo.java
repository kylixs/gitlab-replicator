package com.gitlab.mirror.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Target Project Info Entity
 * <p>
 * Stores information about projects created in target GitLab
 *
 * @author GitLab Mirror Team
 */
@Data
@TableName("target_project_info")
public class TargetProjectInfo {

    /**
     * Primary key ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Associated sync project ID (unique)
     */
    @TableField("sync_project_id")
    private Long syncProjectId;

    /**
     * GitLab project ID in target instance
     */
    @TableField("gitlab_project_id")
    private Long gitlabProjectId;

    /**
     * Full project path (with namespace)
     */
    @TableField("path_with_namespace")
    private String pathWithNamespace;

    /**
     * Project name
     */
    @TableField("name")
    private String name;

    /**
     * Default branch
     */
    @TableField("default_branch")
    private String defaultBranch;

    /**
     * Latest commit SHA (default branch)
     * Used for monitoring and change detection
     */
    @TableField("latest_commit_sha")
    private String latestCommitSha;

    /**
     * Commit count
     * Used for monitoring sync progress
     */
    @TableField("commit_count")
    private Integer commitCount;

    /**
     * Branch count
     * Used for monitoring branch consistency
     */
    @TableField("branch_count")
    private Integer branchCount;

    /**
     * Repository size in bytes
     * Used for monitoring storage usage
     */
    @TableField("repository_size")
    private Long repositorySize;

    /**
     * Last activity timestamp
     * Used for monitoring sync delay
     */
    @TableField("last_activity_at")
    private LocalDateTime lastActivityAt;

    /**
     * Visibility level
     */
    @TableField("visibility")
    private String visibility;

    /**
     * Target project status
     * Values: not_exist, creating, created, ready, error, deleted
     */
    @TableField("status")
    private String status;

    /**
     * Last checked timestamp
     */
    @TableField("last_checked_at")
    private LocalDateTime lastCheckedAt;

    /**
     * Error message
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * Retry count
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * Created time (auto-fill on insert)
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Updated time (auto-fill on insert and update)
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * Target project status constants
     */
    public static class Status {
        public static final String NOT_EXIST = "not_exist";
        public static final String CREATING = "creating";
        public static final String CREATED = "created";
        public static final String READY = "ready";
        public static final String ERROR = "error";
        public static final String DELETED = "deleted";
    }
}
