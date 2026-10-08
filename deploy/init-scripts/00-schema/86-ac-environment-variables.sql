-- Environment variable catalog (Admin) + email connection Vault binding.
-- TEXT: default_value required; current_value optional (blank falls back to default).
-- VAULT: vault_secret_path only; admin-center reads HashiCorp KV v2 data.data.password.

CREATE TABLE IF NOT EXISTS ac_environment_variables (
    id VARCHAR(36) PRIMARY KEY,
    var_key VARCHAR(100) NOT NULL,
    deploy_env VARCHAR(16) NOT NULL,
    value_kind VARCHAR(16) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    default_value TEXT,
    current_value TEXT,
    vault_secret_path VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    CONSTRAINT uq_ac_env_var_key_env UNIQUE (var_key, deploy_env),
    CONSTRAINT chk_ac_env_deploy_env CHECK (deploy_env IN ('dev', 'sit', 'uat', 'prod')),
    CONSTRAINT chk_ac_env_value_kind CHECK (value_kind IN ('TEXT', 'VAULT')),
    CONSTRAINT chk_ac_env_kind_payload CHECK (
        (value_kind = 'TEXT'
            AND default_value IS NOT NULL
            AND btrim(default_value) <> ''
            AND vault_secret_path IS NULL)
        OR
        (value_kind = 'VAULT'
            AND vault_secret_path IS NOT NULL
            AND btrim(vault_secret_path) <> ''
            AND default_value IS NULL
            AND current_value IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_ac_env_var_kind ON ac_environment_variables (value_kind);
CREATE INDEX IF NOT EXISTS idx_ac_env_var_deploy ON ac_environment_variables (deploy_env);

COMMENT ON TABLE ac_environment_variables IS
    'Admin environment catalog: TEXT (current/default) or VAULT (KV v2 secret path)';
COMMENT ON COLUMN ac_environment_variables.var_key IS
    'Stable key referenced by Email Connection password_env_key and ZIP export';
COMMENT ON COLUMN ac_environment_variables.vault_secret_path IS
    'Vault KV v2 path for VAULT rows (under VAULT_KV_MOUNT), e.g. workflow/email/qq-inbound';

ALTER TABLE dw_email_connections ADD COLUMN IF NOT EXISTS password_env_key VARCHAR(100);
ALTER TABLE sys_email_connections ADD COLUMN IF NOT EXISTS password_env_key VARCHAR(100);

COMMENT ON COLUMN dw_email_connections.password_env_key IS
    'References ac_environment_variables.var_key (VAULT kind) for mailbox password';
COMMENT ON COLUMN sys_email_connections.password_env_key IS
    'References ac_environment_variables.var_key (VAULT kind) for mailbox password';
