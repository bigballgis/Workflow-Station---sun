-- ========================================
-- Admin 用户权限完整检查脚本
-- 用于在另一台电脑上验证 admin 用户的权限配置
-- ========================================

\echo '========================================='
\echo '1. 检查 admin 用户是否存在'
\echo '========================================='
SELECT 
    id, 
    username, 
    email,
    full_name,
    status, 
    deleted,
    created_at
FROM sys_users 
WHERE id = 'admin-001';

\echo ''
\echo '========================================='
\echo '2. 检查 admin 的角色分配'
\echo '========================================='
SELECT 
    ur.id,
    ur.user_id, 
    ur.role_id, 
    r.code as role_code, 
    r.name as role_name,
    r.type as role_type,
    ur.assigned_at
FROM sys_user_roles ur 
LEFT JOIN sys_roles r ON ur.role_id = r.id 
WHERE ur.user_id = 'admin-001';

\echo ''
\echo '========================================='
\echo '3. 检查 SYS_ADMIN 角色是否存在'
\echo '========================================='
SELECT 
    id, 
    code, 
    name, 
    type, 
    status, 
    is_system,
    description
FROM sys_roles 
WHERE code = 'SYS_ADMIN';

\echo ''
\echo '========================================='
\echo '4. 检查 SYS_ADMIN 角色的权限数量'
\echo '========================================='
SELECT COUNT(*) as permission_count 
FROM sys_role_permissions 
WHERE role_id = 'SYS_ADMIN_ROLE';

\echo ''
\echo '========================================='
\echo '5. 检查 SYS_ADMIN 角色的具体权限'
\echo '========================================='
SELECT 
    rp.id,
    p.code as permission_code, 
    p.name as permission_name, 
    p.resource, 
    p.action 
FROM sys_role_permissions rp 
LEFT JOIN sys_permissions p ON rp.permission_id = p.id 
WHERE rp.role_id = 'SYS_ADMIN_ROLE' 
ORDER BY p.code;

\echo ''
\echo '========================================='
\echo '6. 检查所有 ADMIN 类型权限是否存在'
\echo '========================================='
SELECT 
    id,
    code,
    name,
    resource,
    action
FROM sys_permissions
WHERE type = 'ADMIN'
ORDER BY code;

\echo ''
\echo '========================================='
\echo '7. 检查 admin 是否在 System Administrators 虚拟组'
\echo '========================================='
SELECT 
    vgm.id,
    vgm.user_id, 
    vgm.group_id,
    vg.code as group_code, 
    vg.name as group_name,
    vg.type as group_type,
    vgm.joined_at
FROM sys_virtual_group_members vgm 
LEFT JOIN sys_virtual_groups vg ON vgm.group_id = vg.id 
WHERE vgm.user_id = 'admin-001';

\echo ''
\echo '========================================='
\echo '8. 检查 System Administrators 虚拟组详情'
\echo '========================================='
SELECT 
    id,
    code,
    name,
    type,
    status,
    description
FROM sys_virtual_groups 
WHERE code = 'SYS_ADMINS';

\echo ''
\echo '========================================='
\echo '9. 检查 System Administrators 虚拟组的角色绑定'
\echo '========================================='
SELECT 
    vgr.id,
    vgr.virtual_group_id, 
    vgr.role_id,
    vg.code as group_code,
    r.code as role_code, 
    r.name as role_name 
FROM sys_virtual_group_roles vgr 
LEFT JOIN sys_virtual_groups vg ON vgr.virtual_group_id = vg.id
LEFT JOIN sys_roles r ON vgr.role_id = r.id 
WHERE vgr.virtual_group_id = 'vg-sys-admins';

\echo ''
\echo '========================================='
\echo '10. 检查 admin 的业务单元关联'
\echo '========================================='
SELECT 
    ubu.id,
    ubu.user_id,
    ubu.business_unit_id,
    bu.code as bu_code,
    bu.name as bu_name
FROM sys_user_business_units ubu
LEFT JOIN sys_business_units bu ON ubu.business_unit_id = bu.id
WHERE ubu.user_id = 'admin-001';

\echo ''
\echo '========================================='
\echo '检查完成！'
\echo '========================================='
\echo ''
\echo '期望结果总结：'
\echo '1. admin 用户存在，status=ACTIVE, deleted=false'
\echo '2. admin 有 1 个角色：SYS_ADMIN'
\echo '3. SYS_ADMIN 角色存在，type=ADMIN, is_system=true'
\echo '4. SYS_ADMIN 角色有 9 个权限'
\echo '5. 9 个权限都是 ADMIN:* 类型'
\echo '6. 所有 9 个 ADMIN 权限都存在于 sys_permissions'
\echo '7. admin 是 System Administrators 虚拟组成员'
\echo '8. System Administrators 虚拟组存在，code=SYS_ADMINS'
\echo '9. System Administrators 虚拟组绑定了 SYS_ADMIN 角色'
\echo '10. admin 关联了 2 个业务单元（HQ, IT）'
\echo ''
