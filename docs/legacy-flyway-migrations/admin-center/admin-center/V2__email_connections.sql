-- Synced email connections from developer workstation (runtime source)
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

CREATE INDEX IF NOT EXISTS idx_sys_email_conn_fu ON sys_email_connections(function_unit_id);
