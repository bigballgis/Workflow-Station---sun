-- 权限变更历史表
CREATE TABLE admin_permission_change_history (
    id VARCHAR(36) PRIMARY KEY,
    change_type VARCHAR(50) NOT NULL,
    target_user_id VARCHAR(36),
    target_role_id VARCHAR(36),
    target_permission_id VARCHAR(36),
    old_value VARCHAR(500),
    new_value VARCHAR(500),
    reason VARCHAR(500),
    changed_by VARCHAR(36) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500)
);

-- 索引
CREATE INDEX idx_pch_target_user_id ON admin_permission_change_history(target_user_id);
CREATE INDEX idx_pch_target_role_id ON admin_permission_change_history(target_role_id);
CREATE INDEX idx_pch_change_type ON admin_permission_change_history(change_type);
CREATE INDEX idx_pch_changed_by ON admin_permission_change_history(changed_by);
CREATE INDEX idx_pch_changed_at ON admin_permission_change_history(changed_at);

-- 注释
COMMENT ON TABLE admin_permission_change_history IS '权限变更历史表';
COMMENT ON COLUMN admin_permission_change_history.id IS '主键ID';
COMMENT ON COLUMN admin_permission_change_history.change_type IS '变更类型: ROLE_ASSIGNED, ROLE_REMOVED, PERMISSION_GRANTED, PERMISSION_REVOKED, DELEGATION_CREATED, DELEGATION_REVOKED';
COMMENT ON COLUMN admin_permission_change_history.target_user_id IS '目标用户ID';
COMMENT ON COLUMN admin_permission_change_history.target_role_id IS '目标角色ID';
COMMENT ON COLUMN admin_permission_change_history.target_permission_id IS '目标权限ID';
COMMENT ON COLUMN admin_permission_change_history.old_value IS '变更前值';
COMMENT ON COLUMN admin_permission_change_history.new_value IS '变更后值';
COMMENT ON COLUMN admin_permission_change_history.reason IS '变更原因';
COMMENT ON COLUMN admin_permission_change_history.changed_by IS '操作人ID';
COMMENT ON COLUMN admin_permission_change_history.changed_at IS '变更时间';
COMMENT ON COLUMN admin_permission_change_history.ip_address IS 'IP地址';
COMMENT ON COLUMN admin_permission_change_history.user_agent IS '用户代理';
