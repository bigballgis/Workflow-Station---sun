-- ========================================
-- 完整修复 Admin 用户权限问题
-- 在另一台电脑上执行此脚本
-- ========================================

-- ========================================
-- 1. 创建 SYS_ADMIN 角色（如果不存在）
-- ========================================
INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)
VALUES (
    'SYS_ADMIN_ROLE',
    'SYS_ADMIN',
    'System Administrator',
    'ADMIN',
    'Full system access',
    'ACTIVE',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    is_system = EXCLUDED.is_system,
    updated_at = CURRENT_TIMESTAMP;

\echo '✓ SYS_ADMIN 角色已创建/更新'

-- ========================================
-- 2. 创建 ADMIN 类型权限（如果不存在）
-- ========================================
INSERT INTO sys_permissions (id, code, name, type, resource, action, description, created_at) VALUES
('perm-admin-user-read', 'ADMIN:USER:READ', 'View Users', 'ADMIN', 'user', 'read', 'View user list and details', CURRENT_TIMESTAMP),
('perm-admin-user-write', 'ADMIN:USER:WRITE', 'Manage Users', 'ADMIN', 'user', 'write', 'Create, update, delete users', CURRENT_TIMESTAMP),
('perm-admin-role-read', 'ADMIN:ROLE:READ', 'View Roles', 'ADMIN', 'role', 'read', 'View role list and details', CURRENT_TIMESTAMP),
('perm-admin-role-write', 'ADMIN:ROLE:WRITE', 'Manage Roles', 'ADMIN', 'role', 'write', 'Create, update, delete roles', CURRENT_TIMESTAMP),
('perm-admin-bu-read', 'ADMIN:BU:READ', 'View Business Units', 'ADMIN', 'business_unit', 'read', 'View business unit list', CURRENT_TIMESTAMP),
('perm-admin-bu-write', 'ADMIN:BU:WRITE', 'Manage Business Units', 'ADMIN', 'business_unit', 'write', 'Create, update, delete business units', CURRENT_TIMESTAMP),
('perm-admin-config-read', 'ADMIN:CONFIG:READ', 'View System Config', 'ADMIN', 'config', 'read', 'View system configuration', CURRENT_TIMESTAMP),
('perm-admin-config-write', 'ADMIN:CONFIG:WRITE', 'Manage System Config', 'ADMIN', 'config', 'write', 'Update system configuration', CURRENT_TIMESTAMP),
('perm-admin-audit-read', 'ADMIN:AUDIT:READ', 'View Audit Logs', 'ADMIN', 'audit', 'read', 'View audit logs', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    description = EXCLUDED.description;

\echo '✓ ADMIN 权限已创建/更新'

-- ========================================
-- 3. 将权限分配给 SYS_ADMIN 角色
-- ========================================
INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at) VALUES
('rp-sysadmin-perm-admin-user-read', 'SYS_ADMIN_ROLE', 'perm-admin-user-read', CURRENT_TIMESTAMP),
('rp-sysadmin-perm-admin-user-write', 'SYS_ADMIN_ROLE', 'perm-admin-user-write', CURRENT_TIMESTAMP),
('rp-sysadmin-perm-admin-role-read', 'SYS_ADMIN_ROLE', 'perm-admin-role-read', CURRENT_TIMESTAMP),
('rp-sysadmin-perm-admin-role-write', 'SYS_ADMIN_ROLE', 'perm-admin-role-write', CURRENT_TIMESTAMP),
('rp-sysadmin-perm-admin-bu-read', 'SYS_ADMIN_ROLE', 'perm-admin-bu-read', CURRENT_TIMESTAMP),
('rp-sysadmin-perm-admin-bu-write', 'SYS_ADMIN_ROLE', 'perm-admin-bu-write', CURRENT_TIMESTAMP),
('rp-sysadmin-perm-admin-config-read', 'SYS_ADMIN_ROLE', 'perm-admin-config-read', CURRENT_TIMESTAMP),
('rp-sysadmin-perm-admin-config-write', 'SYS_ADMIN_ROLE', 'perm-admin-config-write', CURRENT_TIMESTAMP),
('rp-sysadmin-perm-admin-audit-read', 'SYS_ADMIN_ROLE', 'perm-admin-audit-read', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

\echo '✓ SYS_ADMIN 角色权限已分配'

-- ========================================
-- 4. 确保 admin 用户存在
-- ========================================
INSERT INTO sys_users (id, username, password_hash, email, full_name, status, language, deleted, created_at, updated_at)
VALUES (
    'admin-001',
    'admin',
    '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO',  -- 密码: admin123
    'admin@example.com',
    'System Administrator',
    'ACTIVE',
    'zh_CN',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    status = 'ACTIVE',
    deleted = false,
    updated_at = CURRENT_TIMESTAMP;

\echo '✓ admin 用户已创建/更新'

-- ========================================
-- 5. 分配 SYS_ADMIN 角色给 admin 用户
-- ========================================
-- 先删除可能存在的错误记录
DELETE FROM sys_user_roles WHERE user_id = 'admin-001' AND role_id != 'SYS_ADMIN_ROLE';

-- 插入或更新正确的角色分配
INSERT INTO sys_user_roles (id, user_id, role_id, assigned_at, assigned_by)
VALUES (
    'ur-admin-001-SYS_ADMIN_ROLE',
    'admin-001',
    'SYS_ADMIN_ROLE',
    CURRENT_TIMESTAMP,
    'system'
)
ON CONFLICT (id) DO UPDATE SET
    role_id = 'SYS_ADMIN_ROLE',
    assigned_at = CURRENT_TIMESTAMP;

\echo '✓ admin 用户角色已分配'

-- ========================================
-- 6. 创建 System Administrators 虚拟组（如果不存在）
-- ========================================
INSERT INTO sys_virtual_groups (id, code, name, type, description, status, created_at, updated_at)
VALUES (
    'vg-sys-admins',
    'SYS_ADMINS',
    'System Administrators',
    'SYSTEM',
    'System administrators with full access',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

\echo '✓ System Administrators 虚拟组已创建/更新'

-- ========================================
-- 7. 将 admin 加入 System Administrators 虚拟组
-- ========================================
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES (
    'vgm-admin-sysadmins',
    'vg-sys-admins',
    'admin-001',
    CURRENT_TIMESTAMP,
    'system'
)
ON CONFLICT (id) DO NOTHING;

\echo '✓ admin 已加入 System Administrators 虚拟组'

-- ========================================
-- 8. 绑定 SYS_ADMIN 角色到 System Administrators 虚拟组
-- ========================================
INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at, created_by)
VALUES (
    'vgr-sysadmins-sysadmin',
    'vg-sys-admins',
    'SYS_ADMIN_ROLE',
    CURRENT_TIMESTAMP,
    'system'
)
ON CONFLICT (id) DO NOTHING;

\echo '✓ System Administrators 虚拟组已绑定 SYS_ADMIN 角色'

-- ========================================
-- 9. 验证修复结果
-- ========================================
\echo ''
\echo '========================================='
\echo '验证修复结果'
\echo '========================================='

\echo ''
\echo '1. admin 用户信息：'
SELECT id, username, status, deleted FROM sys_users WHERE id = 'admin-001';

\echo ''
\echo '2. admin 的角色分配：'
SELECT 
    ur.id,
    ur.user_id,
    ur.role_id,
    r.code as role_code,
    r.name as role_name,
    r.type as role_type
FROM sys_user_roles ur
LEFT JOIN sys_roles r ON ur.role_id = r.id
WHERE ur.user_id = 'admin-001';

\echo ''
\echo '3. SYS_ADMIN 角色的权限数量：'
SELECT COUNT(*) as permission_count 
FROM sys_role_permissions 
WHERE role_id = 'SYS_ADMIN_ROLE';

\echo ''
\echo '4. admin 所属的虚拟组：'
SELECT 
    vgm.user_id,
    vg.code as group_code,
    vg.name as group_name
FROM sys_virtual_group_members vgm
LEFT JOIN sys_virtual_groups vg ON vgm.group_id = vg.id
WHERE vgm.user_id = 'admin-001';

\echo ''
\echo '========================================='
\echo '修复完成！'
\echo '========================================='
\echo ''
\echo '期望结果：'
\echo '1. admin 用户存在，status=ACTIVE, deleted=false'
\echo '2. admin 有角色：role_id=SYS_ADMIN_ROLE, role_code=SYS_ADMIN, role_type=ADMIN'
\echo '3. SYS_ADMIN 角色有 9 个权限'
\echo '4. admin 是 System Administrators (SYS_ADMINS) 虚拟组成员'
\echo ''
\echo '如果以上结果正确，请重启后端服务并重新登录'
\echo ''
