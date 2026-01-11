-- =====================================================
-- Platform Security V2: System Data Initialization
-- Note: This file initializes system roles and permissions
--       NO test users or sensitive data included
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

-- User Portal Roles
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
    ('BUSINESS_USER_ROLE', 'BUSINESS_USER', 'Business User', 'BUSINESS', 'Standard business user access', 'ACTIVE', true, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- =====================================================
-- 2. System Permissions
-- =====================================================

INSERT INTO sys_permissions (id, code, name, type, resource, action, description, created_at)
VALUES 
    -- Admin permissions
    ('perm-admin-user-read', 'ADMIN:USER:READ', 'View Users', 'ADMIN', 'user', 'read', 'View user list and details', NOW()),
    ('perm-admin-user-write', 'ADMIN:USER:WRITE', 'Manage Users', 'ADMIN', 'user', 'write', 'Create, update, delete users', NOW()),
    ('perm-admin-role-read', 'ADMIN:ROLE:READ', 'View Roles', 'ADMIN', 'role', 'read', 'View role list and details', NOW()),
    ('perm-admin-role-write', 'ADMIN:ROLE:WRITE', 'Manage Roles', 'ADMIN', 'role', 'write', 'Create, update, delete roles', NOW()),
    ('perm-admin-dept-read', 'ADMIN:DEPT:READ', 'View Departments', 'ADMIN', 'department', 'read', 'View department structure', NOW()),
    ('perm-admin-dept-write', 'ADMIN:DEPT:WRITE', 'Manage Departments', 'ADMIN', 'department', 'write', 'Create, update, delete departments', NOW()),
    ('perm-admin-audit-read', 'ADMIN:AUDIT:READ', 'View Audit Logs', 'ADMIN', 'audit', 'read', 'View audit logs', NOW()),
    ('perm-admin-config-read', 'ADMIN:CONFIG:READ', 'View System Config', 'ADMIN', 'config', 'read', 'View system configuration', NOW()),
    ('perm-admin-config-write', 'ADMIN:CONFIG:WRITE', 'Manage System Config', 'ADMIN', 'config', 'write', 'Modify system configuration', NOW()),
    
    -- Developer permissions
    ('perm-dev-fu-create', 'DEV:FU:CREATE', 'Create Function Unit', 'DEVELOPER', 'function_unit', 'create', 'Create new function units', NOW()),
    ('perm-dev-fu-edit', 'DEV:FU:EDIT', 'Edit Function Unit', 'DEVELOPER', 'function_unit', 'edit', 'Edit function units', NOW()),
    ('perm-dev-fu-delete', 'DEV:FU:DELETE', 'Delete Function Unit', 'DEVELOPER', 'function_unit', 'delete', 'Delete function units', NOW()),
    ('perm-dev-fu-deploy', 'DEV:FU:DEPLOY', 'Deploy Function Unit', 'DEVELOPER', 'function_unit', 'deploy', 'Deploy function units', NOW()),
    ('perm-dev-fu-approve', 'DEV:FU:APPROVE', 'Approve Function Unit', 'DEVELOPER', 'function_unit', 'approve', 'Approve function unit deployments', NOW()),
    ('perm-dev-process-design', 'DEV:PROCESS:DESIGN', 'Design Process', 'DEVELOPER', 'process', 'design', 'Design workflow processes', NOW()),
    ('perm-dev-form-design', 'DEV:FORM:DESIGN', 'Design Form', 'DEVELOPER', 'form', 'design', 'Design forms', NOW()),
    ('perm-dev-table-design', 'DEV:TABLE:DESIGN', 'Design Table', 'DEVELOPER', 'table', 'design', 'Design data tables', NOW()),
    
    -- Business user permissions
    ('perm-portal-task-view', 'PORTAL:TASK:VIEW', 'View Tasks', 'BUSINESS', 'task', 'view', 'View assigned tasks', NOW()),
    ('perm-portal-task-process', 'PORTAL:TASK:PROCESS', 'Process Tasks', 'BUSINESS', 'task', 'process', 'Process and complete tasks', NOW()),
    ('perm-portal-process-start', 'PORTAL:PROCESS:START', 'Start Process', 'BUSINESS', 'process', 'start', 'Start new process instances', NOW()),
    ('perm-portal-process-view', 'PORTAL:PROCESS:VIEW', 'View Process', 'BUSINESS', 'process', 'view', 'View process instances', NOW())
ON CONFLICT (code) DO NOTHING;

-- =====================================================
-- 3. Role-Permission Assignments
-- =====================================================

-- SYS_ADMIN gets all admin permissions
INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT 
    'rp-sysadmin-' || p.id,
    'SYS_ADMIN_ROLE',
    p.id,
    NOW()
FROM sys_permissions p
WHERE p.code LIKE 'ADMIN:%'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- AUDITOR gets read-only admin permissions
INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT 
    'rp-auditor-' || p.id,
    'AUDITOR_ROLE',
    p.id,
    NOW()
FROM sys_permissions p
WHERE p.code LIKE 'ADMIN:%:READ'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- TECH_DIRECTOR gets all developer permissions
INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT 
    'rp-techdir-' || p.id,
    'TECH_DIRECTOR_ROLE',
    p.id,
    NOW()
FROM sys_permissions p
WHERE p.code LIKE 'DEV:%'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- TEAM_LEADER gets most developer permissions (except approve)
INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT 
    'rp-teamlead-' || p.id,
    'TEAM_LEADER_ROLE',
    p.id,
    NOW()
FROM sys_permissions p
WHERE p.code LIKE 'DEV:%' AND p.code NOT LIKE '%:APPROVE' AND p.code NOT LIKE '%:DEPLOY'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- DEVELOPER gets basic developer permissions
INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT 
    'rp-dev-' || p.id,
    'DEVELOPER_ROLE',
    p.id,
    NOW()
FROM sys_permissions p
WHERE p.code IN ('DEV:FU:CREATE', 'DEV:FU:EDIT', 'DEV:PROCESS:DESIGN', 'DEV:FORM:DESIGN', 'DEV:TABLE:DESIGN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- BUSINESS_USER gets portal permissions
INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT 
    'rp-bizuser-' || p.id,
    'BUSINESS_USER_ROLE',
    p.id,
    NOW()
FROM sys_permissions p
WHERE p.code LIKE 'PORTAL:%'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- =====================================================
-- 4. System Dictionaries
-- =====================================================

INSERT INTO sys_dictionaries (id, code, name, description, type, status, version, sort_order, created_at, updated_at)
VALUES 
    ('sys-dict-001', 'USER_STATUS', '用户状态', '用户账户状态字典', 'SYSTEM', 'ACTIVE', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-dict-002', 'GENDER', '性别', '性别字典', 'SYSTEM', 'ACTIVE', 1, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-dict-003', 'LANGUAGE', '语言', '系统支持的语言', 'SYSTEM', 'ACTIVE', 1, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-dict-004', 'ROLE_TYPE', '角色类型', '系统角色类型', 'SYSTEM', 'ACTIVE', 1, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-dict-005', 'PERMISSION_TYPE', '权限类型', '权限分类', 'SYSTEM', 'ACTIVE', 1, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_dictionary_items (id, dictionary_id, item_code, name, name_en, name_zh_cn, value, status, sort_order, created_at, updated_at)
VALUES 
    -- User Status
    ('sys-item-001', 'sys-dict-001', 'ACTIVE', '启用', 'Active', '启用', 'ACTIVE', 'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-item-002', 'sys-dict-001', 'INACTIVE', '禁用', 'Inactive', '禁用', 'INACTIVE', 'ACTIVE', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-item-003', 'sys-dict-001', 'LOCKED', '锁定', 'Locked', '锁定', 'LOCKED', 'ACTIVE', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Gender
    ('sys-item-004', 'sys-dict-002', 'MALE', '男', 'Male', '男', 'M', 'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-item-005', 'sys-dict-002', 'FEMALE', '女', 'Female', '女', 'F', 'ACTIVE', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-item-006', 'sys-dict-002', 'OTHER', '其他', 'Other', '其他', 'O', 'ACTIVE', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Language
    ('sys-item-007', 'sys-dict-003', 'EN', '英文', 'English', '英文', 'en', 'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-item-008', 'sys-dict-003', 'ZH_CN', '简体中文', 'Simplified Chinese', '简体中文', 'zh-CN', 'ACTIVE', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-item-009', 'sys-dict-003', 'ZH_TW', '繁体中文', 'Traditional Chinese', '繁体中文', 'zh-TW', 'ACTIVE', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Role Type
    ('sys-item-010', 'sys-dict-004', 'ADMIN', '管理员', 'Admin', '管理员', 'ADMIN', 'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-item-011', 'sys-dict-004', 'DEVELOPER', '开发者', 'Developer', '开发者', 'DEVELOPER', 'ACTIVE', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-item-012', 'sys-dict-004', 'BUSINESS', '业务用户', 'Business', '业务用户', 'BUSINESS', 'ACTIVE', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Permission Type
    ('sys-item-013', 'sys-dict-005', 'ADMIN', '管理权限', 'Admin Permission', '管理权限', 'ADMIN', 'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-item-014', 'sys-dict-005', 'DEVELOPER', '开发权限', 'Developer Permission', '开发权限', 'DEVELOPER', 'ACTIVE', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-item-015', 'sys-dict-005', 'BUSINESS', '业务权限', 'Business Permission', '业务权限', 'BUSINESS', 'ACTIVE', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

COMMENT ON TABLE sys_roles IS 'System roles initialized';
COMMENT ON TABLE sys_permissions IS 'System permissions initialized';
