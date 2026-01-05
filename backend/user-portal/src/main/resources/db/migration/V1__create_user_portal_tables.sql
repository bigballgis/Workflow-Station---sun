-- 用户门户数据库表结构

-- 用户偏好设置表
CREATE TABLE up_user_preference (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL UNIQUE,
    theme VARCHAR(20) DEFAULT 'light',
    theme_color VARCHAR(20) DEFAULT '#DB0011',
    font_size VARCHAR(10) DEFAULT 'medium',
    layout_density VARCHAR(10) DEFAULT 'normal',
    language VARCHAR(10) DEFAULT 'zh-CN',
    timezone VARCHAR(50) DEFAULT 'Asia/Shanghai',
    date_format VARCHAR(20) DEFAULT 'YYYY-MM-DD',
    page_size INTEGER DEFAULT 20,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_up_user_preference_user_id ON up_user_preference(user_id);

-- 工作台布局表
CREATE TABLE up_dashboard_layout (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    component_id VARCHAR(50) NOT NULL,
    component_type VARCHAR(50) NOT NULL,
    grid_x INTEGER NOT NULL,
    grid_y INTEGER NOT NULL,
    grid_w INTEGER NOT NULL,
    grid_h INTEGER NOT NULL,
    is_visible BOOLEAN DEFAULT TRUE,
    config JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, component_id)
);

CREATE INDEX idx_up_dashboard_layout_user_id ON up_dashboard_layout(user_id);

-- 通知偏好表
CREATE TABLE up_notification_preference (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    email_enabled BOOLEAN DEFAULT TRUE,
    browser_enabled BOOLEAN DEFAULT TRUE,
    in_app_enabled BOOLEAN DEFAULT TRUE,
    quiet_start_time TIME,
    quiet_end_time TIME,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, notification_type)
);

CREATE INDEX idx_up_notification_preference_user_id ON up_notification_preference(user_id);

-- 委托规则表
CREATE TABLE up_delegation_rule (
    id BIGSERIAL PRIMARY KEY,
    delegator_id VARCHAR(64) NOT NULL,
    delegate_id VARCHAR(64) NOT NULL,
    delegation_type VARCHAR(20) NOT NULL,
    process_types JSONB,
    priority_filter JSONB,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_up_delegation_rule_delegator_id ON up_delegation_rule(delegator_id);
CREATE INDEX idx_up_delegation_rule_delegate_id ON up_delegation_rule(delegate_id);
CREATE INDEX idx_up_delegation_rule_status ON up_delegation_rule(status);

-- 权限申请表
CREATE TABLE up_permission_request (
    id BIGSERIAL PRIMARY KEY,
    applicant_id VARCHAR(64) NOT NULL,
    request_type VARCHAR(20) NOT NULL,
    permissions JSONB NOT NULL,
    reason TEXT NOT NULL,
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    status VARCHAR(20) DEFAULT 'PENDING',
    approver_id VARCHAR(64),
    approve_time TIMESTAMP,
    approve_comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_up_permission_request_applicant_id ON up_permission_request(applicant_id);
CREATE INDEX idx_up_permission_request_status ON up_permission_request(status);

-- 收藏流程表
CREATE TABLE up_favorite_process (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    process_definition_key VARCHAR(255) NOT NULL,
    display_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, process_definition_key)
);

CREATE INDEX idx_up_favorite_process_user_id ON up_favorite_process(user_id);

-- 流程草稿表
CREATE TABLE up_process_draft (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    process_definition_key VARCHAR(255) NOT NULL,
    form_data JSONB NOT NULL,
    attachments JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_up_process_draft_user_id ON up_process_draft(user_id);
CREATE INDEX idx_up_process_draft_process_key ON up_process_draft(process_definition_key);

-- 委托审计记录表
CREATE TABLE up_delegation_audit (
    id BIGSERIAL PRIMARY KEY,
    delegator_id VARCHAR(64) NOT NULL,
    delegate_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64),
    operation_type VARCHAR(50) NOT NULL,
    operation_result VARCHAR(50),
    operation_detail TEXT,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_up_delegation_audit_delegator_id ON up_delegation_audit(delegator_id);
CREATE INDEX idx_up_delegation_audit_delegate_id ON up_delegation_audit(delegate_id);
CREATE INDEX idx_up_delegation_audit_task_id ON up_delegation_audit(task_id);
CREATE INDEX idx_up_delegation_audit_created_at ON up_delegation_audit(created_at);
