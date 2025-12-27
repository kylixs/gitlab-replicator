-- Migration: Add webhook trigger support (simplified - no event storage)
-- Date: 2025-12-27
-- Description: Add webhook trigger fields to sync_task for fast sync

-- Add trigger_source to sync_task (check if column exists first)
SET @col_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'gitlab_mirror'
    AND TABLE_NAME = 'sync_task'
    AND COLUMN_NAME = 'trigger_source'
);

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE sync_task ADD COLUMN trigger_source VARCHAR(20) DEFAULT ''scheduled'' COMMENT ''scheduled/webhook/manual/api - determines priority in scheduler''',
    'SELECT ''Column trigger_source already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add webhook_event_id column
SET @col_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'gitlab_mirror'
    AND TABLE_NAME = 'sync_task'
    AND COLUMN_NAME = 'webhook_event_id'
);

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE sync_task ADD COLUMN webhook_event_id BIGINT COMMENT ''Reserved for future webhook event tracking (currently unused)''',
    'SELECT ''Column webhook_event_id already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add index for webhook-triggered task queries
SET @index_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'gitlab_mirror'
    AND TABLE_NAME = 'sync_task'
    AND INDEX_NAME = 'idx_trigger_source_status_next_run'
);

SET @sql = IF(@index_exists = 0,
    'ALTER TABLE sync_task ADD INDEX idx_trigger_source_status_next_run (trigger_source, task_status, next_run_at)',
    'SELECT ''Index idx_trigger_source_status_next_run already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Update existing tasks to have default trigger_source
UPDATE sync_task
SET trigger_source = 'scheduled'
WHERE trigger_source IS NULL;
