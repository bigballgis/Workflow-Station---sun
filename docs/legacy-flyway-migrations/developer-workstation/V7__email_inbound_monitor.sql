-- Inbound email monitoring (developer-workstation design-time).
-- Mirrors init-scripts 48-dw-email-monitor-rules.sql.

ALTER TABLE dw_email_connections ADD COLUMN IF NOT EXISTS direction VARCHAR(20) NOT NULL DEFAULT 'OUTBOUND';
ALTER TABLE dw_email_connections ADD COLUMN IF NOT EXISTS oauth_provider VARCHAR(20);
ALTER TABLE dw_email_connections ADD COLUMN IF NOT EXISTS oauth_refresh_token_encrypted TEXT;
ALTER TABLE dw_email_connections ADD COLUMN IF NOT EXISTS oauth_access_token_encrypted TEXT;
ALTER TABLE dw_email_connections ADD COLUMN IF NOT EXISTS token_expires_at TIMESTAMP;
ALTER TABLE dw_email_connections ADD COLUMN IF NOT EXISTS mailbox_address VARCHAR(255);
ALTER TABLE dw_email_connections ADD COLUMN IF NOT EXISTS oauth_scopes TEXT;

ALTER TABLE dw_email_connections ALTER COLUMN host DROP NOT NULL;
ALTER TABLE dw_email_connections ALTER COLUMN from_email DROP NOT NULL;

CREATE TABLE IF NOT EXISTS dw_email_monitor_rules (
    id BIGSERIAL PRIMARY KEY,
    rule_uid VARCHAR(64) NOT NULL UNIQUE,
    function_unit_id BIGINT NOT NULL REFERENCES dw_function_units(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    connection_uid VARCHAR(64) NOT NULL,
    process_definition_key VARCHAR(255),
    start_event_id VARCHAR(255),
    folder_label VARCHAR(255) DEFAULT 'INBOX',
    filter_from VARCHAR(255),
    filter_subject VARCHAR(500),
    action_type VARCHAR(30) NOT NULL DEFAULT 'START_PROCESS',
    target_form_id BIGINT,
    target_binding_id VARCHAR(64),
    system_initiator_user_id VARCHAR(64),
    extraction_rules JSONB,
    correlation JSONB,
    poll_interval_seconds INTEGER NOT NULL DEFAULT 60,
    review_on_missing BOOLEAN DEFAULT TRUE,
    last_sync_cursor TEXT,
    last_synced_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(function_unit_id, name)
);

CREATE INDEX IF NOT EXISTS idx_dw_email_monitor_fu ON dw_email_monitor_rules(function_unit_id);
CREATE INDEX IF NOT EXISTS idx_dw_email_monitor_uid ON dw_email_monitor_rules(rule_uid);
