package com.gitlab.mirror.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Project Branch Snapshot Entity
 * <p>
 * Stores detailed branch information for accurate comparison between source and target.
 *
 * @author GitLab Mirror Team
 */
@Data
@TableName("project_branch_snapshot")
public class ProjectBranchSnapshot {

    /**
     * Primary key
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Reference to sync_project
     */
    private Long syncProjectId;

    /**
     * Project type: source/target
     */
    private String projectType;

    /**
     * Branch name
     */
    private String branchName;

    /**
     * Latest commit SHA
     */
    private String commitSha;

    /**
     * Latest commit message
     */
    private String commitMessage;

    /**
     * Latest commit author
     */
    private String commitAuthor;

    /**
     * Latest commit time
     */
    private LocalDateTime committedAt;

    /**
     * Is default branch
     */
    private Boolean isDefault;

    /**
     * Is protected branch
     */
    private Boolean isProtected;

    /**
     * Snapshot time
     */
    private LocalDateTime snapshotAt;

    /**
     * Creation time
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Update time
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * Project type constants
     */
    public static class ProjectType {
        public static final String SOURCE = "source";
        public static final String TARGET = "target";
    }
}
