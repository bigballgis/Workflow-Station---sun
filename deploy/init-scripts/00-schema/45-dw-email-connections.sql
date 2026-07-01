-- Align with Flyway V5 (developer-workstation): SMTP connections per function unit
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

-- Inbound IMAP endpoint (nullable; used when direction is INBOUND/BOTH). Align with Flyway V8.
ALTER TABLE dw_email_connections ADD COLUMN IF NOT EXISTS imap_host VARCHAR(255);
ALTER TABLE dw_email_connections ADD COLUMN IF NOT EXISTS imap_port INTEGER;
ALTER TABLE dw_email_connections ADD COLUMN IF NOT EXISTS imap_use_ssl BOOLEAN DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_dw_email_conn_fu ON dw_email_connections(function_unit_id);
CREATE INDEX IF NOT EXISTS idx_dw_email_conn_uid ON dw_email_connections(connection_uid);

COMMENT ON TABLE dw_email_connections IS 'Per–Function Unit SMTP/IMAP connections (design-time source for email Send Tasks and inbound monitor)';
