-- 虚拟组表
CREATE TABLE admin_virtual_groups (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    description TEXT,
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64)
);

CREATE INDEX idx_vgroup_type ON admin_virtual_groups(type);
CREATE INDEX idx_vgroup_status ON admin_virtual_groups(status);
CREATE INDEX idx_vgroup_valid ON admin_virtual_groups(valid_from, valid_to);
CREATE INDEX idx_vgroup_name ON admin_virtual_groups(name);

-- 虚拟组成员表
CREATE TABLE admin_virtual_group_members (
    id VARCHAR(64) PRIMARY KEY,
    group_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(group_id, user_id),
    FOREIGN KEY (group_id) REFERENCES admin_virtual_groups(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES admin_users(id) ON DELETE CASCADE
);

CREATE INDEX idx_vgroup_member_group ON admin_virtual_group_members(group_id);
CREATE INDEX idx_vgroup_member_user ON admin_virtual_group_members(user_id);
CREATE INDEX idx_vgroup_member_role ON admin_virtual_group_members(role);
