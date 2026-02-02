-- =====================================================
-- Test Users Initialization
-- =====================================================
-- This script creates 5 test users and assigns them to virtual groups
-- Password for all users: password (BCrypt hash: $2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa)
-- =====================================================

\echo '========================================='
\echo 'Creating Test Users...'
\echo '========================================='

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

\echo '✓ 5 test users created successfully'
\echo ''

\echo '========================================='
\echo 'Assigning Users to Virtual Groups...'
\echo '========================================='

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

-- Add designer to Designers group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES 
('vgm-designer-001', 'vg-designers', 'user-designer', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (group_id, user_id) DO NOTHING;

\echo '✓ All users assigned to virtual groups successfully'
\echo ''

\echo '========================================='
\echo 'Test Users Summary'
\echo '========================================='
\echo 'Login Credentials (all passwords: password):'
\echo ''
\echo '  1. admin / password'
\echo '     Role: SYS_ADMIN (系统管理员)'
\echo '     Group: SYSTEM_ADMINISTRATORS'
\echo '     Access: Full system access'
\echo ''
\echo '  2. auditor / password'
\echo '     Role: AUDITOR (审计员)'
\echo '     Group: AUDITORS'
\echo '     Access: Audit logs and monitoring'
\echo ''
\echo '  3. manager / password'
\echo '     Role: MANAGER (部门经理)'
\echo '     Group: MANAGERS'
\echo '     Access: Department workflows and approvals'
\echo ''
\echo '  4. developer / password'
\echo '     Role: DEVELOPER (工作流开发者)'
\echo '     Group: DEVELOPERS'
\echo '     Access: Developer workstation'
\echo ''
\echo '  5. designer / password'
\echo '     Role: DESIGNER (工作流设计师)'
\echo '     Group: DESIGNERS'
\echo '     Access: Process and form design'
\echo '========================================='
