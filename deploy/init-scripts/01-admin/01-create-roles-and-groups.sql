-- =====================================================
-- System Default Roles and Virtual Groups Initialization
-- =====================================================
-- This script creates 6 system default roles and 6 virtual groups
-- Password for all users: password (BCrypt hash: $2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa)
-- =====================================================

-- 1. System Administrator Role
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
('role-sys-admin', 'SYS_ADMIN', 'System Administrator', 'ADMIN', 'System administrator with full access to all system functions', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 2. Auditor Role
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
('role-auditor', 'AUDITOR', 'Auditor', 'ADMIN', 'System auditor with read-only access to audit logs and system monitoring', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 3. Department Manager Role
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
('role-manager', 'MANAGER', 'Department Manager', 'BU_BOUNDED', 'Department manager with access to team workflows and approvals', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 4. Technical Lead Role
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
('role-tech-lead', 'TECH_LEAD', 'Technical Lead', 'DEVELOPER', 'Technical lead with full permissions on function units: create, edit, delete, deploy, and publish', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 5. Team Lead Role
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
('role-team-lead', 'TEAM_LEAD', 'Team Lead', 'DEVELOPER', 'Team lead with permissions to create, edit, deploy, and publish function units (cannot delete)', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 6. Developer Role
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
('role-developer', 'DEVELOPER', 'Developer', 'DEVELOPER', 'Developer with permissions to edit, deploy, and publish existing function units (cannot create or delete)', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

<<<<<<< HEAD
\echo '✓ 6 system roles created successfully'
\echo ''

\echo '========================================='
\echo 'Creating Virtual Groups...'
\echo '========================================='

=======
>>>>>>> 782ced6 (fix deploy script and flyway)
-- 1. System Administrators Virtual Group
INSERT INTO sys_virtual_groups (id, code, name, type, description, status, created_at, updated_at)
VALUES 
('vg-sys-admins', 'SYSTEM_ADMINISTRATORS', 'System Administrators', 'SYSTEM', 'Virtual group for system administrators with full system access', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 2. Auditors Virtual Group
INSERT INTO sys_virtual_groups (id, code, name, type, description, status, created_at, updated_at)
VALUES 
('vg-auditors', 'AUDITORS', 'Auditors', 'SYSTEM', 'Virtual group for system auditors with monitoring and audit access', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 3. Managers Virtual Group (CUSTOM - not a system default)
INSERT INTO sys_virtual_groups (id, code, name, type, description, status, created_at, updated_at)
VALUES 
('vg-managers', 'MANAGERS', 'Department Managers', 'CUSTOM', 'Virtual group for department managers', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 4. Technical Leads Virtual Group
INSERT INTO sys_virtual_groups (id, code, name, type, description, status, created_at, updated_at)
VALUES 
('vg-tech-leads', 'TECH_LEADS', 'Technical Leads', 'SYSTEM', 'Virtual group for technical leads with full function unit management permissions', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 5. Team Leads Virtual Group
INSERT INTO sys_virtual_groups (id, code, name, type, description, status, created_at, updated_at)
VALUES 
('vg-team-leads', 'TEAM_LEADS', 'Team Leads', 'SYSTEM', 'Virtual group for team leads with create and deployment permissions', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 6. Developers Virtual Group
INSERT INTO sys_virtual_groups (id, code, name, type, description, status, created_at, updated_at)
VALUES 
('vg-developers', 'DEVELOPERS', 'Developers', 'SYSTEM', 'Virtual group for developers with edit and deployment permissions', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

<<<<<<< HEAD
\echo '✓ 6 virtual groups created successfully'
\echo ''

\echo '========================================='
\echo 'Binding Roles to Virtual Groups...'
\echo '========================================='

=======
>>>>>>> 782ced6 (fix deploy script and flyway)
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
