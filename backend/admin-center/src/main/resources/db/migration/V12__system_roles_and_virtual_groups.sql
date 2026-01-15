-- =====================================================
-- V12: System Roles and Virtual Groups
-- 
-- This migration:
-- 1. Updates virtual group type constraint to only allow SYSTEM and CUSTOM
-- 2. Creates 5 system roles (is_system = true)
-- 3. Creates 5 system virtual groups (type = 'SYSTEM')
-- 4. Binds each system role to its corresponding virtual group
-- 5. Adds admin user to System Administrators group
-- =====================================================

-- 1. Update existing virtual groups to use new type values
UPDATE sys_virtual_groups SET type = 'CUSTOM' WHERE type NOT IN ('SYSTEM', 'CUSTOM');

-- 2. Drop old constraint if exists and add new one
ALTER TABLE sys_virtual_groups DROP CONSTRAINT IF EXISTS chk_virtual_group_type;
ALTER TABLE sys_virtual_groups ADD CONSTRAINT chk_virtual_group_type 
    CHECK (type IN ('SYSTEM', 'CUSTOM'));

-- 3. Create 5 system roles (if not exists)
INSERT INTO sys_roles (id, name, code, description, type, is_system, status, created_at, updated_at)
VALUES 
    ('SYS_ADMIN_ROLE', 'System Administrator', 'SYS_ADMIN', 'Full system access', 'BU_UNBOUNDED', true, 'ACTIVE', NOW(), NOW()),
    ('AUDITOR_ROLE', 'Auditor', 'AUDITOR', 'System audit access', 'BU_UNBOUNDED', true, 'ACTIVE', NOW(), NOW()),
    ('DEVELOPER_ROLE', 'Developer', 'DEVELOPER', 'Development access', 'BU_UNBOUNDED', true, 'ACTIVE', NOW(), NOW()),
    ('TEAM_LEADER_ROLE', 'Team Leader', 'TEAM_LEADER', 'Team management access', 'BU_UNBOUNDED', true, 'ACTIVE', NOW(), NOW()),
    ('TECH_DIRECTOR_ROLE', 'Technical Director', 'TECH_DIRECTOR', 'Technical direction access', 'BU_UNBOUNDED', true, 'ACTIVE', NOW(), NOW())
ON CONFLICT (code) DO UPDATE SET 
    is_system = true,
    updated_at = NOW();

-- 4. Create 5 system virtual groups (if not exists)
INSERT INTO sys_virtual_groups (id, name, code, description, type, status, created_at, updated_at)
VALUES 
    ('vg-sys-admins', 'System Administrators', 'SYS_ADMINS', 'System administrators with full access', 'SYSTEM', 'ACTIVE', NOW(), NOW()),
    ('vg-auditors', 'Auditors', 'AUDITORS', 'System auditors', 'SYSTEM', 'ACTIVE', NOW(), NOW()),
    ('vg-developers', 'Developers', 'DEVELOPERS', 'System developers', 'SYSTEM', 'ACTIVE', NOW(), NOW()),
    ('vg-team-leaders', 'Team Leaders', 'TEAM_LEADERS', 'Team leaders', 'SYSTEM', 'ACTIVE', NOW(), NOW()),
    ('vg-tech-directors', 'Technical Directors', 'TECH_DIRECTORS', 'Technical directors', 'SYSTEM', 'ACTIVE', NOW(), NOW())
ON CONFLICT (code) DO UPDATE SET 
    type = 'SYSTEM',
    updated_at = NOW();

-- 5. Bind each system role to its corresponding virtual group
INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at, created_by)
VALUES 
    ('vgr-sysadmins-sysadmin', 'vg-sys-admins', 'SYS_ADMIN_ROLE', NOW(), 'system'),
    ('vgr-auditors-auditor', 'vg-auditors', 'AUDITOR_ROLE', NOW(), 'system'),
    ('vgr-developers-developer', 'vg-developers', 'DEVELOPER_ROLE', NOW(), 'system'),
    ('vgr-teamleaders-teamleader', 'vg-team-leaders', 'TEAM_LEADER_ROLE', NOW(), 'system'),
    ('vgr-techdirectors-techdirector', 'vg-tech-directors', 'TECH_DIRECTOR_ROLE', NOW(), 'system')
ON CONFLICT (virtual_group_id, role_id) DO NOTHING;

-- 6. Add admin user to System Administrators group (if not already a member)
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_by, joined_at)
SELECT 'vgm-admin-sysadmins', 'vg-sys-admins', id, 'system', NOW()
FROM sys_users WHERE username = 'admin'
ON CONFLICT (group_id, user_id) DO NOTHING;

-- 7. Add comment
COMMENT ON TABLE sys_virtual_groups IS 'Virtual groups for role assignment. type=SYSTEM groups cannot be deleted. SYS_ADMINS group must have at least 1 member.';
