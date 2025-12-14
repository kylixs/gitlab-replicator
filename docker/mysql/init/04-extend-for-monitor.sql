-- 统一项目监控 - 数据模型扩展
-- 扩展 SOURCE_PROJECT_INFO 和 TARGET_PROJECT_INFO 表，新增监控字段
-- 创建 MONITOR_ALERT 告警表

USE gitlab_mirror;

-- ==================== 扩展 SOURCE_PROJECT_INFO 表 ====================
ALTER TABLE source_project_info
ADD COLUMN latest_commit_sha VARCHAR(64) COMMENT '最新提交SHA（默认分支）' AFTER default_branch,
ADD COLUMN commit_count INT COMMENT '提交数量' AFTER latest_commit_sha,
ADD COLUMN branch_count INT COMMENT '分支数量' AFTER commit_count;

-- 注意：repository_size 和 last_activity_at 字段已存在，无需重复添加
-- 创建索引（last_activity_at 的索引已存在）

-- ==================== 扩展 TARGET_PROJECT_INFO 表 ====================
ALTER TABLE target_project_info
ADD COLUMN default_branch VARCHAR(255) COMMENT '默认分支' AFTER name,
ADD COLUMN latest_commit_sha VARCHAR(64) COMMENT '最新提交SHA（默认分支）' AFTER default_branch,
ADD COLUMN commit_count INT COMMENT '提交数量' AFTER latest_commit_sha,
ADD COLUMN branch_count INT COMMENT '分支数量' AFTER commit_count,
ADD COLUMN repository_size BIGINT COMMENT '仓库大小（字节）' AFTER branch_count,
ADD COLUMN last_activity_at DATETIME COMMENT '最后活动时间' AFTER repository_size,
ADD INDEX idx_last_activity (last_activity_at);

-- ==================== 创建 MONITOR_ALERT 表 ====================
CREATE TABLE IF NOT EXISTS monitor_alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    sync_project_id BIGINT NOT NULL COMMENT '关联同步项目ID',
    alert_type VARCHAR(50) NOT NULL COMMENT '告警类型: sync_delay/commit_diff/branch_diff/size_diff/target_missing',
    severity VARCHAR(20) NOT NULL COMMENT '严重程度: critical/high/medium/low',
    title VARCHAR(255) NOT NULL COMMENT '告警标题',
    description TEXT NOT NULL COMMENT '告警描述',
    metadata TEXT COMMENT '元数据（JSON格式，包含差异详情）',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态: active/acknowledged/resolved/muted',
    triggered_at DATETIME NOT NULL COMMENT '触发时间',
    resolved_at DATETIME COMMENT '解决时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (sync_project_id) REFERENCES sync_project(id) ON DELETE CASCADE,
    INDEX idx_project (sync_project_id),
    INDEX idx_status_severity (status, severity, triggered_at),
    INDEX idx_type (alert_type, triggered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控告警表';
