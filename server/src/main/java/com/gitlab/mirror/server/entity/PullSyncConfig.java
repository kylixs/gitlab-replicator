package com.gitlab.mirror.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Pull Sync Config Entity
 * <p>
 * Stores Pull Sync specific static configuration
 *
 * @author GitLab Mirror Team
 */
@Data
@TableName("pull_sync_config")
public class PullSyncConfig {

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
     * Sync priority
     * Values: critical, high, normal, low
     */
    @TableField("priority")
    private String priority;

    /**
     * Whether sync is enabled
     */
    @TableField("enabled")
    private Boolean enabled;

    /**
     * Local repository path
     */
    @TableField("local_repo_path")
    private String localRepoPath;

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
     * Priority constants
     */
    public static class Priority {
        public static final String CRITICAL = "critical";
        public static final String HIGH = "high";
        public static final String NORMAL = "normal";
        public static final String LOW = "low";
    }
}
