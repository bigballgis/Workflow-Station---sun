-- =====================================================
-- 清理工作流测试数据脚本
-- 用于重新开始测试工作流
-- =====================================================

-- 1. 清理 user-portal 模块的流程相关数据
TRUNCATE TABLE up_process_history CASCADE;
TRUNCATE TABLE up_process_instance CASCADE;
TRUNCATE TABLE up_process_draft CASCADE;

-- 2. 清理 workflow-engine-core 模块的数据
TRUNCATE TABLE wf_extended_task_info CASCADE;
TRUNCATE TABLE wf_process_variables CASCADE;
TRUNCATE TABLE wf_exception_records CASCADE;
TRUNCATE TABLE wf_audit_logs CASCADE;

-- 3. 清理 Flowable 引擎的运行时数据
-- 注意：这些表由 Flowable 自动创建和管理

-- 清理运行时任务相关
DELETE FROM act_ru_identitylink;
DELETE FROM act_ru_variable;
DELETE FROM act_ru_task;
DELETE FROM act_ru_execution;

-- 清理历史数据
DELETE FROM act_hi_identitylink;
DELETE FROM act_hi_detail;
DELETE FROM act_hi_comment;
DELETE FROM act_hi_attachment;
DELETE FROM act_hi_varinst;
DELETE FROM act_hi_taskinst;
DELETE FROM act_hi_actinst;
DELETE FROM act_hi_procinst;

-- 清理作业相关
DELETE FROM act_ru_timer_job;
DELETE FROM act_ru_suspended_job;
DELETE FROM act_ru_deadletter_job;
DELETE FROM act_ru_job;
DELETE FROM act_ru_history_job;
DELETE FROM act_ru_external_job;

-- 清理事件订阅
DELETE FROM act_ru_event_subscr;

-- 4. 可选：清理流程定义（如果需要重新部署）
-- 取消注释以下行来清理流程定义
-- DELETE FROM act_re_model;
-- DELETE FROM act_ge_bytearray;
-- DELETE FROM act_re_procdef;
-- DELETE FROM act_re_deployment;

-- 5. 重置序列（如果需要）
-- ALTER SEQUENCE up_process_instance_id_seq RESTART WITH 1;
-- ALTER SEQUENCE up_process_history_id_seq RESTART WITH 1;

SELECT 'Workflow test data cleanup completed!' as message;
