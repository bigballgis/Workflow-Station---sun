-- =====================================================
-- Platform Security V2: Initial Data
-- System roles, permissions, virtual groups, business units, and test users
-- =====================================================

-- =====================================================
-- 1. System Roles (5 system roles + 11 business roles)
-- =====================================================
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES 
    -- System roles (is_system = true)
    ('SYS_ADMIN_ROLE', 'SYS_ADMIN', 'System Administrator', 'ADMIN', 'Full system access', 'ACTIVE', true, NOW(), NOW()),
    ('AUDITOR_ROLE', 'AUDITOR', 'Auditor', 'ADMIN', 'System audit access', 'ACTIVE', true, NOW(), NOW()),
    ('DEVELOPER_ROLE', 'DEVELOPER', 'Developer', 'DEVELOPER', 'Development access', 'ACTIVE', true, NOW(), NOW()),
    ('TEAM_LEADER_ROLE', 'TEAM_LEADER', 'Team Leader', 'DEVELOPER', 'Team management access', 'ACTIVE', true, NOW(), NOW()),
    ('TECH_DIRECTOR_ROLE', 'TECH_DIRECTOR', 'Technical Director', 'DEVELOPER', 'Technical direction access', 'ACTIVE', true, NOW(), NOW()),
    -- Business roles (is_system = false)
    ('MANAGER_ROLE', 'MANAGER', 'Manager', 'BU_UNBOUNDED', 'Department manager role', 'ACTIVE', false, NOW(), NOW()),
    ('USER_ROLE', 'USER', 'User', 'BU_UNBOUNDED', 'Standard user role', 'ACTIVE', false, NOW(), NOW()),
    ('BRANCH_MANAGER_ROLE', 'BRANCH_MANAGER', 'Branch Manager', 'BU_BOUNDED', 'Branch manager role', 'ACTIVE', false, NOW(), NOW()),
    ('COMPLIANCE_OFFICER_ROLE', 'COMPLIANCE_OFFICER', 'Compliance Officer', 'BU_BOUNDED', 'Compliance officer role', 'ACTIVE', false, NOW(), NOW()),
    ('COUNTERSIGN_APPROVER_ROLE', 'COUNTERSIGN_APPROVER', 'Countersign Approver', 'BU_UNBOUNDED', 'Countersign approver role', 'ACTIVE', false, NOW(), NOW()),
    ('DEPT_SUPERVISOR_ROLE', 'DEPT_SUPERVISOR', 'Department Supervisor', 'BU_BOUNDED', 'Department supervisor role', 'ACTIVE', false, NOW(), NOW()),
    ('FINANCE_REVIEWER_ROLE', 'FINANCE_REVIEWER', 'Finance Reviewer', 'BU_BOUNDED', 'Finance reviewer role', 'ACTIVE', false, NOW(), NOW()),
    ('LOAN_OFFICER_ROLE', 'LOAN_OFFICER', 'Loan Officer', 'BU_BOUNDED', 'Loan officer role', 'ACTIVE', false, NOW(), NOW()),
    ('PURCHASE_REVIEWER_ROLE', 'PURCHASE_REVIEWER', 'Purchase Reviewer', 'BU_BOUNDED', 'Purchase reviewer role', 'ACTIVE', false, NOW(), NOW()),
    ('RISK_ANALYST_ROLE', 'RISK_ANALYST', 'Risk Analyst', 'BU_BOUNDED', 'Risk analyst role', 'ACTIVE', false, NOW(), NOW()),
    ('SENIOR_APPROVER_ROLE', 'SENIOR_APPROVER', 'Senior Approver', 'BU_BOUNDED', 'Senior approver role', 'ACTIVE', false, NOW(), NOW())
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    is_system = EXCLUDED.is_system,
    updated_at = NOW();

-- =====================================================
-- 2. System Virtual Groups (5 system + 7 custom groups)
-- =====================================================
INSERT INTO sys_virtual_groups (id, name, code, description, type, status, created_at, updated_at)
VALUES 
    -- System groups (type = SYSTEM, cannot be deleted)
    ('vg-sys-admins', 'System Administrators', 'SYS_ADMINS', 'System administrators with full access', 'SYSTEM', 'ACTIVE', NOW(), NOW()),
    ('vg-auditors', 'Auditors', 'AUDITORS', 'System auditors', 'SYSTEM', 'ACTIVE', NOW(), NOW()),
    ('vg-developers', 'Developers', 'DEVELOPERS', 'System developers', 'SYSTEM', 'ACTIVE', NOW(), NOW()),
    ('vg-team-leaders', 'Team Leaders', 'TEAM_LEADERS', 'Team leaders', 'SYSTEM', 'ACTIVE', NOW(), NOW()),
    ('vg-tech-directors', 'Technical Directors', 'TECH_DIRECTORS', 'Technical directors', 'SYSTEM', 'ACTIVE', NOW(), NOW()),
    -- Custom groups (type = CUSTOM)
    ('vg-branch-managers', 'Branch Managers', 'BRANCH_MANAGERS', 'Branch managers group', 'CUSTOM', 'ACTIVE', NOW(), NOW()),
    ('vg-compliance-officers', 'Compliance Officers', 'COMPLIANCE_OFFICERS', 'Compliance officers group', 'CUSTOM', 'ACTIVE', NOW(), NOW()),
    ('vg-countersign-approvers', 'Countersign Approvers', 'COUNTERSIGN_APPROVERS', 'Countersign approvers group', 'CUSTOM', 'ACTIVE', NOW(), NOW()),
    ('vg-dept-supervisors', 'Department Supervisors', 'DEPT_SUPERVISORS', 'Department supervisors group', 'CUSTOM', 'ACTIVE', NOW(), NOW()),
    ('vg-general-users', 'General Users', 'GENERAL_USERS', 'General users group', 'CUSTOM', 'ACTIVE', NOW(), NOW()),
    ('vg-loan-officers', 'Loan Officers', 'LOAN_OFFICERS', 'Loan officers group', 'CUSTOM', 'ACTIVE', NOW(), NOW()),
    ('vg-risk-analysts', 'Risk Analysts', 'RISK_ANALYSTS', 'Risk analysts group', 'CUSTOM', 'ACTIVE', NOW(), NOW())
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    updated_at = NOW();

