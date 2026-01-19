-- =====================================================
-- 添加虚拟组 type 字段的 CHECK 约束
-- =====================================================
-- 生成时间: 2026-01-18
-- 原因: 
--   1. Flyway 脚本 V1__init_schema.sql 中定义了 CHECK 约束
--   2. 但数据库中实际没有这个约束
--   3. 需要确保 type 字段只能为 'SYSTEM' 或 'CUSTOM'
-- =====================================================

-- 检查是否已存在约束
SELECT 
    conname as constraint_name,
    pg_get_constraintdef(oid) as constraint_definition
FROM pg_constraint
WHERE conrelid = 'sys_virtual_groups'::regclass
AND contype = 'c'
AND conname LIKE '%type%';

-- 如果约束不存在，则添加约束
DO $$
BEGIN
    -- 检查约束是否已存在
    IF NOT EXISTS (
        SELECT 1 
        FROM pg_constraint 
        WHERE conrelid = 'sys_virtual_groups'::regclass 
        AND contype = 'c' 
        AND conname = 'chk_virtual_group_type'
    ) THEN
        -- 添加 CHECK 约束
        ALTER TABLE sys_virtual_groups 
        ADD CONSTRAINT chk_virtual_group_type 
        CHECK (type IN ('SYSTEM', 'CUSTOM'));
        
        RAISE NOTICE '✅ 已添加 CHECK 约束: chk_virtual_group_type';
    ELSE
        RAISE NOTICE '⚠️  约束 chk_virtual_group_type 已存在，跳过';
    END IF;
END $$;

-- 验证约束已添加
SELECT 
    conname as constraint_name,
    pg_get_constraintdef(oid) as constraint_definition
FROM pg_constraint
WHERE conrelid = 'sys_virtual_groups'::regclass
AND contype = 'c'
AND conname LIKE '%type%';

-- 测试约束是否生效（应该失败）
-- 注意：这个测试会失败，因为 'INVALID' 不在允许的值列表中
-- 如果需要测试，可以取消下面的注释
/*
DO $$
BEGIN
    BEGIN
        INSERT INTO sys_virtual_groups (id, name, code, type) 
        VALUES ('test-invalid', 'Test', 'TEST_INVALID', 'INVALID');
        RAISE EXCEPTION '约束未生效：允许了无效的 type 值';
    EXCEPTION
        WHEN check_violation THEN
            RAISE NOTICE '✅ 约束生效：成功阻止了无效的 type 值';
    END;
END $$;
*/
