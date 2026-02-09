-- =====================================================
-- Test Users Initialization
-- =====================================================
-- This script creates 5 test users and assigns them to virtual groups
-- Password for all users: password (BCrypt hash: $2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa)
-- =====================================================
-- 1. Super Admin User (超级管理员)
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, status, language, must_change_password, created_at, updated_at, deleted)
VALUES 
('user-admin', 'admin', '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa', 'admin@example.com', '超级管理员', 'System Administrator', 'ACTIVE', 'zh_CN', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false)
ON CONFLICT (username) DO UPDATE SET 
    password_hash = EXCLUDED.password_hash,
    email = EXCLUDED.email,
    display_name = EXCLUDED.display_name,
    full_name = EXCLUDED.full_name,
    updated_at = CURRENT_TIMESTAMP;

-- 2. Auditor User (审计员)
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, status, language, must_change_password, created_at, updated_at, deleted)
VALUES 
('user-auditor', 'auditor', '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa', 'auditor@example.com', '审计员', 'System Auditor', 'ACTIVE', 'zh_CN', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false)
ON CONFLICT (username) DO UPDATE SET 
    password_hash = EXCLUDED.password_hash,
    email = EXCLUDED.email,
    display_name = EXCLUDED.display_name,
    full_name = EXCLUDED.full_name,
    updated_at = CURRENT_TIMESTAMP;

-- 3. Manager User (部门经理)
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, status, language, must_change_password, created_at, updated_at, deleted)
VALUES 
('user-manager', 'manager', '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa', 'manager@example.com', '部门经理', 'Department Manager', 'ACTIVE', 'zh_CN', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false)
ON CONFLICT (username) DO UPDATE SET 
    password_hash = EXCLUDED.password_hash,
    email = EXCLUDED.email,
    display_name = EXCLUDED.display_name,
    full_name = EXCLUDED.full_name,
    updated_at = CURRENT_TIMESTAMP;

-- 4. Developer User (工作流开发者)
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, status, language, must_change_password, created_at, updated_at, deleted)
VALUES 
('user-developer', 'developer', '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa', 'developer@example.com', '工作流开发者', 'Workflow Developer', 'ACTIVE', 'zh_CN', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false)
ON CONFLICT (username) DO UPDATE SET 
    password_hash = EXCLUDED.password_hash,
    email = EXCLUDED.email,
    display_name = EXCLUDED.display_name,
    full_name = EXCLUDED.full_name,
    updated_at = CURRENT_TIMESTAMP;

-- 5. Designer User (工作流设计师)
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, status, language, must_change_password, created_at, updated_at, deleted)
VALUES 
('user-designer', 'designer', '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa', 'designer@example.com', '工作流设计师', 'Workflow Designer', 'ACTIVE', 'zh_CN', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false)
ON CONFLICT (username) DO UPDATE SET 
    password_hash = EXCLUDED.password_hash,
    email = EXCLUDED.email,
    display_name = EXCLUDED.display_name,
    full_name = EXCLUDED.full_name,
    updated_at = CURRENT_TIMESTAMP;

-- Add admin to System Administrators group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES 
('vgm-admin-001', 'vg-sys-admins', 'user-admin', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (group_id, user_id) DO NOTHING;

-- Add auditor to Auditors group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES 
('vgm-auditor-001', 'vg-auditors', 'user-auditor', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (group_id, user_id) DO NOTHING;

-- Add manager to Managers group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES 
('vgm-manager-001', 'vg-managers', 'user-manager', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (group_id, user_id) DO NOTHING;

-- Add developer to Developers group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES 
('vgm-developer-001', 'vg-developers', 'user-developer', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (group_id, user_id) DO NOTHING;

-- Add designer to Developers group (designers are also developers)
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES 
('vgm-designer-001', 'vg-developers', 'user-designer', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (group_id, user_id) DO NOTHING;

-- =====================================================
-- Assign Roles to Users
-- =====================================================

-- Assign SYS_ADMIN role to admin user
INSERT INTO sys_user_roles (id, user_id, role_id, assigned_at, assigned_by)
VALUES 
('ur-admin-001', 'user-admin', 'role-sys-admin', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Assign AUDITOR role to auditor user
INSERT INTO sys_user_roles (id, user_id, role_id, assigned_at, assigned_by)
VALUES 
('ur-auditor-001', 'user-auditor', 'role-auditor', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Assign MANAGER role to manager user
INSERT INTO sys_user_roles (id, user_id, role_id, assigned_at, assigned_by)
VALUES 
('ur-manager-001', 'user-manager', 'role-manager', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Assign DEVELOPER role to developer user
INSERT INTO sys_user_roles (id, user_id, role_id, assigned_at, assigned_by)
VALUES 
('ur-developer-001', 'user-developer', 'role-developer', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Assign DEVELOPER role to designer user
INSERT INTO sys_user_roles (id, user_id, role_id, assigned_at, assigned_by)
VALUES 
('ur-designer-001', 'user-designer', 'role-developer', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (user_id, role_id) DO NOTHING;