-- =====================================================
-- 3. Bind Roles to Virtual Groups
-- =====================================================
INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at, created_by)
VALUES 
    ('vgr-sysadmins-sysadmin', 'vg-sys-admins', 'SYS_ADMIN_ROLE', NOW(), 'system'),
    ('vgr-auditors-auditor', 'vg-auditors', 'AUDITOR_ROLE', NOW(), 'system'),
    ('vgr-developers-developer', 'vg-developers', 'DEVELOPER_ROLE', NOW(), 'system'),
    ('vgr-teamleaders-teamleader', 'vg-team-leaders', 'TEAM_LEADER_ROLE', NOW(), 'system'),
    ('vgr-techdirectors-techdirector', 'vg-tech-directors', 'TECH_DIRECTOR_ROLE', NOW(), 'system'),
    ('vgr-branchmanagers-branchmanager', 'vg-branch-managers', 'BRANCH_MANAGER_ROLE', NOW(), 'system'),
    ('vgr-complianceofficers-complianceofficer', 'vg-compliance-officers', 'COMPLIANCE_OFFICER_ROLE', NOW(), 'system'),
    ('vgr-countersignapprovers-countersignapprover', 'vg-countersign-approvers', 'COUNTERSIGN_APPROVER_ROLE', NOW(), 'system'),
    ('vgr-deptsupervisors-deptsupervisor', 'vg-dept-supervisors', 'DEPT_SUPERVISOR_ROLE', NOW(), 'system'),
    ('vgr-generalusers-user', 'vg-general-users', 'USER_ROLE', NOW(), 'system'),
    ('vgr-loanofficers-loanofficer', 'vg-loan-officers', 'LOAN_OFFICER_ROLE', NOW(), 'system'),
    ('vgr-riskanalysts-riskanalyst', 'vg-risk-analysts', 'RISK_ANALYST_ROLE', NOW(), 'system')
ON CONFLICT (virtual_group_id, role_id) DO NOTHING;

-- =====================================================
-- 4. System Permissions
-- =====================================================
INSERT INTO sys_permissions (id, code, name, type, resource, action, description, created_at)
VALUES 
    -- Admin permissions
    ('perm-admin-user-read', 'ADMIN:USER:READ', 'View Users', 'ADMIN', 'user', 'read', 'View user list and details', NOW()),
    ('perm-admin-user-write', 'ADMIN:USER:WRITE', 'Manage Users', 'ADMIN', 'user', 'write', 'Create, update, delete users', NOW()),
    ('perm-admin-role-read', 'ADMIN:ROLE:READ', 'View Roles', 'ADMIN', 'role', 'read', 'View role list and details', NOW()),
    ('perm-admin-role-write', 'ADMIN:ROLE:WRITE', 'Manage Roles', 'ADMIN', 'role', 'write', 'Create, update, delete roles', NOW()),
    ('perm-admin-bu-read', 'ADMIN:BU:READ', 'View Business Units', 'ADMIN', 'business_unit', 'read', 'View business unit structure', NOW()),
    ('perm-admin-bu-write', 'ADMIN:BU:WRITE', 'Manage Business Units', 'ADMIN', 'business_unit', 'write', 'Create, update, delete business units', NOW()),
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
-- 5. Role-Permission Assignments
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

-- USER gets portal permissions
INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT 
    'rp-user-' || p.id,
    'USER_ROLE',
    p.id,
    NOW()
FROM sys_permissions p
WHERE p.code LIKE 'PORTAL:%'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- =====================================================
-- 6. Developer Role Permissions (for developer-workstation)
-- =====================================================

-- TECH_DIRECTOR: All developer permissions
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
SELECT 
    gen_random_uuid()::varchar,
    'TECH_DIRECTOR_ROLE',
    p.permission,
    CURRENT_TIMESTAMP
FROM (VALUES 
    ('FUNCTION_UNIT_CREATE'), ('FUNCTION_UNIT_UPDATE'), ('FUNCTION_UNIT_DELETE'), ('FUNCTION_UNIT_VIEW'),
    ('FUNCTION_UNIT_DEVELOP'), ('FUNCTION_UNIT_PUBLISH'),
    ('FORM_CREATE'), ('FORM_UPDATE'), ('FORM_DELETE'), ('FORM_VIEW'),
    ('PROCESS_CREATE'), ('PROCESS_UPDATE'), ('PROCESS_DELETE'), ('PROCESS_VIEW'),
    ('TABLE_CREATE'), ('TABLE_UPDATE'), ('TABLE_DELETE'), ('TABLE_VIEW'),
    ('ACTION_CREATE'), ('ACTION_UPDATE'), ('ACTION_DELETE'), ('ACTION_VIEW')
) AS p(permission)
ON CONFLICT (role_id, permission) DO NOTHING;

