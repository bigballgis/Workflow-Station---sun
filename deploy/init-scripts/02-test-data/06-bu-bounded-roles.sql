-- =====================================================
-- TEST DATA: BU-Bounded Roles
-- 
-- BU-Bounded roles are roles that require business unit membership to be activated.
-- Users get these roles through virtual groups, but the roles only become active
-- when the user is also a member of a business unit.
-- 
-- This script creates:
-- 1. BU-Bounded roles (type = 'BU_BOUNDED')
-- 2. Associates roles with business units (eligible roles)
-- 3. Creates virtual groups for these roles
-- 4. Assigns users to virtual groups
-- 5. Assigns users to business units (to activate the roles)
-- =====================================================

-- =====================================================
-- 1. Create BU-Bounded Roles
-- =====================================================

INSERT INTO sys_roles (id, name, code, description, type, is_system, status, created_at, updated_at)
VALUES 
    ('BRANCH_MANAGER_ROLE', 'Branch Manager', 'BRANCH_MANAGER', 'Branch management access - requires BU membership', 'BU_BOUNDED', false, 'ACTIVE', NOW(), NOW()),
    ('DEPT_SUPERVISOR_ROLE', 'Department Supervisor', 'DEPT_SUPERVISOR', 'Department supervision access - requires BU membership', 'BU_BOUNDED', false, 'ACTIVE', NOW(), NOW()),
    ('LOAN_OFFICER_ROLE', 'Loan Officer', 'LOAN_OFFICER', 'Loan processing access - requires BU membership', 'BU_BOUNDED', false, 'ACTIVE', NOW(), NOW()),
    ('RISK_ANALYST_ROLE', 'Risk Analyst', 'RISK_ANALYST', 'Risk analysis access - requires BU membership', 'BU_BOUNDED', false, 'ACTIVE', NOW(), NOW()),
    ('COMPLIANCE_OFFICER_ROLE', 'Compliance Officer', 'COMPLIANCE_OFFICER', 'Compliance review access - requires BU membership', 'BU_BOUNDED', false, 'ACTIVE', NOW(), NOW())
ON CONFLICT (code) DO UPDATE SET 
    type = 'BU_BOUNDED',
    description = EXCLUDED.description,
    updated_at = NOW();

-- =====================================================
-- 2. Associate BU-Bounded Roles with Business Units (Eligible Roles)
-- This defines which roles can be applied for in each business unit
-- =====================================================

-- Beijing Branch - eligible roles
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at, created_by)
VALUES 
    ('bur-bj-branch-mgr', 'DEPT-BRANCH-BJ', 'BRANCH_MANAGER_ROLE', NOW(), 'system'),
    ('bur-bj-loan-officer', 'DEPT-BRANCH-BJ', 'LOAN_OFFICER_ROLE', NOW(), 'system'),
    ('bur-bj-risk-analyst', 'DEPT-BRANCH-BJ', 'RISK_ANALYST_ROLE', NOW(), 'system')
ON CONFLICT (business_unit_id, role_id) DO NOTHING;

-- Shanghai Branch - eligible roles
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at, created_by)
VALUES 
    ('bur-sh-branch-mgr', 'DEPT-BRANCH-SH', 'BRANCH_MANAGER_ROLE', NOW(), 'system'),
    ('bur-sh-loan-officer', 'DEPT-BRANCH-SH', 'LOAN_OFFICER_ROLE', NOW(), 'system'),
    ('bur-sh-risk-analyst', 'DEPT-BRANCH-SH', 'RISK_ANALYST_ROLE', NOW(), 'system')
ON CONFLICT (business_unit_id, role_id) DO NOTHING;

-- Guangzhou Branch - eligible roles
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at, created_by)
VALUES 
    ('bur-gz-branch-mgr', 'DEPT-BRANCH-GZ', 'BRANCH_MANAGER_ROLE', NOW(), 'system'),
    ('bur-gz-loan-officer', 'DEPT-BRANCH-GZ', 'LOAN_OFFICER_ROLE', NOW(), 'system')
ON CONFLICT (business_unit_id, role_id) DO NOTHING;

