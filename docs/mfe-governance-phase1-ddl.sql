-- MFE Governance Phase 1 DDL
-- Admin Center managed frontend module registry (configuration SoT)

BEGIN;

CREATE TABLE IF NOT EXISTS ac_frontend_module_registry (
  id                    BIGSERIAL PRIMARY KEY,
  host_app              VARCHAR(64)  NOT NULL, -- user-portal / admin-center / developer-workstation
  module_code           VARCHAR(128) NOT NULL,
  display_name          VARCHAR(255) NOT NULL,
  route_path            VARCHAR(255) NOT NULL,
  icon                  VARCHAR(64),
  order_no              INT          NOT NULL DEFAULT 100,
  remote_entry_url      VARCHAR(512) NOT NULL,
  exposed_module        VARCHAR(128) NOT NULL DEFAULT './App',
  enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
  required_permissions  JSONB        NOT NULL DEFAULT '[]'::jsonb,
  tenant_scope          JSONB        NOT NULL DEFAULT '[]'::jsonb,
  env                   VARCHAR(32)  NOT NULL, -- DEV / SIT / UAT / PROD
  version               VARCHAR(64)  NOT NULL,
  created_by            VARCHAR(64),
  created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by            VARCHAR(64),
  updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Unique module per host + env
CREATE UNIQUE INDEX IF NOT EXISTS uk_ac_fm_host_env_module
  ON ac_frontend_module_registry(host_app, env, module_code);

-- Unique route per host + env
CREATE UNIQUE INDEX IF NOT EXISTS uk_ac_fm_host_env_route
  ON ac_frontend_module_registry(host_app, env, route_path);

-- Runtime query index
CREATE INDEX IF NOT EXISTS idx_ac_fm_runtime
  ON ac_frontend_module_registry(host_app, env, enabled, order_no);

-- JSONB search indexes
CREATE INDEX IF NOT EXISTS idx_ac_fm_required_permissions
  ON ac_frontend_module_registry USING GIN(required_permissions);

CREATE INDEX IF NOT EXISTS idx_ac_fm_tenant_scope
  ON ac_frontend_module_registry USING GIN(tenant_scope);

-- Basic value checks
ALTER TABLE ac_frontend_module_registry
  ADD CONSTRAINT chk_ac_fm_env
  CHECK (env IN ('DEV', 'SIT', 'UAT', 'PROD'));

COMMIT;

