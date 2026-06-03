-- Gateway Governance Phase 1 DDL
-- Scope: Admin Center embedded Gateway Domain (Metadata SoT)
-- Notes:
-- 1) This script is a design baseline for implementation.
-- 2) Use ac_gateway_* naming to align with admin-center domain style.
-- 3) Runtime provider remains Kong; metadata remains platform Source of Truth.

BEGIN;

-- =========================================================
-- 1) API Definition
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_api_definition (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  api_code           VARCHAR(128) NOT NULL,
  name               VARCHAR(255) NOT NULL,
  domain             VARCHAR(128),
  base_path          VARCHAR(512) NOT NULL,
  protocol           VARCHAR(32)  NOT NULL DEFAULT 'HTTP',
  status             VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
  description        TEXT,
  created_by         VARCHAR(64),
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by         VARCHAR(64),
  updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ac_gw_api_def_tenant_code
  ON ac_gateway_api_definition (tenant_id, api_code);

CREATE INDEX IF NOT EXISTS idx_ac_gw_api_def_tenant_status
  ON ac_gateway_api_definition (tenant_id, status);


-- =========================================================
-- 2) API Version
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_api_version (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  api_definition_id  BIGINT       NOT NULL,
  version            VARCHAR(64)  NOT NULL,
  openapi_doc        TEXT,
  upstream_ref       VARCHAR(255),
  lifecycle_status   VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
  created_by         VARCHAR(64),
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by         VARCHAR(64),
  updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ac_gw_api_ver_def
    FOREIGN KEY (api_definition_id) REFERENCES ac_gateway_api_definition (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ac_gw_api_ver_def_version
  ON ac_gateway_api_version (api_definition_id, version);

CREATE INDEX IF NOT EXISTS idx_ac_gw_api_ver_tenant_status
  ON ac_gateway_api_version (tenant_id, lifecycle_status);

CREATE INDEX IF NOT EXISTS idx_ac_gw_api_ver_tenant_def
  ON ac_gateway_api_version (tenant_id, api_definition_id);


-- =========================================================
-- 3) Application
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_application (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  app_code           VARCHAR(128) NOT NULL,
  name               VARCHAR(255) NOT NULL,
  owner              VARCHAR(128),
  status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  description        TEXT,
  created_by         VARCHAR(64),
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by         VARCHAR(64),
  updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ac_gw_app_tenant_code
  ON ac_gateway_application (tenant_id, app_code);

CREATE INDEX IF NOT EXISTS idx_ac_gw_app_tenant_status
  ON ac_gateway_application (tenant_id, status);


-- =========================================================
-- 4) Credential (Phase 1 minimal)
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_credential (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  application_id     BIGINT       NOT NULL,
  credential_type    VARCHAR(32)  NOT NULL, -- API_KEY/JWT/OAUTH2
  display_name       VARCHAR(255) NOT NULL,
  secret_ref         VARCHAR(255),          -- external secret manager reference
  status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  expires_at         TIMESTAMP,
  created_by         VARCHAR(64),
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by         VARCHAR(64),
  updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ac_gw_cred_app
    FOREIGN KEY (application_id) REFERENCES ac_gateway_application (id)
);

CREATE INDEX IF NOT EXISTS idx_ac_gw_cred_app_type
  ON ac_gateway_credential (application_id, credential_type);

CREATE INDEX IF NOT EXISTS idx_ac_gw_cred_tenant_status
  ON ac_gateway_credential (tenant_id, status);


-- =========================================================
-- 5) Access Policy
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_access_policy (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  api_version_id     BIGINT       NOT NULL,
  application_id     BIGINT,
  policy_type        VARCHAR(32)  NOT NULL, -- JWT/OAUTH2/ACL/API_KEY/IP_WHITELIST
  enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
  policy_json        JSONB        NOT NULL DEFAULT '{}'::jsonb,
  status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  created_by         VARCHAR(64),
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by         VARCHAR(64),
  updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ac_gw_access_policy_api_ver
    FOREIGN KEY (api_version_id) REFERENCES ac_gateway_api_version (id),
  CONSTRAINT fk_ac_gw_access_policy_app
    FOREIGN KEY (application_id) REFERENCES ac_gateway_application (id)
);

CREATE INDEX IF NOT EXISTS idx_ac_gw_access_policy_tenant_api_ver
  ON ac_gateway_access_policy (tenant_id, api_version_id);

CREATE INDEX IF NOT EXISTS idx_ac_gw_access_policy_tenant_app
  ON ac_gateway_access_policy (tenant_id, application_id);

CREATE INDEX IF NOT EXISTS idx_ac_gw_access_policy_jsonb
  ON ac_gateway_access_policy USING GIN (policy_json);


-- =========================================================
-- 6) Traffic Policy
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_traffic_policy (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  api_version_id     BIGINT       NOT NULL,
  policy_type        VARCHAR(32)  NOT NULL, -- RATE_LIMIT/RETRY/TIMEOUT/CANARY/BLUE_GREEN
  enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
  policy_json        JSONB        NOT NULL DEFAULT '{}'::jsonb,
  status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  created_by         VARCHAR(64),
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by         VARCHAR(64),
  updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ac_gw_traffic_policy_api_ver
    FOREIGN KEY (api_version_id) REFERENCES ac_gateway_api_version (id)
);

