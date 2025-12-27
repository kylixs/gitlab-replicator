-- GitLab Mirror Database Schema
-- Version: 1.0.0

-- ============================================
-- Table: sync_project
-- Description: Main table for sync project configuration
-- ============================================
CREATE TABLE IF NOT EXISTS `sync_project` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `project_key` VARCHAR(255) NOT NULL COMMENT 'Project unique key (source path)',
  `source_project_id` BIGINT(20) DEFAULT NULL COMMENT 'Source GitLab project ID',
  `target_project_id` BIGINT(20) DEFAULT NULL COMMENT 'Target GitLab project ID',
  `sync_method` VARCHAR(50) DEFAULT 'pull_sync' COMMENT 'Sync method: push_mirror/pull_sync',
  `sync_status` VARCHAR(50) DEFAULT 'pending' COMMENT 'Sync status: pending/active/failed/paused',
  `enabled` TINYINT(1) DEFAULT 1 COMMENT 'Enable flag: 1=enabled, 0=disabled',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  `deleted` TINYINT(1) DEFAULT 0 COMMENT 'Logical delete flag: 0=active, 1=deleted',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_key` (`project_key`),
  KEY `idx_sync_status` (`sync_status`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Sync project configuration';

-- ============================================
-- Table: source_project_info
-- Description: Source GitLab project metadata
-- ============================================
CREATE TABLE IF NOT EXISTS `source_project_info` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `sync_project_id` BIGINT(20) NOT NULL COMMENT 'Reference to sync_project',
  `project_key` VARCHAR(255) NOT NULL COMMENT 'Project key',
  `project_type` VARCHAR(50) DEFAULT 'source' COMMENT 'Project type: source/target',
  `gitlab_project_id` BIGINT(20) DEFAULT NULL COMMENT 'GitLab project ID',
  `path_with_namespace` VARCHAR(255) DEFAULT NULL COMMENT 'Project full path',
  `default_branch` VARCHAR(255) DEFAULT 'main' COMMENT 'Default branch',
  `latest_commit_sha` VARCHAR(255) DEFAULT NULL COMMENT 'Latest commit SHA',
  `commit_count` INT(11) DEFAULT NULL COMMENT 'Total commit count',
  `branch_count` INT(11) DEFAULT NULL COMMENT 'Total branch count',
  `repository_size` BIGINT(20) DEFAULT NULL COMMENT 'Repository size in bytes',
  `last_activity_at` DATETIME DEFAULT NULL COMMENT 'Last activity time',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  `deleted` TINYINT(1) DEFAULT 0 COMMENT 'Logical delete flag',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_key_type` (`project_key`, `project_type`),
  KEY `idx_sync_project_id` (`sync_project_id`),
  KEY `idx_gitlab_project_id` (`gitlab_project_id`),
  KEY `idx_deleted` (`deleted`),
  CONSTRAINT `fk_source_project_sync` FOREIGN KEY (`sync_project_id`) REFERENCES `sync_project` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Source project metadata';

-- ============================================
-- Table: target_project_info
-- Description: Target GitLab project metadata
-- ============================================
CREATE TABLE IF NOT EXISTS `target_project_info` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `sync_project_id` BIGINT(20) NOT NULL COMMENT 'Reference to sync_project',
  `project_key` VARCHAR(255) NOT NULL COMMENT 'Project key',
  `project_type` VARCHAR(50) DEFAULT 'target' COMMENT 'Project type',
  `gitlab_project_id` BIGINT(20) DEFAULT NULL COMMENT 'GitLab project ID',
  `path_with_namespace` VARCHAR(255) DEFAULT NULL COMMENT 'Project full path',
  `default_branch` VARCHAR(255) DEFAULT 'main' COMMENT 'Default branch',
  `latest_commit_sha` VARCHAR(255) DEFAULT NULL COMMENT 'Latest commit SHA',
  `commit_count` INT(11) DEFAULT NULL COMMENT 'Total commit count',
  `branch_count` INT(11) DEFAULT NULL COMMENT 'Total branch count',
  `repository_size` BIGINT(20) DEFAULT NULL COMMENT 'Repository size in bytes',
  `last_activity_at` DATETIME DEFAULT NULL COMMENT 'Last activity time',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  `deleted` TINYINT(1) DEFAULT 0 COMMENT 'Logical delete flag',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_key_type` (`project_key`, `project_type`),
  KEY `idx_sync_project_id` (`sync_project_id`),
  KEY `idx_gitlab_project_id` (`gitlab_project_id`),
  KEY `idx_deleted` (`deleted`),
  CONSTRAINT `fk_target_project_sync` FOREIGN KEY (`sync_project_id`) REFERENCES `sync_project` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Target project metadata';

-- ============================================
-- Table: push_mirror_config
-- Description: Push mirror configuration
-- ============================================
CREATE TABLE IF NOT EXISTS `push_mirror_config` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `sync_project_id` BIGINT(20) NOT NULL COMMENT 'Reference to sync_project',
  `project_key` VARCHAR(255) NOT NULL COMMENT 'Project key',
  `mirror_id` BIGINT(20) DEFAULT NULL COMMENT 'GitLab mirror ID',
  `mirror_url` VARCHAR(500) DEFAULT NULL COMMENT 'Mirror target URL',
  `mirror_status` VARCHAR(50) DEFAULT 'pending' COMMENT 'Mirror status',
  `last_update_at` DATETIME DEFAULT NULL COMMENT 'Last successful update time',
  `last_error` TEXT DEFAULT NULL COMMENT 'Last error message',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  `deleted` TINYINT(1) DEFAULT 0 COMMENT 'Logical delete flag',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_key` (`project_key`),
  KEY `idx_sync_project_id` (`sync_project_id`),
  KEY `idx_mirror_status` (`mirror_status`),
  KEY `idx_deleted` (`deleted`),
  CONSTRAINT `fk_mirror_config_sync` FOREIGN KEY (`sync_project_id`) REFERENCES `sync_project` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Push mirror configuration';

-- ============================================
-- Table: sync_event
-- Description: Sync event history and logs
-- ============================================
CREATE TABLE IF NOT EXISTS `sync_event` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `sync_project_id` BIGINT(20) DEFAULT NULL COMMENT 'Reference to sync_project',
  `project_key` VARCHAR(255) DEFAULT NULL COMMENT 'Project key',
  `event_type` VARCHAR(50) NOT NULL COMMENT 'Event type: sync_start/sync_success/sync_failed',
  `event_status` VARCHAR(50) DEFAULT 'pending' COMMENT 'Event status',
  `message` TEXT DEFAULT NULL COMMENT 'Event message',
  `details` TEXT DEFAULT NULL COMMENT 'Event details (JSON)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Event time',
  PRIMARY KEY (`id`),
  KEY `idx_sync_project_id` (`sync_project_id`),
  KEY `idx_project_key` (`project_key`),
  KEY `idx_event_type` (`event_type`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Sync event history';

-- ============================================
-- Insert version information
-- ============================================
CREATE TABLE IF NOT EXISTS `schema_version` (
  `version` VARCHAR(50) NOT NULL COMMENT 'Schema version',
  `applied_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Application time',
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Schema version tracking';

INSERT INTO `schema_version` (`version`) VALUES ('1.0.0')
ON DUPLICATE KEY UPDATE `applied_at` = CURRENT_TIMESTAMP;
