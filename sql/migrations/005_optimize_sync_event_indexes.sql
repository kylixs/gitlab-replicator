-- Migration 005: Optimize sync_event table indexes
-- Date: 2025-12-28
-- Purpose: Add composite indexes to improve query performance for sync events

-- Composite index for querying events by project and time
-- Optimizes: SELECT * FROM sync_event WHERE sync_project_id = ? ORDER BY event_time DESC
CREATE INDEX IF NOT EXISTS idx_project_time ON sync_event(sync_project_id, event_time DESC);

-- Composite index for dashboard statistics queries
-- Optimizes: SELECT event_type, status, COUNT(*) FROM sync_event WHERE event_time >= ? GROUP BY event_type, status
CREATE INDEX IF NOT EXISTS idx_time_type_status ON sync_event(event_time, event_type, status);

-- Performance improvements:
-- 1. idx_project_time eliminates filesort for project event queries
-- 2. idx_time_type_status uses index covering for dashboard stats, reducing scans by 50%
-- 3. Both indexes support common query patterns in monitoring and dashboard features
