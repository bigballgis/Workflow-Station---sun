-- =====================================================
-- User Portal V1: All Tables (Consolidated)
-- Tables with up_* prefix for user portal features
-- Consolidated from V1-V2 migrations
-- =====================================================

-- =====================================================
-- 1. User Preferences (up_user_preference)
-- =====================================================
CREATE TABLE IF NOT EXISTS up_user_preference (
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

CREATE INDEX IF NOT EXISTS idx_up_user_preference_user_id ON up_user_preference(user_id);

-- =====================================================
-- 2. Dashboard Layout (up_dashboard_layout)
-- =====================================================
CREATE TABLE IF NOT EXISTS up_dashboard_layout (
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

CREATE INDEX IF NOT EXISTS idx_up_dashboard_layout_user_id ON up_dashboard_layout(user_id);

-- =====================================================
-- 3. Notification Preferences (up_notification_preference)
-- =====================================================
CREATE TABLE IF NOT EXISTS up_notification_preference (
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

CREATE INDEX IF NOT EXISTS idx_up_notification_preference_user_id ON up_notification_preference(user_id);

-- =====================================================
-- 4. Delegation Rules (up_delegation_rule)
-- =====================================================
CREATE TABLE IF NOT EXISTS up_delegation_rule (
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

CREATE INDEX IF NOT EXISTS idx_up_delegation_rule_delegator_id ON up_delegation_rule(delegator_id);
CREATE INDEX IF NOT EXISTS idx_up_delegation_rule_delegate_id ON up_delegation_rule(delegate_id);
CREATE INDEX IF NOT EXISTS idx_up_delegation_rule_status ON up_delegation_rule(status);

-- =====================================================
-- 5. Permission Requests (up_permission_request)
-- =====================================================
CREATE TABLE IF NOT EXISTS up_permission_request (
    id BIGSERIAL PRIMARY KEY,
    applicant_id VARCHAR(64) NOT NULL,
    request_type VARCHAR(30) NOT NULL,
    permissions JSONB,
    reason TEXT NOT NULL,
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    status VARCHAR(20) DEFAULT 'PENDING',
    approver_id VARCHAR(64),
    approve_time TIMESTAMP,
    approve_comment TEXT,
    -- Role request fields
    role_id VARCHAR(64),
    role_name VARCHAR(100),
    organization_unit_id VARCHAR(64),
    organization_unit_name VARCHAR(200),
    -- Virtual group request fields
    virtual_group_id VARCHAR(64),
    virtual_group_name VARCHAR(200),
    -- Business unit request fields
    business_unit_id VARCHAR(64),
    business_unit_name VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_up_permission_request_applicant_id ON up_permission_request(applicant_id);
CREATE INDEX IF NOT EXISTS idx_up_permission_request_status ON up_permission_request(status);

-- =====================================================
-- 6. Favorite Processes (up_favorite_process)
-- =====================================================
CREATE TABLE IF NOT EXISTS up_favorite_process (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    process_definition_key VARCHAR(255) NOT NULL,
    display_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, process_definition_key)
);

CREATE INDEX IF NOT EXISTS idx_up_favorite_process_user_id ON up_favorite_process(user_id);

-- =====================================================
-- 7. Process Drafts (up_process_draft)
-- =====================================================
CREATE TABLE IF NOT EXISTS up_process_draft (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    process_definition_key VARCHAR(255) NOT NULL,
    form_data JSONB NOT NULL,
    attachments JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_up_process_draft_user_id ON up_process_draft(user_id);
CREATE INDEX IF NOT EXISTS idx_up_process_draft_process_key ON up_process_draft(process_definition_key);

-- =====================================================
-- 8. Delegation Audit (up_delegation_audit)
-- =====================================================
CREATE TABLE IF NOT EXISTS up_delegation_audit (
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

CREATE INDEX IF NOT EXISTS idx_up_delegation_audit_delegator_id ON up_delegation_audit(delegator_id);
CREATE INDEX IF NOT EXISTS idx_up_delegation_audit_delegate_id ON up_delegation_audit(delegate_id);
CREATE INDEX IF NOT EXISTS idx_up_delegation_audit_task_id ON up_delegation_audit(task_id);
CREATE INDEX IF NOT EXISTS idx_up_delegation_audit_created_at ON up_delegation_audit(created_at);

-- =====================================================
-- 9. Process Instances (up_process_instance)
-- =====================================================
CREATE TABLE IF NOT EXISTS up_process_instance (
    id VARCHAR(64) PRIMARY KEY,
    process_definition_key VARCHAR(100) NOT NULL,
    process_definition_id VARCHAR(64),
    process_definition_name VARCHAR(255),
    process_instance_id VARCHAR(64),
    business_key VARCHAR(100),
    initiator_id VARCHAR(64),
    start_user_id VARCHAR(64) NOT NULL,
    start_user_name VARCHAR(100),
    title VARCHAR(200),
    status VARCHAR(20) DEFAULT 'RUNNING',
    priority VARCHAR(32),
    variables JSONB,
    variables_json TEXT,
    current_node VARCHAR(255),
    current_assignee VARCHAR(64),
    candidate_users VARCHAR(500),
    start_time TIMESTAMP(6),
    end_time TIMESTAMP(6),
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP(6)
);

CREATE INDEX IF NOT EXISTS idx_up_process_instance_user ON up_process_instance(start_user_id);
CREATE INDEX IF NOT EXISTS idx_up_process_instance_status ON up_process_instance(status);
CREATE INDEX IF NOT EXISTS idx_up_process_instance_key ON up_process_instance(process_definition_key);

-- =====================================================
-- 10. Process History (up_process_history)
-- =====================================================
CREATE TABLE IF NOT EXISTS up_process_history (
    id BIGSERIAL PRIMARY KEY,
    process_instance_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64),
    activity_id VARCHAR(100),
    activity_name VARCHAR(255),
    activity_type VARCHAR(50),
    operation_type VARCHAR(50) NOT NULL,
    operator_id VARCHAR(64) NOT NULL,
    operator_name VARCHAR(100),
    comment TEXT,
    duration BIGINT,
    operation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_process_history_instance ON up_process_history(process_instance_id);

-- =====================================================
-- Comments
-- =====================================================
COMMENT ON TABLE up_user_preference IS 'User preference settings';
COMMENT ON TABLE up_dashboard_layout IS 'Dashboard layout configuration';
COMMENT ON TABLE up_notification_preference IS 'Notification preferences';
COMMENT ON TABLE up_delegation_rule IS 'Delegation rules';
COMMENT ON TABLE up_permission_request IS 'Permission requests';
COMMENT ON TABLE up_favorite_process IS 'Favorite processes';
COMMENT ON TABLE up_process_draft IS 'Process drafts';
COMMENT ON TABLE up_delegation_audit IS 'Delegation audit logs';
COMMENT ON TABLE up_process_instance IS 'Process instances';
COMMENT ON TABLE up_process_history IS 'Process operation history';
