-- Workflow-engine inbound email bookkeeping (engine has ddl-auto=none + no Flyway; init-scripts authoritative).
-- Idempotency ledger: one row per (rule_uid, message_id) ensures an email triggers a process at most once.

CREATE TABLE IF NOT EXISTS we_email_processed_messages (
    id BIGSERIAL PRIMARY KEY,
    rule_uid VARCHAR(64) NOT NULL,
    message_id VARCHAR(512) NOT NULL,
    process_instance_id VARCHAR(64),
    status VARCHAR(30) NOT NULL DEFAULT 'STARTED',
    error_message TEXT,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(rule_uid, message_id)
);

CREATE INDEX IF NOT EXISTS idx_we_email_processed_rule ON we_email_processed_messages(rule_uid);
CREATE INDEX IF NOT EXISTS idx_we_email_processed_status ON we_email_processed_messages(status);

COMMENT ON TABLE we_email_processed_messages IS 'Idempotency + audit ledger for inbound emails consumed by the monitor scheduler (STARTED/REVIEW/FAILED)';
