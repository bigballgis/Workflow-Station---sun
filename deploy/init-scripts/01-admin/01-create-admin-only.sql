-- =====================================================
-- Admin User Only Initialization
-- =====================================================
-- Creates admin + 常用开发测试号，并加入系统管理员虚拟组。
-- Password: admin123（BCrypt，与 admin-center BCryptTest 及前端快捷登录一致）
-- =====================================================

\echo '========================================='
\echo 'Creating Admin User Only...'
\echo '========================================='

-- Admin User（password = admin123）
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, employee_id, status, language, must_change_password, created_at, updated_at, deleted)
VALUES 
('user-admin', 'admin', '$2a$10$XMfQkI8Q4i2ZOLcl.V5RH.SoLTbPpfsxbv0YG21jRr8F7zhNouMle', 'admin@example.com', 'System Admin', 'System Administrator', NULL, 'ACTIVE', 'en', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false)
ON CONFLICT (username) DO UPDATE SET 
    password_hash = EXCLUDED.password_hash,
    email = EXCLUDED.email,
    display_name = EXCLUDED.display_name,
    full_name = EXCLUDED.full_name,
    employee_id = EXCLUDED.employee_id,
    must_change_password = EXCLUDED.must_change_password,
    updated_at = CURRENT_TIMESTAMP;

\echo '✓ Admin user created'

-- 测试账号：登录名须填 username（与工号相同），非仅 employee_id 查询
INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, employee_id, status, language, must_change_password, created_at, updated_at, deleted)
VALUES 
('user-test-44027893', '44027893', '$2a$10$XMfQkI8Q4i2ZOLcl.V5RH.SoLTbPpfsxbv0YG21jRr8F7zhNouMle', '44027893@e2e.workflow.local', '测试用户', '测试用户', '44027893', 'ACTIVE', 'zh_CN', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false)
ON CONFLICT (username) DO UPDATE SET 
    password_hash = EXCLUDED.password_hash,
    email = EXCLUDED.email,
    display_name = EXCLUDED.display_name,
    full_name = EXCLUDED.full_name,
    employee_id = EXCLUDED.employee_id,
    must_change_password = EXCLUDED.must_change_password,
    updated_at = CURRENT_TIMESTAMP;

\echo '✓ Test user 44027893 created'

-- Add admin to System Administrators group
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES 
('vgm-admin-001', 'vg-sys-admins', 'user-admin', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (group_id, user_id) DO NOTHING;

-- 测试号：与 admin 同级虚拟组，便于联调管理端 / 门户
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES 
('vgm-test-44027893', 'vg-sys-admins', 'user-test-44027893', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (group_id, user_id) DO NOTHING;

-- 管理端登录使用 UserRoleService（仅认 sys_role_assignments）；直接 USER 分配最稳妥
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_by, assigned_at, created_at, updated_at)
VALUES
('ra-seed-user-admin', 'role-sys-admin', 'USER', 'user-admin', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ra-seed-44027893', 'role-sys-admin', 'USER', 'user-test-44027893', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

\echo '✓ Admin user assigned to System Administrators group'
\echo '✓ Test user 44027893 assigned to System Administrators group'
\echo ''
\echo 'Login: admin / admin123   OR   44027893 / admin123'
\echo 'IMPORTANT: Change passwords after first login in non-dev environments!'
