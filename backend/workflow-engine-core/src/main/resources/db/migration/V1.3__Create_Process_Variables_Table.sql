-- 创建流程变量表
-- 支持多种数据类型和PostgreSQL JSONB存储
-- 版本: V1.3
-- 作者: Workflow Engine Team

-- 创建流程变量表
CREATE TABLE IF NOT EXISTS wf_process_variables (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    
    -- 关联ID字段
    process_instance_id VARCHAR(64),
    execution_id VARCHAR(64),
    task_id VARCHAR(64),
    case_instance_id VARCHAR(64),
    case_execution_id VARCHAR(64),
    activity_instance_id VARCHAR(64),
    
    -- 多租户支持
    tenant_id VARCHAR(255),
    
    -- 版本控制
    sequence_counter BIGINT DEFAULT 1,
    is_concurrent_local BOOLEAN DEFAULT FALSE,
    
    -- 不同类型的值字段
    text_value TEXT,
    text_value2 TEXT,
    double_value DOUBLE PRECISION,
    long_value BIGINT,
    date_value TIMESTAMP,
    
    -- JSON类型变量 (PostgreSQL JSONB支持)
    json_value JSONB,
    
    -- 二进制数据
    binary_value BYTEA,
    
    -- 审计字段
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    change_reason VARCHAR(500),
    operation_type VARCHAR(20) DEFAULT 'CREATE'
);

