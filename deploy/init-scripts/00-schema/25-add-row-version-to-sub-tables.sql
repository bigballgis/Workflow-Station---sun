-- =====================================================
-- Add row_version Column to Existing Sub-Tables
-- Migration script for multi-instance task dispatch feature
-- Adds optimistic locking support to all SUB type tables
-- =====================================================

-- =====================================================
-- 说明：
-- 本脚本为已存在的子表（table_type = 'SUB'）添加 row_version 列
-- row_version 用于实现乐观锁，防止多实例子任务并发编辑冲突
-- 
-- 执行逻辑：
-- 1. 查询 dw_table_definitions 表，找出所有 table_type = 'SUB' 的表
-- 2. 对每个子表执行 ALTER TABLE ADD COLUMN IF NOT EXISTS
-- 3. 使用 IF NOT EXISTS 确保脚本可重复执行
-- =====================================================

DO $$
DECLARE
    sub_table_record RECORD;
    alter_sql TEXT;
BEGIN
    -- 遍历所有 SUB 类型的表定义
    FOR sub_table_record IN 
        SELECT table_name 
        FROM dw_table_definitions 
        WHERE table_type = 'SUB'
    LOOP
        -- 构建 ALTER TABLE 语句
        alter_sql := format(
            'ALTER TABLE %I ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 1',
            sub_table_record.table_name
        );
        
        -- 执行 ALTER TABLE
        BEGIN
            EXECUTE alter_sql;
            RAISE NOTICE 'Added row_version column to table: %', sub_table_record.table_name;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE WARNING 'Failed to add row_version to table %: %', 
                    sub_table_record.table_name, SQLERRM;
        END;
    END LOOP;
    
    RAISE NOTICE 'Migration completed: row_version column added to all SUB tables';
END $$;

-- =====================================================
-- 验证脚本（可选）
-- 查询所有子表的 row_version 列是否存在
-- =====================================================
-- SELECT 
--     td.table_name,
--     td.table_type,
--     CASE 
--         WHEN EXISTS (
--             SELECT 1 
--             FROM information_schema.columns 
--             WHERE table_name = td.table_name 
--             AND column_name = 'row_version'
--         ) THEN 'EXISTS'
--         ELSE 'MISSING'
--     END AS row_version_status
-- FROM dw_table_definitions td
-- WHERE td.table_type = 'SUB'
-- ORDER BY td.table_name;
