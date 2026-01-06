-- =====================================================
-- Authentication Tables Migration
-- Validates: Requirements 1.1, 1.2
-- =====================================================

-- Enable UUID extension if not exists
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- User table
CREATE TABLE IF NOT EXISTS sys_user (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    display_name VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    department_id VARCHAR(50),
    language VARCHAR(10) DEFAULT 'zh_CN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED'))
);

-- User roles table (for ElementCollection)
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id UUID NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    role_code VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role_code)
);

-- Login audit table
CREATE TABLE IF NOT EXISTS sys_login_audit (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES sys_user(id) ON DELETE SET NULL,
    username VARCHAR(50) NOT NULL,
    action VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    success BOOLEAN NOT NULL DEFAULT true,
    failure_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_audit_action CHECK (action IN ('LOGIN', 'LOGOUT', 'REFRESH', 'TOKEN_REFRESH'))
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_user_username ON sys_user(username);
CREATE INDEX IF NOT EXISTS idx_user_status ON sys_user(status);
CREATE INDEX IF NOT EXISTS idx_user_email ON sys_user(email);
CREATE INDEX IF NOT EXISTS idx_user_department ON sys_user(department_id);
CREATE INDEX IF NOT EXISTS idx_user_role_user ON sys_user_role(user_id);
CREATE INDEX IF NOT EXISTS idx_user_role_code ON sys_user_role(role_code);
CREATE INDEX IF NOT EXISTS idx_login_audit_user ON sys_login_audit(user_id);
CREATE INDEX IF NOT EXISTS idx_login_audit_username ON sys_login_audit(username);
CREATE INDEX IF NOT EXISTS idx_login_audit_created ON sys_login_audit(created_at);
CREATE INDEX IF NOT EXISTS idx_login_audit_action ON sys_login_audit(action);

-- Comments
COMMENT ON TABLE sys_user IS 'System users for authentication';
COMMENT ON TABLE sys_user_role IS 'User role assignments';
COMMENT ON TABLE sys_login_audit IS 'Login/logout audit trail';

COMMENT ON COLUMN sys_user.status IS 'User status: ACTIVE, INACTIVE, LOCKED';
COMMENT ON COLUMN sys_user.language IS 'User preferred language code';
COMMENT ON COLUMN sys_login_audit.action IS 'Audit action: LOGIN, LOGOUT, REFRESH, TOKEN_REFRESH';
