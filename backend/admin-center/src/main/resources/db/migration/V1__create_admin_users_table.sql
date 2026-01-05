-- 用户表
CREATE TABLE admin_users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    full_name VARCHAR(100) NOT NULL,
    employee_id VARCHAR(50),
    department_id VARCHAR(64),
    position VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    must_change_password BOOLEAN DEFAULT FALSE,
    password_expired_at TIMESTAMP,
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(50),
    failed_login_count INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64)
);

-- 用户索引
CREATE INDEX idx_user_username ON admin_users(username);
CREATE INDEX idx_user_email ON admin_users(email);
CREATE INDEX idx_user_department ON admin_users(department_id);
CREATE INDEX idx_user_status ON admin_users(status);
CREATE INDEX idx_user_employee_id ON admin_users(employee_id);

-- 用户角色关联表
CREATE TABLE admin_user_roles (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(64),
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    UNIQUE(user_id, role_id)
);

CREATE INDEX idx_user_roles_user ON admin_user_roles(user_id);
CREATE INDEX idx_user_roles_role ON admin_user_roles(role_id);

-- 密码历史表
CREATE TABLE admin_password_history (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_password_history_user ON admin_password_history(user_id);
