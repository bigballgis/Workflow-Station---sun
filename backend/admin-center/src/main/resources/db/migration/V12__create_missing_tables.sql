-- 系统配置表 (匹配 SystemConfig 实体)
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

-- 系统日志表 (匹配 SystemLog 实体)
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
CREATE INDEX IF NOT EXISTS idx_log_module ON admin_system_logs(module);

-- 安全策略表 (匹配 SecurityPolicy 实体)
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


-- 数据权限规则表 (匹配 DataPermissionRule 实体)
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

-- 列权限表 (匹配 ColumnPermission 实体)
CREATE TABLE IF NOT EXISTS admin_column_permissions (
    id VARCHAR(36) PRIMARY KEY,
    rule_id VARCHAR(36) NOT NULL,
    column_name VARCHAR(100) NOT NULL,
    visible BOOLEAN DEFAULT TRUE,
    masked BOOLEAN DEFAULT FALSE,
    mask_type VARCHAR(50),
    mask_expression VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (rule_id) REFERENCES admin_data_permission_rules(id)
);

CREATE INDEX IF NOT EXISTS idx_col_perm_rule ON admin_column_permissions(rule_id);
CREATE INDEX IF NOT EXISTS idx_col_perm_column ON admin_column_permissions(column_name);

-- 告警表 (匹配 Alert 实体)
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
    FOREIGN KEY (rule_id) REFERENCES admin_alert_rules(id)
);

CREATE INDEX IF NOT EXISTS idx_alert_status ON admin_alerts(status);
CREATE INDEX IF NOT EXISTS idx_alert_severity ON admin_alerts(severity);
CREATE INDEX IF NOT EXISTS idx_alert_created ON admin_alerts(created_at);

-- 审计日志表 (匹配 AuditLog 实体)
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
CREATE INDEX IF NOT EXISTS idx_audit_resource ON admin_audit_logs(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON admin_audit_logs(timestamp);

-- 配置变更历史表 (匹配 ConfigHistory 实体)
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

-- 日志保留策略表 (匹配 LogRetentionPolicy 实体)
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

-- 数据字典数据源表 (匹配 DictionaryDataSource 实体)
CREATE TABLE IF NOT EXISTS admin_dictionary_data_sources (
    id VARCHAR(36) PRIMARY KEY,
    dictionary_id VARCHAR(36) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    connection_string VARCHAR(500),
    table_name VARCHAR(200),
    code_field VARCHAR(100),
    name_field VARCHAR(100),
    value_field VARCHAR(100),
    filter_condition VARCHAR(500),
    order_by_field VARCHAR(100),
    cache_ttl INTEGER DEFAULT 300,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (dictionary_id) REFERENCES admin_dictionaries(id)
);

CREATE INDEX IF NOT EXISTS idx_dict_ds_dict_id ON admin_dictionary_data_sources(dictionary_id);

-- 密码历史表 (匹配 PasswordHistory 实体)
CREATE TABLE IF NOT EXISTS admin_password_history (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES admin_users(id)
);

CREATE INDEX IF NOT EXISTS idx_password_history_user ON admin_password_history(user_id);
