-- ============================================
-- 清理所有 User Portal 相关数据（完整版）
-- ============================================
-- 功能：清空所有 User Portal 相关数据，包括：
--   - 待办任务 (Tasks)
--   - 已处理任务 (Completed)
--   - 我的申请 (My Applications)
--   - 发起流程 (Start Process)
--   - 流程草稿、收藏等
-- 
-- 使用方法：
-- psql -h localhost -U platform -d workflow_platform -f clear-all-user-portal-data.sql
-- 
-- 注意：此操作不可逆，请在执行前备份数据库！
-- ============================================

-- 1. 清理 User Portal 表
TRUNCATE TABLE 
    up_process_instance,        -- 流程实例（我的申请）
    up_process_history,          -- 流程历史记录
    up_process_draft,            -- 流程草稿
    up_favorite_process          -- 收藏的流程
CASCADE;

-- 2. 清理 Flowable 任务表
TRUNCATE TABLE 
    act_ru_task,                 -- 运行时任务（待办任务）
    act_hi_taskinst,             -- 历史任务（已处理任务）
    act_hi_actinst,              -- 历史活动实例
    act_hi_procinst,             -- 历史流程实例
    act_ru_execution,            -- 运行时执行
    act_ru_variable,             -- 运行时变量
    act_hi_varinst,              -- 历史变量
    act_ru_identitylink,         -- 运行时身份链接
    act_hi_identitylink          -- 历史身份链接
CASCADE;

-- 3. 清理 Flowable 流程定义部署表
TRUNCATE TABLE 
    act_re_deployment,           -- 流程部署
    act_re_procdef,              -- 流程定义
    act_ge_bytearray             -- 资源文件（BPMN XML等）
CASCADE;

-- 4. 清理 Admin Center 功能单元表（Start Process 数据源）
TRUNCATE TABLE 
    sys_function_unit_deployments,   -- 功能单元部署记录
    sys_function_unit_contents,      -- 功能单元内容（BPMN、表单等）
    sys_function_unit_dependencies,  -- 功能单元依赖关系
    sys_function_unit_access,        -- 功能单元访问权限
    sys_function_unit_approvals,     -- 功能单元审批记录
    sys_function_units               -- 功能单元主表
CASCADE;

SELECT '所有 User Portal 数据清理完成！' AS status;
