-- =====================================================
-- 修复 sys_users 表约束以与代码保持一致
-- 移除 email 和 full_name 的 NOT NULL 约束
-- =====================================================
-- 生成时间: 2026-01-14
-- 原因: 代码实体中所有 User 类的 email 和 full_name 字段都是 nullable
-- =====================================================

-- 检查表是否存在
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'sys_users') THEN
        -- 移除 email 的 NOT NULL 约束
        ALTER TABLE sys_users ALTER COLUMN email DROP NOT NULL;
        RAISE NOTICE '已移除 sys_users.email 的 NOT NULL 约束';
        
        -- 移除 full_name 的 NOT NULL 约束
        ALTER TABLE sys_users ALTER COLUMN full_name DROP NOT NULL;
        RAISE NOTICE '已移除 sys_users.full_name 的 NOT NULL 约束';
        
        RAISE NOTICE '✅ sys_users 表约束修复完成';
    ELSE
        RAISE NOTICE '⚠️  sys_users 表不存在，跳过修复';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE '❌ 修复失败: %', SQLERRM;
END $$;

-- 验证修改结果
SELECT 
    column_name,
    is_nullable,
    data_type,
    character_maximum_length
FROM information_schema.columns
WHERE table_name = 'sys_users' 
  AND column_name IN ('email', 'full_name', 'username')
ORDER BY column_name;
