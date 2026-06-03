-- Gateway Governance Phase 4 DDL
-- API Marketplace: subscriptions and catalog visibility

BEGIN;

-- =========================================================
-- 1) API Subscription (active grant)
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_api_subscription (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  application_id     BIGINT       NOT NULL,
  api_version_id     BIGINT       NOT NULL,
  environment_id     BIGINT       NOT NULL,
  status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  credential_id      BIGINT,
  granted_by         VARCHAR(64),
  granted_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  revoked_at         TIMESTAMP,
  CONSTRAINT fk_ac_gw_sub_app
    FOREIGN KEY (application_id) REFERENCES ac_gateway_application (id),
  CONSTRAINT fk_ac_gw_sub_api_ver
    FOREIGN KEY (api_version_id) REFERENCES ac_gateway_api_version (id),
  CONSTRAINT fk_ac_gw_sub_env
    FOREIGN KEY (environment_id) REFERENCES ac_gateway_environment (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ac_gw_sub_app_api_env
  ON ac_gateway_api_subscription (tenant_id, application_id, api_version_id, environment_id)
  WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_ac_gw_sub_tenant_app
  ON ac_gateway_api_subscription (tenant_id, application_id);

-- =========================================================
-- 2) Subscription Request (approval workflow)
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_subscription_request (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  application_id     BIGINT       NOT NULL,
  environment_id     BIGINT       NOT NULL,
  api_version_ids    JSONB        NOT NULL DEFAULT '[]'::jsonb,
  status             VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
  justification      TEXT,
  requester_id       VARCHAR(64)  NOT NULL,
  workflow_instance_id VARCHAR(128),
  decided_by         VARCHAR(64),
  decided_at         TIMESTAMP,
  decision_comment   TEXT,
  version           BIGINT       NOT NULL DEFAULT 0,
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ac_gw_sub_req_app
    FOREIGN KEY (application_id) REFERENCES ac_gateway_application (id),
  CONSTRAINT fk_ac_gw_sub_req_env
    FOREIGN KEY (environment_id) REFERENCES ac_gateway_environment (id)
);

CREATE INDEX IF NOT EXISTS idx_ac_gw_sub_req_status
  ON ac_gateway_subscription_request (tenant_id, status, created_at DESC);

-- =========================================================
-- 3) Catalog Visibility
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_catalog_visibility (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  api_definition_id  BIGINT       NOT NULL,
  visibility         VARCHAR(32)  NOT NULL DEFAULT 'INTERNAL',
  visible_in_marketplace BOOLEAN  NOT NULL DEFAULT TRUE,
  allowed_environments JSONB      NOT NULL DEFAULT '[]'::jsonb,
  updated_by         VARCHAR(64),
  updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ac_gw_catalog_vis_api
    FOREIGN KEY (api_definition_id) REFERENCES ac_gateway_api_definition (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ac_gw_catalog_vis_api
  ON ac_gateway_catalog_visibility (tenant_id, api_definition_id);

COMMIT;
