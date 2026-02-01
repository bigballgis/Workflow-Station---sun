-- =====================================================
-- 修复角色 type 字段：将 BUSINESS 改为 BU_UNBOUNDED
-- =====================================================
-- 生成时间: 2026-01-18
-- 原因: 
--   1. 数据库中存在 BUSINESS 类型的角色
--   2. 但枚举 RoleType 中没有 BUSINESS，只有 BU_BOUNDED、BU_UNBOUNDED、ADMIN、DEVELOPER
--   3. Flyway 脚本的 CHECK 约束只允许：'ADMIN', 'DEVELOPER', 'BU_BOUNDED', 'BU_UNBOUNDED'
--   4. BUSINESS 类型的角色应该映射为 BU_UNBOUNDED（业务单元无关型）
-- =====================================================

-- 检查当前的 BUSINESS 类型角色
SELECT id, name, code, type, description 
FROM sys_roles 
WHERE type = 'BUSINESS';

-- 先删除旧的约束（因为约束只允许 BUSINESS，不允许 BU_UNBOUNDED）
ALTER TABLE sys_roles DROP CONSTRAINT IF EXISTS chk_role_type;

-- 将 BUSINESS 类型更新为 BU_UNBOUNDED（业务单元无关型业务角色）
-- BUSINESS 类型的角色通常是通用的业务角色，不需要绑定特定业务单元
UPDATE sys_roles 
SET type = 'BU_UNBOUNDED' 
WHERE type = 'BUSINESS';

-- 重新添加正确的约束（与 Flyway 脚本和枚举一致）
ALTER TABLE sys_roles 
ADD CONSTRAINT chk_role_type 
CHECK (type IN ('ADMIN', 'DEVELOPER', 'BU_BOUNDED', 'BU_UNBOUNDED'));

-- 验证修改结果
SELECT 
    type,
    COUNT(*) as count,
    string_agg(name, ', ') as role_names
FROM sys_roles
GROUP BY type
ORDER BY type;

-- 检查表约束（应该只允许 ADMIN, DEVELOPER, BU_BOUNDED, BU_UNBOUNDED）
SELECT 
    conname as constraint_name,
    pg_get_constraintdef(oid) as constraint_definition
FROM pg_constraint
WHERE conrelid = 'sys_roles'::regclass
AND contype = 'c'
AND conname LIKE '%type%';
