-- Automation runtime identity, published flow scope, and portal write idempotency.
-- Idempotent. Apply on an existing database:
--   docker exec -i platform-postgres-dev psql -U platform -d workflow_platform \
--     -f /docker-entrypoint-initdb.d/00-schema/112-ac-automation-connection.sql

CREATE TABLE IF NOT EXISTS ac_service_accounts (
    user_id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(200) NOT NULL,
    platform_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ac_service_accounts_one_default
    ON ac_service_accounts (platform_default) WHERE platform_default;

CREATE TABLE IF NOT EXISTS ac_automation_runtime_connection (
    workspace_id VARCHAR(64) PRIMARY KEY,
    service_account_user_id VARCHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ac_runtime_service_account
        FOREIGN KEY (service_account_user_id) REFERENCES ac_service_accounts (user_id)
);

CREATE TABLE IF NOT EXISTS ac_automation_flow_scope (
    flow_id VARCHAR(128) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    function_unit_id BIGINT NOT NULL,
    catalog_id VARCHAR(64) NOT NULL,
    capabilities VARCHAR(80) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS up_automation_write_ledger (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
