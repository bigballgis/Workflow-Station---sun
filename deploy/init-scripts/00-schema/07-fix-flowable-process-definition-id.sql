-- 修复 flowable_process_definition_id 字段长度限制
-- 该字段需要存储完整的流程定义ID格式: key:version:uuid
-- 例如: purchase_approval_process:12:0ceb3e23-0597-11f1-bae5-8e1cf03a03ef

-- 扩展字段长度从 64 到 255
ALTER TABLE sys_function_unit_contents 
ALTER COLUMN flowable_process_definition_id TYPE VARCHAR(255);

-- 更新现有数据：将 UUID 格式转换为完整格式
-- 通过查询 Flowable 的流程定义表获取 key 和 version
UPDATE sys_function_unit_contents c
SET flowable_process_definition_id = p.KEY_ || ':' || p.VERSION_ || ':' || c.flowable_process_definition_id
FROM ACT_RE_PROCDEF p
WHERE c.content_type = 'PROCESS'
  AND c.flowable_process_definition_id = p.ID_
  AND c.flowable_process_definition_id NOT LIKE '%:%';  -- 只更新 UUID 格式的记录

-- 验证更新
-- SELECT id, flowable_process_definition_id FROM sys_function_unit_contents WHERE content_type = 'PROCESS';
