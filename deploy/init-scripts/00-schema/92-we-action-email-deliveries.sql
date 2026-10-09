-- Post-action email delivery ledger (workflow-engine-core).
-- One row per ActionEmailRequestedEvent published by user-portal after a Developer Workstation
-- Action with config_json.postEmail succeeded. event_id is the Kafka event id, so a redelivered
-- event is ignored (at most one email per event).
-- Status flow: PENDING -> SENDING -> SENT | FAILED; a transient failure returns the row to PENDING
-- with a later next_attempt_at. A row left in SENDING (engine stopped mid-send) is not retried.
-- Idempotent: safe to re-run.
-- NOTE for existing environments: init-scripts only run on FIRST container start.
-- Apply manually:
--   docker exec -i platform-postgres-dev psql -U <user> -d <db> \
--     -f /docker-entrypoint-initdb.d/00-schema/92-we-action-email-deliveries.sql

CREATE TABLE IF NOT EXISTS we_action_email_deliveries (
    event_id VARCHAR(64) PRIMARY KEY,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    process_instance_id VARCHAR(64),
    action_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ,
    CONSTRAINT chk_we_action_email_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_we_action_email_due
    ON we_action_email_deliveries (status, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_we_action_email_process
    ON we_action_email_deliveries (process_instance_id);

COMMENT ON TABLE we_action_email_deliveries IS
    'Post-action email ledger: one row per Action email request, at most one send per event';