CREATE INDEX IF NOT EXISTS idx_ac_gw_traffic_policy_tenant_api_ver
  ON ac_gateway_traffic_policy (tenant_id, api_version_id);

CREATE INDEX IF NOT EXISTS idx_ac_gw_traffic_policy_jsonb
  ON ac_gateway_traffic_policy USING GIN (policy_json);


-- =========================================================
-- 7) Environment
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_environment (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  env_code           VARCHAR(32)  NOT NULL,  -- DEV/SIT/UAT/PROD
  name               VARCHAR(128) NOT NULL,
  gateway_provider   VARCHAR(32)  NOT NULL DEFAULT 'KONG',
  mode               VARCHAR(32)  NOT NULL DEFAULT 'DB', -- DB/DB_LESS/HYBRID
  admin_endpoint     VARCHAR(512) NOT NULL,
  enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
  created_by         VARCHAR(64),
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by         VARCHAR(64),
  updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ac_gw_env_tenant_code
  ON ac_gateway_environment (tenant_id, env_code);

CREATE INDEX IF NOT EXISTS idx_ac_gw_env_tenant_enabled
  ON ac_gateway_environment (tenant_id, enabled);


-- =========================================================
-- 8) Gateway Release
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_release (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  environment_id     BIGINT       NOT NULL,
  release_no         VARCHAR(64)  NOT NULL,
  state              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT', -- DRAFT/TESTING/PUBLISHED/ROLLED_BACK
  snapshot_json      JSONB        NOT NULL DEFAULT '{}'::jsonb,
  snapshot_hash      VARCHAR(128) NOT NULL,
  description        TEXT,
  created_by         VARCHAR(64),
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by         VARCHAR(64),
  updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ac_gw_release_env
    FOREIGN KEY (environment_id) REFERENCES ac_gateway_environment (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ac_gw_release_tenant_env_no
  ON ac_gateway_release (tenant_id, environment_id, release_no);

CREATE INDEX IF NOT EXISTS idx_ac_gw_release_tenant_state
  ON ac_gateway_release (tenant_id, state);

CREATE INDEX IF NOT EXISTS idx_ac_gw_release_snapshot_jsonb
  ON ac_gateway_release USING GIN (snapshot_json);


-- =========================================================
-- 9) Publish History
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_publish_history (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  release_id         BIGINT       NOT NULL,
  operation          VARCHAR(32)  NOT NULL, -- PUBLISH/ROLLBACK/PROMOTION
  result             VARCHAR(32)  NOT NULL, -- SUCCESS/FAILED
  runtime_revision   VARCHAR(128),
  detail_json        JSONB        NOT NULL DEFAULT '{}'::jsonb,
  operator           VARCHAR(64),
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ac_gw_pub_hist_release
    FOREIGN KEY (release_id) REFERENCES ac_gateway_release (id)
);

CREATE INDEX IF NOT EXISTS idx_ac_gw_pub_hist_release_time
  ON ac_gateway_publish_history (release_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ac_gw_pub_hist_tenant_result
  ON ac_gateway_publish_history (tenant_id, result);

CREATE INDEX IF NOT EXISTS idx_ac_gw_pub_hist_detail_jsonb
  ON ac_gateway_publish_history USING GIN (detail_json);


-- =========================================================
-- 10) Gateway Audit Log (domain-level, can map to existing audit later)
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_audit_log (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  action             VARCHAR(32)  NOT NULL, -- CREATE/UPDATE/DELETE/QUERY/PUBLISH/ROLLBACK
  resource_type      VARCHAR(64)  NOT NULL, -- API/APPLICATION/POLICY/RELEASE
  resource_id        VARCHAR(128),
  success            BOOLEAN      NOT NULL DEFAULT TRUE,
  failure_reason     TEXT,
  before_json        JSONB        NOT NULL DEFAULT '{}'::jsonb,
  after_json         JSONB        NOT NULL DEFAULT '{}'::jsonb,
  operator_id        VARCHAR(64),
  operator_name      VARCHAR(128),
  ip_address         VARCHAR(64),
  user_agent         VARCHAR(512),
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ac_gw_audit_tenant_resource
  ON ac_gateway_audit_log (tenant_id, resource_type, resource_id);

CREATE INDEX IF NOT EXISTS idx_ac_gw_audit_operator_time
  ON ac_gateway_audit_log (operator_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ac_gw_audit_after_jsonb
  ON ac_gateway_audit_log USING GIN (after_json);


-- =========================================================
-- 11) Optional check constraints for state correctness
-- =========================================================
ALTER TABLE ac_gateway_release
  ADD CONSTRAINT chk_ac_gw_release_state
  CHECK (state IN ('DRAFT', 'TESTING', 'PUBLISHED', 'ROLLED_BACK'));

ALTER TABLE ac_gateway_publish_history
  ADD CONSTRAINT chk_ac_gw_pub_hist_operation
  CHECK (operation IN ('PUBLISH', 'ROLLBACK', 'PROMOTION'));

ALTER TABLE ac_gateway_publish_history
  ADD CONSTRAINT chk_ac_gw_pub_hist_result
  CHECK (result IN ('SUCCESS', 'FAILED'));

COMMIT;
