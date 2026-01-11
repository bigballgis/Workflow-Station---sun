-- =====================================================
-- System Data Initialization
-- Version: 2.0
-- Note: This file contains ONLY system configuration data
--       User data should be managed separately for each environment
-- =====================================================

-- =====================================================
-- 1. System Roles
-- =====================================================

-- Admin Center Roles
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
    ('SYS_ADMIN_ROLE', 'SYS_ADMIN', 'System Administrator', 'ADMIN', 'Full system access', 'ACTIVE', true, NOW(), NOW()),
    ('AUDITOR_ROLE', 'AUDITOR', 'Auditor', 'ADMIN', 'Read-only access to audit logs', 'ACTIVE', true, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- Developer Workstation Roles
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
    ('TECH_DIRECTOR_ROLE', 'TECH_DIRECTOR', 'Technical Director', 'DEVELOPER', 'Full development access with approval rights', 'ACTIVE', true, NOW(), NOW()),
    ('TEAM_LEADER_ROLE', 'TEAM_LEADER', 'Team Leader', 'DEVELOPER', 'Team management and code review', 'ACTIVE', true, NOW(), NOW()),
    ('DEVELOPER_ROLE', 'DEVELOPER', 'Developer', 'DEVELOPER', 'Development and testing access', 'ACTIVE', true, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- User Portal Roles (System roles only - test roles are in test data)
-- No system roles for User Portal - all business roles are test data

-- =====================================================
-- 2. Developer Role Permissions
-- =====================================================

-- Tech Director Permissions
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
VALUES 
    ('perm-td-1', 'TECH_DIRECTOR_ROLE', 'function_unit:create', NOW()),
    ('perm-td-2', 'TECH_DIRECTOR_ROLE', 'function_unit:edit', NOW()),
    ('perm-td-3', 'TECH_DIRECTOR_ROLE', 'function_unit:delete', NOW()),
    ('perm-td-4', 'TECH_DIRECTOR_ROLE', 'function_unit:deploy', NOW()),
    ('perm-td-5', 'TECH_DIRECTOR_ROLE', 'function_unit:approve', NOW()),
    ('perm-td-6', 'TECH_DIRECTOR_ROLE', 'process:design', NOW()),
    ('perm-td-7', 'TECH_DIRECTOR_ROLE', 'form:design', NOW()),
    ('perm-td-8', 'TECH_DIRECTOR_ROLE', 'table:design', NOW()),
    ('perm-td-9', 'TECH_DIRECTOR_ROLE', 'team:manage', NOW())
ON CONFLICT (role_id, permission) DO NOTHING;

-- Team Leader Permissions
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
VALUES 
    ('perm-tl-1', 'TEAM_LEADER_ROLE', 'function_unit:create', NOW()),
    ('perm-tl-2', 'TEAM_LEADER_ROLE', 'function_unit:edit', NOW()),
    ('perm-tl-3', 'TEAM_LEADER_ROLE', 'function_unit:submit', NOW()),
    ('perm-tl-4', 'TEAM_LEADER_ROLE', 'process:design', NOW()),
    ('perm-tl-5', 'TEAM_LEADER_ROLE', 'form:design', NOW()),
    ('perm-tl-6', 'TEAM_LEADER_ROLE', 'table:design', NOW()),
    ('perm-tl-7', 'TEAM_LEADER_ROLE', 'code:review', NOW())
ON CONFLICT (role_id, permission) DO NOTHING;

-- Developer Permissions
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
VALUES 
    ('perm-dev-1', 'DEVELOPER_ROLE', 'function_unit:create', NOW()),
    ('perm-dev-2', 'DEVELOPER_ROLE', 'function_unit:edit', NOW()),
    ('perm-dev-3', 'DEVELOPER_ROLE', 'process:design', NOW()),
    ('perm-dev-4', 'DEVELOPER_ROLE', 'form:design', NOW()),
    ('perm-dev-5', 'DEVELOPER_ROLE', 'table:design', NOW())
ON CONFLICT (role_id, permission) DO NOTHING;

-- =====================================================
-- 3. Default Icons
-- =====================================================

INSERT INTO dw_icons (id, name, category, svg_content, created_at)
VALUES 
    ('icon-process', 'Process', 'workflow', '<svg viewBox="0 0 24 24"><path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/></svg>', NOW()),
    ('icon-form', 'Form', 'ui', '<svg viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="9" y1="9" x2="15" y2="9"/><line x1="9" y1="13" x2="15" y2="13"/></svg>', NOW()),
    ('icon-table', 'Table', 'data', '<svg viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="3" y1="15" x2="21" y2="15"/><line x1="9" y1="3" x2="9" y2="21"/></svg>', NOW()),
    ('icon-user', 'User', 'system', '<svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 4-6 8-6s8 2 8 6"/></svg>', NOW()),
    ('icon-settings', 'Settings', 'system', '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M12 1v4M12 19v4M4.22 4.22l2.83 2.83M16.95 16.95l2.83 2.83M1 12h4M19 12h4M4.22 19.78l2.83-2.83M16.95 7.05l2.83-2.83"/></svg>', NOW())
ON CONFLICT DO NOTHING;

-- =====================================================
-- 4. System Dictionaries
-- =====================================================

INSERT INTO sys_dictionaries (id, code, name, type, description, status, created_at)
VALUES 
    ('dict-status', 'STATUS', 'Status', 'SYSTEM', 'Common status values', 'ACTIVE', NOW()),
    ('dict-priority', 'PRIORITY', 'Priority', 'SYSTEM', 'Task priority levels', 'ACTIVE', NOW()),
    ('dict-approval', 'APPROVAL_STATUS', 'Approval Status', 'WORKFLOW', 'Approval workflow status', 'ACTIVE', NOW())
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_dictionary_items (id, dictionary_id, item_code, item_value, label, sort_order, status)
VALUES 
    ('item-status-1', 'dict-status', 'ACTIVE', 'Active', '启用', 1, 'ACTIVE'),
    ('item-status-2', 'dict-status', 'INACTIVE', 'Inactive', '禁用', 2, 'ACTIVE'),
    ('item-status-3', 'dict-status', 'PENDING', 'Pending', '待处理', 3, 'ACTIVE'),
    ('item-priority-1', 'dict-priority', 'HIGH', 'High', '高', 1, 'ACTIVE'),
    ('item-priority-2', 'dict-priority', 'MEDIUM', 'Medium', '中', 2, 'ACTIVE'),
    ('item-priority-3', 'dict-priority', 'LOW', 'Low', '低', 3, 'ACTIVE'),
    ('item-approval-1', 'dict-approval', 'PENDING', 'Pending', '待审批', 1, 'ACTIVE'),
    ('item-approval-2', 'dict-approval', 'APPROVED', 'Approved', '已通过', 2, 'ACTIVE'),
    ('item-approval-3', 'dict-approval', 'REJECTED', 'Rejected', '已拒绝', 3, 'ACTIVE')
ON CONFLICT DO NOTHING;

COMMENT ON TABLE sys_roles IS 'System roles initialized';
