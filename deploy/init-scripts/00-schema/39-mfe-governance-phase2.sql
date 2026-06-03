-- MFE Governance Phase 2 DDL
-- Version history and health check support for frontend module registry

BEGIN;

-- Table: ac_frontend_module_version
-- Tracks historical versions per module registry entry for rollback support
CREATE TABLE IF NOT EXISTS ac_frontend_module_version (
  id                 BIGSERIAL PRIMARY KEY,
  module_registry_id BIGINT       NOT NULL,
  version            VARCHAR(64)  NOT NULL,
  remote_entry_url   VARCHAR(512) NOT NULL,
  is_active          BOOLEAN      NOT NULL DEFAULT FALSE,
  release_note       TEXT,
  created_by         VARCHAR(64),
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ac_fmv_registry
    FOREIGN KEY (module_registry_id) REFERENCES ac_frontend_module_registry(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ac_fmv_registry_version
  ON ac_frontend_module_version(module_registry_id, version);

CREATE INDEX IF NOT EXISTS idx_ac_fmv_active
  ON ac_frontend_module_version(module_registry_id, is_active, created_at DESC);

-- Table: ac_frontend_module_health_log
-- Records health check results for each module
CREATE TABLE IF NOT EXISTS ac_frontend_module_health_log (
  id                 BIGSERIAL PRIMARY KEY,
  module_registry_id BIGINT       NOT NULL,
  status             VARCHAR(32)  NOT NULL, -- HEALTHY / UNHEALTHY
  detail             TEXT,
  checked_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ac_fmhl_registry
    FOREIGN KEY (module_registry_id) REFERENCES ac_frontend_module_registry(id)
);

CREATE INDEX IF NOT EXISTS idx_ac_fmhl_registry_time
  ON ac_frontend_module_health_log(module_registry_id, checked_at DESC);

COMMIT;