-- 创建索引以优化查询性能
CREATE INDEX IF NOT EXISTS idx_variable_name ON wf_process_variables(name);
CREATE INDEX IF NOT EXISTS idx_variable_proc_inst ON wf_process_variables(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_variable_task ON wf_process_variables(task_id);
CREATE INDEX IF NOT EXISTS idx_variable_execution ON wf_process_variables(execution_id);
CREATE INDEX IF NOT EXISTS idx_variable_created_time ON wf_process_variables(created_time);
CREATE INDEX IF NOT EXISTS idx_variable_type ON wf_process_variables(type);
CREATE INDEX IF NOT EXISTS idx_variable_tenant ON wf_process_variables(tenant_id);

-- 复合索引优化常用查询
CREATE INDEX IF NOT EXISTS idx_variable_proc_inst_name ON wf_process_variables(process_instance_id, name);
CREATE INDEX IF NOT EXISTS idx_variable_proc_inst_type ON wf_process_variables(process_instance_id, type);
CREATE INDEX IF NOT EXISTS idx_variable_name_created_time ON wf_process_variables(name, created_time DESC);

-- JSON字段的GIN索引（PostgreSQL特性）
CREATE INDEX IF NOT EXISTS idx_variable_json_gin ON wf_process_variables USING GIN(json_value);

-- 全文搜索索引
CREATE INDEX IF NOT EXISTS idx_variable_text_search ON wf_process_variables USING GIN(
    to_tsvector('english', COALESCE(name, '') || ' ' || COALESCE(text_value, '') || ' ' || COALESCE(change_reason, ''))
);

-- 添加约束
ALTER TABLE wf_process_variables 
ADD CONSTRAINT chk_variable_type 
CHECK (type IN ('STRING', 'INTEGER', 'LONG', 'DOUBLE', 'BOOLEAN', 'DATE', 'JSON', 'FILE', 'BINARY', 'DELETED'));

ALTER TABLE wf_process_variables 
ADD CONSTRAINT chk_operation_type 
CHECK (operation_type IN ('CREATE', 'UPDATE', 'DELETE'));

-- 添加部分唯一约束（同一流程实例中的变量名在同一时间点应该唯一）
-- 注意：这里不添加严格的唯一约束，因为我们需要保留历史记录

-- 创建触发器函数来自动更新 updated_time
CREATE OR REPLACE FUNCTION update_variable_updated_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_time = CURRENT_TIMESTAMP;
    IF NEW.sequence_counter IS NOT NULL THEN
        NEW.sequence_counter = OLD.sequence_counter + 1;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 创建触发器
DROP TRIGGER IF EXISTS trigger_update_variable_updated_time ON wf_process_variables;
CREATE TRIGGER trigger_update_variable_updated_time
    BEFORE UPDATE ON wf_process_variables
    FOR EACH ROW
    EXECUTE FUNCTION update_variable_updated_time();

-- 创建视图：当前变量值（每个变量的最新值）
CREATE OR REPLACE VIEW v_current_process_variables AS
SELECT DISTINCT ON (process_instance_id, name) 
    id,
    name,
    type,
    process_instance_id,
    execution_id,
    task_id,
    text_value,
    double_value,
    long_value,
    date_value,
    json_value,
    binary_value,
    created_time,
    updated_time,
    created_by,
    updated_by,
    tenant_id,
    sequence_counter
FROM wf_process_variables 
WHERE type != 'DELETED'
ORDER BY process_instance_id, name, created_time DESC;

-- 创建视图：变量变更统计
CREATE OR REPLACE VIEW v_variable_change_statistics AS
SELECT 
    process_instance_id,
    name,
    COUNT(*) as change_count,
    MIN(created_time) as first_created,
    MAX(updated_time) as last_updated,
    COUNT(DISTINCT created_by) as modifier_count
FROM wf_process_variables 
WHERE type != 'DELETED'
GROUP BY process_instance_id, name;

-- 创建函数：获取变量的当前值
CREATE OR REPLACE FUNCTION get_current_variable_value(
    p_process_instance_id VARCHAR(64),
    p_variable_name VARCHAR(255)
) RETURNS TABLE (
    variable_type VARCHAR(20),
    text_val TEXT,
    double_val DOUBLE PRECISION,
    long_val BIGINT,
    date_val TIMESTAMP,
    json_val JSONB,
    binary_val BYTEA
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        v.type,
        v.text_value,
        v.double_value,
        v.long_value,
        v.date_value,
        v.json_value,
        v.binary_value
    FROM wf_process_variables v
    WHERE v.process_instance_id = p_process_instance_id 
      AND v.name = p_variable_name
      AND v.type != 'DELETED'
    ORDER BY v.created_time DESC
    LIMIT 1;
END;
$$ LANGUAGE plpgsql;

-- 创建函数：清理过期的变量历史记录
CREATE OR REPLACE FUNCTION cleanup_variable_history(
    p_retention_days INTEGER DEFAULT 90
) RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM wf_process_variables 
    WHERE created_time < CURRENT_TIMESTAMP - INTERVAL '1 day' * p_retention_days
      AND type = 'DELETED';
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- 添加表注释
COMMENT ON TABLE wf_process_variables IS '流程变量表 - 存储流程执行过程中的变量值和历史记录';
COMMENT ON COLUMN wf_process_variables.id IS '主键ID';
COMMENT ON COLUMN wf_process_variables.name IS '变量名称';
COMMENT ON COLUMN wf_process_variables.type IS '变量类型：STRING, INTEGER, LONG, DOUBLE, BOOLEAN, DATE, JSON, FILE, BINARY, DELETED';
COMMENT ON COLUMN wf_process_variables.process_instance_id IS '流程实例ID';
COMMENT ON COLUMN wf_process_variables.execution_id IS '执行ID';
COMMENT ON COLUMN wf_process_variables.task_id IS '任务ID';
COMMENT ON COLUMN wf_process_variables.json_value IS 'JSON类型变量值，使用PostgreSQL JSONB格式';
COMMENT ON COLUMN wf_process_variables.sequence_counter IS '序列计数器，用于版本控制';
COMMENT ON COLUMN wf_process_variables.is_concurrent_local IS '是否为并发本地变量';
COMMENT ON COLUMN wf_process_variables.change_reason IS '变更原因';
COMMENT ON COLUMN wf_process_variables.operation_type IS '操作类型：CREATE, UPDATE, DELETE';

-- 插入初始化数据（如果需要）
-- INSERT INTO wf_process_variables (id, name, type, text_value, created_time, operation_type)
-- VALUES ('init-001', 'system.version', 'STRING', '1.0.0', CURRENT_TIMESTAMP, 'CREATE');

COMMIT;