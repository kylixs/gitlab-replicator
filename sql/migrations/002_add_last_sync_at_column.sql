-- Migration: Add last_sync_at column to sync_project table
-- Date: 2025-12-25
-- Description: This column tracks the last successful sync time for each project
-- Required by: Projects list view to display last sync timestamp

ALTER TABLE sync_project
ADD COLUMN IF NOT EXISTS last_sync_at DATETIME NULL COMMENT 'Last sync timestamp'
AFTER updated_at;