-- Shenzhen Branch - eligible roles
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at, created_by)
VALUES 
    ('bur-sz-branch-mgr', 'DEPT-BRANCH-SZ', 'BRANCH_MANAGER_ROLE', NOW(), 'system'),
    ('bur-sz-loan-officer', 'DEPT-BRANCH-SZ', 'LOAN_OFFICER_ROLE', NOW(), 'system')
ON CONFLICT (business_unit_id, role_id) DO NOTHING;

-- Corporate Banking - eligible roles
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at, created_by)
VALUES 
    ('bur-corp-dept-super', 'DEPT-CORP-BANKING', 'DEPT_SUPERVISOR_ROLE', NOW(), 'system'),
    ('bur-corp-loan-officer', 'DEPT-CORP-BANKING', 'LOAN_OFFICER_ROLE', NOW(), 'system'),
    ('bur-corp-risk-analyst', 'DEPT-CORP-BANKING', 'RISK_ANALYST_ROLE', NOW(), 'system')
ON CONFLICT (business_unit_id, role_id) DO NOTHING;

-- Compliance Department - eligible roles
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at, created_by)
VALUES 
    ('bur-compliance-officer', 'DEPT-COMPLIANCE', 'COMPLIANCE_OFFICER_ROLE', NOW(), 'system'),
    ('bur-compliance-super', 'DEPT-COMPLIANCE', 'DEPT_SUPERVISOR_ROLE', NOW(), 'system')
ON CONFLICT (business_unit_id, role_id) DO NOTHING;

-- Risk Management - eligible roles
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at, created_by)
VALUES 
    ('bur-risk-analyst', 'DEPT-RISK', 'RISK_ANALYST_ROLE', NOW(), 'system'),
    ('bur-risk-super', 'DEPT-RISK', 'DEPT_SUPERVISOR_ROLE', NOW(), 'system')
ON CONFLICT (business_unit_id, role_id) DO NOTHING;

-- =====================================================
-- 3. Create Virtual Groups for BU-Bounded Roles
-- Users get roles through virtual groups
-- =====================================================

INSERT INTO sys_virtual_groups (id, name, code, description, type, status, created_at, updated_at)
VALUES 
    ('vg-branch-managers', 'Branch Managers', 'BRANCH_MANAGERS', 'Users with branch manager role (requires BU membership)', 'CUSTOM', 'ACTIVE', NOW(), NOW()),
    ('vg-dept-supervisors', 'Department Supervisors', 'DEPT_SUPERVISORS', 'Users with department supervisor role (requires BU membership)', 'CUSTOM', 'ACTIVE', NOW(), NOW()),
    ('vg-loan-officers', 'Loan Officers', 'LOAN_OFFICERS', 'Users with loan officer role (requires BU membership)', 'CUSTOM', 'ACTIVE', NOW(), NOW()),
    ('vg-risk-analysts', 'Risk Analysts', 'RISK_ANALYSTS', 'Users with risk analyst role (requires BU membership)', 'CUSTOM', 'ACTIVE', NOW(), NOW()),
    ('vg-compliance-officers', 'Compliance Officers', 'COMPLIANCE_OFFICERS', 'Users with compliance officer role (requires BU membership)', 'CUSTOM', 'ACTIVE', NOW(), NOW())
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = NOW();

-- =====================================================
-- 4. Bind BU-Bounded Roles to Virtual Groups
-- =====================================================

INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at, created_by)
VALUES 
    ('vgr-branch-mgrs-role', 'vg-branch-managers', 'BRANCH_MANAGER_ROLE', NOW(), 'system'),
    ('vgr-dept-supers-role', 'vg-dept-supervisors', 'DEPT_SUPERVISOR_ROLE', NOW(), 'system'),
    ('vgr-loan-officers-role', 'vg-loan-officers', 'LOAN_OFFICER_ROLE', NOW(), 'system'),
    ('vgr-risk-analysts-role', 'vg-risk-analysts', 'RISK_ANALYST_ROLE', NOW(), 'system'),
    ('vgr-compliance-officers-role', 'vg-compliance-officers', 'COMPLIANCE_OFFICER_ROLE', NOW(), 'system')
ON CONFLICT (virtual_group_id, role_id) DO NOTHING;

-- =====================================================
-- 5. Add Users to Virtual Groups (gives them the role)
-- =====================================================

