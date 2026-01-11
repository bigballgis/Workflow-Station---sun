-- =====================================================
-- Database Schema Initialization
-- Version: 2.0 - Unified sys_* tables
-- =====================================================

-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =====================================================
-- CORE SYSTEM TABLES (sys_* prefix)
-- These tables are shared across all services
-- =====================================================

-- 1. Users Table
CREATE TABLE IF NOT EXISTS sys_users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(50),
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(50),
    employee_id VARCHAR(50),
    department_id VARCHAR(64),
    position VARCHAR(100),
    entity_manager_id VARCHAR(64),
    function_manager_id VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    language VARCHAR(10) DEFAULT 'zh_CN',
    must_change_password BOOLEAN DEFAULT false,
    password_expired_at TIMESTAMP,
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(50),
    failed_login_count INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(64),
    CONSTRAINT chk_sys_user_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED'))
);

CREATE INDEX IF NOT EXISTS idx_sys_users_username ON sys_users(username);
CREATE INDEX IF NOT EXISTS idx_sys_users_email ON sys_users(email);
CREATE INDEX IF NOT EXISTS idx_sys_users_status ON sys_users(status);
CREATE INDEX IF NOT EXISTS idx_sys_users_department ON sys_users(department_id);
CREATE INDEX IF NOT EXISTS idx_sys_users_employee_id ON sys_users(employee_id);
CREATE INDEX IF NOT EXISTS idx_sys_users_deleted ON sys_users(deleted);


-- 2. Departments Table
CREATE TABLE IF NOT EXISTS sys_departments (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    parent_id VARCHAR(64),
    level INTEGER DEFAULT 1,
    path VARCHAR(500),
    sort_order INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    description TEXT,
    cost_center VARCHAR(50),
    location VARCHAR(200),
    manager_id VARCHAR(64),
    secondary_manager_id VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    CONSTRAINT fk_dept_parent FOREIGN KEY (parent_id) REFERENCES sys_departments(id)
);

CREATE INDEX IF NOT EXISTS idx_sys_dept_parent ON sys_departments(parent_id);
CREATE INDEX IF NOT EXISTS idx_sys_dept_code ON sys_departments(code);
CREATE INDEX IF NOT EXISTS idx_sys_dept_path ON sys_departments(path);

-- 3. Roles Table
CREATE TABLE IF NOT EXISTS sys_roles (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'BUSINESS',
    description TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    is_system BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    CONSTRAINT chk_role_type CHECK (type IN ('ADMIN', 'DEVELOPER', 'BUSINESS'))
);

CREATE INDEX IF NOT EXISTS idx_sys_roles_code ON sys_roles(code);
CREATE INDEX IF NOT EXISTS idx_sys_roles_type ON sys_roles(type);

