-- =====================================================
-- 查询 admin 用户所在的虚拟组并授予 admin-center 登录权限
-- =====================================================

-- 1. 查询 admin 用户信息
SELECT 
    id,
    username,
    email,
    full_name,
    display_name,
    status,
    deleted
FROM sys_users 
WHERE username = 'admin';

-- 2. 查询 admin 用户所在的虚拟组
SELECT 
    vg.id as group_id,
    vg.name as group_name,
    vg.code as group_code,
    vg.type as group_type,
    vg.status as group_status,
    vgm.joined_at,
    vgm.added_by
FROM sys_virtual_groups vg
JOIN sys_virtual_group_members vgm ON vg.id = vgm.group_id
JOIN sys_users u ON vgm.user_id = u.id
WHERE u.username = 'admin'
ORDER BY vgm.joined_at DESC;

-- 3. 查询 admin 用户当前的角色分配
SELECT 
    ra.id as assignment_id,
    ra.target_type,
    ra.target_id,
    r.id as role_id,
    r.code as role_code,
    r.name as role_name,
    r.type as role_type,
    r.status as role_status,
    ra.assigned_at,
    ra.assigned_by,
    ra.valid_from,
    ra.valid_to
FROM sys_role_assignments ra
JOIN sys_roles r ON ra.role_id = r.id
JOIN sys_users u ON ra.target_id = u.id
WHERE u.username = 'admin' 
  AND ra.target_type = 'USER'
ORDER BY ra.assigned_at DESC;

-- 4. 查询可以登录 admin-center 的角色（SYS_ADMIN 或 AUDITOR）
SELECT 
    id,
    code,
    name,
    type,
    status
FROM sys_roles
WHERE code IN ('SYS_ADMIN', 'AUDITOR')
  AND status = 'ACTIVE';

-- =====================================================
-- 5. 授予 admin 用户 admin-center 登录权限
-- 注意：admin-center 需要 SYS_ADMIN 或 AUDITOR 角色
-- =====================================================

-- 方法 1: 如果 admin 还没有 SYS_ADMIN 角色，则添加
-- 注意：需要先获取 admin 用户的 ID 和 SYS_ADMIN 角色的 ID
INSERT INTO sys_role_assignments (
    id,
    role_id,
    target_type,
    target_id,
    assigned_at,
    assigned_by,
    created_at,
    updated_at
)
SELECT 
    'ra-admin-sysadmin-' || TO_CHAR(NOW(), 'YYYYMMDDHH24MISS'),
    'SYS_ADMIN_ROLE',  -- SYS_ADMIN 角色的 ID
    'USER',
    u.id,  -- admin 用户的 ID
    CURRENT_TIMESTAMP,
    'system',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM sys_users u
WHERE u.username = 'admin'
  AND NOT EXISTS (
      SELECT 1 
      FROM sys_role_assignments ra
      JOIN sys_roles r ON ra.role_id = r.id
      WHERE ra.target_type = 'USER'
        AND ra.target_id = u.id
        AND r.code = 'SYS_ADMIN'
        AND (ra.valid_to IS NULL OR ra.valid_to >= NOW())
  )
ON CONFLICT DO NOTHING;

-- 方法 2: 如果 admin 还没有 AUDITOR 角色，则添加（作为备选）
-- INSERT INTO sys_role_assignments (
--     id,
--     role_id,
--     target_type,
--     target_id,
--     assigned_at,
--     assigned_by,
--     created_at,
--     updated_at
-- )
-- SELECT 
--     'ra-admin-auditor-' || TO_CHAR(NOW(), 'YYYYMMDDHH24MISS'),
--     'AUDITOR_ROLE',  -- AUDITOR 角色的 ID
--     'USER',
--     u.id,  -- admin 用户的 ID
--     CURRENT_TIMESTAMP,
--     'system',
--     CURRENT_TIMESTAMP,
--     CURRENT_TIMESTAMP
-- FROM sys_users u
-- WHERE u.username = 'admin'
--   AND NOT EXISTS (
--       SELECT 1 
--       FROM sys_role_assignments ra
--       JOIN sys_roles r ON ra.role_id = r.id
--       WHERE ra.target_type = 'USER'
--         AND ra.target_id = u.id
--         AND r.code = 'AUDITOR'
--         AND (ra.valid_to IS NULL OR ra.valid_to >= NOW())
--   )
-- ON CONFLICT DO NOTHING;

-- =====================================================
-- 6. 验证 admin 用户是否拥有 admin-center 登录权限
-- =====================================================
SELECT 
    u.username,
    u.id as user_id,
    r.code as role_code,
    r.name as role_name,
    CASE 
        WHEN r.code IN ('SYS_ADMIN', 'AUDITOR') THEN '✅ 有权限登录 admin-center'
        ELSE '❌ 无权限登录 admin-center'
    END as access_status
FROM sys_users u
JOIN sys_role_assignments ra ON ra.target_type = 'USER' AND ra.target_id = u.id
JOIN sys_roles r ON ra.role_id = r.id
WHERE u.username = 'admin'
  AND r.status = 'ACTIVE'
  AND (ra.valid_from IS NULL OR ra.valid_from <= NOW())
  AND (ra.valid_to IS NULL OR ra.valid_to >= NOW())
ORDER BY r.code;

-- =====================================================
-- 7. 可选：将 admin 添加到虚拟组（如果需要）
-- =====================================================
-- 示例：将 admin 添加到某个虚拟组
-- 注意：需要先确定要添加到的虚拟组 ID
-- INSERT INTO sys_virtual_group_members (
--     id,
--     group_id,
--     user_id,
--     joined_at,
--     added_by
-- )
-- SELECT 
--     'vgm-admin-' || vg.id || '-' || u.id,
--     vg.id,  -- 虚拟组 ID（需要替换为实际的虚拟组 ID）
--     u.id,   -- admin 用户 ID
--     CURRENT_TIMESTAMP,
--     'system'
-- FROM sys_users u
-- CROSS JOIN sys_virtual_groups vg
-- WHERE u.username = 'admin'
--   AND vg.id = 'YOUR_VIRTUAL_GROUP_ID'  -- 替换为实际的虚拟组 ID
--   AND NOT EXISTS (
--       SELECT 1 
--       FROM sys_virtual_group_members vgm
--       WHERE vgm.group_id = vg.id 
--         AND vgm.user_id = u.id
--   )
-- ON CONFLICT DO NOTHING;