-- Branch Managers virtual group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_by, joined_at)
VALUES 
    ('vgm-bm-corp-mgr', 'vg-branch-managers', 'corp-manager-001', 'system', NOW()),
    ('vgm-bm-hr-mgr', 'vg-branch-managers', 'hr-manager-001', 'system', NOW())
ON CONFLICT (group_id, user_id) DO NOTHING;

-- Loan Officers virtual group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_by, joined_at)
VALUES 
    ('vgm-lo-corp-analyst', 'vg-loan-officers', 'corp-analyst-001', 'system', NOW()),
    ('vgm-lo-corp-officer', 'vg-loan-officers', 'corp-officer-001', 'system', NOW())
ON CONFLICT (group_id, user_id) DO NOTHING;

-- Risk Analysts virtual group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_by, joined_at)
VALUES 
    ('vgm-ra-risk-lead', 'vg-risk-analysts', 'risk-lead-001', 'system', NOW())
ON CONFLICT (group_id, user_id) DO NOTHING;

-- Department Supervisors virtual group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_by, joined_at)
VALUES 
    ('vgm-ds-core-lead', 'vg-dept-supervisors', 'core-lead-001', 'system', NOW()),
    ('vgm-ds-channel-lead', 'vg-dept-supervisors', 'channel-lead-001', 'system', NOW())
ON CONFLICT (group_id, user_id) DO NOTHING;

-- =====================================================
-- 6. Add Users to Business Units (activates BU-Bounded roles)
-- =====================================================

-- Add corp-manager-001 to Beijing Branch (activates BRANCH_MANAGER role)
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at)
VALUES 
    ('ubu-corp-mgr-bj', 'corp-manager-001', 'DEPT-BRANCH-BJ', NOW())
ON CONFLICT (user_id, business_unit_id) DO NOTHING;

-- Add hr-manager-001 to Shanghai Branch (activates BRANCH_MANAGER role)
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at)
VALUES 
    ('ubu-hr-mgr-sh', 'hr-manager-001', 'DEPT-BRANCH-SH', NOW())
ON CONFLICT (user_id, business_unit_id) DO NOTHING;

-- Add corp-analyst-001 to Corporate Banking (activates LOAN_OFFICER role)
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at)
VALUES 
    ('ubu-corp-analyst-corp', 'corp-analyst-001', 'DEPT-CORP-BANKING', NOW())
ON CONFLICT (user_id, business_unit_id) DO NOTHING;

-- Add corp-officer-001 to Beijing Branch (activates LOAN_OFFICER role)
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at)
VALUES 
    ('ubu-corp-officer-bj', 'corp-officer-001', 'DEPT-BRANCH-BJ', NOW())
ON CONFLICT (user_id, business_unit_id) DO NOTHING;

-- Add risk-lead-001 to Risk Management (activates RISK_ANALYST role)
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at)
VALUES 
    ('ubu-risk-lead-risk', 'risk-lead-001', 'DEPT-RISK', NOW())
ON CONFLICT (user_id, business_unit_id) DO NOTHING;

-- Add core-lead-001 to Corporate Banking (activates DEPT_SUPERVISOR role)
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at)
VALUES 
    ('ubu-core-lead-corp', 'core-lead-001', 'DEPT-CORP-BANKING', NOW())
ON CONFLICT (user_id, business_unit_id) DO NOTHING;

-- =====================================================
-- Summary:
-- 
-- BU-Bounded Role Activation Flow:
-- 1. User joins a virtual group → gets the role (but not yet active)
-- 2. User joins a business unit that has the role as eligible → role becomes active
-- 
-- Example: corp-manager-001
-- - Joined virtual group 'vg-branch-managers' → has BRANCH_MANAGER role
-- - Joined business unit 'DEPT-BRANCH-BJ' which has BRANCH_MANAGER as eligible role
-- - Result: BRANCH_MANAGER role is now ACTIVE for this user
-- 
-- Example: hr-specialist-001 (not in this script)
-- - If joins 'vg-branch-managers' → has BRANCH_MANAGER role (inactive)
-- - Not a member of any business unit with BRANCH_MANAGER eligible
-- - Result: BRANCH_MANAGER role is INACTIVE (will show reminder in user-portal)
-- =====================================================

COMMENT ON TABLE sys_business_unit_roles IS 'Eligible roles for each business unit. BU-Bounded roles require both virtual group membership AND business unit membership to be active.';
