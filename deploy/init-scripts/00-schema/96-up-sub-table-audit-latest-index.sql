-- Latest per-row/per-field audit lookup used by synchronous sub-table saves.
-- Run with psql autocommit; CREATE INDEX CONCURRENTLY cannot run inside a transaction.
-- Existing databases must apply this script explicitly; restarting containers is not a migration.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_change_history_sub_table_latest
    ON up_change_history (process_instance_id, sub_table_name, row_identifier,
                          field_name, change_type, timestamp DESC, id DESC)
    WHERE sub_table_name IS NOT NULL;

COMMENT ON INDEX idx_change_history_sub_table_latest IS
    'Ordered latest field audit lookup by process, sub-table, row, field and change type';
