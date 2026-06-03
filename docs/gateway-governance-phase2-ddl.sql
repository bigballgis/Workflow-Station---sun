-- Gateway Governance Phase 2 DDL
-- Drift, monitoring, release approval extensions

BEGIN;

-- =========================================================
-- 1) Drift Report
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_drift_report (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  environment_id     BIGINT       NOT NULL,
  sync_mode          VARCHAR(32)  NOT NULL DEFAULT 'REPORT_ONLY',
  status             VARCHAR(32)  NOT NULL DEFAULT 'COMPLETED',
  missing_count      INT          NOT NULL DEFAULT 0,
  extra_count        INT          NOT NULL DEFAULT 0,
  mismatch_count     INT          NOT NULL DEFAULT 0,
  report_json        JSONB        NOT NULL DEFAULT '{}'::jsonb,
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ac_gw_drift_env
    FOREIGN KEY (environment_id) REFERENCES ac_gateway_environment (id)
);

CREATE INDEX IF NOT EXISTS idx_ac_gw_drift_tenant_env_time
  ON ac_gateway_drift_report (tenant_id, environment_id, created_at DESC);

-- =========================================================
-- 2) Release Approval
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_release_approval (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  release_id         BIGINT       NOT NULL,
  approver_role      VARCHAR(64),
  approver_id        VARCHAR(64),
  status             VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
  comment            TEXT,
  decided_at         TIMESTAMP,
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ac_gw_release_approval_release
    FOREIGN KEY (release_id) REFERENCES ac_gateway_release (id)
);

CREATE INDEX IF NOT EXISTS idx_ac_gw_release_approval_release
  ON ac_gateway_release_approval (release_id, status);

-- =========================================================
-- 3) Metrics Snapshot (read model cache)
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_metrics_snapshot (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  api_definition_id  BIGINT,
  environment_id     BIGINT       NOT NULL,
  period_start       TIMESTAMP    NOT NULL,
  period_end         TIMESTAMP    NOT NULL,
  qps                NUMERIC(18,4),
  p50_latency_ms     NUMERIC(18,4),
  p95_latency_ms     NUMERIC(18,4),
  error_rate         NUMERIC(8,6),
  metrics_json       JSONB        NOT NULL DEFAULT '{}'::jsonb,
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ac_gw_metrics_env
    FOREIGN KEY (environment_id) REFERENCES ac_gateway_environment (id)
);

CREATE INDEX IF NOT EXISTS idx_ac_gw_metrics_api_env_period
  ON ac_gateway_metrics_snapshot (tenant_id, api_definition_id, environment_id, period_end DESC);

-- =========================================================
-- 4) Extend gateway_release for promotion
-- =========================================================
ALTER TABLE ac_gateway_release
  ADD COLUMN IF NOT EXISTS source_release_id BIGINT,
  ADD COLUMN IF NOT EXISTS promoted_from_env_id BIGINT;

COMMIT;
