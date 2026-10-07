-- SLA due date recalculation jobs (user-portal). One job re-stamps the due date of every open case
-- (status RUNNING / SUSPENDED) of one Function Unit after its lead time changed.
-- Items record only UPDATED / SKIPPED / FAILED cases; unchanged cases are counted, not stored.
-- Jobs whose heartbeat went stale while PENDING/RUNNING are marked FAILED when a portal instance starts.
-- Idempotent: safe to re-run.
-- NOTE for existing environments: init-scripts only run on FIRST container start.
-- Apply manually:
--   docker exec -i platform-postgres-dev psql -U <user> -d <db> \
--     -f /docker-entrypoint-initdb.d/00-schema/90-up-sla-recalc-jobs.sql
-- Run it with plain `psql -f` (autocommit), NOT `-1` / `--single-transaction`: the index on
-- up_process_instance is built CONCURRENTLY so a live table is not write-locked during the build.

CREATE TABLE IF NOT EXISTS up_sla_recalc_jobs (
    id VARCHAR(36) PRIMARY KEY,
    function_unit_code VARCHAR(100) NOT NULL,
    policy_version INTEGER,
    lead_time_days INTEGER,
    triggered_by VARCHAR(64),
    status VARCHAR(20) NOT NULL,
    total_count INTEGER NOT NULL DEFAULT 0,
    updated_count INTEGER NOT NULL DEFAULT 0,
    unchanged_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    CONSTRAINT chk_up_sla_job_status CHECK (status IN
        ('PENDING', 'RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'SUPERSEDED'))
);

CREATE INDEX IF NOT EXISTS idx_up_sla_jobs_fu
    ON up_sla_recalc_jobs (function_unit_code, submitted_at DESC);
CREATE INDEX IF NOT EXISTS idx_up_sla_jobs_status
    ON up_sla_recalc_jobs (status, finished_at);

CREATE TABLE IF NOT EXISTS up_sla_recalc_job_items (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL REFERENCES up_sla_recalc_jobs(id) ON DELETE CASCADE,
    process_instance_id VARCHAR(64) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    old_due_date VARCHAR(32),
    new_due_date VARCHAR(32),
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_up_sla_item_outcome CHECK (outcome IN ('UPDATED', 'SKIPPED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_up_sla_items_job
    ON up_sla_recalc_job_items (job_id, outcome);

-- Keyset scan of one Function Unit's open cases. CONCURRENTLY: up_process_instance is the largest
-- runtime table and a plain CREATE INDEX blocks every case write until it finishes. If a concurrent
-- build is interrupted it leaves an INVALID index that IF NOT EXISTS then skips; check with
--   SELECT indisvalid FROM pg_index WHERE indexrelid = 'idx_up_pi_fu_status_id'::regclass;
-- and DROP INDEX CONCURRENTLY + re-run if it is false.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_up_pi_fu_status_id
    ON up_process_instance (function_unit_code, status, id);

COMMENT ON TABLE up_sla_recalc_jobs IS
    'SLA due date recalculation jobs (one per lead time change / manual retrigger)';
COMMENT ON TABLE up_sla_recalc_job_items IS
    'Per-case outcome of an SLA recalculation job (UPDATED / SKIPPED / FAILED only)';
