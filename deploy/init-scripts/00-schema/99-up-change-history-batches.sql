-- Optional on legacy rows; apply before deploying the updated user-portal service.
ALTER TABLE up_change_history ADD COLUMN IF NOT EXISTS batch_id VARCHAR(36);
ALTER TABLE up_change_history ADD COLUMN IF NOT EXISTS batch_time TIMESTAMPTZ;
COMMENT ON COLUMN up_change_history.batch_id IS 'Actual save/event identity, shared across main and sub-table audit rows';
COMMENT ON COLUMN up_change_history.batch_time IS 'Operation time for display grouping; original detail timestamps remain unchanged';
