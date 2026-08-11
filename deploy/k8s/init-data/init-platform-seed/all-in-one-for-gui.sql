-- =============================================================================
-- ALL-IN-ONE platform seed (admin only) — pure SQL for GUI clients
-- Contains only deploy/init-scripts/01-admin/*.sql (roles, users, permissions).
-- Demo packs 08 / 15 / 16 are NOT included.
-- Run AFTER: init-platform-schema/all-in-one-for-gui.sql AND Flowable DDL (init-flowable/create/)
-- =============================================================================

SET client_min_messages = WARNING;
SET timezone = 'UTC';


-- =============================================================================
-- 01-admin/01-create-roles-and-groups.sql
-- =============================================================================
-- =====================================================
-- System Default Roles and Virtual Groups Initialization
-- =====================================================
-- This script creates 6 system default roles and 6 virtual groups
-- Password for all users: password (BCrypt hash: $2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa)
-- =====================================================

-- 1. System Administrator Role
INSERT INTO sys_roles (id, code, name, type, display_name, status, is_system, created_at, updated_at)
VALUES 
('role-sys-admin', 'SYS_ADMIN', 'System Administrator', 'ADMIN', 'System administrator with full access to all system functions', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    updated_at = CURRENT_TIMESTAMP;

-- 2. Auditor Role
INSERT INTO sys_roles (id, code, name, type, display_name, status, is_system, created_at, updated_at)
VALUES 
('role-auditor', 'AUDITOR', 'Auditor', 'ADMIN', 'System auditor with read-only access to audit logs and system monitoring', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    updated_at = CURRENT_TIMESTAMP;

-- 3. Department Manager Role
INSERT INTO sys_roles (id, code, name, type, display_name, status, is_system, created_at, updated_at)
VALUES 
('role-manager', 'MANAGER', 'Department Manager', 'BU_BOUNDED', 'Department manager with access to team workflows and approvals', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    updated_at = CURRENT_TIMESTAMP;

-- 4. Technical Lead Role
INSERT INTO sys_roles (id, code, name, type, display_name, status, is_system, created_at, updated_at)
VALUES 
('role-tech-lead', 'TECH_LEAD', 'Technical Lead', 'DEVELOPER', 'Technical lead with full permissions on function units: create, edit, delete, deploy, and publish', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    updated_at = CURRENT_TIMESTAMP;

-- 5. Team Lead Role
INSERT INTO sys_roles (id, code, name, type, display_name, status, is_system, created_at, updated_at)
VALUES 
('role-team-lead', 'TEAM_LEAD', 'Team Lead', 'DEVELOPER', 'Team lead with permissions to create, edit, deploy, and publish function units (cannot delete)', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    updated_at = CURRENT_TIMESTAMP;

-- 6. Developer Role
INSERT INTO sys_roles (id, code, name, type, display_name, status, is_system, created_at, updated_at)
VALUES 
('role-developer', 'DEVELOPER', 'Developer', 'DEVELOPER', 'Developer with permissions to edit, deploy, and publish existing function units (cannot create or delete)', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    updated_at = CURRENT_TIMESTAMP;

-- 1. System Administrators Virtual Group
INSERT INTO sys_virtual_groups (id, code, name, type, display_name, status, created_at, updated_at)
VALUES 
('vg-sys-admins', 'SYSTEM_ADMINISTRATORS', 'System Administrators', 'SYSTEM', 'Virtual group for system administrators with full system access', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    display_name = EXCLUDED.display_name,
    updated_at = CURRENT_TIMESTAMP;

-- 2. Auditors Virtual Group
INSERT INTO sys_virtual_groups (id, code, name, type, display_name, status, created_at, updated_at)
VALUES 
('vg-auditors', 'AUDITORS', 'Auditors', 'SYSTEM', 'Virtual group for system auditors with monitoring and audit access', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    display_name = EXCLUDED.display_name,
    updated_at = CURRENT_TIMESTAMP;

-- 3. Managers Virtual Group (CUSTOM - not a system default)
INSERT INTO sys_virtual_groups (id, code, name, type, display_name, status, created_at, updated_at)
VALUES 
('vg-managers', 'MANAGERS', 'Department Managers', 'CUSTOM', 'Virtual group for department managers', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    display_name = EXCLUDED.display_name,
    updated_at = CURRENT_TIMESTAMP;

-- 4. Technical Leads Virtual Group
INSERT INTO sys_virtual_groups (id, code, name, type, display_name, status, created_at, updated_at)
VALUES 
('vg-tech-leads', 'TECH_LEADS', 'Technical Leads', 'SYSTEM', 'Virtual group for technical leads with full function unit management permissions', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    display_name = EXCLUDED.display_name,
    updated_at = CURRENT_TIMESTAMP;

-- 5. Team Leads Virtual Group
INSERT INTO sys_virtual_groups (id, code, name, type, display_name, status, created_at, updated_at)
VALUES 
('vg-team-leads', 'TEAM_LEADS', 'Team Leads', 'SYSTEM', 'Virtual group for team leads with create and deployment permissions', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    display_name = EXCLUDED.display_name,
    updated_at = CURRENT_TIMESTAMP;

-- 6. Developers Virtual Group
INSERT INTO sys_virtual_groups (id, code, name, type, display_name, status, created_at, updated_at)
VALUES 
('vg-developers', 'DEVELOPERS', 'Developers', 'SYSTEM', 'Virtual group for developers with edit and deployment permissions', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    display_name = EXCLUDED.display_name,
    updated_at = CURRENT_TIMESTAMP;

-- 7. Default Development Team (CUSTOM) — fallback team so existing/unassigned function units
-- stay visible to non-admin developers after team-based visibility takes effect.
INSERT INTO sys_virtual_groups (id, code, name, type, display_name, status, created_at, updated_at)
VALUES 
('vg-default-dev-team', 'DEFAULT_DEV_TEAM', 'Default Development Team', 'CUSTOM', 'Fallback team for function units migrated before team-based visibility', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    display_name = EXCLUDED.display_name,
    updated_at = CURRENT_TIMESTAMP;

-- 8. Public Development Group (CUSTOM, no members) — its function units are always visible to
-- every developer-workstation user, overlaid on top of the user's currently selected team.
-- Existing/legacy function units are migrated into this group (see init-scripts 01-admin/08).
INSERT INTO sys_virtual_groups (id, code, name, type, display_name, status, created_at, updated_at)
VALUES 
('vg-dev-public', 'DEV_TEAM_PUBLIC', 'Public', 'CUSTOM', 'Built-in public group: its function units are visible to every developer-workstation user', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    display_name = EXCLUDED.display_name,
    updated_at = CURRENT_TIMESTAMP;



-- Bind SYS_ADMIN role to System Administrators group
INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at, created_by)
VALUES 
('vgr-sys-admin-001', 'vg-sys-admins', 'role-sys-admin', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (virtual_group_id, role_id) DO NOTHING;

-- Bind AUDITOR role to Auditors group
INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at, created_by)
VALUES 
('vgr-auditor-001', 'vg-auditors', 'role-auditor', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (virtual_group_id, role_id) DO NOTHING;

-- Bind MANAGER role to Managers group
INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at, created_by)
VALUES 
('vgr-manager-001', 'vg-managers', 'role-manager', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (virtual_group_id, role_id) DO NOTHING;

-- Bind TECH_LEAD role to Technical Leads group
INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at, created_by)
VALUES 
('vgr-tech-lead-001', 'vg-tech-leads', 'role-tech-lead', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (virtual_group_id, role_id) DO NOTHING;

-- Bind TEAM_LEAD role to Team Leads group
INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at, created_by)
VALUES 
('vgr-team-lead-001', 'vg-team-leads', 'role-team-lead', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (virtual_group_id, role_id) DO NOTHING;

-- Bind DEVELOPER role to Developers group
INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at, created_by)
VALUES 
('vgr-developer-001', 'vg-developers', 'role-developer', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (virtual_group_id, role_id) DO NOTHING;

-- =============================================================================
-- 01-admin/01-create-admin-only.sql
-- =============================================================================
-- =====================================================
-- Admin User Only Initialization
-- =====================================================
-- Creates admin + 常用开发测试号，并加入系统管理员虚拟组。
-- Password: admin123（BCrypt，与 admin-center BCryptTest 及前端快捷登录一致）
-- =====================================================


-- Admin User（password = admin123）
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, employee_id, status, language, must_change_password, created_at, updated_at, deleted)
VALUES 
('user-admin', 'admin', '$2a$10$XMfQkI8Q4i2ZOLcl.V5RH.SoLTbPpfsxbv0YG21jRr8F7zhNouMle', 'admin@example.com', 'System Admin', 'System Administrator', NULL, 'ACTIVE', 'en', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false)
ON CONFLICT (username) DO UPDATE SET 
    password_hash = EXCLUDED.password_hash,
    email = EXCLUDED.email,
    display_name = EXCLUDED.display_name,
    full_name = EXCLUDED.full_name,
    employee_id = EXCLUDED.employee_id,
    must_change_password = EXCLUDED.must_change_password,
    updated_at = CURRENT_TIMESTAMP;


-- 测试账号：登录名须填 username（与工号相同），非仅 employee_id 查询
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, employee_id, status, language, must_change_password, created_at, updated_at, deleted)
VALUES 
('user-test-44027893', '44027893', '$2a$10$XMfQkI8Q4i2ZOLcl.V5RH.SoLTbPpfsxbv0YG21jRr8F7zhNouMle', '44027893@e2e.workflow.local', '测试用户', '测试用户', '44027893', 'ACTIVE', 'zh_CN', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false)
ON CONFLICT (username) DO UPDATE SET 
    password_hash = EXCLUDED.password_hash,
    email = EXCLUDED.email,
    display_name = EXCLUDED.display_name,
    full_name = EXCLUDED.full_name,
    employee_id = EXCLUDED.employee_id,
    must_change_password = EXCLUDED.must_change_password,
    updated_at = CURRENT_TIMESTAMP;


-- Add admin to System Administrators group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES 
('vgm-admin-001', 'vg-sys-admins', 'user-admin', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (group_id, user_id) DO NOTHING;

-- 测试号：与 admin 同级虚拟组，便于联调管理端 / 门户
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES 
('vgm-test-44027893', 'vg-sys-admins', 'user-test-44027893', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (group_id, user_id) DO NOTHING;

-- 管理端登录使用 UserRoleService（仅认 sys_role_assignments）；直接 USER 分配最稳妥
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_by, assigned_at, created_at, updated_at)
VALUES
('ra-seed-user-admin', 'role-sys-admin', 'USER', 'user-admin', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ra-seed-44027893', 'role-sys-admin', 'USER', 'user-test-44027893', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;


-- =============================================================================
-- 01-admin/02-init-developer-permissions.sql
-- =============================================================================
-- =====================================================
-- Initialize Developer Role Permissions
-- 与 admin-center DeveloperPermissionService 默认映射对齐
-- =====================================================


-- TECH_LEAD: 全部 DeveloperPermission 枚举
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
SELECT 
    gen_random_uuid()::varchar,
    'role-tech-lead',
    p.permission,
    CURRENT_TIMESTAMP
FROM (VALUES 
    ('FUNCTION_UNIT_CREATE'), ('FUNCTION_UNIT_UPDATE'), ('FUNCTION_UNIT_DELETE'), ('FUNCTION_UNIT_VIEW'),
    ('FUNCTION_UNIT_DEVELOP'), ('FUNCTION_UNIT_PUBLISH'), ('FUNCTION_UNIT_ASSIGN_DEV_GROUP'),
    ('FORM_CREATE'), ('FORM_UPDATE'), ('FORM_DELETE'), ('FORM_VIEW'),
    ('PROCESS_CREATE'), ('PROCESS_UPDATE'), ('PROCESS_DELETE'), ('PROCESS_VIEW'),
    ('TABLE_CREATE'), ('TABLE_UPDATE'), ('TABLE_DELETE'), ('TABLE_VIEW'),
    ('ACTION_CREATE'), ('ACTION_UPDATE'), ('ACTION_DELETE'), ('ACTION_VIEW')
) AS p(permission)
ON CONFLICT (role_id, permission) DO NOTHING;


-- TEAM_LEAD: 与 DEVELOPER 相同设计能力 + 功能单元创建/删除/分配开发组
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
SELECT 
    gen_random_uuid()::varchar,
    'role-team-lead',
    p.permission,
    CURRENT_TIMESTAMP
FROM (VALUES 
    ('FUNCTION_UNIT_CREATE'), ('FUNCTION_UNIT_UPDATE'), ('FUNCTION_UNIT_DELETE'), ('FUNCTION_UNIT_VIEW'),
    ('FUNCTION_UNIT_DEVELOP'), ('FUNCTION_UNIT_PUBLISH'), ('FUNCTION_UNIT_ASSIGN_DEV_GROUP'),
    ('FORM_CREATE'), ('FORM_UPDATE'), ('FORM_DELETE'), ('FORM_VIEW'),
    ('PROCESS_CREATE'), ('PROCESS_UPDATE'), ('PROCESS_DELETE'), ('PROCESS_VIEW'),
    ('TABLE_CREATE'), ('TABLE_UPDATE'), ('TABLE_DELETE'), ('TABLE_VIEW'),
    ('ACTION_CREATE'), ('ACTION_UPDATE'), ('ACTION_DELETE'), ('ACTION_VIEW')
) AS p(permission)
ON CONFLICT (role_id, permission) DO NOTHING;


-- DEVELOPER: TEAM_LEAD 去掉功能单元创建/删除/分配开发组
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
SELECT 
    gen_random_uuid()::varchar,
    'role-developer',
    p.permission,
    CURRENT_TIMESTAMP
FROM (VALUES 
    ('FUNCTION_UNIT_UPDATE'), ('FUNCTION_UNIT_VIEW'),
    ('FUNCTION_UNIT_DEVELOP'), ('FUNCTION_UNIT_PUBLISH'),
    ('FORM_CREATE'), ('FORM_UPDATE'), ('FORM_DELETE'), ('FORM_VIEW'),
    ('PROCESS_CREATE'), ('PROCESS_UPDATE'), ('PROCESS_DELETE'), ('PROCESS_VIEW'),
    ('TABLE_CREATE'), ('TABLE_UPDATE'), ('TABLE_DELETE'), ('TABLE_VIEW'),
    ('ACTION_CREATE'), ('ACTION_UPDATE'), ('ACTION_DELETE'), ('ACTION_VIEW')
) AS p(permission)
ON CONFLICT (role_id, permission) DO NOTHING;


-- =============================================================================
-- 01-admin/03-sync-role-tables.sql
-- =============================================================================
-- =====================================================
-- Sync Role Assignment Tables
-- =====================================================
-- Purpose: Synchronize data from sys_virtual_group_roles to sys_role_assignments
-- This ensures both tables have consistent data during the transition period.
-- 
-- Background:
-- - sys_virtual_group_roles: Legacy table used by management UI
-- - sys_role_assignments: New unified table used by permission queries
-- 
-- This script should be run AFTER 01-create-roles-and-groups.sql
-- =====================================================


-- Migrate data from sys_virtual_group_roles to sys_role_assignments
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_by, assigned_at, created_at, updated_at)
SELECT 
    'ra-' || vgr.id as id,
    vgr.role_id,
    'VIRTUAL_GROUP' as target_type,
    vgr.virtual_group_id as target_id,
    COALESCE(vgr.created_by, 'system') as assigned_by,
    COALESCE(vgr.created_at, CURRENT_TIMESTAMP) as assigned_at,
    COALESCE(vgr.created_at, CURRENT_TIMESTAMP) as created_at,
    CURRENT_TIMESTAMP as updated_at
FROM sys_virtual_group_roles vgr
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_assignments ra
    WHERE ra.role_id = vgr.role_id
    AND ra.target_type = 'VIRTUAL_GROUP'
    AND ra.target_id = vgr.virtual_group_id
)
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;


-- Verify data consistency

SELECT 
    'sys_virtual_group_roles' as source_table,
    COUNT(*) as record_count
FROM sys_virtual_group_roles
UNION ALL
SELECT 
    'sys_role_assignments (VIRTUAL_GROUP)' as source_table,
    COUNT(*) as record_count
FROM sys_role_assignments
WHERE target_type = 'VIRTUAL_GROUP';


SELECT 
    vg.name as virtual_group,
    r.name as role,
    r.type as role_type,
    CASE 
        WHEN ra.id IS NOT NULL THEN '✓ Synced'
        ELSE '✗ Not Synced'
    END as status
FROM sys_virtual_group_roles vgr
JOIN sys_virtual_groups vg ON vgr.virtual_group_id = vg.id
JOIN sys_roles r ON vgr.role_id = r.id
LEFT JOIN sys_role_assignments ra ON 
    ra.role_id = vgr.role_id 
    AND ra.target_type = 'VIRTUAL_GROUP' 
    AND ra.target_id = vgr.virtual_group_id
ORDER BY vg.name;


-- =============================================================================
-- 01-admin/04-admin-permissions.sql
-- =============================================================================
-- =====================================================
-- Admin Center: System Permissions & Role Bindings
-- =====================================================
-- Inserts base permissions into sys_permissions and binds
-- them to the system roles created in 01-create-roles-and-groups.sql
-- =====================================================


-- ---- Permissions ----
-- sys_permissions columns: id, code, name, type, resource, action, display_name, created_at, parent_id, sort_order
INSERT INTO sys_permissions (id, code, name, type, resource, action, display_name, sort_order, created_at) VALUES
  ('perm-basic-access', 'basic:access',  'Basic Access',     'PLATFORM', 'platform', 'access', 'Basic access to the platform',              1,  CURRENT_TIMESTAMP),
  ('perm-user-read',    'user:read',     'Read Users',       'USER',     'user',     'read',   'View user list and details',                10, CURRENT_TIMESTAMP),
  ('perm-user-write',   'user:write',    'Write Users',      'USER',     'user',     'write',  'Create and update users',                   11, CURRENT_TIMESTAMP),
  ('perm-user-delete',  'user:delete',   'Delete Users',     'USER',     'user',     'delete', 'Delete users',                              12, CURRENT_TIMESTAMP),
  ('perm-role-read',    'role:read',     'Read Roles',       'ROLE',     'role',     'read',   'View roles and permissions',                20, CURRENT_TIMESTAMP),
  ('perm-role-write',   'role:write',    'Write Roles',      'ROLE',     'role',     'write',  'Create and update roles',                   21, CURRENT_TIMESTAMP),
  ('perm-role-delete',  'role:delete',   'Delete Roles',     'ROLE',     'role',     'delete', 'Delete roles',                              22, CURRENT_TIMESTAMP),
  ('perm-sys-admin',    'system:admin',  'System Admin',     'SYSTEM',   'system',   'admin',  'Full system administration access',         30, CURRENT_TIMESTAMP),
  ('perm-sys-config',   'system:config', 'System Config',    'SYSTEM',   'system',   'config', 'Manage system configuration',               31, CURRENT_TIMESTAMP),
  ('perm-audit-read',   'audit:read',    'Read Audit Logs',  'AUDIT',    'audit',    'read',   'View audit logs and operation history',     40, CURRENT_TIMESTAMP),
  ('perm-log-read',     'log:read',      'Read Logs',        'AUDIT',    'log',      'read',   'View system and security logs',             41, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;


-- ---- SYS_ADMIN role gets all permissions ----
INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT
    'rp-sysadmin-' || p.id,
    'role-sys-admin',
    p.id,
    CURRENT_TIMESTAMP
FROM sys_permissions p
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ---- AUDITOR role: audit:read, log:read, user:read, basic:access ----
INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT
    'rp-auditor-' || p.id,
    'role-auditor',
    p.id,
    CURRENT_TIMESTAMP
FROM sys_permissions p
WHERE p.code IN ('basic:access', 'user:read', 'audit:read', 'log:read')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- =============================================================================
-- 01-admin/05-e2e-test-users-and-business-units.sql
-- =============================================================================
-- =====================================================
-- E2E 仿真：业务单元 + 门户测试用户
-- =====================================================
-- 与默认管理员相同密码: password
-- BCrypt: $2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa
-- 用途：采购/报销/简单审批等多租户与数字信贷虚拟组流程联调
-- =====================================================

-- 虚拟组：聚合所有 E2E 账号（不绑定额外角色亦可登录门户）
INSERT INTO sys_virtual_groups (id, code, name, type, display_name, status, created_at, updated_at)
VALUES (
    'vg-e2e-workflow',
    'E2E_WORKFLOW_SIMULATION',
    'E2E 流程仿真用户组',
    'CUSTOM',
    '端到端测试：报销、采购、审批、信贷流水线仿真账号',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (code) DO UPDATE SET
    name        = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    updated_at  = CURRENT_TIMESTAMP;

-- 业务单元：总部 + 财务共享 + 数字化部门
INSERT INTO sys_business_units (
    id, code, name, parent_id, level, path, sort_order, status, description,
    created_at, created_by, updated_at, updated_by
)
VALUES
(
    'bu-e2e-hq',
    'E2E_HQ',
    '华东制造集团总部',
    NULL,
    1,
    '/E2E_HQ',
    10,
    'ACTIVE',
    '仿真集团根节点',
    CURRENT_TIMESTAMP,
    'system',
    CURRENT_TIMESTAMP,
    'system'
),
(
    'bu-e2e-finance',
    'E2E_FINANCE',
    '共享财务中心',
    'bu-e2e-hq',
    2,
    '/E2E_HQ/E2E_FINANCE',
    20,
    'ACTIVE',
    '报销、采购审批归属组织',
    CURRENT_TIMESTAMP,
    'system',
    CURRENT_TIMESTAMP,
    'system'
),
(
    'bu-e2e-it',
    'E2E_IT',
    '数字化运营部',
    'bu-e2e-hq',
    2,
    '/E2E_HQ/E2E_IT',
    30,
    'ACTIVE',
    '功能单元设计与流程运维',
    CURRENT_TIMESTAMP,
    'system',
    CURRENT_TIMESTAMP,
    'system'
)
ON CONFLICT (code) DO UPDATE SET
    name        = EXCLUDED.name,
    parent_id   = EXCLUDED.parent_id,
    level       = EXCLUDED.level,
    path        = EXCLUDED.path,
    description = EXCLUDED.description,
    updated_at  = CURRENT_TIMESTAMP,
    updated_by  = 'system';

-- 财务中心可用「部门经理」角色（绑定到业务单元）
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at, created_by)
VALUES (
    'bur-e2e-fin-mgr',
    'bu-e2e-finance',
    'role-manager',
    CURRENT_TIMESTAMP,
    'system'
)
ON CONFLICT (business_unit_id, role_id) DO NOTHING;

-- 测试用户（7 人：申请人、经理、财务、信贷四岗）
INSERT INTO sys_users (
    id, username, password_hash, email, display_name, full_name,
    employee_id, position, status, language, must_change_password,
    created_at, updated_at, deleted
)
VALUES
(
    'user-e2e-zhangwei',
    'e2e_zhangwei',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'zhangwei@e2e.workflow.local',
    '张伟',
    '张伟',
    'E26-1001',
    '业务专员',
    'ACTIVE',
    'zh_CN',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
),
(
    'user-e2e-lina',
    'e2e_lina',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'lina@e2e.workflow.local',
    '李娜',
    '李娜',
    'E26-2001',
    '财务中心经理',
    'ACTIVE',
    'zh_CN',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
),
(
    'user-e2e-wangfang',
    'e2e_wangfang',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'wangfang@e2e.workflow.local',
    '王芳',
    '王芳',
    'E26-1002',
    '费用会计',
    'ACTIVE',
    'zh_CN',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
),
(
    'user-e2e-zhaomin',
    'e2e_zhaomin',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'zhaomin@e2e.workflow.local',
    '赵敏',
    '赵敏',
    'E26-3001',
    '信贷材料审核员',
    'ACTIVE',
    'zh_CN',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
),
(
    'user-e2e-sunqiang',
    'e2e_sunqiang',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'sunqiang@e2e.workflow.local',
    '孙强',
    '孙强',
    'E26-3002',
    '信贷调查员',
    'ACTIVE',
    'zh_CN',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
),
(
    'user-e2e-zhoujie',
    'e2e_zhoujie',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'zhoujie@e2e.workflow.local',
    '周杰',
    '周杰',
    'E26-3003',
    '风控专员',
    'ACTIVE',
    'zh_CN',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
),
(
    'user-e2e-wugang',
    'e2e_wugang',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'wugang@e2e.workflow.local',
    '吴刚',
    '吴刚',
    'E26-3004',
    '放款执行',
    'ACTIVE',
    'zh_CN',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
)
ON CONFLICT (username) DO UPDATE SET
    email           = EXCLUDED.email,
    display_name    = EXCLUDED.display_name,
    full_name       = EXCLUDED.full_name,
    employee_id     = EXCLUDED.employee_id,
    position        = EXCLUDED.position,
    password_hash   = EXCLUDED.password_hash,
    updated_at      = CURRENT_TIMESTAMP;

-- 用户 — 业务单元隶属
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by)
VALUES
('ubu-e2e-zw-fin', 'user-e2e-zhangwei', 'bu-e2e-finance', CURRENT_TIMESTAMP, 'system'),
('ubu-e2e-ln-fin', 'user-e2e-lina',      'bu-e2e-finance', CURRENT_TIMESTAMP, 'system'),
('ubu-e2e-wf-fin', 'user-e2e-wangfang',  'bu-e2e-finance', CURRENT_TIMESTAMP, 'system'),
('ubu-e2e-zm-fin', 'user-e2e-zhaomin',   'bu-e2e-finance', CURRENT_TIMESTAMP, 'system'),
('ubu-e2e-sq-fin', 'user-e2e-sunqiang',  'bu-e2e-finance', CURRENT_TIMESTAMP, 'system'),
('ubu-e2e-zj-fin', 'user-e2e-zhoujie',   'bu-e2e-finance', CURRENT_TIMESTAMP, 'system'),
('ubu-e2e-wg-fin', 'user-e2e-wugang',    'bu-e2e-finance', CURRENT_TIMESTAMP, 'system'),
('ubu-e2e-44027893', 'user-test-44027893', 'bu-e2e-finance', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (user_id, business_unit_id) DO NOTHING;

-- 李娜：财务中心 + 部门经理（业务单元角色）
INSERT INTO sys_user_business_unit_roles (id, user_id, business_unit_id, role_id, created_at, created_by)
VALUES (
    'ubur-e2e-lina-mgr',
    'user-e2e-lina',
    'bu-e2e-finance',
    'role-manager',
    CURRENT_TIMESTAMP,
    'system'
)
ON CONFLICT (user_id, business_unit_id, role_id) DO NOTHING;

-- 虚拟组成员：全员进入 E2E 组；经理进入 MANAGERS 虚拟组（与角色脚本中的 vg-managers 一致）
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES
('vgm-e2e-zw', 'vg-e2e-workflow', 'user-e2e-zhangwei', CURRENT_TIMESTAMP, 'system'),
('vgm-e2e-ln', 'vg-e2e-workflow', 'user-e2e-lina',      CURRENT_TIMESTAMP, 'system'),
('vgm-e2e-wf', 'vg-e2e-workflow', 'user-e2e-wangfang',  CURRENT_TIMESTAMP, 'system'),
('vgm-e2e-zm', 'vg-e2e-workflow', 'user-e2e-zhaomin',   CURRENT_TIMESTAMP, 'system'),
('vgm-e2e-sq', 'vg-e2e-workflow', 'user-e2e-sunqiang',  CURRENT_TIMESTAMP, 'system'),
('vgm-e2e-zj', 'vg-e2e-workflow', 'user-e2e-zhoujie',   CURRENT_TIMESTAMP, 'system'),
('vgm-e2e-wg', 'vg-e2e-workflow', 'user-e2e-wugang',    CURRENT_TIMESTAMP, 'system'),
('vgm-e2e-44027893', 'vg-e2e-workflow', 'user-test-44027893', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (group_id, user_id) DO NOTHING;

INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES (
    'vgm-e2e-ln-mgr',
    'vg-managers',
    'user-e2e-lina',
    CURRENT_TIMESTAMP,
    'system'
)
ON CONFLICT (group_id, user_id) DO NOTHING;

