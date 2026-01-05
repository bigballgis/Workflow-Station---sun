-- 权限委托表
CREATE TABLE admin_permission_delegations (
    id VARCHAR(64) PRIMARY KEY,
    delegator_id VARCHAR(64) NOT NULL,
    delegatee_id VARCHAR(64) NOT NULL,
    permission_id VARCHAR(64) NOT NULL,
    delegation_type VARCHAR(20) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP,
    reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    conditions JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    revoked_at TIMESTAMP,
    revoked_by VARCHAR(64),
    revoke_reason TEXT,
    
    FOREIGN KEY (permission_id) REFERENCES admin_permissions(id),
    INDEX idx_delegation_delegator (delegator_id),
    INDEX idx_delegation_delegatee (delegatee_id),
    INDEX idx_delegation_permission (permission_id),
    INDEX idx_delegation_status (status),
    INDEX idx_delegation_valid (valid_from, valid_to),
    INDEX idx_delegation_type (delegation_type)
);

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
    
    FOREIGN KEY (permission_id) REFERENCES admin_permissions(id),
    INDEX idx_conflict_user (user_id),
    INDEX idx_conflict_permission (permission_id),
    INDEX idx_conflict_status (status),
    INDEX idx_conflict_strategy (resolution_strategy),
    INDEX idx_conflict_detected (detected_at)
);

-- 添加权限委托相关的注释
COMMENT ON TABLE admin_permission_delegations IS '权限委托表';
COMMENT ON COLUMN admin_permission_delegations.delegator_id IS '委托人ID';
COMMENT ON COLUMN admin_permission_delegations.delegatee_id IS '受委托人ID';
COMMENT ON COLUMN admin_permission_delegations.delegation_type IS '委托类型：TEMPORARY-临时委托，PROXY-代理委托，TRANSFER-转移委托';
COMMENT ON COLUMN admin_permission_delegations.valid_from IS '委托生效时间';
COMMENT ON COLUMN admin_permission_delegations.valid_to IS '委托失效时间';
COMMENT ON COLUMN admin_permission_delegations.conditions IS '委托条件（JSON格式）';

-- 添加权限冲突相关的注释
COMMENT ON TABLE admin_permission_conflicts IS '权限冲突表';
COMMENT ON COLUMN admin_permission_conflicts.conflict_source1 IS '冲突源1';
COMMENT ON COLUMN admin_permission_conflicts.conflict_source2 IS '冲突源2';
COMMENT ON COLUMN admin_permission_conflicts.resolution_strategy IS '解决策略：DENY-拒绝，ALLOW-允许，HIGHEST_PRIVILEGE-最高权限，LOWEST_PRIVILEGE-最低权限，LATEST-最新权限，MANUAL-手动解决';
COMMENT ON COLUMN admin_permission_conflicts.status IS '状态：PENDING-待解决，RESOLVED-已解决，IGNORED-已忽略';