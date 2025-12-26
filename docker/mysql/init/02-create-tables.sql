-- GitLab Mirror 数据表创建脚本
-- 创建顺序: 按照依赖关系依次创建

USE gitlab_mirror;

-- ==================== 1. SYNC_PROJECT (核心主表，无依赖) ====================
CREATE TABLE IF NOT EXISTS sync_project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    project_key VARCHAR(500) NOT NULL UNIQUE COMMENT '项目唯一标识(源路径)',
    sync_method VARCHAR(50) NOT NULL DEFAULT 'push_mirror' COMMENT '同步方式: push_mirror/pull_mirror/clone_push',
    sync_status VARCHAR(50) NOT NULL DEFAULT 'pending' COMMENT '同步状态: pending/target_created/mirror_configured/active/failed',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用同步',
    error_message TEXT COMMENT '错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次发现时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_sync_at DATETIME NULL COMMENT 'Last sync timestamp',
    INDEX idx_sync_status (sync_status),
    INDEX idx_enabled (enabled),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='同步项目主表';

-- ==================== 2. SOURCE_PROJECT_INFO (依赖 SYNC_PROJECT) ====================
CREATE TABLE IF NOT EXISTS source_project_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    sync_project_id BIGINT NOT NULL UNIQUE COMMENT '关联同步项目ID',
    gitlab_project_id BIGINT NOT NULL COMMENT 'GitLab项目ID',
    path_with_namespace VARCHAR(500) NOT NULL COMMENT '项目完整路径',
    group_path VARCHAR(500) COMMENT '分组路径',
    name VARCHAR(255) NOT NULL COMMENT '项目名称',
    default_branch VARCHAR(255) COMMENT '默认分支',
    visibility VARCHAR(50) COMMENT '可见性: private/internal/public',
    archived TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否归档',
    empty_repo TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否空仓库',
    repository_size BIGINT COMMENT '仓库大小(字节)',
    star_count INT DEFAULT 0 COMMENT '星标数',
    fork_count INT DEFAULT 0 COMMENT 'Fork数',
    last_activity_at DATETIME COMMENT '最后活动时间',
    metadata JSON COMMENT '其他元数据',
    synced_at DATETIME COMMENT '信息同步时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (sync_project_id) REFERENCES sync_project(id) ON DELETE CASCADE,
    INDEX idx_gitlab_project_id (gitlab_project_id),
    INDEX idx_path (path_with_namespace),
    INDEX idx_archived (archived),
    INDEX idx_last_activity (last_activity_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='源项目信息表';

-- ==================== 3. TARGET_PROJECT_INFO (依赖 SYNC_PROJECT) ====================
CREATE TABLE IF NOT EXISTS target_project_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    sync_project_id BIGINT NOT NULL UNIQUE COMMENT '关联同步项目ID',
    gitlab_project_id BIGINT COMMENT 'GitLab项目ID',
    path_with_namespace VARCHAR(500) COMMENT '项目完整路径',
    name VARCHAR(255) COMMENT '项目名称',
    visibility VARCHAR(50) COMMENT '可见性',
    status VARCHAR(50) NOT NULL DEFAULT 'not_exist' COMMENT '状态: not_exist/creating/created/ready/error/deleted',
    last_checked_at DATETIME COMMENT '最后检查时间',
    error_message TEXT COMMENT '错误信息',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (sync_project_id) REFERENCES sync_project(id) ON DELETE CASCADE,
    INDEX idx_gitlab_project_id (gitlab_project_id),
    INDEX idx_status (status),
    INDEX idx_last_checked (last_checked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='目标项目信息表';

-- ==================== 4. PUSH_MIRROR_CONFIG (依赖 SYNC_PROJECT) ====================
CREATE TABLE IF NOT EXISTS push_mirror_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    sync_project_id BIGINT NOT NULL UNIQUE COMMENT '关联同步项目ID',
    gitlab_mirror_id BIGINT COMMENT 'GitLab Remote Mirror ID',
    mirror_url VARCHAR(1000) COMMENT '镜像URL(不含token)',
    last_update_status VARCHAR(50) COMMENT '最后更新状态: finished/failed/started/pending',
    last_update_at DATETIME COMMENT '最后更新时间',
    last_successful_update_at DATETIME COMMENT '最后成功更新时间',
    consecutive_failures INT DEFAULT 0 COMMENT '连续失败次数',
    error_message TEXT COMMENT '错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (sync_project_id) REFERENCES sync_project(id) ON DELETE CASCADE,
    INDEX idx_gitlab_mirror_id (gitlab_mirror_id),
    INDEX idx_last_update_status (last_update_status),
    INDEX idx_last_update_at (last_update_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Push Mirror配置表';

-- ==================== 5. PULL_SYNC_CONFIG (依赖 SYNC_PROJECT) ====================
CREATE TABLE IF NOT EXISTS pull_sync_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    sync_project_id BIGINT NOT NULL UNIQUE COMMENT '关联同步项目ID',
    priority VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT '优先级: critical/high/normal/low',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    local_repo_path VARCHAR(500) COMMENT '本地仓库路径',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (sync_project_id) REFERENCES sync_project(id) ON DELETE CASCADE,
    INDEX idx_priority (priority),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Pull同步配置表';

-- ==================== 6. SYNC_TASK (依赖 SYNC_PROJECT) ====================
CREATE TABLE IF NOT EXISTS sync_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    sync_project_id BIGINT NOT NULL UNIQUE COMMENT '关联同步项目ID(唯一)',
    task_type VARCHAR(20) NOT NULL COMMENT '任务类型: push/pull',
    task_status VARCHAR(20) NOT NULL DEFAULT 'waiting' COMMENT '任务状态: waiting/pending/running',
    next_run_at TIMESTAMP(6) NULL COMMENT '下次执行时间',
    last_run_at TIMESTAMP(6) NULL COMMENT '上次执行时间',
    started_at TIMESTAMP(6) NULL COMMENT '本次开始时间',
    completed_at TIMESTAMP(6) NULL COMMENT '本次完成时间',
    duration_seconds INT COMMENT '本次执行耗时(秒)',
    has_changes TINYINT(1) COMMENT '本次是否有变更',
    changes_count INT COMMENT '本次变更数量',
    source_commit_sha VARCHAR(64) COMMENT '本次源SHA',
    target_commit_sha VARCHAR(64) COMMENT '本次目标SHA',
    last_sync_status VARCHAR(20) COMMENT '最后同步状态: success/failed',
    error_type VARCHAR(50) COMMENT '错误类型',
    error_message TEXT COMMENT '错误信息',
    consecutive_failures INT DEFAULT 0 COMMENT '连续失败次数',
    force_sync TINYINT(1) DEFAULT 0 COMMENT '强制同步标志-跳过变更检测',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (sync_project_id) REFERENCES sync_project(id) ON DELETE CASCADE,
    INDEX idx_task_status (task_status),
    INDEX idx_next_run_at (next_run_at),
    INDEX idx_task_type (task_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一同步任务表';

-- ==================== 7. SYNC_RESULT (依赖 SYNC_PROJECT) ====================
-- Store last sync result for each project (one record per project)
CREATE TABLE IF NOT EXISTS sync_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    sync_project_id BIGINT NOT NULL UNIQUE COMMENT '关联同步项目ID(唯一)',
    last_sync_at DATETIME COMMENT '最后同步时间',
    started_at DATETIME COMMENT '最后同步开始时间',
    completed_at DATETIME COMMENT '最后同步完成时间',
    sync_status VARCHAR(20) COMMENT '同步状态: success/failed/skipped',
    has_changes TINYINT(1) COMMENT '是否有变更',
    changes_count INT COMMENT '变更数量',
    source_commit_sha VARCHAR(64) COMMENT '源提交SHA',
    target_commit_sha VARCHAR(64) COMMENT '目标提交SHA',
    duration_seconds INT COMMENT '执行耗时(秒)',
    error_message TEXT COMMENT '错误信息',
    summary TEXT COMMENT '同步摘要',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (sync_project_id) REFERENCES sync_project(id) ON DELETE CASCADE,
    INDEX idx_last_sync_at (last_sync_at),
    INDEX idx_sync_status (sync_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='同步结果表-每项目一条记录';

-- ==================== 8. SYNC_EVENT (依赖 SYNC_PROJECT) ====================
-- Only records events with changes or failures (not skipped syncs)
CREATE TABLE IF NOT EXISTS sync_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    sync_project_id BIGINT NOT NULL COMMENT '关联同步项目ID',
    event_type VARCHAR(100) NOT NULL COMMENT '事件类型: push_detected/sync_started/sync_finished/sync_failed/mirror_created/mirror_updated',
    event_source VARCHAR(50) COMMENT '事件来源: webhook/polling/manual/system',
    status VARCHAR(50) COMMENT '状态: success/failed/running',
    commit_sha VARCHAR(255) COMMENT '提交SHA',
    ref VARCHAR(255) COMMENT '引用(分支/标签)',
    branch_name VARCHAR(255) COMMENT '分支名称',
    duration_seconds INT COMMENT '耗时(秒)',
    error_message TEXT COMMENT '错误信息',
    event_data JSON COMMENT '事件详细数据',
    event_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
    started_at DATETIME COMMENT '同步开始时间',
    completed_at DATETIME COMMENT '同步完成时间',
    FOREIGN KEY (sync_project_id) REFERENCES sync_project(id) ON DELETE CASCADE,
    INDEX idx_sync_project_id (sync_project_id),
    INDEX idx_event_type (event_type),
    INDEX idx_event_time (event_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='同步事件表';

-- 完成提示
SELECT 'GitLab Mirror 数据表创建完成' AS message;
