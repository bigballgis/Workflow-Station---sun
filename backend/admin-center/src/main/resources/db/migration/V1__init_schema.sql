-- =====================================================
-- Admin Center V1: All Admin Tables
-- Tables with admin_* prefix for admin-center specific features
-- Note: sys_* tables are managed by platform-security
-- =====================================================

-- =====================================================
-- 1. Password History (admin_password_history)
-- =====================================================
CREATE TABLE IF NOT EXISTS admin_password_history (
    id VARCHAR(64) PRIMARY KEY,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    password_hash VARCHAR(255) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    CONSTRAINT fk_password_history_user FOREIGN KEY (user_id) REFERENCES sys_users(id)
);

CREATE INDEX IF NOT EXISTS idx_password_history_user ON admin_password_history(user_id);
CREATE INDEX IF NOT EXISTS idx_password_history_created ON admin_password_history(created_at);

-- =====================================================
-- 2. Permission Delegations (admin_permission_delegations)
-- =====================================================
CREATE TABLE IF NOT EXISTS admin_permission_delegations (
    id VARCHAR(64) PRIMARY KEY,
    delegator_id VARCHAR(64) NOT NULL,
    delegatee_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    conditions JSONB,
    created_by VARCHAR(64),
    delegation_type VARCHAR(20) NOT NULL,
    revoke_reason TEXT,
    revoked_at TIMESTAMP(6) WITH TIME ZONE,
    revoked_by VARCHAR(64),
    valid_from TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    valid_to TIMESTAMP(6) WITH TIME ZONE,
    permission_id VARCHAR(64) NOT NULL,
    CONSTRAINT fk_deleg_delegator FOREIGN KEY (delegator_id) REFERENCES sys_users(id),
    CONSTRAINT fk_deleg_delegatee FOREIGN KEY (delegatee_id) REFERENCES sys_users(id),
    CONSTRAINT chk_delegation_type CHECK (delegation_type IN ('TEMPORARY', 'PROXY', 'TRANSFER'))
);

CREATE INDEX IF NOT EXISTS idx_delegation_delegator ON admin_permission_delegations(delegator_id);
CREATE INDEX IF NOT EXISTS idx_delegation_delegatee ON admin_permission_delegations(delegatee_id);
CREATE INDEX IF NOT EXISTS idx_delegation_status ON admin_permission_delegations(status);

-- =====================================================
-- 3. Permission Conflicts (admin_permission_conflicts)
-- =====================================================
CREATE TABLE IF NOT EXISTS admin_permission_conflicts (
    id VARCHAR(64) PRIMARY KEY,
    conflict_description TEXT,
    conflict_source1 VARCHAR(100) NOT NULL,
    conflict_source2 VARCHAR(100) NOT NULL,
    detected_at TIMESTAMP(6) WITH TIME ZONE,
    resolution_result TEXT,
    resolution_strategy VARCHAR(30),
    resolved_at TIMESTAMP(6) WITH TIME ZONE,
    resolved_by VARCHAR(64),
    status VARCHAR(20) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    permission_id VARCHAR(64) NOT NULL,
    CONSTRAINT chk_resolution_strategy CHECK (resolution_strategy IN ('DENY', 'ALLOW', 'HIGHEST_PRIVILEGE', 'LOWEST_PRIVILEGE', 'LATEST', 'MANUAL'))
);

CREATE INDEX IF NOT EXISTS idx_conflict_user ON admin_permission_conflicts(user_id);
CREATE INDEX IF NOT EXISTS idx_conflict_status ON admin_permission_conflicts(status);

