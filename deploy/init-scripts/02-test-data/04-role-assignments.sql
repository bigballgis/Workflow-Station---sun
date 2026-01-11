-- =====================================================
-- TEST DATA: Role Assignments
-- Assigns roles to test users using sys_role_assignments
-- =====================================================

-- =====================================================
-- 0. Test Business Roles (Non-system roles)
-- =====================================================

-- Business roles for testing (is_system = false)
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
    ('MANAGER_ROLE', 'MANAGER', 'Manager', 'BUSINESS', 'Department manager with approval permissions', 'ACTIVE', false, NOW(), NOW()),
    ('USER_ROLE', 'USER', 'User', 'BUSINESS', 'Regular user with basic permissions', 'ACTIVE', false, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- =====================================================
-- 1. HR Users Role Assignments
-- =====================================================

-- HR Users - MANAGER role
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_at)
VALUES 
    ('ra-user-hr-mgr', 'MANAGER_ROLE', 'USER', 'hr-manager-001', NOW())
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- HR Users - USER role
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_at)
VALUES 
    ('ra-user-hr-spec', 'USER_ROLE', 'USER', 'hr-specialist-001', NOW()),
    ('ra-user-hr-rec', 'USER_ROLE', 'USER', 'hr-recruiter-001', NOW())
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- =====================================================
-- 2. Corporate Banking Users Role Assignments
-- =====================================================

-- Corporate Banking Users - MANAGER role
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_at)
VALUES 
    ('ra-user-corp-dir', 'MANAGER_ROLE', 'USER', 'corp-director-001', NOW()),
    ('ra-user-corp-mgr', 'MANAGER_ROLE', 'USER', 'corp-manager-001', NOW())
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- Corporate Banking Users - USER role
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_at)
VALUES 
    ('ra-user-corp-ana', 'USER_ROLE', 'USER', 'corp-analyst-001', NOW()),
    ('ra-user-corp-off', 'USER_ROLE', 'USER', 'corp-officer-001', NOW())
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- =====================================================
-- 3. IT Users Role Assignments
-- =====================================================

-- IT Users - TECH_DIRECTOR role
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_at)
VALUES 
    ('ra-user-tech-dir', 'TECH_DIRECTOR_ROLE', 'USER', 'tech-director-001', NOW())
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- IT Users - TEAM_LEADER role
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_at)
VALUES 
    ('ra-user-core-lead', 'TEAM_LEADER_ROLE', 'USER', 'core-lead-001', NOW()),
    ('ra-user-channel-lead', 'TEAM_LEADER_ROLE', 'USER', 'channel-lead-001', NOW()),
    ('ra-user-risk-lead', 'TEAM_LEADER_ROLE', 'USER', 'risk-lead-001', NOW())
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- IT Users - DEVELOPER role
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_at)
VALUES 
    ('ra-user-dev-john', 'DEVELOPER_ROLE', 'USER', 'dev-john-001', NOW()),
    ('ra-user-dev-mary', 'DEVELOPER_ROLE', 'USER', 'dev-mary-001', NOW()),
    ('ra-user-dev-peter', 'DEVELOPER_ROLE', 'USER', 'dev-peter-001', NOW()),
    ('ra-user-dev-lisa', 'DEVELOPER_ROLE', 'USER', 'dev-lisa-001', NOW()),
    ('ra-user-dev-alex', 'DEVELOPER_ROLE', 'USER', 'dev-alex-001', NOW()),
    ('ra-user-dev-emma', 'DEVELOPER_ROLE', 'USER', 'dev-emma-001', NOW())
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- =====================================================
-- TEST ACCOUNTS SUMMARY
-- =====================================================
-- 
-- Admin Center:
--   admin / admin123 (System Admin - SYS_ADMIN role)
--
-- User Portal (HR):
--   hr.manager / admin123 (HR Manager - MANAGER role)
--   hr.specialist / admin123 (HR Specialist - USER role)
--   hr.recruiter / admin123 (Recruiter - USER role)
--
-- User Portal (Corporate Banking):
--   corp.director / admin123 (Director - MANAGER role)
--   corp.manager / admin123 (Senior Manager - MANAGER role)
--   corp.analyst / admin123 (Business Analyst - USER role)
--   corp.officer / admin123 (Relationship Officer - USER role)
--
-- Developer Workstation:
--   tech.director / admin123 (Technical Director - TECH_DIRECTOR role)
--   core.lead / admin123 (Core Banking Team Leader - TEAM_LEADER role)
--   channel.lead / admin123 (Channel Team Leader - TEAM_LEADER role)
--   risk.lead / admin123 (Risk Team Leader - TEAM_LEADER role)
--   dev.john / admin123 (Senior Developer - DEVELOPER role)
--   dev.mary / admin123 (Developer - DEVELOPER role)
--   dev.peter / admin123 (Senior Developer - DEVELOPER role)
--   dev.lisa / admin123 (Developer - DEVELOPER role)
--   dev.alex / admin123 (Senior Developer - DEVELOPER role)
--   dev.emma / admin123 (Developer - DEVELOPER role)
--
-- =====================================================
