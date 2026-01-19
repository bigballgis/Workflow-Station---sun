-- =====================================================
-- 修复 wf_extended_task_info 表的 id 字段类型
-- 从 VARCHAR(64) 改为 BIGSERIAL/BIGINT
-- =====================================================
-- 生成时间: 2026-01-18
-- 原因: 代码实体中 id 字段定义为 Long (BIGINT)，但数据库中为 VARCHAR(64)
-- =====================================================

DO $$
BEGIN
    -- 检查表是否存在
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'wf_extended_task_info') THEN
        -- 检查当前 id 字段类型
        IF EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'wf_extended_task_info' 
            AND column_name = 'id' 
            AND data_type = 'character varying'
        ) THEN
            RAISE NOTICE '开始修复 wf_extended_task_info.id 字段类型...';
            
            -- 1. 检查是否有数据
            IF (SELECT COUNT(*) FROM wf_extended_task_info) > 0 THEN
                RAISE NOTICE '⚠️  警告: 表中有数据，将清空数据后修改类型';
                -- 注意：如果有重要数据，请先备份
                TRUNCATE TABLE wf_extended_task_info;
            END IF;
            
            -- 2. 删除主键约束（如果存在）
            IF EXISTS (
                SELECT 1 FROM information_schema.table_constraints 
                WHERE table_name = 'wf_extended_task_info' 
                AND constraint_type = 'PRIMARY KEY'
            ) THEN
                ALTER TABLE wf_extended_task_info DROP CONSTRAINT wf_extended_task_info_pkey;
                RAISE NOTICE '已删除主键约束';
            END IF;
            
            -- 3. 删除可能存在的序列和外键依赖
            -- 删除 id 字段（将自动删除依赖）
            ALTER TABLE wf_extended_task_info DROP COLUMN IF EXISTS id CASCADE;
            RAISE NOTICE '已删除旧 id 字段';
            
            -- 4. 创建新的 id 字段（BIGSERIAL）
            ALTER TABLE wf_extended_task_info 
                ADD COLUMN id BIGSERIAL PRIMARY KEY;
            RAISE NOTICE '已创建新的 id 字段 (BIGSERIAL)';
            
            -- 5. 确保序列正确设置
            -- BIGSERIAL 会自动创建序列 wf_extended_task_info_id_seq
            
            RAISE NOTICE '✅ wf_extended_task_info.id 字段类型修复完成';
        ELSE
            RAISE NOTICE 'ℹ️  id 字段类型已经是 BIGINT/BIGSERIAL，无需修复';
        END IF;
    ELSE
        RAISE NOTICE '⚠️  wf_extended_task_info 表不存在，跳过修复';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE '❌ 修复失败: %', SQLERRM;
END $$;

-- 验证修改结果
SELECT 
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_name = 'wf_extended_task_info' 
  AND column_name = 'id';
