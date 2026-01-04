-- 异常记录表
-- 用于记录流程执行过程中的异常信息
-- 需求: 9.1, 9.2, 9.3, 9.4

CREATE TABLE IF NOT EXISTS wf_exception_records (
    id VARCHAR(64) PRIMARY KEY,
    process_instance_id VARCHAR(64),
    process_definition_id VARCHAR(64),
    process_definition_key VARCHAR(255),
    task_id VARCHAR(64),
    task_name VARCHAR(255),
    activity_id VARCHAR(255),
    activity_name VARCHAR(255),
    exception_type VARCHAR(100) NOT NULL,
    exception_class VARCHAR(500),
    exception_message TEXT,
    stack_trace TEXT,
    root_cause TEXT,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    context_data TEXT,
    variables_snapshot TEXT,
    occurred_time TIMESTAMP NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retry_count INTEGER NOT NULL DEFAULT 3,
    next_retry_time TIMESTAMP,
    last_retry_time TIMESTAMP,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_time TIMESTAMP,
    resolved_by VARCHAR(64),
    resolution_method VARCHAR(50),
    resolution_note TEXT,
    alert_sent BOOLEAN NOT NULL DEFAULT FALSE,
    alert_sent_time TIMESTAMP,
    parent_exception_id VARCHAR(64),
    tenant_id VARCHAR(64),
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_exception_process_instance ON wf_exception_records(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_exception_task_id ON wf_exception_records(task_id);
CREATE INDEX IF NOT EXISTS idx_exception_type ON wf_exception_records(exception_type);
CREATE INDEX IF NOT EXISTS idx_exception_severity ON wf_exception_records(severity);
CREATE INDEX IF NOT EXISTS idx_exception_status ON wf_exception_records(status);
CREATE INDEX IF NOT EXISTS idx_exception_occurred_time ON wf_exception_records(occurred_time);
CREATE INDEX IF NOT EXISTS idx_exception_resolved ON wf_exception_records(resolved);
CREATE INDEX IF NOT EXISTS idx_exception_tenant ON wf_exception_records(tenant_id);
CREATE INDEX IF NOT EXISTS idx_exception_process_def_key ON wf_exception_records(process_definition_key);

-- 复合索引：用于查询待重试的异常
CREATE INDEX IF NOT EXISTS idx_exception_pending_retry 
    ON wf_exception_records(status, retry_count, next_retry_time) 
    WHERE status = 'PENDING';

-- 复合索引：用于查询需要告警的异常
CREATE INDEX IF NOT EXISTS idx_exception_need_alert 
    ON wf_exception_records(alert_sent, severity, resolved) 
    WHERE alert_sent = FALSE AND resolved = FALSE;

-- 添加注释
COMMENT ON TABLE wf_exception_records IS '异常记录表';
COMMENT ON COLUMN wf_exception_records.id IS '异常记录ID';
COMMENT ON COLUMN wf_exception_records.process_instance_id IS '流程实例ID';
COMMENT ON COLUMN wf_exception_records.exception_type IS '异常类型';
COMMENT ON COLUMN wf_exception_records.severity IS '严重级别: CRITICAL, HIGH, MEDIUM, LOW';
COMMENT ON COLUMN wf_exception_records.status IS '状态: PENDING, PROCESSING, RESOLVED, IGNORED';
COMMENT ON COLUMN wf_exception_records.resolution_method IS '解决方式: AUTO_RETRY, MANUAL_FIX, IGNORED, COMPENSATED';
