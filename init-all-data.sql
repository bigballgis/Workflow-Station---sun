-- =====================================================
-- Complete System Initialization Data
-- =====================================================

-- 1. System Roles
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at) VALUES 
('SYS_ADMIN_ROLE', 'SYS_ADMIN', 'System Administrator', 'ADMIN', 'Full system access', 'ACTIVE', true, NOW(), NOW()),
('AUDITOR_ROLE', 'AUDITOR', 'Auditor', 'ADMIN', 'System audit access', 'ACTIVE', true, NOW(), NOW()),
('DEVELOPER_ROLE', 'DEVELOPER', 'Developer', 'DEVELOPER', 'Development access', 'ACTIVE', true, NOW(), NOW()),
('TEAM_LEADER_ROLE', 'TEAM_LEADER', 'Team Leader', 'DEVELOPER', 'Team management access', 'ACTIVE', true, NOW(), NOW()),
('TECH_DIRECTOR_ROLE', 'TECH_DIRECTOR', 'Technical Director', 'DEVELOPER', 'Technical direction access', 'ACTIVE', true, NOW(), NOW()),
('MANAGER_ROLE', 'MANAGER', 'Manager', 'BU_UNBOUNDED', 'Department manager role', 'ACTIVE', false, NOW(), NOW()),
('USER_ROLE', 'USER', 'User', 'BU_UNBOUNDED', 'Standard user role', 'ACTIVE', false, NOW(), NOW());

-- 2. System Virtual Groups
INSERT INTO sys_virtual_groups (id, name, code, description, type, status, created_at, updated_at) VALUES 
('vg-sys-admins', 'System Administrators', 'SYS_ADMINS', 'System administrators with full access', 'SYSTEM', 'ACTIVE', NOW(), NOW()),
('vg-auditors', 'Auditors', 'AUDITORS', 'System auditors', 'SYSTEM', 'ACTIVE', NOW(), NOW()),
('vg-developers', 'Developers', 'DEVELOPERS', 'System developers', 'SYSTEM', 'ACTIVE', NOW(), NOW()),
('vg-team-leaders', 'Team Leaders', 'TEAM_LEADERS', 'Team leaders', 'SYSTEM', 'ACTIVE', NOW(), NOW()),
('vg-tech-directors', 'Technical Directors', 'TECH_DIRECTORS', 'Technical directors', 'SYSTEM', 'ACTIVE', NOW(), NOW());

-- 3. Bind Roles to Virtual Groups
INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at, created_by) VALUES 
('vgr-sysadmins-sysadmin', 'vg-sys-admins', 'SYS_ADMIN_ROLE', NOW(), 'system'),
('vgr-auditors-auditor', 'vg-auditors', 'AUDITOR_ROLE', NOW(), 'system'),
('vgr-developers-developer', 'vg-developers', 'DEVELOPER_ROLE', NOW(), 'system'),
('vgr-teamleaders-teamleader', 'vg-team-leaders', 'TEAM_LEADER_ROLE', NOW(), 'system'),
('vgr-techdirectors-techdirector', 'vg-tech-directors', 'TECH_DIRECTOR_ROLE', NOW(), 'system');

-- 4. System Permissions
INSERT INTO sys_permissions (id, code, name, type, resource, action, description, created_at) VALUES 
('perm-admin-user-read', 'ADMIN:USER:READ', 'View Users', 'ADMIN', 'user', 'read', 'View user list and details', NOW()),
('perm-admin-user-write', 'ADMIN:USER:WRITE', 'Manage Users', 'ADMIN', 'user', 'write', 'Create, update, delete users', NOW()),
('perm-admin-role-read', 'ADMIN:ROLE:READ', 'View Roles', 'ADMIN', 'role', 'read', 'View role list and details', NOW()),
('perm-admin-role-write', 'ADMIN:ROLE:WRITE', 'Manage Roles', 'ADMIN', 'role', 'write', 'Create, update, delete roles', NOW()),
('perm-admin-bu-read', 'ADMIN:BU:READ', 'View Business Units', 'ADMIN', 'business_unit', 'read', 'View business unit structure', NOW()),
('perm-admin-bu-write', 'ADMIN:BU:WRITE', 'Manage Business Units', 'ADMIN', 'business_unit', 'write', 'Create, update, delete business units', NOW()),
('perm-dev-fu-create', 'DEV:FU:CREATE', 'Create Function Unit', 'DEVELOPER', 'function_unit', 'create', 'Create new function units', NOW()),
('perm-dev-fu-edit', 'DEV:FU:EDIT', 'Edit Function Unit', 'DEVELOPER', 'function_unit', 'edit', 'Edit function units', NOW());

