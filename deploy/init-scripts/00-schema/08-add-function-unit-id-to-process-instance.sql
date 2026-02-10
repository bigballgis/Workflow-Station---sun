-- 为 up_process_instance 表添加 function_unit_id 列
-- 用于存储流程实例关联的功能单元ID，解决任务详情页面加载流程图时找不到功能单元的问题

-- 添加 function_unit_id 列
ALTER TABLE up_process_instance 
ADD COLUMN IF NOT EXISTS function_unit_id VARCHAR(64);

-- 添加索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_up_process_instance_function_unit_id 
ON up_process_instance(function_unit_id);

-- 添加注释
COMMENT ON COLUMN up_process_instance.function_unit_id IS '功能单元ID';