-- TEAM_LEADER: Most permissions
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
SELECT 
    gen_random_uuid()::varchar,
    'TEAM_LEADER_ROLE',
    p.permission,
    CURRENT_TIMESTAMP
FROM (VALUES 
    ('FUNCTION_UNIT_CREATE'), ('FUNCTION_UNIT_UPDATE'), ('FUNCTION_UNIT_DELETE'), ('FUNCTION_UNIT_VIEW'),
    ('FUNCTION_UNIT_DEVELOP'), ('FUNCTION_UNIT_PUBLISH'),
    ('FORM_CREATE'), ('FORM_UPDATE'), ('FORM_DELETE'), ('FORM_VIEW'),
    ('PROCESS_CREATE'), ('PROCESS_UPDATE'), ('PROCESS_DELETE'), ('PROCESS_VIEW'),
    ('TABLE_CREATE'), ('TABLE_UPDATE'), ('TABLE_DELETE'), ('TABLE_VIEW'),
    ('ACTION_CREATE'), ('ACTION_UPDATE'), ('ACTION_DELETE'), ('ACTION_VIEW')
) AS p(permission)
ON CONFLICT (role_id, permission) DO NOTHING;

-- DEVELOPER: View and develop permissions only
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
SELECT 
    gen_random_uuid()::varchar,
    'DEVELOPER_ROLE',
    p.permission,
    CURRENT_TIMESTAMP
FROM (VALUES 
    ('FUNCTION_UNIT_VIEW'), ('FUNCTION_UNIT_DEVELOP'),
    ('FORM_VIEW'), ('FORM_UPDATE'),
    ('PROCESS_VIEW'), ('PROCESS_UPDATE'),
    ('TABLE_VIEW'),
    ('ACTION_VIEW'), ('ACTION_UPDATE')
) AS p(permission)
ON CONFLICT (role_id, permission) DO NOTHING;

