-- Align with Flyway V3 (admin-center): runtime-synced inbound email monitor rules.
-- (1) Extend sys_email_connections with OAuth inbound fields; (2) synced monitor rules.

ALTER TABLE sys_email_connections ADD COLUMN IF NOT EXISTS direction VARCHAR(20) NOT NULL DEFAULT 'OUTBOUND';
ALTER TABLE sys_email_connections ADD COLUMN IF NOT EXISTS oauth_provider VARCHAR(20);
ALTER TABLE sys_email_connections ADD COLUMN IF NOT EXISTS oauth_refresh_token_encrypted TEXT;
ALTER TABLE sys_email_connections ADD COLUMN IF NOT EXISTS oauth_access_token_encrypted TEXT;
ALTER TABLE sys_email_connections ADD COLUMN IF NOT EXISTS token_expires_at TIMESTAMP;
ALTER TABLE sys_email_connections ADD COLUMN IF NOT EXISTS mailbox_address VARCHAR(255);
ALTER TABLE sys_email_connections ADD COLUMN IF NOT EXISTS oauth_scopes TEXT;

ALTER TABLE sys_email_connections ALTER COLUMN host DROP NOT NULL;
ALTER TABLE sys_email_connections ALTER COLUMN from_email DROP NOT NULL;

CREATE TABLE IF NOT EXISTS sys_email_monitor_rules (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64) NOT NULL REFERENCES sys_function_units(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    connection_uid VARCHAR(64) NOT NULL,
    process_definition_key VARCHAR(255),
    start_event_id VARCHAR(255),
    folder_label VARCHAR(255) DEFAULT 'INBOX',
    filter_from VARCHAR(255),
    filter_subject VARCHAR(500),
    action_type VARCHAR(30) NOT NULL DEFAULT 'START_PROCESS',
    target_form_id VARCHAR(64),
    target_binding_id VARCHAR(64),
    system_initiator_user_id VARCHAR(64),
    extraction_rules JSONB,
    correlation JSONB,
    poll_interval_seconds INTEGER NOT NULL DEFAULT 60,
    review_on_missing BOOLEAN DEFAULT TRUE,
    last_sync_cursor TEXT,
    last_synced_at TIMESTAMP,
    synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(function_unit_id, name)
);

CREATE INDEX IF NOT EXISTS idx_sys_email_monitor_fu ON sys_email_monitor_rules(function_unit_id);
CREATE INDEX IF NOT EXISTS idx_sys_email_monitor_enabled ON sys_email_monitor_rules(enabled);

COMMENT ON TABLE sys_email_monitor_rules IS 'Inbound email monitor rules synced from developer workstation on Function Unit import/deploy';