-- 4. User-Role Association Table
CREATE TABLE IF NOT EXISTS sys_user_roles (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(64),
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_roles(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_sys_user_roles_user ON sys_user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_roles_role ON sys_user_roles(role_id);

-- 5. Permissions Table
CREATE TABLE IF NOT EXISTS sys_permissions (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50),
    resource VARCHAR(100),
    action VARCHAR(50),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. Role-Permission Association Table
CREATE TABLE IF NOT EXISTS sys_role_permissions (
    id VARCHAR(64) PRIMARY KEY,
    role_id VARCHAR(64) NOT NULL,
    permission_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_role_perm_role FOREIGN KEY (role_id) REFERENCES sys_roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_perm_perm FOREIGN KEY (permission_id) REFERENCES sys_permissions(id) ON DELETE CASCADE,
    CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id)
);

-- 7. Login Audit Table
CREATE TABLE IF NOT EXISTS sys_login_audit (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(64),
    username VARCHAR(50) NOT NULL,
    action VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    success BOOLEAN DEFAULT true,
    failure_reason VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_login_audit_user ON sys_login_audit(user_id);
CREATE INDEX IF NOT EXISTS idx_login_audit_username ON sys_login_audit(username);
CREATE INDEX IF NOT EXISTS idx_login_audit_created ON sys_login_audit(created_at);

-- 8. Role Assignments Table (for flexible role assignment to users/groups/departments)
CREATE TABLE IF NOT EXISTS sys_role_assignments (
    id VARCHAR(64) PRIMARY KEY,
    role_id VARCHAR(64) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(64),
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    CONSTRAINT fk_role_assign_role FOREIGN KEY (role_id) REFERENCES sys_roles(id) ON DELETE CASCADE,
    CONSTRAINT uk_role_target UNIQUE (role_id, target_type, target_id),
    CONSTRAINT chk_target_type CHECK (target_type IN ('USER', 'GROUP', 'DEPARTMENT', 'DEPARTMENT_HIERARCHY', 'VIRTUAL_GROUP'))
);

CREATE INDEX IF NOT EXISTS idx_role_assign_role ON sys_role_assignments(role_id);
CREATE INDEX IF NOT EXISTS idx_role_assign_target ON sys_role_assignments(target_type, target_id);

-- Add foreign key from users to departments
ALTER TABLE sys_users ADD CONSTRAINT fk_user_department 
    FOREIGN KEY (department_id) REFERENCES sys_departments(id);

-- =====================================================
-- VIRTUAL GROUPS (sys_* prefix - shared across services)
-- =====================================================

-- Virtual Groups
CREATE TABLE IF NOT EXISTS sys_virtual_groups (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    type VARCHAR(50) DEFAULT 'STATIC',
    rule_expression TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS sys_virtual_group_members (
    id VARCHAR(64) PRIMARY KEY,
    group_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    added_by VARCHAR(64),
    CONSTRAINT fk_vg_member_group FOREIGN KEY (group_id) REFERENCES sys_virtual_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_vg_member_user FOREIGN KEY (user_id) REFERENCES sys_users(id) ON DELETE CASCADE,
    CONSTRAINT uk_vg_member UNIQUE (group_id, user_id)
);

CREATE TABLE IF NOT EXISTS sys_virtual_group_task_history (
    id VARCHAR(64) PRIMARY KEY,
    group_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    assigned_user_id VARCHAR(64),
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    status VARCHAR(20),
    CONSTRAINT fk_vg_task_group FOREIGN KEY (group_id) REFERENCES sys_virtual_groups(id)
);

-- =====================================================
-- ADMIN CENTER SPECIFIC TABLES (admin_* prefix)
-- =====================================================

-- Audit Logs
CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id VARCHAR(64) PRIMARY KEY,
    action VARCHAR(50) NOT NULL,
    user_id VARCHAR(64),
    username VARCHAR(100),
    resource_type VARCHAR(50),
    resource_id VARCHAR(64),
    old_value TEXT,
    new_value TEXT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_action ON admin_audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_admin_audit_user ON admin_audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_admin_audit_created ON admin_audit_logs(created_at);

-- System Configs
CREATE TABLE IF NOT EXISTS admin_system_configs (
    id VARCHAR(64) PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT,
    description TEXT,
    category VARCHAR(50),
    is_encrypted BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Permission Delegations
CREATE TABLE IF NOT EXISTS admin_permission_delegations (
    id VARCHAR(64) PRIMARY KEY,
    delegator_id VARCHAR(64) NOT NULL,
    delegatee_id VARCHAR(64) NOT NULL,
    permission_type VARCHAR(50),
    resource_type VARCHAR(50),
    resource_id VARCHAR(64),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_deleg_delegator FOREIGN KEY (delegator_id) REFERENCES sys_users(id),
    CONSTRAINT fk_deleg_delegatee FOREIGN KEY (delegatee_id) REFERENCES sys_users(id)
);

-- =====================================================
-- DEVELOPER WORKSTATION TABLES (dw_* prefix)
-- =====================================================

CREATE TABLE IF NOT EXISTS dw_function_units (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'DRAFT',
    version INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS dw_process_definitions (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64),
    name VARCHAR(100) NOT NULL,
    process_key VARCHAR(100) NOT NULL,
    bpmn_xml TEXT,
    version INTEGER DEFAULT 1,
    status VARCHAR(20) DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    CONSTRAINT fk_process_fu FOREIGN KEY (function_unit_id) REFERENCES dw_function_units(id)
);

CREATE TABLE IF NOT EXISTS dw_form_definitions (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64),
    name VARCHAR(100) NOT NULL,
    form_key VARCHAR(100) NOT NULL,
    schema_json TEXT,
    layout_json TEXT,
    version INTEGER DEFAULT 1,
    status VARCHAR(20) DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    CONSTRAINT fk_form_fu FOREIGN KEY (function_unit_id) REFERENCES dw_function_units(id)
);

CREATE TABLE IF NOT EXISTS dw_table_definitions (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64),
    name VARCHAR(100) NOT NULL,
    table_name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    CONSTRAINT fk_table_fu FOREIGN KEY (function_unit_id) REFERENCES dw_function_units(id)
);

CREATE TABLE IF NOT EXISTS dw_field_definitions (
    id VARCHAR(64) PRIMARY KEY,
    table_id VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    column_name VARCHAR(100) NOT NULL,
    data_type VARCHAR(50) NOT NULL,
    length INTEGER,
    is_nullable BOOLEAN DEFAULT true,
    is_primary_key BOOLEAN DEFAULT false,
    default_value TEXT,
    description TEXT,
    sort_order INTEGER DEFAULT 0,
    CONSTRAINT fk_field_table FOREIGN KEY (table_id) REFERENCES dw_table_definitions(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS dw_action_definitions (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64),
    name VARCHAR(100) NOT NULL,
    action_key VARCHAR(100) NOT NULL,
    type VARCHAR(50),
    config_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    CONSTRAINT fk_action_fu FOREIGN KEY (function_unit_id) REFERENCES dw_function_units(id)
);

CREATE TABLE IF NOT EXISTS dw_versions (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64) NOT NULL,
    version_number INTEGER NOT NULL,
    status VARCHAR(20) DEFAULT 'DRAFT',
    release_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    CONSTRAINT fk_version_fu FOREIGN KEY (function_unit_id) REFERENCES dw_function_units(id)
);

CREATE TABLE IF NOT EXISTS dw_icons (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    svg_content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dw_operation_logs (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64),
    operation VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50),
    resource_id VARCHAR(64),
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- USER PORTAL TABLES (up_* prefix)
-- =====================================================

CREATE TABLE IF NOT EXISTS up_process_instance (
    id VARCHAR(64) PRIMARY KEY,
    process_definition_key VARCHAR(100) NOT NULL,
    process_instance_id VARCHAR(64),
    business_key VARCHAR(100),
    initiator_id VARCHAR(64),
    title VARCHAR(200),
    status VARCHAR(20) DEFAULT 'RUNNING',
    variables_json TEXT,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS up_process_draft (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    process_definition_key VARCHAR(100) NOT NULL,
    title VARCHAR(200),
    form_data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS up_favorite_process (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    process_definition_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_favorite UNIQUE (user_id, process_definition_key)
);

CREATE TABLE IF NOT EXISTS up_user_preference (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL UNIQUE,
    theme VARCHAR(20) DEFAULT 'light',
    language VARCHAR(10) DEFAULT 'zh_CN',
    notifications_enabled BOOLEAN DEFAULT true,
    preferences_json TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS up_notification_preference (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    enabled BOOLEAN DEFAULT true,
    channels VARCHAR(100),
    CONSTRAINT uk_notif_pref UNIQUE (user_id, notification_type)
);

CREATE TABLE IF NOT EXISTS up_dashboard_layout (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    component_id VARCHAR(50) NOT NULL,
    position_x INTEGER DEFAULT 0,
    position_y INTEGER DEFAULT 0,
    width INTEGER DEFAULT 1,
    height INTEGER DEFAULT 1,
    config_json TEXT,
    CONSTRAINT uk_dashboard UNIQUE (user_id, component_id)
);

CREATE TABLE IF NOT EXISTS up_delegation_rule (
    id VARCHAR(64) PRIMARY KEY,
    delegator_id VARCHAR(64) NOT NULL,
    delegatee_id VARCHAR(64) NOT NULL,
    process_definition_key VARCHAR(100),
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS up_delegation_audit (
    id VARCHAR(64) PRIMARY KEY,
    delegation_id VARCHAR(64),
    action VARCHAR(50) NOT NULL,
    performed_by VARCHAR(64),
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS up_permission_request (
    id VARCHAR(64) PRIMARY KEY,
    requester_id VARCHAR(64) NOT NULL,
    permission_type VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50),
    resource_id VARCHAR(64),
    reason TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    reviewer_id VARCHAR(64),
    review_comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP
);

-- =====================================================
-- WORKFLOW ENGINE TABLES (wf_* prefix)
-- =====================================================

CREATE TABLE IF NOT EXISTS wf_extended_task_info (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL UNIQUE,
    process_instance_id VARCHAR(64),
    assignment_type VARCHAR(20),
    original_assignee VARCHAR(64),
    delegated_from VARCHAR(64),
    priority INTEGER DEFAULT 50,
    due_date TIMESTAMP,
    reminder_sent BOOLEAN DEFAULT false,
    custom_data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wf_task_id ON wf_extended_task_info(task_id);
CREATE INDEX IF NOT EXISTS idx_wf_task_process ON wf_extended_task_info(process_instance_id);

CREATE TABLE IF NOT EXISTS wf_process_variables (
    id VARCHAR(64) PRIMARY KEY,
    process_instance_id VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50),
    value TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wf_var_process ON wf_process_variables(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_var_name ON wf_process_variables(name);

CREATE TABLE IF NOT EXISTS wf_audit_logs (
    id VARCHAR(64) PRIMARY KEY,
    process_instance_id VARCHAR(64),
    task_id VARCHAR(64),
    user_id VARCHAR(64),
    operation_type VARCHAR(50) NOT NULL,
    operation_detail TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wf_audit_process ON wf_audit_logs(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_audit_user ON wf_audit_logs(user_id);

CREATE TABLE IF NOT EXISTS wf_exception_records (
    id VARCHAR(64) PRIMARY KEY,
    process_instance_id VARCHAR(64),
    task_id VARCHAR(64),
    exception_type VARCHAR(100),
    exception_message TEXT,
    stack_trace TEXT,
    handled BOOLEAN DEFAULT false,
    handled_by VARCHAR(64),
    handled_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- DICTIONARY TABLES
-- =====================================================

CREATE TABLE IF NOT EXISTS sys_dictionaries (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50),
    description TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    version INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_dictionary_items (
    id VARCHAR(64) PRIMARY KEY,
    dictionary_id VARCHAR(64) NOT NULL,
    item_code VARCHAR(50) NOT NULL,
    item_value VARCHAR(200) NOT NULL,
    name VARCHAR(200),
    label VARCHAR(100),
    sort_order INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    extra_data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dict_item FOREIGN KEY (dictionary_id) REFERENCES sys_dictionaries(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_dict_item_dict ON sys_dictionary_items(dictionary_id);

-- =====================================================
-- FUNCTION UNIT ACCESS CONTROL
-- =====================================================

CREATE TABLE IF NOT EXISTS sys_function_unit_access (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    access_level VARCHAR(20) DEFAULT 'READ',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    CONSTRAINT uk_fu_access UNIQUE (function_unit_id, role_id)
);

CREATE TABLE IF NOT EXISTS sys_function_unit_approvals (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    submitted_by VARCHAR(64),
    submitted_at TIMESTAMP,
    reviewed_by VARCHAR(64),
    reviewed_at TIMESTAMP,
    review_comment TEXT
);

CREATE TABLE IF NOT EXISTS sys_function_unit_deployments (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    environment VARCHAR(20) NOT NULL,
    deployed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deployed_by VARCHAR(64),
    status VARCHAR(20) DEFAULT 'SUCCESS'
);

-- =====================================================
-- DEVELOPER ROLE PERMISSIONS
-- =====================================================

CREATE TABLE IF NOT EXISTS sys_developer_role_permissions (
    id VARCHAR(64) PRIMARY KEY,
    role_id VARCHAR(64) NOT NULL,
    permission VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    CONSTRAINT uk_dev_role_perm UNIQUE (role_id, permission)
);

COMMENT ON TABLE sys_users IS 'Unified user table for all services';
COMMENT ON TABLE sys_departments IS 'Organization/Department hierarchy';
COMMENT ON TABLE sys_roles IS 'Role definitions';
COMMENT ON TABLE sys_user_roles IS 'User-Role associations';
COMMENT ON TABLE sys_permissions IS 'Permission definitions';
COMMENT ON TABLE sys_role_permissions IS 'Role-Permission associations';
