-- =====================================================
-- TEST DATA: Virtual Groups
-- Virtual groups for cross-service role assignment
-- =====================================================

-- =====================================================
-- 1. Create Virtual Groups
-- =====================================================

INSERT INTO sys_virtual_groups (id, name, code, description, type, status, created_at, updated_at)
VALUES 
    ('vg-all-managers', 'All Managers', 'ALL_MANAGERS', 'All department managers across the organization', 'STATIC', 'ACTIVE', NOW(), NOW()),
    ('vg-all-developers', 'All Developers', 'ALL_DEVELOPERS', 'All developers in IT department', 'STATIC', 'ACTIVE', NOW(), NOW()),
    ('vg-team-leads', 'Team Leaders', 'TEAM_LEADS', 'All team leaders', 'STATIC', 'ACTIVE', NOW(), NOW()),
    ('vg-senior-devs', 'Senior Developers', 'SENIOR_DEVS', 'Senior developers with elevated permissions', 'STATIC', 'ACTIVE', NOW(), NOW()),
    ('vg-approvers', 'Approvers', 'APPROVERS', 'Users who can approve workflow tasks', 'STATIC', 'ACTIVE', NOW(), NOW())
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = NOW();

-- =====================================================
-- 2. Add Members to Virtual Groups
-- =====================================================

-- All Managers Group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_at)
VALUES 
    ('vgm-mgr-hr', 'vg-all-managers', 'hr-manager-001', NOW()),
    ('vgm-mgr-corp-dir', 'vg-all-managers', 'corp-director-001', NOW()),
    ('vgm-mgr-corp-mgr', 'vg-all-managers', 'corp-manager-001', NOW()),
    ('vgm-mgr-tech', 'vg-all-managers', 'tech-director-001', NOW())
ON CONFLICT (group_id, user_id) DO NOTHING;

-- All Developers Group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_at)
VALUES 
    ('vgm-dev-john', 'vg-all-developers', 'dev-john-001', NOW()),
    ('vgm-dev-mary', 'vg-all-developers', 'dev-mary-001', NOW()),
    ('vgm-dev-peter', 'vg-all-developers', 'dev-peter-001', NOW()),
    ('vgm-dev-lisa', 'vg-all-developers', 'dev-lisa-001', NOW()),
    ('vgm-dev-alex', 'vg-all-developers', 'dev-alex-001', NOW()),
    ('vgm-dev-emma', 'vg-all-developers', 'dev-emma-001', NOW()),
    ('vgm-dev-core-lead', 'vg-all-developers', 'core-lead-001', NOW()),
    ('vgm-dev-channel-lead', 'vg-all-developers', 'channel-lead-001', NOW()),
    ('vgm-dev-risk-lead', 'vg-all-developers', 'risk-lead-001', NOW())
ON CONFLICT (group_id, user_id) DO NOTHING;

-- Team Leaders Group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_at)
VALUES 
    ('vgm-lead-core', 'vg-team-leads', 'core-lead-001', NOW()),
    ('vgm-lead-channel', 'vg-team-leads', 'channel-lead-001', NOW()),
    ('vgm-lead-risk', 'vg-team-leads', 'risk-lead-001', NOW())
ON CONFLICT (group_id, user_id) DO NOTHING;

-- Senior Developers Group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_at)
VALUES 
    ('vgm-sr-john', 'vg-senior-devs', 'dev-john-001', NOW()),
    ('vgm-sr-peter', 'vg-senior-devs', 'dev-peter-001', NOW()),
    ('vgm-sr-alex', 'vg-senior-devs', 'dev-alex-001', NOW())
ON CONFLICT (group_id, user_id) DO NOTHING;

-- Approvers Group (managers + tech director)
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_at)
VALUES 
    ('vgm-appr-hr', 'vg-approvers', 'hr-manager-001', NOW()),
    ('vgm-appr-corp-dir', 'vg-approvers', 'corp-director-001', NOW()),
    ('vgm-appr-corp-mgr', 'vg-approvers', 'corp-manager-001', NOW()),
    ('vgm-appr-tech', 'vg-approvers', 'tech-director-001', NOW()),
    ('vgm-appr-core-lead', 'vg-approvers', 'core-lead-001', NOW()),
    ('vgm-appr-channel-lead', 'vg-approvers', 'channel-lead-001', NOW()),
    ('vgm-appr-risk-lead', 'vg-approvers', 'risk-lead-001', NOW())
ON CONFLICT (group_id, user_id) DO NOTHING;

-- =====================================================
-- 3. Assign Roles to Virtual Groups
-- =====================================================

-- All Managers get MANAGER role through virtual group
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_at)
VALUES 
    ('ra-vg-managers', 'MANAGER_ROLE', 'VIRTUAL_GROUP', 'vg-all-managers', NOW())
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- All Developers get DEVELOPER role through virtual group
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_at)
VALUES 
    ('ra-vg-developers', 'DEVELOPER_ROLE', 'VIRTUAL_GROUP', 'vg-all-developers', NOW())
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- Team Leaders get TEAM_LEADER role through virtual group
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_at)
VALUES 
    ('ra-vg-team-leads', 'TEAM_LEADER_ROLE', 'VIRTUAL_GROUP', 'vg-team-leads', NOW())
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

COMMENT ON TABLE sys_virtual_groups IS 'Test virtual groups for cross-service role assignment';
