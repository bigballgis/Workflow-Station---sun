-- =====================================================
-- User Portal: change history (up_change_history)
-- Aligns with com.portal.entity.ChangeHistory and
-- backend/user-portal/.../V402__create_change_history.sql
-- =====================================================

CREATE TABLE IF NOT EXISTS up_change_history (
    id                  BIGSERIAL PRIMARY KEY,
    process_instance_id VARCHAR(64) NOT NULL,
    task_instance_id    VARCHAR(64),
    stage_id            VARCHAR(255),
    user_id             VARCHAR(64) NOT NULL,
    timestamp           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    field_name          VARCHAR(255) NOT NULL,
    old_value           TEXT,
    new_value           TEXT,
    change_type         VARCHAR(30) NOT NULL DEFAULT 'FIELD_UPDATE',
    sub_table_name      VARCHAR(255),
    row_identifier      VARCHAR(255),
    is_concurrent       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_change_history_process ON up_change_history(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_change_history_task ON up_change_history(task_instance_id);
CREATE INDEX IF NOT EXISTS idx_change_history_timestamp ON up_change_history(process_instance_id, timestamp);

COMMENT ON TABLE up_change_history IS 'Per-field change history for portal process/task forms';
