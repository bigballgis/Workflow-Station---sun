-- Add optimistic locking column to user-portal entities
ALTER TABLE up_delegation_rule ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE up_process_instance ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE up_process_draft ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;
