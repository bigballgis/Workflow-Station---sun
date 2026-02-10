-- =====================================================
-- 创建角色分配记录
-- =====================================================
-- 此脚本直接创建角色分配记录到 sys_role_assignments 表
-- 不依赖 sys_virtual_group_roles 表
-- 
-- 角色分配类型：
-- - VIRTUAL_GROUP: 虚拟组角色分配
-- - USER: 直接用户角色分配

-- =====================================================
-- 虚拟组角色分配
-- =====================================================

-- SYSTEM_ADMINISTRATORS 组 -> SYS_ADMIN 角色
INSERT INTO sys_role_assignments (
    id,
    role_id,
    target_type,
    target_id,
    assigned_by,
    assigned_at,
    valid_from,
    valid_to,
    created_at,
    created_by
) VALUES (
    'ra-vg-sys-admins-sys-admin',
    'role-sys-admin',
    'VIRTUAL_GROUP',
    'vg-sys-admins',
    'system',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    NULL,
    CURRENT_TIMESTAMP,
    'system'
) ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- AUDITORS 组 -> AUDITOR 角色
INSERT INTO sys_role_assignments (
    id,
    role_id,
    target_type,
    target_id,
    assigned_by,
    assigned_at,
    valid_from,
    valid_to,
    created_at,
    created_by
) VALUES (
    'ra-vg-auditors-auditor',
    'role-auditor',
    'VIRTUAL_GROUP',
    'vg-auditors',
    'system',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    NULL,
    CURRENT_TIMESTAMP,
    'system'
) ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- MANAGERS 组 -> MANAGER 角色
INSERT INTO sys_role_assignments (
    id,
    role_id,
    target_type,
    target_id,
    assigned_by,
    assigned_at,
    valid_from,
    valid_to,
    created_at,
    created_by
) VALUES (
    'ra-vg-managers-manager',
    'role-manager',
    'VIRTUAL_GROUP',
    'vg-managers',
    'system',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    NULL,
    CURRENT_TIMESTAMP,
    'system'
) ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- DEVELOPERS 组 -> DEVELOPER 角色
INSERT INTO sys_role_assignments (
    id,
    role_id,
    target_type,
    target_id,
    assigned_by,
    assigned_at,
    valid_from,
    valid_to,
    created_at,
    created_by
) VALUES (
    'ra-vg-developers-developer',
    'role-developer',
    'VIRTUAL_GROUP',
    'vg-developers',
    'system',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    NULL,
    CURRENT_TIMESTAMP,
    'system'
) ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- TEAM_LEADS 组 -> TEAM_LEAD 角色
INSERT INTO sys_role_assignments (
    id,
    role_id,
    target_type,
    target_id,
    assigned_by,
    assigned_at,
    valid_from,
    valid_to,
    created_at,
    created_by
) VALUES (
    'ra-vg-team-leads-team-lead',
    'role-team-lead',
    'VIRTUAL_GROUP',
    'vg-team-leads',
    'system',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    NULL,
    CURRENT_TIMESTAMP,
    'system'
) ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- TECH_LEADS 组 -> TECH_LEAD 角色
INSERT INTO sys_role_assignments (
    id,
    role_id,
    target_type,
    target_id,
    assigned_by,
    assigned_at,
    valid_from,
    valid_to,
    created_at,
    created_by
) VALUES (
    'ra-vg-tech-leads-tech-lead',
    'role-tech-lead',
    'VIRTUAL_GROUP',
    'vg-tech-leads',
    'system',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    NULL,
    CURRENT_TIMESTAMP,
    'system'
) ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- =====================================================
-- 验证插入结果
-- =====================================================
-- 显示所有角色分配
SELECT 
    ra.id,
    r.code as role_code,
    r.name as role_name,
    ra.target_type,
    vg.code as group_code,
    vg.name as group_name
FROM sys_role_assignments ra
JOIN sys_roles r ON ra.role_id = r.id
LEFT JOIN sys_virtual_groups vg ON ra.target_id = vg.id
WHERE ra.target_type = 'VIRTUAL_GROUP'
ORDER BY vg.code, r.code;

-- 显示统计信息
SELECT 
    'Total role assignments' as description,
    COUNT(*) as count
FROM sys_role_assignments
UNION ALL
SELECT 
    'Virtual group assignments' as description,
    COUNT(*) as count
FROM sys_role_assignments
WHERE target_type = 'VIRTUAL_GROUP'
UNION ALL
SELECT 
    'Direct user assignments' as description,
    COUNT(*) as count
FROM sys_role_assignments
WHERE target_type = 'USER';
