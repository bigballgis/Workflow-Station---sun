-- Single-task delegate target: USER (delegated_to) or paired BU+Role (codes).
-- Does not change Flowable ACT_RU_TASK.ASSIGNEE_. Idempotent.

ALTER TABLE IF EXISTS wf_extended_task_info
    ADD COLUMN IF NOT EXISTS delegated_target_type VARCHAR(20);

ALTER TABLE IF EXISTS wf_extended_task_info
    ADD COLUMN IF NOT EXISTS delegated_bu_code VARCHAR(64);

ALTER TABLE IF EXISTS wf_extended_task_info
    ADD COLUMN IF NOT EXISTS delegated_role_code VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_delegated_bu_role
    ON wf_extended_task_info (delegated_bu_code, delegated_role_code);

COMMENT ON COLUMN wf_extended_task_info.delegated_target_type IS
    'Delegate target: USER or BU_ROLE; null on legacy USER rows';
COMMENT ON COLUMN wf_extended_task_info.delegated_bu_code IS
    'BU code when delegated_target_type is BU_ROLE';
COMMENT ON COLUMN wf_extended_task_info.delegated_role_code IS
    'Role code when delegated_target_type is BU_ROLE';
