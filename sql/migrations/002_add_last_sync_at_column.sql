-- Add last_sync_at column to sync_project table
-- This column tracks the last successful sync time for each project

ALTER TABLE sync_project
ADD COLUMN last_sync_at TIMESTAMP NULL COMMENT '最后同步成功时间'
AFTER updated_at;
