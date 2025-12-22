-- Migration: Add project_branch_snapshot table
-- Version: 1.1.0
-- Description: Store detailed branch information for accurate comparison

-- ============================================
-- Table: project_branch_snapshot
-- Description: Detailed branch information snapshot for source and target projects
-- ============================================
CREATE TABLE IF NOT EXISTS `project_branch_snapshot` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `sync_project_id` BIGINT(20) NOT NULL COMMENT 'Reference to sync_project',
  `project_type` VARCHAR(50) NOT NULL COMMENT 'Project type: source/target',
  `branch_name` VARCHAR(255) NOT NULL COMMENT 'Branch name',
  `commit_sha` VARCHAR(255) NOT NULL COMMENT 'Latest commit SHA',
  `commit_message` TEXT DEFAULT NULL COMMENT 'Latest commit message',
  `commit_author` VARCHAR(255) DEFAULT NULL COMMENT 'Latest commit author',
  `committed_at` DATETIME DEFAULT NULL COMMENT 'Latest commit time',
  `is_default` TINYINT(1) DEFAULT 0 COMMENT 'Is default branch: 1=yes, 0=no',
  `is_protected` TINYINT(1) DEFAULT 0 COMMENT 'Is protected branch: 1=yes, 0=no',
  `snapshot_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Snapshot time',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_branch` (`sync_project_id`, `project_type`, `branch_name`),
  KEY `idx_sync_project_id` (`sync_project_id`),
  KEY `idx_project_type` (`project_type`),
  KEY `idx_branch_name` (`branch_name`),
  KEY `idx_commit_sha` (`commit_sha`),
  KEY `idx_snapshot_at` (`snapshot_at`),
  CONSTRAINT `fk_branch_snapshot_sync` FOREIGN KEY (`sync_project_id`) REFERENCES `sync_project` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Project branch snapshot for detailed comparison';

-- ============================================
-- Update schema version
-- ============================================
-- INSERT INTO `schema_version` (`version`) VALUES ('1.1.0')
-- ON DUPLICATE KEY UPDATE `applied_at` = CURRENT_TIMESTAMP;
