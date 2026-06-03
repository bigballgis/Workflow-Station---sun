-- =====================================================
-- Gateway Governance Phase 1 Schema
-- Tables with ac_gateway_* prefix for admin-center embedded domain
-- Source: docs/gateway-governance-phase1-ddl.sql
-- =====================================================

-- =====================================================
-- 1. API Definition
-- =====================================================
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

COMMENT ON TABLE ac_gateway_api_definition IS 'API definitions for Gateway Governance';
COMMENT ON COLUMN ac_gateway_api_definition.status IS 'DRAFT / ACTIVE / DEPRECATED';


-- =====================================================
-- 2. API Version
-- =====================================================
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

COMMENT ON TABLE ac_gateway_api_version IS 'API version definitions';
COMMENT ON COLUMN ac_gateway_api_version.lifecycle_status IS 'DRAFT / ACTIVE / DEPRECATED';


-- =====================================================
-- 3. Application
-- =====================================================
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

COMMENT ON TABLE ac_gateway_application IS 'Gateway applications for API access';


-- =====================================================
-- 4. Credential (Phase 1 minimal)
-- =====================================================
CREATE TABLE IF NOT EXISTS ac_gateway_credential (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  application_id     BIGINT       NOT NULL,
  credential_type    VARCHAR(32)  NOT NULL,
  display_name       VARCHAR(255) NOT NULL,
  secret_ref         VARCHAR(255),
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

COMMENT ON TABLE ac_gateway_credential IS 'Gateway application credentials';
COMMENT ON COLUMN ac_gateway_credential.credential_type IS 'API_KEY / JWT / OAUTH2';
COMMENT ON COLUMN ac_gateway_credential.secret_ref IS 'External secret manager reference';
COMMENT ON COLUMN ac_gateway_credential.status IS 'ACTIVE / REVOKED';


-- =====================================================
-- 5. Access Policy
-- =====================================================
CREATE TABLE IF NOT EXISTS ac_gateway_access_policy (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  api_version_id     BIGINT       NOT NULL,
  application_id     BIGINT,
  policy_type        VARCHAR(32)  NOT NULL,
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

COMMENT ON TABLE ac_gateway_access_policy IS 'API access policies (auth/authZ rules)';
COMMENT ON COLUMN ac_gateway_access_policy.policy_type IS 'JWT / OAUTH2 / ACL / API_KEY / IP_WHITELIST';


-- =====================================================
-- 6. Traffic Policy
-- =====================================================
CREATE TABLE IF NOT EXISTS ac_gateway_traffic_policy (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  api_version_id     BIGINT       NOT NULL,
  policy_type        VARCHAR(32)  NOT NULL,
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

COMMENT ON TABLE ac_gateway_traffic_policy IS 'API traffic policies (rate limit, timeout, etc.)';
COMMENT ON COLUMN ac_gateway_traffic_policy.policy_type IS 'RATE_LIMIT / RETRY / TIMEOUT / CANARY / BLUE_GREEN';


-- =====================================================
-- 7. Environment
-- =====================================================
CREATE TABLE IF NOT EXISTS ac_gateway_environment (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  env_code           VARCHAR(32)  NOT NULL,
  name               VARCHAR(128) NOT NULL,
  gateway_provider   VARCHAR(32)  NOT NULL DEFAULT 'KONG',
  mode               VARCHAR(32)  NOT NULL DEFAULT 'DB',
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

COMMENT ON TABLE ac_gateway_environment IS 'Gateway environment configuration';
COMMENT ON COLUMN ac_gateway_environment.env_code IS 'DEV / SIT / UAT / PROD';
COMMENT ON COLUMN ac_gateway_environment.gateway_provider IS 'KONG / APISIX / ...';
COMMENT ON COLUMN ac_gateway_environment.mode IS 'DB / DB_LESS / HYBRID';


-- =====================================================
-- 8. Gateway Release
-- =====================================================
CREATE TABLE IF NOT EXISTS ac_gateway_release (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  environment_id     BIGINT       NOT NULL,
  release_no         VARCHAR(64)  NOT NULL,
  state              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
  snapshot_json      JSONB        NOT NULL DEFAULT '{}'::jsonb,
  snapshot_hash      VARCHAR(128) NOT NULL,
  description        TEXT,
  created_by         VARCHAR(64),
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by         VARCHAR(64),
  updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ac_gw_release_env
    FOREIGN KEY (environment_id) REFERENCES ac_gateway_environment (id),
  CONSTRAINT chk_ac_gw_release_state
    CHECK (state IN ('DRAFT', 'TESTING', 'PUBLISHED', 'ROLLED_BACK'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ac_gw_release_tenant_env_no
  ON ac_gateway_release (tenant_id, environment_id, release_no);

CREATE INDEX IF NOT EXISTS idx_ac_gw_release_tenant_state
  ON ac_gateway_release (tenant_id, state);

CREATE INDEX IF NOT EXISTS idx_ac_gw_release_snapshot_jsonb
  ON ac_gateway_release USING GIN (snapshot_json);

COMMENT ON TABLE ac_gateway_release IS 'Gateway release records (publish/rollback)';
COMMENT ON COLUMN ac_gateway_release.state IS 'DRAFT / TESTING / PUBLISHED / ROLLED_BACK';
COMMENT ON COLUMN ac_gateway_release.snapshot_json IS 'Full snapshot of API versions + policies at release time';


-- =====================================================
-- 9. Publish History
-- =====================================================
CREATE TABLE IF NOT EXISTS ac_gateway_publish_history (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  release_id         BIGINT       NOT NULL,
  operation          VARCHAR(32)  NOT NULL,
  result             VARCHAR(32)  NOT NULL,
  runtime_revision   VARCHAR(128),
  detail_json        JSONB        NOT NULL DEFAULT '{}'::jsonb,
  operator           VARCHAR(64),
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ac_gw_pub_hist_release
    FOREIGN KEY (release_id) REFERENCES ac_gateway_release (id),
  CONSTRAINT chk_ac_gw_pub_hist_operation
    CHECK (operation IN ('PUBLISH', 'ROLLBACK', 'PROMOTION')),
  CONSTRAINT chk_ac_gw_pub_hist_result
    CHECK (result IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_ac_gw_pub_hist_release_time
  ON ac_gateway_publish_history (release_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ac_gw_pub_hist_tenant_result
  ON ac_gateway_publish_history (tenant_id, result);

CREATE INDEX IF NOT EXISTS idx_ac_gw_pub_hist_detail_jsonb
  ON ac_gateway_publish_history USING GIN (detail_json);

COMMENT ON TABLE ac_gateway_publish_history IS 'Publish operation history for releases';
COMMENT ON COLUMN ac_gateway_publish_history.operation IS 'PUBLISH / ROLLBACK / PROMOTION';
COMMENT ON COLUMN ac_gateway_publish_history.result IS 'SUCCESS / FAILED';
COMMENT ON COLUMN ac_gateway_publish_history.runtime_revision IS 'Gateway runtime revision ID post-operation';


-- =====================================================
-- 10. Gateway Audit Log (domain-level)
-- =====================================================
CREATE TABLE IF NOT EXISTS ac_gateway_audit_log (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  action             VARCHAR(32)  NOT NULL,
  resource_type      VARCHAR(64)  NOT NULL,
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

COMMENT ON TABLE ac_gateway_audit_log IS 'Gateway domain-level audit log';
COMMENT ON COLUMN ac_gateway_audit_log.action IS 'CREATE / UPDATE / DELETE / QUERY / PUBLISH / ROLLBACK';
COMMENT ON COLUMN ac_gateway_audit_log.resource_type IS 'API / APPLICATION / POLICY / RELEASE';

-- ============================================================
-- Seed gateway environments (DEV / SIT / UAT / PROD)
-- ============================================================
INSERT INTO ac_gateway_environment (id, tenant_id, env_code, name, gateway_provider, mode, admin_endpoint, enabled, created_at, updated_at) VALUES
  (1, 'default', 'DEV',  'Development',      'KONG', 'DB', 'http://kong:8001',  true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2, 'default', 'SIT',  'System Test',      'KONG', 'DB', 'http://kong-sit:8001',  true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (3, 'default', 'UAT',  'User Acceptance',  'KONG', 'DB', 'http://kong-uat:8001',  true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (4, 'default', 'PROD', 'Production',       'KONG', 'DB', 'http://kong-prod:8001', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

