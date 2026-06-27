-- 主表 Request ID 配置:有序字段 + 分隔符,拼成一条 request 的人类可读标识(如 HR-2026-001)。
-- 仅 MAIN 表有意义;存量行为 NULL,运行时走缺省(列表显示 '-',表单不出现该字段)。
ALTER TABLE dw_table_definitions
    ADD COLUMN IF NOT EXISTS request_id_config JSONB;