-- =====================================================
-- 4. Permission Change History (admin_permission_change_history)
-- =====================================================
CREATE TABLE IF NOT EXISTS admin_permission_change_history (
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

CREATE INDEX IF NOT EXISTS idx_pch_target_user_id ON admin_permission_change_history(target_user_id);
CREATE INDEX IF NOT EXISTS idx_pch_change_type ON admin_permission_change_history(change_type);
CREATE INDEX IF NOT EXISTS idx_pch_changed_at ON admin_permission_change_history(changed_at);


-- =====================================================
-- 5. Alert Rules (admin_alert_rules)
-- =====================================================
CREATE TABLE IF NOT EXISTS admin_alert_rules (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    metric_name VARCHAR(50),
    operator VARCHAR(20),
    threshold DOUBLE PRECISION,
    duration INTEGER,
    severity VARCHAR(20),
    notify_channels VARCHAR(500),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alert_rules_name ON admin_alert_rules(name);
CREATE INDEX IF NOT EXISTS idx_alert_rules_enabled ON admin_alert_rules(enabled);

-- =====================================================
-- 6. Alerts (admin_alerts)
-- =====================================================
CREATE TABLE IF NOT EXISTS admin_alerts (
    id VARCHAR(36) PRIMARY KEY,
    rule_id VARCHAR(36),
    title VARCHAR(200) NOT NULL,
    message TEXT,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    metric_value DOUBLE PRECISION,
    acknowledged_by VARCHAR(36),
    acknowledged_at TIMESTAMP,
    resolved_by VARCHAR(36),
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alert_rule FOREIGN KEY (rule_id) REFERENCES admin_alert_rules(id)
);

CREATE INDEX IF NOT EXISTS idx_alert_status ON admin_alerts(status);
CREATE INDEX IF NOT EXISTS idx_alert_severity ON admin_alerts(severity);

-- =====================================================
-- 7. System Configs (admin_system_configs)
-- =====================================================
CREATE TABLE IF NOT EXISTS admin_system_configs (
    id VARCHAR(36) PRIMARY KEY,
    category VARCHAR(50) NOT NULL,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_name VARCHAR(100) NOT NULL,
    config_value TEXT,
    default_value VARCHAR(500),
    value_type VARCHAR(20),
    description VARCHAR(500),
    encrypted BOOLEAN DEFAULT FALSE,
    editable BOOLEAN DEFAULT TRUE,
    version INTEGER,
    environment VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_config_category ON admin_system_configs(category);
CREATE INDEX IF NOT EXISTS idx_config_key ON admin_system_configs(config_key);

-- =====================================================
-- 8. System Logs (admin_system_logs)
-- =====================================================
CREATE TABLE IF NOT EXISTS admin_system_logs (
    id VARCHAR(36) PRIMARY KEY,
    log_type VARCHAR(50) NOT NULL,
    log_level VARCHAR(20) NOT NULL,
    module VARCHAR(100),
    action VARCHAR(100),
    message TEXT,
    stack_trace TEXT,
    user_id VARCHAR(64),
    user_name VARCHAR(100),
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    request_url VARCHAR(500),
    request_method VARCHAR(10),
    response_time BIGINT,
    response_status INTEGER,
    request_body TEXT,
    response_body TEXT,
    extra_data TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_log_type ON admin_system_logs(log_type);
CREATE INDEX IF NOT EXISTS idx_log_level ON admin_system_logs(log_level);
CREATE INDEX IF NOT EXISTS idx_log_timestamp ON admin_system_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_log_user ON admin_system_logs(user_id);

-- =====================================================
-- 9. Security Policies (admin_security_policies)
-- =====================================================
CREATE TABLE IF NOT EXISTS admin_security_policies (
    id VARCHAR(36) PRIMARY KEY,
    policy_type VARCHAR(50) NOT NULL UNIQUE,
    policy_name VARCHAR(100) NOT NULL,
    policy_config TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64)
);

-- =====================================================
-- 10. Data Permission Rules (admin_data_permission_rules)
-- =====================================================
CREATE TABLE IF NOT EXISTS admin_data_permission_rules (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    permission_type VARCHAR(20) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(36) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    data_scope VARCHAR(30) NOT NULL,
    custom_filter TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 100,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dp_rule_type ON admin_data_permission_rules(permission_type);
CREATE INDEX IF NOT EXISTS idx_dp_rule_target ON admin_data_permission_rules(target_type, target_id);

-- =====================================================
-- 11. Column Permissions (admin_column_permissions)
-- =====================================================
CREATE TABLE IF NOT EXISTS admin_column_permissions (
    id VARCHAR(36) PRIMARY KEY,
    rule_id VARCHAR(36) NOT NULL,
    column_name VARCHAR(100) NOT NULL,
    visible BOOLEAN DEFAULT TRUE,
    masked BOOLEAN DEFAULT FALSE,
    mask_type VARCHAR(50),
    mask_expression VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_col_perm_rule FOREIGN KEY (rule_id) REFERENCES admin_data_permission_rules(id)
);

CREATE INDEX IF NOT EXISTS idx_col_perm_rule ON admin_column_permissions(rule_id);

-- =====================================================
-- 12. Audit Logs (admin_audit_logs)
-- =====================================================
CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(64),
    resource_name VARCHAR(200),
    user_id VARCHAR(64) NOT NULL,
    user_name VARCHAR(100),
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    old_value TEXT,
    new_value TEXT,
    change_details TEXT,
    success BOOLEAN,
    failure_reason VARCHAR(500),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_action ON admin_audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_user ON admin_audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON admin_audit_logs(timestamp);

-- =====================================================
-- 13. Config History (admin_config_history)
-- =====================================================
CREATE TABLE IF NOT EXISTS admin_config_history (
    id VARCHAR(36) PRIMARY KEY,
    config_id VARCHAR(36) NOT NULL,
    config_key VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    old_version INTEGER,
    new_version INTEGER,
    change_reason VARCHAR(500),
    changed_by VARCHAR(64),
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_config_history_key ON admin_config_history(config_key);
CREATE INDEX IF NOT EXISTS idx_config_history_time ON admin_config_history(changed_at);

-- =====================================================
-- 14. Log Retention Policies (admin_log_retention_policies)
-- =====================================================
CREATE TABLE IF NOT EXISTS admin_log_retention_policies (
    id VARCHAR(36) PRIMARY KEY,
    log_type VARCHAR(50) NOT NULL UNIQUE,
    retention_days INTEGER NOT NULL,
    archive_after_days INTEGER,
    archive_location VARCHAR(500),
    compression_enabled BOOLEAN DEFAULT FALSE,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64)
);

-- Comments
COMMENT ON TABLE admin_password_history IS 'Password history for password policy enforcement';
COMMENT ON TABLE admin_permission_delegations IS 'Permission delegation records';
COMMENT ON TABLE admin_permission_conflicts IS 'Permission conflict tracking';
COMMENT ON TABLE admin_alert_rules IS 'Alert rules configuration';
COMMENT ON TABLE admin_system_configs IS 'System configuration';
COMMENT ON TABLE admin_audit_logs IS 'Audit trail';
