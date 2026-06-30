-- Email connections per function unit (SMTP accounts)
CREATE TABLE IF NOT EXISTS dw_email_connections (
    id BIGSERIAL PRIMARY KEY,
    connection_uid VARCHAR(64) NOT NULL UNIQUE,
    function_unit_id BIGINT NOT NULL REFERENCES dw_function_units(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    connection_type VARCHAR(30) NOT NULL DEFAULT 'SMTP',
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL DEFAULT 587,
    username VARCHAR(255),
    password_encrypted TEXT,
    from_email VARCHAR(255) NOT NULL,
    from_name VARCHAR(100),
    use_tls BOOLEAN DEFAULT TRUE,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(function_unit_id, name)
);

CREATE INDEX IF NOT EXISTS idx_dw_email_conn_fu ON dw_email_connections(function_unit_id);
CREATE INDEX IF NOT EXISTS idx_dw_email_conn_uid ON dw_email_connections(connection_uid);
