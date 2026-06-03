-- Gateway Governance Phase 3 DDL
-- Phase 3 is primarily micro frontend extraction; no mandatory business schema changes.
-- Optional: host remote version pin for rollback tracking.

BEGIN;

CREATE TABLE IF NOT EXISTS ac_gateway_mfe_deploy (
  id                 BIGSERIAL PRIMARY KEY,
  remote_name        VARCHAR(64)  NOT NULL DEFAULT 'gateway-mfe',
  version            VARCHAR(64)  NOT NULL,
  remote_entry_url   VARCHAR(512) NOT NULL,
  deployed_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deployed_by        VARCHAR(64),
  active             BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_ac_gw_mfe_deploy_active
  ON ac_gateway_mfe_deploy (remote_name, active, deployed_at DESC);

COMMIT;
