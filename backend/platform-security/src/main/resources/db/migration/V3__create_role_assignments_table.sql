-- 创建角色分配表
-- 支持将角色分配给用户、部门、部门层级或虚拟组

CREATE TABLE IF NOT EXISTS sys_role_assignments (
    id VARCHAR(64) PRIMARY KEY,
    role_id VARCHAR(64) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(64),
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 唯一约束：同一角色不能重复分配给同一目标
    CONSTRAINT uk_role_target UNIQUE (role_id, target_type, target_id),
    
    -- 外键约束：角色必须存在
    CONSTRAINT fk_role_assignment_role FOREIGN KEY (role_id) REFERENCES sys_roles(id) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_role_assignments_role ON sys_role_assignments(role_id);
CREATE INDEX IF NOT EXISTS idx_role_assignments_target ON sys_role_assignments(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_role_assignments_valid ON sys_role_assignments(valid_from, valid_to);

-- 添加注释
COMMENT ON TABLE sys_role_assignments IS '角色分配表 - 支持将角色分配给用户、部门、部门层级或虚拟组';
COMMENT ON COLUMN sys_role_assignments.target_type IS '分配目标类型: USER, DEPARTMENT, DEPARTMENT_HIERARCHY, VIRTUAL_GROUP';
COMMENT ON COLUMN sys_role_assignments.target_id IS '分配目标ID: 用户ID/部门ID/虚拟组ID';
