-- =====================================================
-- 17. Add lock_version column to user-portal tables
-- =====================================================
-- JPA entities ProcessInstance, DelegationRule, ProcessDraft
-- use @Version with lock_version column for optimistic locking,
-- but the database tables were missing this column.

ALTER TABLE up_process_instance
ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE up_delegation_rule
ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE up_process_draft
ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN up_process_instance.lock_version IS 'Optimistic locking version';
COMMENT ON COLUMN up_delegation_rule.lock_version IS 'Optimistic locking version';
COMMENT ON COLUMN up_process_draft.lock_version IS 'Optimistic locking version';
