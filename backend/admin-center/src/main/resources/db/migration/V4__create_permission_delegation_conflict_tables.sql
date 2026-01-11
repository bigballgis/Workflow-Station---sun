-- 权限冲突表
CREATE TABLE admin_permission_conflicts (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    permission_id VARCHAR(64) NOT NULL,
    conflict_source1 VARCHAR(100) NOT NULL,
    conflict_source2 VARCHAR(100) NOT NULL,
    conflict_description TEXT,
    resolution_strategy VARCHAR(30),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    resolution_result TEXT,
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(64),
    
    FOREIGN KEY (permission_id) REFERENCES admin_permissions(id)
);

CREATE INDEX idx_conflict_user ON admin_permission_conflicts(user_id);
CREATE INDEX idx_conflict_permission ON admin_permission_conflicts(permission_id);
CREATE INDEX idx_conflict_status ON admin_permission_conflicts(status);
CREATE INDEX idx_conflict_strategy ON admin_permission_conflicts(resolution_strategy);
CREATE INDEX idx_conflict_detected ON admin_permission_conflicts(detected_at);

-- 添加权限冲突相关的注释
COMMENT ON TABLE admin_permission_conflicts IS '权限冲突表';
COMMENT ON COLUMN admin_permission_conflicts.conflict_source1 IS '冲突源1';
COMMENT ON COLUMN admin_permission_conflicts.conflict_source2 IS '冲突源2';
COMMENT ON COLUMN admin_permission_conflicts.resolution_strategy IS '解决策略：DENY-拒绝，ALLOW-允许，HIGHEST_PRIVILEGE-最高权限，LOWEST_PRIVILEGE-最低权限，LATEST-最新权限，MANUAL-手动解决';
COMMENT ON COLUMN admin_permission_conflicts.status IS '状态：PENDING-待解决，RESOLVED-已解决，IGNORED-已忽略';
