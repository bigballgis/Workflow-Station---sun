-- =====================================================
-- User Portal: add standalone indexes on up_change_history
-- for global cross-process audit log queries
-- =====================================================

CREATE INDEX IF NOT EXISTS idx_change_history_user_id ON up_change_history(user_id);
CREATE INDEX IF NOT EXISTS idx_change_history_timestamp_standalone ON up_change_history(timestamp);

COMMENT ON INDEX idx_change_history_user_id IS 'Supports global audit query filtered by user';
COMMENT ON INDEX idx_change_history_timestamp_standalone IS 'Supports global audit query filtered/sorted by time range';
