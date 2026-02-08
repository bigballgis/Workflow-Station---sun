-- 添加 ACTION 内容类型到 sys_function_unit_contents 表的约束中

-- 1. 删除旧的约束
ALTER TABLE sys_function_unit_contents 
DROP CONSTRAINT IF EXISTS chk_content_type;

-- 2. 添加新的约束，包含 ACTION 类型
ALTER TABLE sys_function_unit_contents 
ADD CONSTRAINT chk_content_type 
CHECK (content_type IN ('PROCESS', 'FORM', 'DATA_TABLE', 'SCRIPT', 'ACTION'));

-- 3. 验证约束
SELECT conname, pg_get_constraintdef(oid) 
FROM pg_constraint 
WHERE conname = 'chk_content_type';
