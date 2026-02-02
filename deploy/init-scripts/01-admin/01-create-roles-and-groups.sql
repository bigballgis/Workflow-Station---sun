-- =====================================================
-- System Default Roles and Virtual Groups Initialization
-- =====================================================
-- This script creates 5 system default roles and 5 virtual groups
-- Password for all users: password (BCrypt hash: $2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa)
-- =====================================================

\echo '========================================='
\echo 'Creating System Default Roles...'
\echo '========================================='

-- 1. System Administrator Role (系统管理员)
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
('role-sys-admin', 'SYS_ADMIN', '系统管理员', 'ADMIN', 'System administrator with full access to all system functions', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 2. Auditor Role (审计员)
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
('role-auditor', 'AUDITOR', '审计员', 'ADMIN', 'System auditor with read-only access to audit logs and system monitoring', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 3. Department Manager Role (部门经理)
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
('role-manager', 'MANAGER', '部门经理', 'BU_BOUNDED', 'Department manager with access to team workflows and approvals', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 4. Workflow Developer Role (工作流开发者)
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
('role-developer', 'DEVELOPER', '工作流开发者', 'DEVELOPER', 'Workflow developer with access to developer workstation', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 5. Workflow Designer Role (工作流设计师)
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
('role-designer', 'DESIGNER', '工作流设计师', 'DEVELOPER', 'Workflow designer with access to process and form design tools', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

\echo '✓ 5 system roles created successfully'
\echo ''

\echo '========================================='
\echo 'Creating Virtual Groups...'
\echo '========================================='

-- 1. System Administrators Virtual Group
INSERT INTO sys_virtual_groups (id, code, name, type, description, status, created_at, updated_at)
VALUES 
('vg-sys-admins', 'SYSTEM_ADMINISTRATORS', '系统管理员组', 'SYSTEM', 'Virtual group for system administrators with full system access', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 2. Auditors Virtual Group
INSERT INTO sys_virtual_groups (id, code, name, type, description, status, created_at, updated_at)
VALUES 
('vg-auditors', 'AUDITORS', '审计员组', 'SYSTEM', 'Virtual group for system auditors with monitoring and audit access', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 3. Managers Virtual Group
INSERT INTO sys_virtual_groups (id, code, name, type, description, status, created_at, updated_at)
VALUES 
('vg-managers', 'MANAGERS', '部门经理组', 'SYSTEM', 'Virtual group for department managers', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 4. Developers Virtual Group
INSERT INTO sys_virtual_groups (id, code, name, type, description, status, created_at, updated_at)
VALUES 
('vg-developers', 'DEVELOPERS', '工作流开发者组', 'SYSTEM', 'Virtual group for workflow developers', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 5. Designers Virtual Group
INSERT INTO sys_virtual_groups (id, code, name, type, description, status, created_at, updated_at)
VALUES 
('vg-designers', 'DESIGNERS', '工作流设计师组', 'SYSTEM', 'Virtual group for workflow designers', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

\echo '✓ 5 virtual groups created successfully'
\echo ''

\echo '========================================='
\echo 'Binding Roles to Virtual Groups...'
\echo '========================================='

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

-- Bind DEVELOPER role to Developers group
INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at, created_by)
VALUES 
('vgr-developer-001', 'vg-developers', 'role-developer', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (virtual_group_id, role_id) DO NOTHING;

-- Bind DESIGNER role to Designers group
INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at, created_by)
VALUES 
('vgr-designer-001', 'vg-designers', 'role-designer', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (virtual_group_id, role_id) DO NOTHING;

\echo '✓ All roles bound to virtual groups successfully'
\echo ''

\echo '========================================='
\echo 'System Roles and Groups Summary'
\echo '========================================='
\echo 'Roles Created:'
\echo '  1. SYS_ADMIN (系统管理员) - Full system access'
\echo '  2. AUDITOR (审计员) - Audit and monitoring access'
\echo '  3. MANAGER (部门经理) - Department management'
\echo '  4. DEVELOPER (工作流开发者) - Workflow development'
\echo '  5. DESIGNER (工作流设计师) - Process design'
\echo ''
\echo 'Virtual Groups Created:'
\echo '  1. SYSTEM_ADMINISTRATORS → SYS_ADMIN'
\echo '  2. AUDITORS → AUDITOR'
\echo '  3. MANAGERS → MANAGER'
\echo '  4. DEVELOPERS → DEVELOPER'
\echo '  5. DESIGNERS → DESIGNER'
\echo '========================================='
