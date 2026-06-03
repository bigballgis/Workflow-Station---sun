-- MFE Governance Phase 3 DDL
-- Optional preload/warmup fields for runtime orchestration

BEGIN;

ALTER TABLE ac_frontend_module_registry
  ADD COLUMN IF NOT EXISTS warmup_required BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS preload_priority INT NOT NULL DEFAULT 100;

CREATE INDEX IF NOT EXISTS idx_ac_fm_preload
  ON ac_frontend_module_registry(host_app, env, enabled, preload_priority);

COMMIT;

