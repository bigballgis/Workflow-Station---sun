-- Align with Flyway V2 (admin-center): synced email connections for runtime credential lookup
CREATE TABLE IF NOT EXISTS sys_email_connections (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64) NOT NULL REFERENCES sys_function_units(id) ON DELETE CASCADE,
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
    synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(function_unit_id, name)
);

-- Inbound IMAP endpoint (nullable; synced from developer-workstation). Align with admin Flyway V215.
ALTER TABLE sys_email_connections ADD COLUMN IF NOT EXISTS imap_host VARCHAR(255);
ALTER TABLE sys_email_connections ADD COLUMN IF NOT EXISTS imap_port INTEGER;
ALTER TABLE sys_email_connections ADD COLUMN IF NOT EXISTS imap_use_ssl BOOLEAN DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_sys_email_conn_fu ON sys_email_connections(function_unit_id);

COMMENT ON TABLE sys_email_connections IS 'SMTP/IMAP connections synced from developer workstation on Function Unit import/deploy';
