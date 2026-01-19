-- =====================================================
-- 修复虚拟组类型：将 STATIC 改为 SYSTEM
-- =====================================================
-- 生成时间: 2026-01-18
-- 原因: 数据库中使用了 STATIC 类型，但 VirtualGroupType 枚举只支持 SYSTEM 和 CUSTOM
-- =====================================================

-- 检查当前的 STATIC 类型虚拟组
SELECT id, name, code, type, status 
FROM sys_virtual_groups 
WHERE type = 'STATIC';

-- 将 STATIC 类型更新为 SYSTEM 类型
-- STATIC 类型的虚拟组通常是系统预定义的，应该归类为 SYSTEM
UPDATE sys_virtual_groups 
SET type = 'SYSTEM' 
WHERE type = 'STATIC';

-- 验证修改结果
SELECT 
    type,
    COUNT(*) as count,
    string_agg(name, ', ') as group_names
FROM sys_virtual_groups
GROUP BY type
ORDER BY type;

-- 检查表约束（应该只允许 SYSTEM 和 CUSTOM）
SELECT 
    conname as constraint_name,
    pg_get_constraintdef(oid) as constraint_definition
FROM pg_constraint
WHERE conrelid = 'sys_virtual_groups'::regclass
AND contype = 'c';
