-- =====================================================
-- TEST DATA: Virtual Groups
-- 
-- Virtual groups for cross-service role assignment
-- 
-- IMPORTANT: 
-- 1. Roles should ONLY be assigned to virtual groups, NOT directly to users
-- 2. Users get roles by joining virtual groups
-- 3. SYSTEM type groups cannot be deleted
-- 4. SYS_ADMINS group must have at least 1 member
-- =====================================================

-- =====================================================
-- 1. System Virtual Groups (type = 'SYSTEM', cannot delete)
-- These are created by Flyway migration V12
-- =====================================================

-- Ensure system virtual groups exist with correct type
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

-- =====================================================
-- 2. Custom Virtual Groups (type = 'CUSTOM', can delete)
-- =====================================================

INSERT INTO sys_virtual_groups (id, name, code, description, type, status, created_at, updated_at)
VALUES 
    -- Business Groups
    ('vg-all-managers', 'All Managers', 'ALL_MANAGERS', 'All department managers across the organization', 'CUSTOM', 'ACTIVE', NOW(), NOW()),
    ('vg-senior-devs', 'Senior Developers', 'SENIOR_DEVS', 'Senior developers with elevated permissions', 'CUSTOM', 'ACTIVE', NOW(), NOW()),
    ('vg-approvers', 'Approvers', 'APPROVERS', 'Users who can approve workflow tasks', 'CUSTOM', 'ACTIVE', NOW(), NOW())
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = NOW();

-- =====================================================
-- 3. Add Members to System Virtual Groups
-- =====================================================

-- System Administrators Group (admin user - REQUIRED, cannot be empty)
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_by, joined_at)
SELECT 'vgm-admin-sysadmins', 'vg-sys-admins', id, 'system', NOW()
FROM sys_users WHERE username = 'admin'
ON CONFLICT (group_id, user_id) DO NOTHING;

-- Developers Group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_by, joined_at)
VALUES 
    ('vgm-dev-john', 'vg-developers', 'dev-john-001', 'system', NOW()),
    ('vgm-dev-mary', 'vg-developers', 'dev-mary-001', 'system', NOW()),
    ('vgm-dev-peter', 'vg-developers', 'dev-peter-001', 'system', NOW()),
    ('vgm-dev-lisa', 'vg-developers', 'dev-lisa-001', 'system', NOW()),
    ('vgm-dev-alex', 'vg-developers', 'dev-alex-001', 'system', NOW()),
    ('vgm-dev-emma', 'vg-developers', 'dev-emma-001', 'system', NOW()),
    ('vgm-dev-core-lead', 'vg-developers', 'core-lead-001', 'system', NOW()),
    ('vgm-dev-channel-lead', 'vg-developers', 'channel-lead-001', 'system', NOW()),
    ('vgm-dev-risk-lead', 'vg-developers', 'risk-lead-001', 'system', NOW())
ON CONFLICT (group_id, user_id) DO NOTHING;

-- Team Leaders Group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_by, joined_at)
VALUES 
    ('vgm-lead-core', 'vg-team-leaders', 'core-lead-001', 'system', NOW()),
    ('vgm-lead-channel', 'vg-team-leaders', 'channel-lead-001', 'system', NOW()),
    ('vgm-lead-risk', 'vg-team-leaders', 'risk-lead-001', 'system', NOW())
ON CONFLICT (group_id, user_id) DO NOTHING;

-- Technical Directors Group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_by, joined_at)
VALUES 
    ('vgm-tech-dir', 'vg-tech-directors', 'tech-director-001', 'system', NOW())
ON CONFLICT (group_id, user_id) DO NOTHING;

-- =====================================================
-- 4. Add Members to Custom Virtual Groups
-- =====================================================

-- All Managers Group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_by, joined_at)
VALUES 
    ('vgm-mgr-hr', 'vg-all-managers', 'hr-manager-001', 'system', NOW()),
    ('vgm-mgr-corp-dir', 'vg-all-managers', 'corp-director-001', 'system', NOW()),
    ('vgm-mgr-corp-mgr', 'vg-all-managers', 'corp-manager-001', 'system', NOW()),
    ('vgm-mgr-tech', 'vg-all-managers', 'tech-director-001', 'system', NOW())
ON CONFLICT (group_id, user_id) DO NOTHING;

-- Senior Developers Group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_by, joined_at)
VALUES 
    ('vgm-sr-john', 'vg-senior-devs', 'dev-john-001', 'system', NOW()),
    ('vgm-sr-peter', 'vg-senior-devs', 'dev-peter-001', 'system', NOW()),
    ('vgm-sr-alex', 'vg-senior-devs', 'dev-alex-001', 'system', NOW())
ON CONFLICT (group_id, user_id) DO NOTHING;

-- Approvers Group (managers + tech director)
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_by, joined_at)
VALUES 
    ('vgm-appr-hr', 'vg-approvers', 'hr-manager-001', 'system', NOW()),
    ('vgm-appr-corp-dir', 'vg-approvers', 'corp-director-001', 'system', NOW()),
    ('vgm-appr-corp-mgr', 'vg-approvers', 'corp-manager-001', 'system', NOW()),
    ('vgm-appr-tech', 'vg-approvers', 'tech-director-001', 'system', NOW()),
    ('vgm-appr-core-lead', 'vg-approvers', 'core-lead-001', 'system', NOW()),
    ('vgm-appr-channel-lead', 'vg-approvers', 'channel-lead-001', 'system', NOW()),
    ('vgm-appr-risk-lead', 'vg-approvers', 'risk-lead-001', 'system', NOW())
ON CONFLICT (group_id, user_id) DO NOTHING;

-- =====================================================
-- 5. Assign Roles to Virtual Groups
-- IMPORTANT: This is the ONLY way to assign roles.
-- Roles should NOT be assigned directly to users.
-- =====================================================

-- System role bindings (created by Flyway migration V12)
-- Ensure they exist
INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at, created_by)
VALUES 
    ('vgr-sysadmins-sysadmin', 'vg-sys-admins', 'SYS_ADMIN_ROLE', NOW(), 'system'),
    ('vgr-auditors-auditor', 'vg-auditors', 'AUDITOR_ROLE', NOW(), 'system'),
    ('vgr-developers-developer', 'vg-developers', 'DEVELOPER_ROLE', NOW(), 'system'),
    ('vgr-teamleaders-teamleader', 'vg-team-leaders', 'TEAM_LEADER_ROLE', NOW(), 'system'),
    ('vgr-techdirectors-techdirector', 'vg-tech-directors', 'TECH_DIRECTOR_ROLE', NOW(), 'system')
ON CONFLICT (virtual_group_id, role_id) DO NOTHING;

-- Custom group role bindings
-- All Managers get MANAGER role
INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at, created_by)
VALUES ('vgr-managers-manager', 'vg-all-managers', 'MANAGER_ROLE', NOW(), 'system')
ON CONFLICT (virtual_group_id, role_id) DO NOTHING;

-- Approvers get USER role (basic permissions for workflow approval)
INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at, created_by)
VALUES ('vgr-approvers-user', 'vg-approvers', 'USER_ROLE', NOW(), 'system')
ON CONFLICT (virtual_group_id, role_id) DO NOTHING;

-- =====================================================
-- Comments
-- =====================================================

COMMENT ON TABLE sys_virtual_groups IS 'Virtual groups for role assignment. type=SYSTEM groups cannot be deleted. SYS_ADMINS group must have at least 1 member.';
