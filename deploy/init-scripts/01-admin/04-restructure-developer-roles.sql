-- =====================================================
-- Restructure Developer Workstation Roles
-- =====================================================
-- This script removes old DEVELOPER and DESIGNER roles
-- and creates three new roles with proper permissions:
-- 1. TECH_LEAD - Technical Lead (full permissions)
-- 2. TEAM_LEAD - Team Lead (create, deploy, publish, no delete)
-- 3. DEVELOPER - Developer (edit, deploy, publish only)
-- =====================================================

\echo '========================================='
\echo 'Cleaning Up Old Developer Roles...'
\echo '========================================='

-- Step 1: Remove role assignments for old roles
DELETE FROM sys_role_assignments 
WHERE role_id IN ('role-developer', 'role-designer');

-- Step 2: Remove virtual group role bindings for old roles
DELETE FROM sys_virtual_group_roles 
WHERE role_id IN ('role-developer', 'role-designer');

-- Step 3: Remove user role assignments for old roles
DELETE FROM sys_user_roles 
WHERE role_id IN ('role-developer', 'role-designer');

-- Step 4: Remove old virtual groups
DELETE FROM sys_virtual_groups 
WHERE id IN ('vg-developers', 'vg-designers');

-- Step 5: Remove old roles
DELETE FROM sys_roles 
WHERE id IN ('role-developer', 'role-designer');

\echo '✓ Old developer roles and groups cleaned up'
\echo ''

\echo '========================================='
\echo 'Creating New Developer Workstation Roles...'
\echo '========================================='

-- 1. Technical Lead Role (技术主管)
-- Full permissions: create, edit, delete, deploy, publish
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
('role-tech-lead', 'TECH_LEAD', 'Technical Lead', 'DEVELOPER', 'Technical lead with full permissions on function units: create, edit, delete, deploy, and publish', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 2. Team Lead Role (技术组长)
-- Permissions: create, edit, deploy, publish (no delete)
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
('role-team-lead', 'TEAM_LEAD', 'Team Lead', 'DEVELOPER', 'Team lead with permissions to create, edit, deploy, and publish function units (cannot delete)', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 3. Developer Role (开发工程师)
-- Permissions: edit, deploy, publish only (no create, no delete)
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
('role-developer', 'DEVELOPER', 'Developer', 'DEVELOPER', 'Developer with permissions to edit, deploy, and publish existing function units (cannot create or delete)', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

\echo '✓ 3 new developer roles created successfully'
\echo ''

\echo '========================================='
\echo 'Creating Virtual Groups...'
\echo '========================================='

-- 1. Technical Leads Virtual Group
INSERT INTO sys_virtual_groups (id, code, name, type, description, status, created_at, updated_at)
VALUES 
('vg-tech-leads', 'TECH_LEADS', 'Technical Leads', 'SYSTEM', 'Virtual group for technical leads with full function unit management permissions', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 2. Team Leads Virtual Group
INSERT INTO sys_virtual_groups (id, code, name, type, description, status, created_at, updated_at)
VALUES 
('vg-team-leads', 'TEAM_LEADS', 'Team Leads', 'SYSTEM', 'Virtual group for team leads with create and deployment permissions', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 3. Developers Virtual Group
INSERT INTO sys_virtual_groups (id, code, name, type, description, status, created_at, updated_at)
VALUES 
('vg-developers', 'DEVELOPERS', 'Developers', 'SYSTEM', 'Virtual group for developers with edit and deployment permissions', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

\echo '✓ 3 virtual groups created successfully'
\echo ''

\echo '========================================='
\echo 'Binding Roles to Virtual Groups...'
\echo '========================================='

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

\echo '✓ All roles bound to virtual groups successfully'
\echo ''

\echo '========================================='
\echo 'Developer Workstation Roles Summary'
\echo '========================================='
\echo 'Roles Created:'
\echo '  1. TECH_LEAD (Technical Lead)'
\echo '     - Permissions: CREATE, EDIT, DELETE, DEPLOY, PUBLISH'
\echo '     - Full control over function units'
\echo ''
\echo '  2. TEAM_LEAD (Team Lead)'
\echo '     - Permissions: CREATE, EDIT, DEPLOY, PUBLISH'
\echo '     - Cannot: DELETE function units'
\echo ''
\echo '  3. DEVELOPER (Developer)'
\echo '     - Permissions: EDIT, DEPLOY, PUBLISH'
\echo '     - Cannot: CREATE or DELETE function units'
\echo ''
\echo 'Virtual Groups Created:'
\echo '  1. TECH_LEADS → TECH_LEAD'
\echo '  2. TEAM_LEADS → TEAM_LEAD'
\echo '  3. DEVELOPERS → DEVELOPER'
\echo '========================================='
\echo ''
\echo 'Note: Permission enforcement must be implemented in the'
\echo 'Developer Workstation backend service.'
\echo '========================================='
