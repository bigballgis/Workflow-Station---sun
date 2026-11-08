-- Per-flow runtime identity. Null means the workspace default, then the platform default.
-- Idempotent. Apply on an existing database after 112.

ALTER TABLE ac_automation_flow_scope
    ADD COLUMN IF NOT EXISTS service_account_user_id VARCHAR(64);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_ac_flow_scope_service_account'
    ) THEN
        ALTER TABLE ac_automation_flow_scope
            ADD CONSTRAINT fk_ac_flow_scope_service_account
            FOREIGN KEY (service_account_user_id) REFERENCES ac_service_accounts (user_id);
    END IF;
END $$;
