-- SLA policy (Admin): per Function Unit lead time in calendar days, maintained in production by
-- authorised admins. The portal derives each case's due date = start date + lead_time_days on every
-- write path, and a recalculation job re-stamps open cases after a change.
-- Which fields hold the start / due date is design-time config: dw_table_definitions.sla_config.
-- The lead time itself is environment data and is NOT exported with the Function Unit.
-- Idempotent: safe to re-run.
-- NOTE for existing environments: init-scripts only run on FIRST container start.
-- Apply manually:
--   docker exec -i platform-postgres-dev psql -U <user> -d <db> \
--     -f /docker-entrypoint-initdb.d/00-schema/88-ac-sla-policies.sql

CREATE TABLE IF NOT EXISTS ac_sla_policies (
    function_unit_code VARCHAR(100) PRIMARY KEY,
    lead_time_days INTEGER NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    CONSTRAINT chk_ac_sla_lead_time_days CHECK (lead_time_days BETWEEN 1 AND 3650)
);

CREATE TABLE IF NOT EXISTS ac_sla_policy_history (
    id VARCHAR(36) PRIMARY KEY,
    function_unit_code VARCHAR(100) NOT NULL,
    old_lead_time_days INTEGER,
    new_lead_time_days INTEGER NOT NULL,
    old_version INTEGER,
    new_version INTEGER NOT NULL,
    change_reason VARCHAR(500),
    changed_by VARCHAR(64),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recalc_job_id VARCHAR(36),
    dispatch_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    dispatch_error VARCHAR(1000),
    CONSTRAINT chk_ac_sla_history_dispatch CHECK (dispatch_status IN ('PENDING', 'DISPATCHED', 'DISPATCH_FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_ac_sla_history_fu
    ON ac_sla_policy_history (function_unit_code, changed_at DESC);

COMMENT ON TABLE ac_sla_policies IS
    'Per Function Unit SLA lead time (calendar days); due date = start date + lead_time_days';
COMMENT ON TABLE ac_sla_policy_history IS
    'SLA lead time change history with the recalculation job it dispatched';
