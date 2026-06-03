-- Gateway Governance Phase 5 DDL
-- Multi-gateway governance rules and compliance

BEGIN;

-- =========================================================
-- 1) Governance Rule
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_governance_rule (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  rule_code          VARCHAR(128) NOT NULL,
  name               VARCHAR(255) NOT NULL,
  environment_code   VARCHAR(32),
  rule_type          VARCHAR(32)  NOT NULL,
  severity           VARCHAR(32)  NOT NULL DEFAULT 'WARN',
  expression         TEXT         NOT NULL,
  enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
  description        TEXT,
  created_by         VARCHAR(64),
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by         VARCHAR(64),
  updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ac_gw_gov_rule_tenant_code
  ON ac_gateway_governance_rule (tenant_id, rule_code);

CREATE INDEX IF NOT EXISTS idx_ac_gw_gov_rule_env_enabled
  ON ac_gateway_governance_rule (tenant_id, environment_code, enabled);

ALTER TABLE ac_gateway_governance_rule
  ADD CONSTRAINT chk_ac_gw_gov_rule_severity
  CHECK (severity IN ('BLOCK', 'WARN'));

-- =========================================================
-- 2) Compliance Check Result
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_compliance_check (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  release_id         BIGINT       NOT NULL,
  passed             BOOLEAN      NOT NULL,
  violations_json    JSONB        NOT NULL DEFAULT '[]'::jsonb,
  warnings_json      JSONB        NOT NULL DEFAULT '[]'::jsonb,
  checked_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  checked_by         VARCHAR(64),
  CONSTRAINT fk_ac_gw_compliance_release
    FOREIGN KEY (release_id) REFERENCES ac_gateway_release (id)
);

CREATE INDEX IF NOT EXISTS idx_ac_gw_compliance_release
  ON ac_gateway_compliance_check (release_id, checked_at DESC);

-- =========================================================
-- 3) Provider Runtime Revision (multi-provider tracking)
-- =========================================================
CREATE TABLE IF NOT EXISTS ac_gateway_provider_revision (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          VARCHAR(64)  NOT NULL,
  release_id         BIGINT       NOT NULL,
  environment_id     BIGINT       NOT NULL,
  gateway_provider   VARCHAR(32)  NOT NULL,
  runtime_revision   VARCHAR(128),
  detail_json        JSONB        NOT NULL DEFAULT '{}'::jsonb,
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ac_gw_provider_rev_release
    FOREIGN KEY (release_id) REFERENCES ac_gateway_release (id),
  CONSTRAINT fk_ac_gw_provider_rev_env
    FOREIGN KEY (environment_id) REFERENCES ac_gateway_environment (id)
);

CREATE INDEX IF NOT EXISTS idx_ac_gw_provider_rev_release
  ON ac_gateway_provider_revision (release_id, gateway_provider);

COMMIT;
