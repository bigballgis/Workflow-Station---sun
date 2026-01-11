-- =====================================================
-- Platform Security V1: Core System Tables
-- All sys_* tables for authentication and authorization
-- =====================================================

-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =====================================================
-- 1. Users Table (sys_users)
-- =====================================================
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

-- =====================================================
-- 2. Departments Table (sys_departments)
-- =====================================================
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

ALTER TABLE sys_users ADD CONSTRAINT fk_user_department 
    FOREIGN KEY (department_id) REFERENCES sys_departments(id);


-- =====================================================
-- 3. Roles Table (sys_roles)
-- =====================================================
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

-- =====================================================
-- 4. User-Role Association Table (sys_user_roles)
-- =====================================================
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

-- =====================================================
-- 5. Role Assignments Table (sys_role_assignments)
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_role_assignments (
    id VARCHAR(64) PRIMARY KEY,
    role_id VARCHAR(64) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(64),
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_target UNIQUE (role_id, target_type, target_id),
    CONSTRAINT fk_role_assignment_role FOREIGN KEY (role_id) REFERENCES sys_roles(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_role_assignments_role ON sys_role_assignments(role_id);
CREATE INDEX IF NOT EXISTS idx_role_assignments_target ON sys_role_assignments(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_role_assignments_valid ON sys_role_assignments(valid_from, valid_to);

-- =====================================================
-- 6. Permissions Table (sys_permissions)
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_permissions (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50),
    resource VARCHAR(100),
    action VARCHAR(50),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    parent_id VARCHAR(64),
    sort_order INTEGER
);

CREATE INDEX IF NOT EXISTS idx_sys_permissions_parent ON sys_permissions(parent_id);

-- =====================================================
-- 7. Role-Permission Association Table (sys_role_permissions)
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_role_permissions (
    id VARCHAR(64) PRIMARY KEY,
    role_id VARCHAR(64) NOT NULL,
    permission_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    condition_type VARCHAR(50),
    condition_value JSONB,
    granted_at TIMESTAMP(6) WITH TIME ZONE,
    granted_by VARCHAR(64),
    CONSTRAINT fk_role_perm_role FOREIGN KEY (role_id) REFERENCES sys_roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_perm_perm FOREIGN KEY (permission_id) REFERENCES sys_permissions(id) ON DELETE CASCADE,
    CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id)
);


-- =====================================================
-- 8. Login Audit Table (sys_login_audit)
-- =====================================================
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

-- =====================================================
-- 9. Virtual Groups (sys_virtual_groups)
-- =====================================================
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

-- =====================================================
-- 10. Virtual Group Members (sys_virtual_group_members)
-- =====================================================
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

-- =====================================================
-- 11. Virtual Group Task History (sys_virtual_group_task_history)
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_virtual_group_task_history (
    id VARCHAR(64) PRIMARY KEY,
    group_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    from_user_id VARCHAR(64),
    to_user_id VARCHAR(64),
    assigned_user_id VARCHAR(64),
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    status VARCHAR(20),
    reason TEXT,
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vg_task_group FOREIGN KEY (group_id) REFERENCES sys_virtual_groups(id),
    CONSTRAINT chk_vg_task_action_type CHECK (action_type IN ('CREATED', 'ASSIGNED', 'CLAIMED', 'DELEGATED', 'COMPLETED', 'CANCELLED', 'RETURNED'))
);

CREATE INDEX IF NOT EXISTS idx_vg_task_history_task ON sys_virtual_group_task_history(task_id);
CREATE INDEX IF NOT EXISTS idx_vg_task_history_group ON sys_virtual_group_task_history(group_id);
CREATE INDEX IF NOT EXISTS idx_vg_task_history_action ON sys_virtual_group_task_history(action_type);
CREATE INDEX IF NOT EXISTS idx_vg_task_history_created ON sys_virtual_group_task_history(created_at);

-- Comments
COMMENT ON TABLE sys_users IS 'Unified user table for all services';
COMMENT ON TABLE sys_departments IS 'Organization/Department hierarchy';
COMMENT ON TABLE sys_roles IS 'Role definitions';
COMMENT ON TABLE sys_user_roles IS 'User-Role associations';
COMMENT ON TABLE sys_role_assignments IS 'Role assignments to users/departments/groups';
COMMENT ON TABLE sys_permissions IS 'Permission definitions';
COMMENT ON TABLE sys_role_permissions IS 'Role-Permission associations';
COMMENT ON TABLE sys_login_audit IS 'Login/logout audit trail';
COMMENT ON TABLE sys_virtual_groups IS 'Virtual groups for cross-service role assignment';
COMMENT ON TABLE sys_virtual_group_members IS 'Virtual group member associations';
COMMENT ON TABLE sys_virtual_group_task_history IS 'Virtual group task assignment history';


-- =====================================================
-- 12. Dictionaries (sys_dictionaries)
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_dictionaries (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50),
    description TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    cache_ttl INTEGER,
    created_by VARCHAR(36),
    data_source_config TEXT,
    data_source_type VARCHAR(20),
    sort_order INTEGER,
    updated_by VARCHAR(36),
    version INTEGER DEFAULT 0,
    CONSTRAINT chk_dict_data_source_type CHECK (data_source_type IN ('DATABASE', 'API', 'FILE', 'STATIC'))
);

CREATE INDEX IF NOT EXISTS idx_dict_code ON sys_dictionaries(code);
CREATE INDEX IF NOT EXISTS idx_dict_type ON sys_dictionaries(type);
CREATE INDEX IF NOT EXISTS idx_dict_status ON sys_dictionaries(status);

