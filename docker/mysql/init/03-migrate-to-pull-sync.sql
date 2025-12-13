-- Migration Script for Pull Sync Feature
-- This script migrates existing Push Mirror projects to use the unified SYNC_TASK table

USE gitlab_mirror;

-- ==================== 1. Initialize SYNC_TASK for existing Push Mirror projects ====================
-- Create task records for all existing Push Mirror projects that don't have one yet

INSERT INTO sync_task (
    sync_project_id,
    task_type,
    task_status,
    next_run_at,
    consecutive_failures,
    created_at,
    updated_at
)
SELECT
    sp.id as sync_project_id,
    'push' as task_type,
    'waiting' as task_status,
    NOW() as next_run_at,
    COALESCE(pmc.consecutive_failures, 0) as consecutive_failures,
    NOW() as created_at,
    NOW() as updated_at
FROM sync_project sp
INNER JOIN push_mirror_config pmc ON sp.id = pmc.sync_project_id
WHERE sp.sync_method = 'push_mirror'
  AND NOT EXISTS (
      SELECT 1 FROM sync_task st WHERE st.sync_project_id = sp.id
  );

-- ==================== 2. Update SYNC_TASK with existing Push Mirror status ====================
-- Synchronize status from push_mirror_config to sync_task

UPDATE sync_task st
INNER JOIN push_mirror_config pmc ON st.sync_project_id = pmc.sync_project_id
SET
    st.last_sync_status = CASE
        WHEN pmc.last_update_status = 'finished' THEN 'success'
        WHEN pmc.last_update_status = 'failed' THEN 'failed'
        ELSE NULL
    END,
    st.last_run_at = pmc.last_update_at,
    st.error_message = pmc.error_message,
    st.consecutive_failures = COALESCE(pmc.consecutive_failures, 0)
WHERE st.task_type = 'push';

-- ==================== 3. Verification queries ====================
-- Display migration results

SELECT 'Migration completed successfully' AS status;

SELECT
    COUNT(*) as total_push_tasks,
    SUM(CASE WHEN last_sync_status = 'success' THEN 1 ELSE 0 END) as successful_tasks,
    SUM(CASE WHEN last_sync_status = 'failed' THEN 1 ELSE 0 END) as failed_tasks,
    SUM(CASE WHEN consecutive_failures >= 5 THEN 1 ELSE 0 END) as tasks_with_high_failures
FROM sync_task
WHERE task_type = 'push';
