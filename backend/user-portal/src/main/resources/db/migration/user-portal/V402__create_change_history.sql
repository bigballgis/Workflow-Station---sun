CREATE TABLE up_change_history (
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

CREATE INDEX idx_change_history_process ON up_change_history(process_instance_id);
CREATE INDEX idx_change_history_task ON up_change_history(task_instance_id);
CREATE INDEX idx_change_history_timestamp ON up_change_history(process_instance_id, timestamp);
