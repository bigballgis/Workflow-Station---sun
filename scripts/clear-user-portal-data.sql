-- ============================================
-- 清理 User Portal 数据脚本
-- ============================================
-- 功能：清空待办任务、已处理任务、我的申请、发起流程相关数据
-- 
-- 使用方法：
-- 在数据库工具中，请逐条执行以下语句，或者使用命令行：
-- psql -h localhost -U platform -d workflow_platform -f clear-user-portal-data.sql
-- 
-- 注意：此操作不可逆，请在执行前备份数据库！
-- ============================================

-- 清理所有相关表（一次性执行）
TRUNCATE TABLE 
    -- User Portal 表
    up_process_instance,        -- 流程实例（我的申请）
    up_process_history,          -- 流程历史记录
    up_process_draft,            -- 流程草稿
    up_favorite_process,         -- 收藏的流程
    -- Flowable 任务表
    act_ru_task,                 -- 运行时任务（待办任务）
    act_hi_taskinst,             -- 历史任务（已处理任务）
    act_hi_actinst,              -- 历史活动实例
    act_hi_procinst,             -- 历史流程实例
    act_ru_execution,            -- 运行时执行
    act_ru_variable,             -- 运行时变量
    act_hi_varinst,              -- 历史变量
    act_ru_identitylink,         -- 运行时身份链接
    act_hi_identitylink,         -- 历史身份链接
    -- Flowable 流程定义部署表（可选，如果要清理已部署的流程定义）
    act_re_deployment,           -- 流程部署
    act_re_procdef,              -- 流程定义
    act_ge_bytearray             -- 资源文件（BPMN XML等）
CASCADE;
