-- 清理 User Portal 数据（简化版）
-- 执行前请确保已备份数据库！

BEGIN;

-- User Portal 表
TRUNCATE TABLE up_process_instance CASCADE;
TRUNCATE TABLE up_process_history CASCADE;
TRUNCATE TABLE up_process_draft CASCADE;
TRUNCATE TABLE up_favorite_process CASCADE;

-- Flowable 任务表
TRUNCATE TABLE act_ru_task CASCADE;
TRUNCATE TABLE act_hi_taskinst CASCADE;
TRUNCATE TABLE act_hi_actinst CASCADE;
TRUNCATE TABLE act_hi_procinst CASCADE;
TRUNCATE TABLE act_ru_execution CASCADE;
TRUNCATE TABLE act_ru_variable CASCADE;
TRUNCATE TABLE act_hi_varinst CASCADE;
TRUNCATE TABLE act_ru_identitylink CASCADE;
TRUNCATE TABLE act_hi_identitylink CASCADE;

-- Flowable 流程定义部署表（清理已部署的流程定义）
TRUNCATE TABLE act_re_deployment CASCADE;
TRUNCATE TABLE act_re_procdef CASCADE;
TRUNCATE TABLE act_ge_bytearray CASCADE;

COMMIT;

SELECT '数据清理完成！' AS status;