-- 5. Role-Permission Assignments
INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT 'rp-sysadmin-' || p.id, 'SYS_ADMIN_ROLE', p.id, NOW()
FROM sys_permissions p WHERE p.code LIKE 'ADMIN:%';

INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT 'rp-techdir-' || p.id, 'TECH_DIRECTOR_ROLE', p.id, NOW()
FROM sys_permissions p WHERE p.code LIKE 'DEV:%';

-- 6. Test Users (Password: password123)
INSERT INTO sys_users (id, username, password_hash, email, full_name, display_name, status, language, created_at, updated_at) VALUES 
('admin-001', 'admin', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 'admin@example.com', 'System Administrator', 'System Admin', 'ACTIVE', 'zh_CN', NOW(), NOW()),
('tech-director-001', 'tech.director', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 'tech.director@bank.com', 'Robert Sun', 'Robert Sun', 'ACTIVE', 'zh_CN', NOW(), NOW()),
('hr-manager-001', 'hr.manager', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 'hr.manager@bank.com', 'Sarah Chen', 'Sarah Chen', 'ACTIVE', 'zh_CN', NOW(), NOW()),
('corp-manager-001', 'corp.manager', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 'corp.manager@bank.com', 'Linda Li', 'Linda Li', 'ACTIVE', 'zh_CN', NOW(), NOW()),
('dev-john-001', 'dev.john', '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO', 'john.dev@bank.com', 'John Smith', 'John Developer', 'ACTIVE', 'zh_CN', NOW(), NOW());

-- 7. User-Business Unit Assignments
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by) VALUES 
('ubu-admin-hq', 'admin-001', 'DEPT-HQ', NOW(), 'system'),
('ubu-techdirector-it', 'tech-director-001', 'DEPT-IT', NOW(), 'system'),
('ubu-hrmanager-hr', 'hr-manager-001', 'DEPT-HR', NOW(), 'system'),
('ubu-corpmanager-corpbanking', 'corp-manager-001', 'DEPT-CORP-BANKING', NOW(), 'system'),
('ubu-devjohn-devcore', 'dev-john-001', 'DEPT-DEV-CORE', NOW(), 'system');

-- 8. Virtual Group Members
INSERT INTO sys_virtual_group_members (id, group_id, user_id, added_by, joined_at) VALUES 
('vgm-admin-sysadmins', 'vg-sys-admins', 'admin-001', 'system', NOW()),
('vgm-techdirector-techdirectors', 'vg-tech-directors', 'tech-director-001', 'system', NOW());

-- 9. Role Assignments
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_at, assigned_by) VALUES 
('ra-admin-sys-admin', 'SYS_ADMIN_ROLE', 'USER', 'admin-001', NOW(), 'system'),
('ra-tech-director-tech-director', 'TECH_DIRECTOR_ROLE', 'USER', 'tech-director-001', NOW(), 'system');

-- 10. Sync to sys_user_roles
INSERT INTO sys_user_roles (id, user_id, role_id, assigned_at, assigned_by) VALUES
('ur-admin-001-SYS_ADMIN_ROLE', 'admin-001', 'SYS_ADMIN_ROLE', NOW(), 'system'),
('ur-tech-director-001-TECH_DIRECTOR_ROLE', 'tech-director-001', 'TECH_DIRECTOR_ROLE', NOW(), 'system');