-- =====================================================
-- 13. Dictionary Items (sys_dictionary_items)
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_dictionary_items (
    id VARCHAR(64) PRIMARY KEY,
    dictionary_id VARCHAR(64) NOT NULL,
    parent_id VARCHAR(64),
    item_code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    name_en VARCHAR(200),
    name_zh_cn VARCHAR(200),
    name_zh_tw VARCHAR(200),
    value VARCHAR(500),
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    sort_order INTEGER DEFAULT 0,
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    ext_attributes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(36),
    CONSTRAINT fk_dict_item FOREIGN KEY (dictionary_id) REFERENCES sys_dictionaries(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_dict_item_dict_id ON sys_dictionary_items(dictionary_id);
CREATE INDEX IF NOT EXISTS idx_dict_item_code ON sys_dictionary_items(dictionary_id, item_code);

-- =====================================================
-- 14. Dictionary Versions (sys_dictionary_versions)
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_dictionary_versions (
    id VARCHAR(36) PRIMARY KEY,
    dictionary_id VARCHAR(36) NOT NULL,
    version INTEGER NOT NULL,
    snapshot_data TEXT NOT NULL,
    change_description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    CONSTRAINT uk_dict_version UNIQUE (dictionary_id, version)
);

CREATE INDEX IF NOT EXISTS idx_dict_ver_dict_id ON sys_dictionary_versions(dictionary_id);

-- =====================================================
-- 15. Dictionary Data Sources (sys_dictionary_data_sources)
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_dictionary_data_sources (
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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dict_ds_dict_id ON sys_dictionary_data_sources(dictionary_id);

-- =====================================================
-- 16. Function Units (sys_function_units)
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_function_units (
    id VARCHAR(64) PRIMARY KEY,
    checksum VARCHAR(64),
    code VARCHAR(50) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    created_by VARCHAR(64),
    description TEXT,
    digital_signature TEXT,
    enabled BOOLEAN NOT NULL,
    imported_at TIMESTAMP(6) WITH TIME ZONE,
    imported_by VARCHAR(64),
    name VARCHAR(100) NOT NULL,
    package_path VARCHAR(500),
    package_size BIGINT,
    status VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    updated_by VARCHAR(64),
    validated_at TIMESTAMP(6) WITH TIME ZONE,
    validated_by VARCHAR(64),
    version VARCHAR(20) NOT NULL,
    CONSTRAINT chk_func_unit_status CHECK (status IN ('DRAFT', 'VALIDATED', 'DEPLOYED', 'DEPRECATED'))
);

-- =====================================================
-- 17. Function Unit Deployments (sys_function_unit_deployments)
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_function_unit_deployments (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    strategy VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    deployed_at TIMESTAMP,
    deployed_by VARCHAR(64),
    completed_at TIMESTAMP,
    rollback_to_id VARCHAR(64),
    error_message TEXT,
    deployment_log TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    CONSTRAINT fk_deployment_func_unit FOREIGN KEY (function_unit_id) REFERENCES sys_function_units(id)
);

CREATE INDEX IF NOT EXISTS idx_fu_deployment_func_unit ON sys_function_unit_deployments(function_unit_id);
CREATE INDEX IF NOT EXISTS idx_fu_deployment_status ON sys_function_unit_deployments(status);

-- =====================================================
-- 18. Function Unit Approvals (sys_function_unit_approvals)
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_function_unit_approvals (
    id VARCHAR(64) PRIMARY KEY,
    deployment_id VARCHAR(64) NOT NULL,
    approval_type VARCHAR(20) NOT NULL,
    approver_id VARCHAR(64) NOT NULL,
    approver_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    comment TEXT,
    approved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_approval_deployment FOREIGN KEY (deployment_id) REFERENCES sys_function_unit_deployments(id)
);

CREATE INDEX IF NOT EXISTS idx_fu_approval_deployment ON sys_function_unit_approvals(deployment_id);

-- =====================================================
-- 19. Function Unit Dependencies (sys_function_unit_dependencies)
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_function_unit_dependencies (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64) NOT NULL,
    dependency_code VARCHAR(50) NOT NULL,
    dependency_version VARCHAR(20) NOT NULL,
    dependency_type VARCHAR(20) NOT NULL DEFAULT 'REQUIRED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dependency_func_unit FOREIGN KEY (function_unit_id) REFERENCES sys_function_units(id)
);

CREATE INDEX IF NOT EXISTS idx_fu_dependency_func_unit ON sys_function_unit_dependencies(function_unit_id);

-- =====================================================
-- 20. Function Unit Contents (sys_function_unit_contents)
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_function_unit_contents (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64) NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    content_name VARCHAR(200) NOT NULL,
    content_path VARCHAR(500),
    content_data TEXT,
    checksum VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_content_func_unit FOREIGN KEY (function_unit_id) REFERENCES sys_function_units(id)
);

CREATE INDEX IF NOT EXISTS idx_fu_content_func_unit ON sys_function_unit_contents(function_unit_id);

-- =====================================================
-- 21. Function Unit Access (sys_function_unit_access)
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_function_unit_access (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64) NOT NULL,
    access_type VARCHAR(20) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    CONSTRAINT fk_access_func_unit FOREIGN KEY (function_unit_id) REFERENCES sys_function_units(id)
);

CREATE INDEX IF NOT EXISTS idx_fu_access_func_unit ON sys_function_unit_access(function_unit_id);

-- Additional Comments
COMMENT ON TABLE sys_dictionaries IS 'Data dictionaries';
COMMENT ON TABLE sys_dictionary_items IS 'Dictionary items';
COMMENT ON TABLE sys_function_units IS 'Function unit packages';
