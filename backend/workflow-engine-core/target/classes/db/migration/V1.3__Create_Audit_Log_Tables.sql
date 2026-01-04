-- 创建审计日志表
-- 用于记录所有流程操作的审计轨迹，支持合规检查和业务分析

CREATE TABLE wf_audit_logs (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    resource_name VARCHAR(255),
    operation_description TEXT,
    before_data JSONB,
    after_data JSONB,
    operation_result VARCHAR(20) NOT NULL,
    error_message TEXT,
    timestamp TIMESTAMP NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    session_id VARCHAR(128),
    request_id VARCHAR(64),
    duration_ms BIGINT,
    tenant_id VARCHAR(64),
    context_data JSONB,
    risk_level VARCHAR(20),
    is_sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- 创建时间和更新时间（用于数据管理）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引以优化查询性能
CREATE INDEX idx_audit_user_id ON wf_audit_logs(user_id);
CREATE INDEX idx_audit_operation_type ON wf_audit_logs(operation_type);
CREATE INDEX idx_audit_resource_type ON wf_audit_logs(resource_type);
CREATE INDEX idx_audit_resource_id ON wf_audit_logs(resource_id);
CREATE INDEX idx_audit_timestamp ON wf_audit_logs(timestamp);
CREATE INDEX idx_audit_ip_address ON wf_audit_logs(ip_address);
CREATE INDEX idx_audit_session_id ON wf_audit_logs(session_id);
CREATE INDEX idx_audit_request_id ON wf_audit_logs(request_id);
CREATE INDEX idx_audit_tenant_id ON wf_audit_logs(tenant_id);
CREATE INDEX idx_audit_risk_level ON wf_audit_logs(risk_level);
CREATE INDEX idx_audit_is_sensitive ON wf_audit_logs(is_sensitive);
CREATE INDEX idx_audit_operation_result ON wf_audit_logs(operation_result);

-- 复合索引以优化常用查询
CREATE INDEX idx_audit_composite_user_op_time ON wf_audit_logs(user_id, operation_type, timestamp);
CREATE INDEX idx_audit_composite_resource_time ON wf_audit_logs(resource_type, resource_id, timestamp);
CREATE INDEX idx_audit_composite_tenant_time ON wf_audit_logs(tenant_id, timestamp);
CREATE INDEX idx_audit_composite_risk_time ON wf_audit_logs(risk_level, timestamp);

-- 创建分区表（按月分区，提高大数据量查询性能）
-- 注意：PostgreSQL 10+ 支持声明式分区
CREATE TABLE wf_audit_logs_y2026m01 PARTITION OF wf_audit_logs
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');

CREATE TABLE wf_audit_logs_y2026m02 PARTITION OF wf_audit_logs
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');

CREATE TABLE wf_audit_logs_y2026m03 PARTITION OF wf_audit_logs
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');

-- 创建触发器自动更新updated_at字段
CREATE OR REPLACE FUNCTION update_audit_log_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_audit_log_updated_at
    BEFORE UPDATE ON wf_audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION update_audit_log_updated_at();

-- 创建审计日志清理函数（用于定期清理过期数据）
CREATE OR REPLACE FUNCTION cleanup_expired_audit_logs(retention_days INTEGER DEFAULT 365)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
    cutoff_date TIMESTAMP;
BEGIN
    cutoff_date := CURRENT_TIMESTAMP - INTERVAL '1 day' * retention_days;
    
    DELETE FROM wf_audit_logs 
    WHERE timestamp < cutoff_date;
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    
    -- 记录清理操作
    INSERT INTO wf_audit_logs (
        id, user_id, operation_type, resource_type, resource_id,
        operation_description, operation_result, timestamp, risk_level, is_sensitive
    ) VALUES (
        gen_random_uuid()::text,
        'SYSTEM',
        'CLEANUP_DATA',
        'AUDIT_LOG',
        'BATCH',
        '清理过期审计日志，删除 ' || deleted_count || ' 条记录',
        'SUCCESS',
        CURRENT_TIMESTAMP,
        'LOW',
        FALSE
    );
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- 创建审计日志统计视图
CREATE VIEW v_audit_log_statistics AS
SELECT 
    DATE_TRUNC('day', timestamp) as log_date,
    operation_type,
    resource_type,
    risk_level,
    operation_result,
    COUNT(*) as operation_count,
    COUNT(DISTINCT user_id) as unique_users,
    COUNT(DISTINCT ip_address) as unique_ips,
    AVG(duration_ms) as avg_duration_ms,
    MAX(duration_ms) as max_duration_ms,
    SUM(CASE WHEN is_sensitive THEN 1 ELSE 0 END) as sensitive_operations
FROM wf_audit_logs
WHERE timestamp >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY 
    DATE_TRUNC('day', timestamp),
    operation_type,
    resource_type,
    risk_level,
    operation_result
ORDER BY log_date DESC, operation_count DESC;

-- 创建用户活动统计视图
CREATE VIEW v_user_activity_statistics AS
SELECT 
    user_id,
    DATE_TRUNC('day', timestamp) as activity_date,
    COUNT(*) as total_operations,
    COUNT(DISTINCT operation_type) as operation_types,
    COUNT(DISTINCT resource_type) as resource_types,
    SUM(CASE WHEN operation_result = 'SUCCESS' THEN 1 ELSE 0 END) as successful_operations,
    SUM(CASE WHEN operation_result = 'FAILURE' THEN 1 ELSE 0 END) as failed_operations,
    SUM(CASE WHEN is_sensitive THEN 1 ELSE 0 END) as sensitive_operations,
    MIN(timestamp) as first_activity,
    MAX(timestamp) as last_activity,
    COUNT(DISTINCT session_id) as unique_sessions,
    COUNT(DISTINCT ip_address) as unique_ips
FROM wf_audit_logs
WHERE timestamp >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY user_id, DATE_TRUNC('day', timestamp)
ORDER BY activity_date DESC, total_operations DESC;

-- 创建风险监控视图
CREATE VIEW v_risk_monitoring AS
SELECT 
    ip_address,
    user_id,
    DATE_TRUNC('hour', timestamp) as time_window,
    COUNT(*) as operation_count,
    COUNT(DISTINCT operation_type) as operation_types,
    SUM(CASE WHEN operation_result = 'FAILURE' THEN 1 ELSE 0 END) as failure_count,
    SUM(CASE WHEN risk_level = 'HIGH' OR risk_level = 'CRITICAL' THEN 1 ELSE 0 END) as high_risk_operations,
    SUM(CASE WHEN is_sensitive THEN 1 ELSE 0 END) as sensitive_operations,
    CASE 
        WHEN COUNT(*) > 1000 THEN 'CRITICAL'
        WHEN COUNT(*) > 500 THEN 'HIGH'
        WHEN COUNT(*) > 100 THEN 'MEDIUM'
        ELSE 'LOW'
    END as risk_assessment
FROM wf_audit_logs
WHERE timestamp >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
GROUP BY ip_address, user_id, DATE_TRUNC('hour', timestamp)
HAVING COUNT(*) > 10  -- 只显示活跃的IP/用户
ORDER BY operation_count DESC, time_window DESC;

-- 添加表注释
COMMENT ON TABLE wf_audit_logs IS '审计日志表，记录所有流程操作的审计轨迹';
COMMENT ON COLUMN wf_audit_logs.id IS '审计日志唯一标识';
COMMENT ON COLUMN wf_audit_logs.user_id IS '操作用户ID';
COMMENT ON COLUMN wf_audit_logs.operation_type IS '操作类型：CREATE, UPDATE, DELETE, EXECUTE等';
COMMENT ON COLUMN wf_audit_logs.resource_type IS '资源类型：PROCESS_DEFINITION, PROCESS_INSTANCE, TASK等';
COMMENT ON COLUMN wf_audit_logs.resource_id IS '资源ID';
COMMENT ON COLUMN wf_audit_logs.before_data IS '操作前数据（JSON格式，敏感数据已脱敏）';
COMMENT ON COLUMN wf_audit_logs.after_data IS '操作后数据（JSON格式，敏感数据已脱敏）';
COMMENT ON COLUMN wf_audit_logs.operation_result IS '操作结果：SUCCESS, FAILURE, PARTIAL';
COMMENT ON COLUMN wf_audit_logs.risk_level IS '风险等级：LOW, MEDIUM, HIGH, CRITICAL';
COMMENT ON COLUMN wf_audit_logs.is_sensitive IS '是否为敏感操作';
COMMENT ON COLUMN wf_audit_logs.context_data IS '额外的上下文信息（JSON格式）';

-- 创建定期清理任务的示例（需要配合定时任务系统）
-- 这里只是创建函数，实际调度需要在应用层或使用pg_cron扩展
CREATE OR REPLACE FUNCTION schedule_audit_log_cleanup()
RETURNS void AS $$
BEGIN
    -- 每月清理超过1年的审计日志
    PERFORM cleanup_expired_audit_logs(365);
    
    -- 记录清理任务执行
    INSERT INTO wf_audit_logs (
        id, user_id, operation_type, resource_type, resource_id,
        operation_description, operation_result, timestamp, risk_level, is_sensitive
    ) VALUES (
        gen_random_uuid()::text,
        'SYSTEM',
        'SCHEDULE_CLEANUP',
        'AUDIT_LOG',
        'SCHEDULER',
        '定期清理任务执行完成',
        'SUCCESS',
        CURRENT_TIMESTAMP,
        'LOW',
        FALSE
    );
END;
$$ LANGUAGE plpgsql;