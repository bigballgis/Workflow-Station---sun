-- 部门表
CREATE TABLE admin_departments (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    parent_id VARCHAR(64),
    level INTEGER NOT NULL DEFAULT 1,
    path VARCHAR(500),
    manager_id VARCHAR(64),
    phone VARCHAR(50),
    description TEXT,
    cost_center VARCHAR(50),
    location VARCHAR(200),
    sort_order INTEGER DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64)
);

-- 部门索引
CREATE INDEX idx_dept_parent ON admin_departments(parent_id);
CREATE INDEX idx_dept_path ON admin_departments(path);
CREATE INDEX idx_dept_status ON admin_departments(status);
CREATE INDEX idx_dept_code ON admin_departments(code);
CREATE INDEX idx_dept_manager ON admin_departments(manager_id);

-- 添加外键约束（用户表的部门ID）
ALTER TABLE admin_users 
ADD CONSTRAINT fk_user_department 
FOREIGN KEY (department_id) REFERENCES admin_departments(id);
