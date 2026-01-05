-- 角色表
CREATE TABLE admin_roles (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL,
    parent_role_id VARCHAR(64),
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64)
);

CREATE INDEX idx_role_type ON admin_roles(type);
CREATE INDEX idx_role_parent ON admin_roles(parent_role_id);
CREATE INDEX idx_role_status ON admin_roles(status);
CREATE INDEX idx_role_code ON admin_roles(code);

-- 权限表
CREATE TABLE admin_permissions (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(100) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL,
    resource VARCHAR(200) NOT NULL,
    action VARCHAR(50) NOT NULL,
    parent_id VARCHAR(64),
    description TEXT,
    sort_order INTEGER DEFAULT 0
);

CREATE INDEX idx_perm_type ON admin_permissions(type);
CREATE INDEX idx_perm_resource ON admin_permissions(resource);
CREATE INDEX idx_perm_parent ON admin_permissions(parent_id);
CREATE INDEX idx_perm_code ON admin_permissions(code);

-- 角色权限关联表
CREATE TABLE admin_role_permissions (
    id VARCHAR(64) PRIMARY KEY,
    role_id VARCHAR(64) NOT NULL,
    permission_id VARCHAR(64) NOT NULL,
    condition_type VARCHAR(50),
    condition_value JSONB,
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    granted_by VARCHAR(64),
    UNIQUE(role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES admin_roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES admin_permissions(id) ON DELETE CASCADE
);

CREATE INDEX idx_role_perm_role ON admin_role_permissions(role_id);
CREATE INDEX idx_role_perm_perm ON admin_role_permissions(permission_id);

-- 权限委托表
CREATE TABLE admin_permission_delegations (
    id VARCHAR(64) PRIMARY KEY,
    from_user_id VARCHAR(64) NOT NULL,
    to_user_id VARCHAR(64) NOT NULL,
    permission_id VARCHAR(64) NOT NULL,
    reason TEXT,
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    FOREIGN KEY (from_user_id) REFERENCES admin_users(id),
    FOREIGN KEY (to_user_id) REFERENCES admin_users(id),
    FOREIGN KEY (permission_id) REFERENCES admin_permissions(id)
);

CREATE INDEX idx_delegation_from ON admin_permission_delegations(from_user_id);
CREATE INDEX idx_delegation_to ON admin_permission_delegations(to_user_id);
CREATE INDEX idx_delegation_status ON admin_permission_delegations(status);
