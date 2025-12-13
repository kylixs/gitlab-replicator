package com.gitlab.mirror.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Source Project Info Entity
 * <p>
 * Stores detailed information fetched from source GitLab
 *
 * @author GitLab Mirror Team
 */
@Data
@TableName(value = "source_project_info", autoResultMap = true)
public class SourceProjectInfo {

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
     * GitLab project ID
     */
    @TableField("gitlab_project_id")
    private Long gitlabProjectId;

    /**
     * Full project path (with namespace)
     * Example: "group1/subgroup/project-name"
     */
    @TableField("path_with_namespace")
    private String pathWithNamespace;

    /**
     * Group path
     */
    @TableField("group_path")
    private String groupPath;

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
     * Visibility level
     * Values: private, internal, public
     */
    @TableField("visibility")
    private String visibility;

    /**
     * Whether project is archived
     */
    @TableField("archived")
    private Boolean archived;

    /**
     * Whether repository is empty
     */
    @TableField("empty_repo")
    private Boolean emptyRepo;

    /**
     * Repository size in bytes
     */
    @TableField("repository_size")
    private Long repositorySize;

    /**
     * Star count
     */
    @TableField("star_count")
    private Integer starCount;

    /**
     * Fork count
     */
    @TableField("fork_count")
    private Integer forkCount;

    /**
     * Last activity timestamp
     */
    @TableField("last_activity_at")
    private LocalDateTime lastActivityAt;

    /**
     * Additional metadata (JSON)
     */
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * Information sync timestamp
     */
    @TableField("synced_at")
    private LocalDateTime syncedAt;

    /**
     * Updated time (auto-fill on insert and update)
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
