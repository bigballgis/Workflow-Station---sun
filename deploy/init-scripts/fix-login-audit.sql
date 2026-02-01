-- Create sys_login_audit table
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS sys_login_audit (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(64),
    username VARCHAR(50) NOT NULL,
    action VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    success BOOLEAN NOT NULL DEFAULT true,
    failure_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_login_audit_user ON sys_login_audit(user_id);
CREATE INDEX IF NOT EXISTS idx_login_audit_username ON sys_login_audit(username);
CREATE INDEX IF NOT EXISTS idx_login_audit_created ON sys_login_audit(created_at);
CREATE INDEX IF NOT EXISTS idx_login_audit_action ON sys_login_audit(action);
