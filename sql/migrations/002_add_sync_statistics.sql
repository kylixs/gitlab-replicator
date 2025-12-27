-- Add sync statistics JSON field to sync_result table
-- This field stores detailed sync statistics including:
-- - branchesCreated: number of branches created
-- - branchesUpdated: number of branches updated
-- - branchesDeleted: number of branches deleted
-- - commitsPushed: total number of commits pushed

ALTER TABLE sync_result
ADD COLUMN statistics JSON NULL COMMENT 'Detailed sync statistics (branches, commits)' AFTER summary;

-- Add sync statistics JSON field to sync_event table
ALTER TABLE sync_event
ADD COLUMN statistics JSON NULL COMMENT 'Detailed sync statistics (branches, commits)' AFTER event_data;

-- Add index on sync_event.event_type for faster filtering
CREATE INDEX idx_event_type ON sync_event(event_type);
