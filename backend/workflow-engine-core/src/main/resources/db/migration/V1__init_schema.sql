-- =====================================================
-- Low-Code Workflow Platform - Initial Schema
-- Version: 1.0.0
-- Database: PostgreSQL 16.5
-- =====================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =====================================================
-- User and Authentication Tables
-- =====================================================

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(200),
    phone VARCHAR(50),
    avatar_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts INT DEFAULT 0,
    locked_until TIMESTAMP,
    password_changed_at TIMESTAMP,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT DEFAULT 0
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);

-- =====================================================
-- Organization and Department Tables
-- =====================================================

CREATE TABLE IF NOT EXISTS organizations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    parent_id UUID REFERENCES organizations(id),
    org_type VARCHAR(50) NOT NULL,
    sort_order INT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT DEFAULT 0
);

CREATE INDEX idx_organizations_code ON organizations(code);
CREATE INDEX idx_organizations_parent ON organizations(parent_id);

CREATE TABLE IF NOT EXISTS user_organizations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    is_primary BOOLEAN DEFAULT FALSE,
    position VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, organization_id)
);

-- =====================================================
-- Role and Permission Tables
-- =====================================================

CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    role_type VARCHAR(50) NOT NULL DEFAULT 'CUSTOM',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(200) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    permission_type VARCHAR(50) NOT NULL,
    resource_type VARCHAR(100),
    action VARCHAR(50),
    parent_id UUID REFERENCES permissions(id),
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_permissions_code ON permissions(code);
CREATE INDEX idx_permissions_type ON permissions(permission_type);

CREATE TABLE IF NOT EXISTS role_permissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_id UUID NOT NULL REFERENCES roles(id),
    permission_id UUID NOT NULL REFERENCES permissions(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS user_roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, role_id)
);

-- =====================================================
-- Permission Delegation Table
-- =====================================================

CREATE TABLE IF NOT EXISTS permission_delegations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    delegator_id UUID NOT NULL REFERENCES users(id),
    delegate_id UUID NOT NULL REFERENCES users(id),
    permission_id UUID NOT NULL REFERENCES permissions(id),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_delegations_delegate ON permission_delegations(delegate_id);
CREATE INDEX idx_delegations_time ON permission_delegations(start_time, end_time);

-- =====================================================
-- Audit Log Table
-- =====================================================

CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID,
    username VARCHAR(100),
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(100),
    old_value JSONB,
    new_value JSONB,
    ip_address VARCHAR(50),
    user_agent TEXT,
    trace_id VARCHAR(100),
    result VARCHAR(20),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_resource ON audit_logs(resource_type, resource_id);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at);

-- =====================================================
-- Saga Transaction Tables
-- =====================================================

CREATE TABLE IF NOT EXISTS saga_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_type VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    context JSONB,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    version BIGINT DEFAULT 0
);

CREATE INDEX idx_saga_status ON saga_transactions(status);
CREATE INDEX idx_saga_type ON saga_transactions(saga_type);

CREATE TABLE IF NOT EXISTS saga_steps (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID NOT NULL REFERENCES saga_transactions(id),
    step_name VARCHAR(100) NOT NULL,
    step_order INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    input_data JSONB,
    output_data JSONB,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX idx_saga_steps_saga ON saga_steps(saga_id);

-- =====================================================
-- Function Unit Tables
-- =====================================================

CREATE TABLE IF NOT EXISTS function_units (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    version VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    category VARCHAR(100),
    icon VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    db_version BIGINT DEFAULT 0
);

CREATE INDEX idx_function_units_code ON function_units(code);
CREATE INDEX idx_function_units_status ON function_units(status);

CREATE TABLE IF NOT EXISTS function_unit_deployments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    function_unit_id UUID NOT NULL REFERENCES function_units(id),
    environment VARCHAR(50) NOT NULL,
    deployed_version VARCHAR(50) NOT NULL,
    deployed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deployed_by UUID,
    status VARCHAR(20) NOT NULL,
    rollback_version VARCHAR(50),
    notes TEXT
);

CREATE INDEX idx_deployments_unit ON function_unit_deployments(function_unit_id);
CREATE INDEX idx_deployments_env ON function_unit_deployments(environment);

-- =====================================================
-- Data Dictionary Tables
-- =====================================================

CREATE TABLE IF NOT EXISTS data_dictionaries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS data_dictionary_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    dictionary_id UUID NOT NULL REFERENCES data_dictionaries(id),
    item_code VARCHAR(100) NOT NULL,
    item_value VARCHAR(500) NOT NULL,
    label_en VARCHAR(200),
    label_zh_cn VARCHAR(200),
    label_zh_tw VARCHAR(200),
    sort_order INT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(dictionary_id, item_code)
);

-- =====================================================
-- Initial Data
-- =====================================================

-- Insert default admin role
INSERT INTO roles (id, code, name, description, role_type, status)
VALUES (uuid_generate_v4(), 'ADMIN', 'System Administrator', 'Full system access', 'SYSTEM', 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

-- Insert default user role
INSERT INTO roles (id, code, name, description, role_type, status)
VALUES (uuid_generate_v4(), 'USER', 'Standard User', 'Basic user access', 'SYSTEM', 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

-- Insert basic permissions
INSERT INTO permissions (id, code, name, permission_type, resource_type, action)
VALUES 
    (uuid_generate_v4(), 'USER_VIEW', 'View Users', 'MODULE', 'USER', 'VIEW'),
    (uuid_generate_v4(), 'USER_CREATE', 'Create Users', 'MODULE', 'USER', 'CREATE'),
    (uuid_generate_v4(), 'USER_UPDATE', 'Update Users', 'MODULE', 'USER', 'UPDATE'),
    (uuid_generate_v4(), 'USER_DELETE', 'Delete Users', 'MODULE', 'USER', 'DELETE'),
    (uuid_generate_v4(), 'ROLE_MANAGE', 'Manage Roles', 'MODULE', 'ROLE', 'MANAGE'),
    (uuid_generate_v4(), 'WORKFLOW_VIEW', 'View Workflows', 'MODULE', 'WORKFLOW', 'VIEW'),
    (uuid_generate_v4(), 'WORKFLOW_CREATE', 'Create Workflows', 'MODULE', 'WORKFLOW', 'CREATE'),
    (uuid_generate_v4(), 'WORKFLOW_DEPLOY', 'Deploy Workflows', 'MODULE', 'WORKFLOW', 'DEPLOY'),
    (uuid_generate_v4(), 'FUNCTION_UNIT_VIEW', 'View Function Units', 'MODULE', 'FUNCTION_UNIT', 'VIEW'),
    (uuid_generate_v4(), 'FUNCTION_UNIT_CREATE', 'Create Function Units', 'MODULE', 'FUNCTION_UNIT', 'CREATE'),
    (uuid_generate_v4(), 'FUNCTION_UNIT_DEPLOY', 'Deploy Function Units', 'MODULE', 'FUNCTION_UNIT', 'DEPLOY')
ON CONFLICT (code) DO NOTHING;
