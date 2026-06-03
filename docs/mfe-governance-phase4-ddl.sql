-- MFE Governance Phase 4 DDL
-- Multi-host policy and operations support

BEGIN;

CREATE TABLE IF NOT EXISTS ac_frontend_module_policy (
  id                BIGSERIAL PRIMARY KEY,
  host_app          VARCHAR(64)  NOT NULL,
  env               VARCHAR(32)  NOT NULL,
  tenant_scope      JSONB        NOT NULL DEFAULT '[]'::jsonb,
  policy_type       VARCHAR(64)  NOT NULL, -- ROLLOUT / BLOCK / APPROVAL
  policy_json       JSONB        NOT NULL DEFAULT '{}'::jsonb,
  enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
  created_by        VARCHAR(64),
  created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ac_fmp_host_env
  ON ac_frontend_module_policy(host_app, env, enabled);

CREATE TABLE IF NOT EXISTS ac_frontend_module_event (
  id                BIGSERIAL PRIMARY KEY,
  host_app          VARCHAR(64)  NOT NULL,
  module_code       VARCHAR(128) NOT NULL,
  event_type        VARCHAR(64)  NOT NULL, -- LOAD_FAIL / SWITCH / ROLLBACK
  event_detail      JSONB        NOT NULL DEFAULT '{}'::jsonb,
  created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ac_fme_host_module_time
  ON ac_frontend_module_event(host_app, module_code, created_at DESC);

COMMIT;

