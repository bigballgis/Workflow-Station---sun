-- =====================================================
-- 清理流程历史数据脚本
-- 用于测试前清理所有流程相关数据
-- =====================================================

-- 清理 user_portal 相关表
TRUNCATE TABLE up_process_history CASCADE;
TRUNCATE TABLE up_process_instance CASCADE;
TRUNCATE TABLE up_process_draft CASCADE;

-- 清理 Flowable 引擎表（按依赖顺序）
TRUNCATE TABLE act_hi_varinst CASCADE;
TRUNCATE TABLE act_hi_detail CASCADE;
TRUNCATE TABLE act_hi_comment CASCADE;
TRUNCATE TABLE act_hi_attachment CASCADE;
TRUNCATE TABLE act_hi_identitylink CASCADE;
TRUNCATE TABLE act_hi_taskinst CASCADE;
TRUNCATE TABLE act_hi_actinst CASCADE;
TRUNCATE TABLE act_hi_procinst CASCADE;

TRUNCATE TABLE act_ru_variable CASCADE;
TRUNCATE TABLE act_ru_identitylink CASCADE;
TRUNCATE TABLE act_ru_task CASCADE;
TRUNCATE TABLE act_ru_execution CASCADE;

-- 显示清理结果
SELECT 'up_process_history' as table_name, COUNT(*) as remaining_records FROM up_process_history
UNION ALL
SELECT 'up_process_instance', COUNT(*) FROM up_process_instance
UNION ALL
SELECT 'up_process_draft', COUNT(*) FROM up_process_draft
UNION ALL
SELECT 'act_ru_task', COUNT(*) FROM act_ru_task
UNION ALL
SELECT 'act_ru_execution', COUNT(*) FROM act_ru_execution
UNION ALL
SELECT 'act_hi_procinst', COUNT(*) FROM act_hi_procinst
UNION ALL
SELECT 'act_hi_taskinst', COUNT(*) FROM act_hi_taskinst;
