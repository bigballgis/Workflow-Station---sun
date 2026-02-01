-- =====================================================
-- 修复虚拟组 type 字段的默认值：从 STATIC 改为 CUSTOM
-- =====================================================
-- 生成时间: 2026-01-18
-- 原因: 
--   1. Flyway 脚本 V1__init_schema.sql 中定义的默认值是 'CUSTOM'
--   2. 但数据库中实际默认值是 'STATIC'
--   3. 枚举类型 VirtualGroupType 只支持 SYSTEM 和 CUSTOM
--   4. CREATE TABLE IF NOT EXISTS 不会更新已存在表的结构
-- =====================================================

-- 检查当前的默认值
SELECT 
    column_name,
    column_default,
    is_nullable,
    data_type
FROM information_schema.columns
WHERE table_name = 'sys_virtual_groups' 
AND column_name = 'type';

-- 修改默认值为 'CUSTOM'（与 Flyway 脚本一致）
ALTER TABLE sys_virtual_groups 
ALTER COLUMN type SET DEFAULT 'CUSTOM';

-- 验证修改结果
SELECT 
    column_name,
    column_default,
    is_nullable,
    data_type
FROM information_schema.columns
WHERE table_name = 'sys_virtual_groups' 
AND column_name = 'type';

-- 检查约束（应该只允许 SYSTEM 和 CUSTOM）
SELECT 
    conname as constraint_name,
    pg_get_constraintdef(oid) as constraint_definition
FROM pg_constraint
WHERE conrelid = 'sys_virtual_groups'::regclass
AND contype = 'c'
AND conname LIKE '%type%';
