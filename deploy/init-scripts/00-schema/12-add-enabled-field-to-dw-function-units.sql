-- 添加 enabled 字段到 dw_function_units 表
-- 日期: 2026-02-07
-- 目的: 实现版本管理功能，允许启用/禁用特定版本

-- 添加 enabled 字段，默认值为 true
ALTER TABLE dw_function_units 
ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT true;

-- 创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_dw_function_units_enabled 
ON dw_function_units(enabled);

-- 创建唯一部分索引：确保每个功能单元代码只有一个启用版本
CREATE UNIQUE INDEX IF NOT EXISTS idx_dw_function_unit_code_enabled 
ON dw_function_units (code) 
WHERE enabled = true;

-- 验证约束
-- 查询每个代码的启用版本数量，应该都是 0 或 1
SELECT code, COUNT(*) as enabled_count
FROM dw_function_units
WHERE enabled = true
GROUP BY code
HAVING COUNT(*) > 1;

-- 添加注释
COMMENT ON COLUMN dw_function_units.enabled IS '版本是否启用（只有启用的版本对用户可见）';
COMMENT ON INDEX idx_dw_function_unit_code_enabled IS '确保每个功能单元代码只有一个启用版本';
