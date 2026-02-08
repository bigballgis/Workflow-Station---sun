-- 添加唯一约束：确保每个功能单元代码只有一个启用版本
-- 日期: 2026-02-06
-- 目的: 防止同一功能单元的多个版本同时启用

-- 创建唯一部分索引
-- 只有当 enabled = true 时才应用唯一约束
CREATE UNIQUE INDEX IF NOT EXISTS idx_function_unit_code_enabled 
ON sys_function_units (code) 
WHERE enabled = true;

-- 验证约束
-- 查询每个代码的启用版本数量，应该都是 0 或 1
SELECT code, COUNT(*) as enabled_count
FROM sys_function_units
WHERE enabled = true
GROUP BY code
HAVING COUNT(*) > 1;

-- 如果上面的查询返回任何结果，说明存在违反约束的数据
-- 需要先修复数据，然后再创建约束

COMMENT ON INDEX idx_function_unit_code_enabled IS '确保每个功能单元代码只有一个启用版本';
