-- ============================================
-- 清理 Admin Center 功能单元数据脚本
-- ============================================
-- 功能：清空 Start Process 页面显示的功能单元数据
-- 
-- 使用方法：
-- 在数据库工具中执行，或使用命令行：
-- psql -h localhost -U platform -d workflow_platform -f clear-admin-center-data.sql
-- 
-- 注意：此操作不可逆，请在执行前备份数据库！
-- ============================================

-- 清理功能单元相关表
TRUNCATE TABLE 
    sys_function_unit_deployments,   -- 功能单元部署记录
    sys_function_unit_contents,      -- 功能单元内容（BPMN、表单等）
    sys_function_unit_dependencies,  -- 功能单元依赖关系
    sys_function_unit_access,        -- 功能单元访问权限
    sys_function_unit_approvals,     -- 功能单元审批记录
    sys_function_units               -- 功能单元主表
CASCADE;