-- =====================================================
-- 7. System Dictionaries
-- =====================================================
INSERT INTO sys_dictionaries (id, code, name, description, type, status, version, sort_order, created_at, updated_at)
VALUES 
    ('sys-dict-001', 'USER_STATUS', 'User Status', 'User account status dictionary', 'SYSTEM', 'ACTIVE', 1, 1, NOW(), NOW()),
    ('sys-dict-002', 'GENDER', 'Gender', 'Gender dictionary', 'SYSTEM', 'ACTIVE', 1, 2, NOW(), NOW()),
    ('sys-dict-003', 'LANGUAGE', 'Language', 'System supported languages', 'SYSTEM', 'ACTIVE', 1, 3, NOW(), NOW()),
    ('sys-dict-004', 'ROLE_TYPE', 'Role Type', 'System role types', 'SYSTEM', 'ACTIVE', 1, 4, NOW(), NOW()),
    ('sys-dict-005', 'PERMISSION_TYPE', 'Permission Type', 'Permission categories', 'SYSTEM', 'ACTIVE', 1, 5, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_dictionary_items (id, dictionary_id, item_code, name, name_en, name_zh_cn, value, status, sort_order, created_at, updated_at)
VALUES 
    -- User Status
    ('sys-item-001', 'sys-dict-001', 'ACTIVE', 'Active', 'Active', '启用', 'ACTIVE', 'ACTIVE', 1, NOW(), NOW()),
    ('sys-item-002', 'sys-dict-001', 'INACTIVE', 'Inactive', 'Inactive', '禁用', 'INACTIVE', 'ACTIVE', 2, NOW(), NOW()),
    ('sys-item-003', 'sys-dict-001', 'LOCKED', 'Locked', 'Locked', '锁定', 'LOCKED', 'ACTIVE', 3, NOW(), NOW()),
    -- Gender
    ('sys-item-004', 'sys-dict-002', 'MALE', 'Male', 'Male', '男', 'M', 'ACTIVE', 1, NOW(), NOW()),
    ('sys-item-005', 'sys-dict-002', 'FEMALE', 'Female', 'Female', '女', 'F', 'ACTIVE', 2, NOW(), NOW()),
    ('sys-item-006', 'sys-dict-002', 'OTHER', 'Other', 'Other', '其他', 'O', 'ACTIVE', 3, NOW(), NOW()),
    -- Language
    ('sys-item-007', 'sys-dict-003', 'EN', 'English', 'English', '英文', 'en', 'ACTIVE', 1, NOW(), NOW()),
    ('sys-item-008', 'sys-dict-003', 'ZH_CN', 'Simplified Chinese', 'Simplified Chinese', '简体中文', 'zh-CN', 'ACTIVE', 2, NOW(), NOW()),
    ('sys-item-009', 'sys-dict-003', 'ZH_TW', 'Traditional Chinese', 'Traditional Chinese', '繁体中文', 'zh-TW', 'ACTIVE', 3, NOW(), NOW()),
    -- Role Type
    ('sys-item-010', 'sys-dict-004', 'ADMIN', 'Admin', 'Admin', '管理员', 'ADMIN', 'ACTIVE', 1, NOW(), NOW()),
    ('sys-item-011', 'sys-dict-004', 'DEVELOPER', 'Developer', 'Developer', '开发者', 'DEVELOPER', 'ACTIVE', 2, NOW(), NOW()),
    ('sys-item-012', 'sys-dict-004', 'BUSINESS', 'Business', 'Business', '业务用户', 'BUSINESS', 'ACTIVE', 3, NOW(), NOW()),
    -- Permission Type
    ('sys-item-013', 'sys-dict-005', 'ADMIN', 'Admin Permission', 'Admin Permission', '管理权限', 'ADMIN', 'ACTIVE', 1, NOW(), NOW()),
    ('sys-item-014', 'sys-dict-005', 'DEVELOPER', 'Developer Permission', 'Developer Permission', '开发权限', 'DEVELOPER', 'ACTIVE', 2, NOW(), NOW()),
    ('sys-item-015', 'sys-dict-005', 'BUSINESS', 'Business Permission', 'Business Permission', '业务权限', 'BUSINESS', 'ACTIVE', 3, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;


-- =====================================================
-- 8. Organization Structure (Business Units)
-- =====================================================

-- Level 1: Head Office
INSERT INTO sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, created_at, updated_at)
VALUES ('DEPT-HQ', 'HQ', 'Head Office', NULL, 1, '/HQ', 1, 'ACTIVE', 'Foreign Enterprise Bank Head Office', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, updated_at = NOW();

-- Level 2: Front Office Departments
INSERT INTO sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-CORP-BANKING', 'CORP_BANKING', 'Corporate Banking', 'DEPT-HQ', 2, '/HQ/CORP_BANKING', 1, 'ACTIVE', 'Corporate Banking Division', 'CC-100', NOW(), NOW()),
('DEPT-RETAIL-BANKING', 'RETAIL_BANKING', 'Retail Banking', 'DEPT-HQ', 2, '/HQ/RETAIL_BANKING', 2, 'ACTIVE', 'Retail Banking Division', 'CC-200', NOW(), NOW()),
('DEPT-TREASURY', 'TREASURY', 'Treasury and Markets', 'DEPT-HQ', 2, '/HQ/TREASURY', 3, 'ACTIVE', 'Treasury and Markets Division', 'CC-300', NOW(), NOW()),
('DEPT-INTL-BANKING', 'INTL_BANKING', 'International Banking', 'DEPT-HQ', 2, '/HQ/INTL_BANKING', 4, 'ACTIVE', 'International Banking Division', 'CC-400', NOW(), NOW()),
('DEPT-WEALTH-MGMT', 'WEALTH_MGMT', 'Wealth Management', 'DEPT-HQ', 2, '/HQ/WEALTH_MGMT', 5, 'ACTIVE', 'Wealth Management Division', 'CC-500', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, updated_at = NOW();

-- Level 2: Middle Office Departments
INSERT INTO sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-RISK', 'RISK', 'Risk Management', 'DEPT-HQ', 2, '/HQ/RISK', 10, 'ACTIVE', 'Risk Management Division', 'CC-600', NOW(), NOW()),
('DEPT-COMPLIANCE', 'COMPLIANCE', 'Compliance', 'DEPT-HQ', 2, '/HQ/COMPLIANCE', 11, 'ACTIVE', 'Compliance Division', 'CC-610', NOW(), NOW()),
('DEPT-CREDIT', 'CREDIT', 'Credit Approval', 'DEPT-HQ', 2, '/HQ/CREDIT', 12, 'ACTIVE', 'Credit Approval Division', 'CC-620', NOW(), NOW()),
('DEPT-LEGAL', 'LEGAL', 'Legal Affairs', 'DEPT-HQ', 2, '/HQ/LEGAL', 13, 'ACTIVE', 'Legal Affairs Division', 'CC-630', NOW(), NOW()),
('DEPT-AUDIT', 'AUDIT', 'Internal Audit', 'DEPT-HQ', 2, '/HQ/AUDIT', 14, 'ACTIVE', 'Internal Audit Division', 'CC-640', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, updated_at = NOW();

-- Level 2: Back Office Departments
INSERT INTO sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-OPERATIONS', 'OPERATIONS', 'Operations', 'DEPT-HQ', 2, '/HQ/OPERATIONS', 20, 'ACTIVE', 'Operations Division', 'CC-700', NOW(), NOW()),
('DEPT-IT', 'IT', 'Information Technology', 'DEPT-HQ', 2, '/HQ/IT', 21, 'ACTIVE', 'Information Technology Division', 'CC-710', NOW(), NOW()),
('DEPT-FINANCE', 'FINANCE', 'Finance', 'DEPT-HQ', 2, '/HQ/FINANCE', 22, 'ACTIVE', 'Finance Division', 'CC-720', NOW(), NOW()),
('DEPT-HR', 'HR', 'Human Resources', 'DEPT-HQ', 2, '/HQ/HR', 23, 'ACTIVE', 'Human Resources Division', 'CC-730', NOW(), NOW()),
('DEPT-ADMIN', 'ADMIN', 'Administration', 'DEPT-HQ', 2, '/HQ/ADMIN', 24, 'ACTIVE', 'Administration Division', 'CC-740', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, updated_at = NOW();

-- Level 2: Branches
INSERT INTO sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-BRANCH-BJ', 'BRANCH_BJ', 'Beijing Branch', 'DEPT-HQ', 2, '/HQ/BRANCH_BJ', 30, 'ACTIVE', 'Beijing Branch', 'CC-800', NOW(), NOW()),
('DEPT-BRANCH-SH', 'BRANCH_SH', 'Shanghai Branch', 'DEPT-HQ', 2, '/HQ/BRANCH_SH', 31, 'ACTIVE', 'Shanghai Branch', 'CC-810', NOW(), NOW()),
('DEPT-BRANCH-GZ', 'BRANCH_GZ', 'Guangzhou Branch', 'DEPT-HQ', 2, '/HQ/BRANCH_GZ', 32, 'ACTIVE', 'Guangzhou Branch', 'CC-820', NOW(), NOW()),
('DEPT-BRANCH-SZ', 'BRANCH_SZ', 'Shenzhen Branch', 'DEPT-HQ', 2, '/HQ/BRANCH_SZ', 33, 'ACTIVE', 'Shenzhen Branch', 'CC-830', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, updated_at = NOW();

-- Level 3: Corporate Banking Sub-departments
INSERT INTO sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-CORP-CLIENT', 'CORP_CLIENT', 'Corporate Client Dept', 'DEPT-CORP-BANKING', 3, '/HQ/CORP_BANKING/CORP_CLIENT', 1, 'ACTIVE', 'Corporate Client Department', 'CC-101', NOW(), NOW()),
('DEPT-CORP-CREDIT', 'CORP_CREDIT', 'Corporate Credit Dept', 'DEPT-CORP-BANKING', 3, '/HQ/CORP_BANKING/CORP_CREDIT', 2, 'ACTIVE', 'Corporate Credit Department', 'CC-102', NOW(), NOW()),
('DEPT-TRADE-FINANCE', 'TRADE_FINANCE', 'Trade Finance Dept', 'DEPT-CORP-BANKING', 3, '/HQ/CORP_BANKING/TRADE_FINANCE', 3, 'ACTIVE', 'Trade Finance Department', 'CC-103', NOW(), NOW()),
('DEPT-CASH-MGMT', 'CASH_MGMT', 'Cash Management Dept', 'DEPT-CORP-BANKING', 3, '/HQ/CORP_BANKING/CASH_MGMT', 4, 'ACTIVE', 'Cash Management Department', 'CC-104', NOW(), NOW()),
('DEPT-TRANSACTION', 'TRANSACTION', 'Transaction Banking', 'DEPT-CORP-BANKING', 3, '/HQ/CORP_BANKING/TRANSACTION', 5, 'ACTIVE', 'Transaction Banking Department', 'CC-105', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, updated_at = NOW();

-- Level 3: IT Sub-departments
INSERT INTO sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-IT-DEV', 'IT_DEV', 'Application Development', 'DEPT-IT', 3, '/HQ/IT/IT_DEV', 1, 'ACTIVE', 'Application Development', 'CC-711', NOW(), NOW()),
('DEPT-IT-INFRA', 'IT_INFRA', 'Infrastructure', 'DEPT-IT', 3, '/HQ/IT/IT_INFRA', 2, 'ACTIVE', 'Infrastructure Department', 'CC-712', NOW(), NOW()),
('DEPT-IT-SECURITY', 'IT_SECURITY', 'Information Security', 'DEPT-IT', 3, '/HQ/IT/IT_SECURITY', 3, 'ACTIVE', 'Information Security', 'CC-713', NOW(), NOW()),
('DEPT-IT-OPS', 'IT_OPS', 'IT Operations Center', 'DEPT-IT', 3, '/HQ/IT/IT_OPS', 4, 'ACTIVE', 'IT Operations Center', 'CC-714', NOW(), NOW()),
('DEPT-IT-DATA', 'IT_DATA', 'Data Management', 'DEPT-IT', 3, '/HQ/IT/IT_DATA', 5, 'ACTIVE', 'Data Management', 'CC-715', NOW(), NOW()),
('DEPT-IT-ARCH', 'IT_ARCH', 'Enterprise Architecture', 'DEPT-IT', 3, '/HQ/IT/IT_ARCH', 6, 'ACTIVE', 'Enterprise Architecture', 'CC-716', NOW(), NOW()),
('DEPT-IT-PMO', 'IT_PMO', 'Project Management Office', 'DEPT-IT', 3, '/HQ/IT/IT_PMO', 7, 'ACTIVE', 'Project Management Office', 'CC-717', NOW(), NOW()),
('DEPT-IT-BA', 'IT_BA', 'Business Analysis Dept', 'DEPT-IT', 3, '/HQ/IT/IT_BA', 8, 'ACTIVE', 'Business Analysis Department', 'CC-718', NOW(), NOW()),
('DEPT-IT-TEST', 'IT_TEST', 'Testing Center', 'DEPT-IT', 3, '/HQ/IT/IT_TEST', 9, 'ACTIVE', 'Testing Center', 'CC-719', NOW(), NOW()),
('DEPT-IT-DEVOPS', 'IT_DEVOPS', 'DevOps Center', 'DEPT-IT', 3, '/HQ/IT/IT_DEVOPS', 10, 'ACTIVE', 'DevOps Center', 'CC-720', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, updated_at = NOW();

-- Level 4: Development Teams
INSERT INTO sys_business_units (id, code, name, parent_id, level, path, sort_order, status, description, cost_center, created_at, updated_at) VALUES 
('DEPT-DEV-CORE', 'DEV_CORE', 'Core Banking Dev Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_CORE', 1, 'ACTIVE', 'Core Banking Development Team', 'CC-7111', NOW(), NOW()),
('DEPT-DEV-CHANNEL', 'DEV_CHANNEL', 'Channel Dev Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_CHANNEL', 2, 'ACTIVE', 'Channel Development Team', 'CC-7112', NOW(), NOW()),
('DEPT-DEV-RISK', 'DEV_RISK', 'Risk System Dev Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_RISK', 3, 'ACTIVE', 'Risk System Development Team', 'CC-7113', NOW(), NOW()),
('DEPT-DEV-DATA', 'DEV_DATA', 'Data Platform Dev Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_DATA', 4, 'ACTIVE', 'Data Platform Development Team', 'CC-7114', NOW(), NOW()),
('DEPT-DEV-MOBILE', 'DEV_MOBILE', 'Mobile Dev Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_MOBILE', 5, 'ACTIVE', 'Mobile Development Team', 'CC-7115', NOW(), NOW()),
('DEPT-DEV-WEB', 'DEV_WEB', 'Web Frontend Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_WEB', 6, 'ACTIVE', 'Web Frontend Team', 'CC-7116', NOW(), NOW()),
('DEPT-DEV-API', 'DEV_API', 'API Platform Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_API', 7, 'ACTIVE', 'API Platform Team', 'CC-7117', NOW(), NOW()),
('DEPT-DEV-AI', 'DEV_AI', 'AI/ML Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_AI', 8, 'ACTIVE', 'AI/ML Team', 'CC-7118', NOW(), NOW()),
('DEPT-DEV-QA', 'DEV_QA', 'Quality Assurance Team', 'DEPT-IT-DEV', 4, '/HQ/IT/IT_DEV/DEV_QA', 9, 'ACTIVE', 'Quality Assurance Team', 'CC-7119', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, updated_at = NOW();


-- =====================================================
-- 9. Test Users (39 users)
-- Password: password123 (BCrypt encoded)
-- =====================================================
INSERT INTO sys_users (id, username, password_hash, email, full_name, display_name, status, language, created_at, updated_at)
VALUES 
    -- System users
    ('admin-001', 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'admin@example.com', 'System Administrator', 'System Admin', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('90140d0a-6fbb-4432-b07d-e208fb6ebd55', 'auditor', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'auditor@example.com', '审计员', '审计员', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('aa220ecd-bb5b-4ba5-aa0a-27af144b9679', 'designer', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'designer@example.com', '流程设计师', '流程设计师', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('635281da-5dbb-4118-9610-dd4d6318dcd6', 'developer', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'developer@example.com', '开发人员', '开发人员', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('b4fe69e8-7313-48c5-865b-878231c24b9f', 'dev_lead', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'dev_lead@example.com', '开发组长', '开发组长', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('7e468949-05ea-4c41-8ab5-484fb0626185', 'senior_dev', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'senior_dev@example.com', '高级开发', '高级开发', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('e9c974a2-3b71-4eba-9082-3b8d8cd03f08', 'super_admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'super_admin@example.com', '超级管理员', '超级管理员', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('f64d52ad-be7a-45ed-9b49-21138310b67c', 'system_admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'system_admin@example.com', '系统管理员', '系统管理员', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('b7890c89-ef16-491b-ba2f-ef559817eb8a', 'team_lead', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'team_lead@example.com', '团队主管', '团队主管', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('fbba7111-5efb-4623-9239-27807c66fede', 'tenant_admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'tenant_admin@example.com', '租户管理员', '租户管理员', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('e23b8e53-b9b0-4a7a-a704-9b1af77f97e3', 'tester', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'tester@example.com', '测试人员', '测试人员', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    -- Business users
    ('9ad52216-f42b-4259-84eb-5e53a8fb0a3b', 'manager', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'manager@example.com', '部门经理', '部门经理', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('e7eb22f1-aa8a-4eda-b4b0-f52d53622b3a', 'employee_a', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'employee_a@example.com', '员工张三', '员工张三', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('c7529039-05aa-4cdc-9b12-c8efa34bd61e', 'employee_b', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'employee_b@example.com', '员工李四', '员工李四', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('48b3dad7-c5a9-4000-8b6b-80453e59a6da', 'finance', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'finance@example.com', '财务人员', '财务人员', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('7a55eeb0-d0cf-4b58-911d-61334643a374', 'hr_staff', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'hr_staff@example.com', 'HR专员', 'HR专员', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    -- Bank staff users
    ('channel-lead-001', 'channel.lead', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'channel.lead@bank.com', 'Grace Lin', 'Grace Lin', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('core-lead-001', 'core.lead', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'core.lead@bank.com', 'Kevin Huang', 'Kevin Huang', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('corp-analyst-001', 'corp.analyst', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'corp.analyst@bank.com', 'David Wu', 'David Wu', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('corp-director-001', 'corp.director', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'corp.director@bank.com', 'James Zhang', 'James Zhang', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('corp-manager-001', 'corp.manager', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'corp.manager@bank.com', 'Linda Li', 'Linda Li', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('corp-officer-001', 'corp.officer', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'corp.officer@bank.com', 'Amy Zhao', 'Amy Zhao', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('hr-manager-001', 'hr.manager', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'hr.manager@bank.com', 'Sarah Chen', 'Sarah Chen', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('hr-recruiter-001', 'hr.recruiter', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'hr.recruiter@bank.com', 'Emily Liu', 'Emily Liu', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('hr-specialist-001', 'hr.specialist', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'hr.specialist@bank.com', 'Michael Wang', 'Michael Wang', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('risk-lead-001', 'risk.lead', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'risk.lead@bank.com', 'Tony Chen', 'Tony Chen', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('tech-director-001', 'tech.director', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'tech.director@bank.com', 'Robert Sun', 'Robert Sun', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    -- Developer users
    ('dev-alex-001', 'dev.alex', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'alex.dev@bank.com', 'Alex Zhou', 'Alex Zhou', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('dev-emma-001', 'dev.emma', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'emma.dev@bank.com', 'Emma Liu', 'Emma Liu', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('dev-john-001', 'dev.john', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'john.dev@bank.com', 'John Smith', 'John Developer', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('dev-lisa-001', 'dev.lisa', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'lisa.dev@bank.com', 'Lisa Wang', 'Lisa Wang', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('dev-mary-001', 'dev.mary', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'mary.dev@bank.com', 'Mary Johnson', 'Mary Johnson', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('dev-peter-001', 'dev.peter', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'peter.dev@bank.com', 'Peter Lee', 'Peter Lee', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    -- Workflow test users
    ('countersign-001', 'countersign.approver1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'daniel.brown@example.com', 'Daniel Brown', 'Daniel', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('countersign-002', 'countersign.approver2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'eva.martinez@example.com', 'Eva Martinez', 'Eva', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('dept-reviewer-001', 'dept.reviewer', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'alice.johnson@example.com', 'Alice Johnson', 'Alice', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('finance-reviewer-001', 'finance.reviewer', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'carol.davis@example.com', 'Carol Davis', 'Carol', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('parent-reviewer-001', 'parent.reviewer', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'bob.smith@example.com', 'Bob Smith', 'Bob', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('purchase-requester-001', 'purchase.requester', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'tom.wilson@example.com', 'Tom Wilson', 'Tom', 'ACTIVE', 'zh_CN', NOW(), NOW())
ON CONFLICT (username) DO NOTHING;


-- =====================================================
-- 10. Assign Users to Business Units (many-to-many)
-- =====================================================
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by)
VALUES 
    -- Admin users
    ('ubu-admin-hq', 'admin-001', 'DEPT-HQ', NOW(), 'system'),
    ('ubu-admin-it', 'admin-001', 'DEPT-IT', NOW(), 'system'),
    ('ubu-auditor-compliance', '90140d0a-6fbb-4432-b07d-e208fb6ebd55', 'DEPT-COMPLIANCE', NOW(), 'system'),
    ('ubu-designer-it', 'aa220ecd-bb5b-4ba5-aa0a-27af144b9679', 'DEPT-IT', NOW(), 'system'),
    ('ubu-developer-it', '635281da-5dbb-4118-9610-dd4d6318dcd6', 'DEPT-IT', NOW(), 'system'),
    ('ubu-devlead-it', 'b4fe69e8-7313-48c5-865b-878231c24b9f', 'DEPT-IT', NOW(), 'system'),
    ('ubu-seniordev-it', '7e468949-05ea-4c41-8ab5-484fb0626185', 'DEPT-IT', NOW(), 'system'),
    ('ubu-superadmin-it', 'e9c974a2-3b71-4eba-9082-3b8d8cd03f08', 'DEPT-IT', NOW(), 'system'),
    ('ubu-sysadmin-it', 'f64d52ad-be7a-45ed-9b49-21138310b67c', 'DEPT-IT', NOW(), 'system'),
    ('ubu-teamlead-ops', 'b7890c89-ef16-491b-ba2f-ef559817eb8a', 'DEPT-OPERATIONS', NOW(), 'system'),
    ('ubu-tenantadmin-it', 'fbba7111-5efb-4623-9239-27807c66fede', 'DEPT-IT', NOW(), 'system'),
    ('ubu-tester-it', 'e23b8e53-b9b0-4a7a-a704-9b1af77f97e3', 'DEPT-IT', NOW(), 'system'),
    -- Business users
    ('ubu-manager-ops', '9ad52216-f42b-4259-84eb-5e53a8fb0a3b', 'DEPT-OPERATIONS', NOW(), 'system'),
    ('ubu-employeea-ops', 'e7eb22f1-aa8a-4eda-b4b0-f52d53622b3a', 'DEPT-OPERATIONS', NOW(), 'system'),
    ('ubu-employeeb-ops', 'c7529039-05aa-4cdc-9b12-c8efa34bd61e', 'DEPT-OPERATIONS', NOW(), 'system'),
    ('ubu-finance-treasury', '48b3dad7-c5a9-4000-8b6b-80453e59a6da', 'DEPT-TREASURY', NOW(), 'system'),
    ('ubu-hrstaff-hq', '7a55eeb0-d0cf-4b58-911d-61334643a374', 'DEPT-HQ', NOW(), 'system'),
    -- Bank staff
    ('ubu-channellead-devchannel', 'channel-lead-001', 'DEPT-DEV-CHANNEL', NOW(), 'system'),
    ('ubu-channellead-devcore', 'channel-lead-001', 'DEPT-DEV-CORE', NOW(), 'system'),
    ('ubu-corelead-corpbanking', 'core-lead-001', 'DEPT-CORP-BANKING', NOW(), 'system'),
    ('ubu-corelead-devcore', 'core-lead-001', 'DEPT-DEV-CORE', NOW(), 'system'),
    ('ubu-corelead-devchannel', 'core-lead-001', 'DEPT-DEV-CHANNEL', NOW(), 'system'),
    ('ubu-corpanalyst-corpbanking', 'corp-analyst-001', 'DEPT-CORP-BANKING', NOW(), 'system'),
    ('ubu-corpdirector-corpbanking', 'corp-director-001', 'DEPT-CORP-BANKING', NOW(), 'system'),
    ('ubu-corpdirector-corpclient', 'corp-director-001', 'DEPT-CORP-CLIENT', NOW(), 'system'),
    ('ubu-corpdirector-corpcredit', 'corp-director-001', 'DEPT-CORP-CREDIT', NOW(), 'system'),
    ('ubu-corpmanager-corpbanking', 'corp-manager-001', 'DEPT-CORP-BANKING', NOW(), 'system'),
    ('ubu-corpmanager-branchbj', 'corp-manager-001', 'DEPT-BRANCH-BJ', NOW(), 'system'),
    ('ubu-corpofficer-corpbanking', 'corp-officer-001', 'DEPT-CORP-BANKING', NOW(), 'system'),
    ('ubu-corpofficer-compliance', 'corp-officer-001', 'DEPT-COMPLIANCE', NOW(), 'system'),
    ('ubu-corpofficer-branchbj', 'corp-officer-001', 'DEPT-BRANCH-BJ', NOW(), 'system'),
    ('ubu-corpofficer-it', 'corp-officer-001', 'DEPT-IT', NOW(), 'system'),
    ('ubu-hrmanager-hr', 'hr-manager-001', 'DEPT-HR', NOW(), 'system'),
    ('ubu-hrmanager-admin', 'hr-manager-001', 'DEPT-ADMIN', NOW(), 'system'),
    ('ubu-hrmanager-branchsh', 'hr-manager-001', 'DEPT-BRANCH-SH', NOW(), 'system'),
    ('ubu-hrrecruiter-hr', 'hr-recruiter-001', 'DEPT-HR', NOW(), 'system'),
    ('ubu-hrspecialist-hr', 'hr-specialist-001', 'DEPT-HR', NOW(), 'system'),
    ('ubu-risklead-risk', 'risk-lead-001', 'DEPT-RISK', NOW(), 'system'),
    ('ubu-risklead-devrisk', 'risk-lead-001', 'DEPT-DEV-RISK', NOW(), 'system'),
    ('ubu-risklead-devcore', 'risk-lead-001', 'DEPT-DEV-CORE', NOW(), 'system'),
    ('ubu-risklead-devchannel', 'risk-lead-001', 'DEPT-DEV-CHANNEL', NOW(), 'system'),
    ('ubu-techdirector-it', 'tech-director-001', 'DEPT-IT', NOW(), 'system'),
    ('ubu-techdirector-itdev', 'tech-director-001', 'DEPT-IT-DEV', NOW(), 'system'),
    -- Developers
    ('ubu-devalex-devrisk', 'dev-alex-001', 'DEPT-DEV-RISK', NOW(), 'system'),
    ('ubu-devemma-devrisk', 'dev-emma-001', 'DEPT-DEV-RISK', NOW(), 'system'),
    ('ubu-devjohn-devrisk', 'dev-john-001', 'DEPT-DEV-RISK', NOW(), 'system'),
    ('ubu-devjohn-devcore', 'dev-john-001', 'DEPT-DEV-CORE', NOW(), 'system'),
    ('ubu-devlisa-devchannel', 'dev-lisa-001', 'DEPT-DEV-CHANNEL', NOW(), 'system'),
    ('ubu-devmary-devcore', 'dev-mary-001', 'DEPT-DEV-CORE', NOW(), 'system'),
    ('ubu-devpeter-devchannel', 'dev-peter-001', 'DEPT-DEV-CHANNEL', NOW(), 'system')
ON CONFLICT (user_id, business_unit_id) DO NOTHING;

-- =====================================================
-- 11. Add Users to Virtual Groups
-- =====================================================
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_by, joined_at)
VALUES 
    -- System Administrators
    ('vgm-admin-sysadmins', 'vg-sys-admins', 'admin-001', 'system', NOW()),
    -- Auditors
    ('vgm-auditor-auditors', 'vg-auditors', '90140d0a-6fbb-4432-b07d-e208fb6ebd55', 'system', NOW()),
    -- Developers
    ('vgm-corpofficer-developers', 'vg-developers', 'corp-officer-001', 'system', NOW()),
    -- Branch Managers
    ('vgm-corpmanager-branchmanagers', 'vg-branch-managers', 'corp-manager-001', 'system', NOW()),
    ('vgm-hrmanager-branchmanagers', 'vg-branch-managers', 'hr-manager-001', 'system', NOW()),
    -- Compliance Officers
    ('vgm-corpofficer-complianceofficers', 'vg-compliance-officers', 'corp-officer-001', 'system', NOW()),
    -- Countersign Approvers
    ('vgm-countersign1-countersignapprovers', 'vg-countersign-approvers', 'countersign-001', 'system', NOW()),
    ('vgm-countersign2-countersignapprovers', 'vg-countersign-approvers', 'countersign-002', 'system', NOW()),
    -- Department Supervisors
    ('vgm-channellead-deptsupervisors', 'vg-dept-supervisors', 'channel-lead-001', 'system', NOW()),
    ('vgm-corelead-deptsupervisors', 'vg-dept-supervisors', 'core-lead-001', 'system', NOW()),
    -- General Users
    ('vgm-countersign1-generalusers', 'vg-general-users', 'countersign-001', 'system', NOW()),
    ('vgm-countersign2-generalusers', 'vg-general-users', 'countersign-002', 'system', NOW()),
    ('vgm-deptreviewer-generalusers', 'vg-general-users', 'dept-reviewer-001', 'system', NOW()),
    ('vgm-financereviewer-generalusers', 'vg-general-users', 'finance-reviewer-001', 'system', NOW()),
    ('vgm-parentreviewer-generalusers', 'vg-general-users', 'parent-reviewer-001', 'system', NOW()),
    ('vgm-purchaserequester-generalusers', 'vg-general-users', 'purchase-requester-001', 'system', NOW()),
    -- Loan Officers
    ('vgm-corpanalyst-loanofficers', 'vg-loan-officers', 'corp-analyst-001', 'system', NOW()),
    ('vgm-corpofficer-loanofficers', 'vg-loan-officers', 'corp-officer-001', 'system', NOW()),
    -- Risk Analysts
    ('vgm-corpofficer-riskanalysts', 'vg-risk-analysts', 'corp-officer-001', 'system', NOW()),
    ('vgm-risklead-riskanalysts', 'vg-risk-analysts', 'risk-lead-001', 'system', NOW())
ON CONFLICT (group_id, user_id) DO NOTHING;
