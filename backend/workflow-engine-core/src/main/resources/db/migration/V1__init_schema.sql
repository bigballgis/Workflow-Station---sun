-- =====================================================
-- Workflow Engine Core V1: All Tables (Consolidated)
-- Tables with wf_* prefix for workflow engine features
-- Note: Core sys_* tables are created by platform-security
-- Aligned with Entity classes
-- =====================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =====================================================
-- 1. Extended Task Info (wf_extended_task_info)
-- Supports multi-dimensional task assignment
-- =====================================================
CREATE TABLE IF NOT EXISTS wf_extended_task_info (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL UNIQUE,
    process_instance_id VARCHAR(64) NOT NULL,
    process_definition_id VARCHAR(64) NOT NULL,
    task_definition_key VARCHAR(255),
    task_name VARCHAR(255),
    task_description VARCHAR(4000),
    assignment_type VARCHAR(20) NOT NULL,
    assignment_target VARCHAR(255) NOT NULL,
    original_assignee VARCHAR(64),
    delegated_to VARCHAR(64),
    delegated_by VARCHAR(64),
    delegated_time TIMESTAMP,
    delegation_reason VARCHAR(500),
    claimed_by VARCHAR(64),
    claimed_time TIMESTAMP,
    priority INTEGER,
    due_date TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    created_time TIMESTAMP NOT NULL,
    updated_time TIMESTAMP,
    completed_time TIMESTAMP,
    completed_by VARCHAR(64),
    form_key VARCHAR(255),
    business_key VARCHAR(255),
    extended_properties TEXT,
    tenant_id VARCHAR(64),
    version BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    created_by VARCHAR(64),
    updated_by VARCHAR(64)
);

-- Indexes for wf_extended_task_info
CREATE INDEX IF NOT EXISTS idx_task_id ON wf_extended_task_info(task_id);
CREATE INDEX IF NOT EXISTS idx_assignment_type ON wf_extended_task_info(assignment_type);
CREATE INDEX IF NOT EXISTS idx_assignment_target ON wf_extended_task_info(assignment_target);
CREATE INDEX IF NOT EXISTS idx_delegated_to ON wf_extended_task_info(delegated_to);
CREATE INDEX IF NOT EXISTS idx_claimed_by ON wf_extended_task_info(claimed_by);
CREATE INDEX IF NOT EXISTS idx_process_instance ON wf_extended_task_info(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_created_time ON wf_extended_task_info(created_time);
CREATE INDEX IF NOT EXISTS idx_due_date ON wf_extended_task_info(due_date);
CREATE INDEX IF NOT EXISTS idx_priority ON wf_extended_task_info(priority);
CREATE INDEX IF NOT EXISTS idx_status ON wf_extended_task_info(status);


-- =====================================================
-- 2. Process Variables (wf_process_variables)
-- Stores process variable history and extended info
-- =====================================================
CREATE TABLE IF NOT EXISTS wf_process_variables (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    process_instance_id VARCHAR(64),
    execution_id VARCHAR(64),
    task_id VARCHAR(64),
    case_instance_id VARCHAR(64),
    case_execution_id VARCHAR(64),
    activity_instance_id VARCHAR(64),
    tenant_id VARCHAR(255),
    sequence_counter BIGINT,
    is_concurrent_local BOOLEAN DEFAULT false,
    text_value TEXT,
    text_value2 TEXT,
    double_value DOUBLE PRECISION,
    long_value BIGINT,
    date_value TIMESTAMP,
    json_value JSONB,
    binary_value BYTEA,
    created_time TIMESTAMP NOT NULL,
    updated_time TIMESTAMP NOT NULL,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    change_reason VARCHAR(500),
    operation_type VARCHAR(20)
);

-- Indexes for wf_process_variables
CREATE INDEX IF NOT EXISTS idx_variable_name ON wf_process_variables(name);
CREATE INDEX IF NOT EXISTS idx_variable_proc_inst ON wf_process_variables(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_variable_task ON wf_process_variables(task_id);
CREATE INDEX IF NOT EXISTS idx_variable_created_time ON wf_process_variables(created_time);

-- =====================================================
-- 3. Workflow Audit Logs (wf_audit_logs)
-- Records all workflow operation audit trails
-- =====================================================
CREATE TABLE IF NOT EXISTS wf_audit_logs (
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
    is_sensitive BOOLEAN NOT NULL DEFAULT false
);

-- Indexes for wf_audit_logs
CREATE INDEX IF NOT EXISTS idx_audit_user_id ON wf_audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_operation_type ON wf_audit_logs(operation_type);
CREATE INDEX IF NOT EXISTS idx_audit_resource_type ON wf_audit_logs(resource_type);
CREATE INDEX IF NOT EXISTS idx_audit_resource_id ON wf_audit_logs(resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON wf_audit_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_ip_address ON wf_audit_logs(ip_address);
CREATE INDEX IF NOT EXISTS idx_audit_session_id ON wf_audit_logs(session_id);
CREATE INDEX IF NOT EXISTS idx_audit_tenant_id ON wf_audit_logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_composite ON wf_audit_logs(user_id, operation_type, timestamp);


-- =====================================================
-- 4. Exception Records (wf_exception_records)
-- Records workflow execution exceptions
-- =====================================================
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
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    context_data TEXT,
    variables_snapshot TEXT,
    occurred_time TIMESTAMP NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retry_count INTEGER NOT NULL DEFAULT 3,
    next_retry_time TIMESTAMP,
    last_retry_time TIMESTAMP,
    resolved BOOLEAN NOT NULL DEFAULT false,
    resolved_time TIMESTAMP,
    resolved_by VARCHAR(64),
    resolution_method VARCHAR(50),
    resolution_note TEXT,
    alert_sent BOOLEAN NOT NULL DEFAULT false,
    alert_sent_time TIMESTAMP,
    parent_exception_id VARCHAR(64),
    tenant_id VARCHAR(64),
    created_time TIMESTAMP NOT NULL,
    updated_time TIMESTAMP,
    CONSTRAINT wf_exception_records_severity_check CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT wf_exception_records_status_check CHECK (status IN ('PENDING', 'PROCESSING', 'RESOLVED', 'IGNORED'))
);

-- Indexes for wf_exception_records
CREATE INDEX IF NOT EXISTS idx_exception_process_instance ON wf_exception_records(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_exception_task_id ON wf_exception_records(task_id);
CREATE INDEX IF NOT EXISTS idx_exception_type ON wf_exception_records(exception_type);
CREATE INDEX IF NOT EXISTS idx_exception_severity ON wf_exception_records(severity);
CREATE INDEX IF NOT EXISTS idx_exception_status ON wf_exception_records(status);
CREATE INDEX IF NOT EXISTS idx_exception_occurred_time ON wf_exception_records(occurred_time);
CREATE INDEX IF NOT EXISTS idx_exception_resolved ON wf_exception_records(resolved);

-- =====================================================
-- Comments
-- =====================================================
COMMENT ON TABLE wf_extended_task_info IS 'Extended task information for workflow tasks with multi-dimensional assignment support';
COMMENT ON TABLE wf_process_variables IS 'Process instance variables with history and extended info';
COMMENT ON TABLE wf_audit_logs IS 'Workflow operation audit logs for compliance and analysis';
COMMENT ON TABLE wf_exception_records IS 'Workflow exception records with retry and resolution tracking';

COMMENT ON COLUMN wf_extended_task_info.assignment_type IS 'Assignment type: USER, VIRTUAL_GROUP, DEPT_ROLE, etc.';
COMMENT ON COLUMN wf_extended_task_info.status IS 'Task status: CREATED, ASSIGNED, CLAIMED, DELEGATED, IN_PROGRESS, COMPLETED, CANCELLED';

COMMENT ON COLUMN wf_process_variables.type IS 'Variable type: STRING, INTEGER, LONG, DOUBLE, BOOLEAN, DATE, JSON, BINARY';

COMMENT ON COLUMN wf_audit_logs.resource_type IS 'Resource type: PROCESS_DEFINITION, PROCESS_INSTANCE, TASK, VARIABLE, FORM, USER, ROLE';
COMMENT ON COLUMN wf_audit_logs.operation_result IS 'Operation result: SUCCESS, FAILURE, PARTIAL';
COMMENT ON COLUMN wf_audit_logs.risk_level IS 'Risk level: LOW, MEDIUM, HIGH, CRITICAL';
COMMENT ON COLUMN wf_audit_logs.is_sensitive IS 'Whether this is a sensitive operation';

COMMENT ON COLUMN wf_exception_records.severity IS 'Severity level: CRITICAL, HIGH, MEDIUM, LOW';
COMMENT ON COLUMN wf_exception_records.status IS 'Exception status: PENDING, PROCESSING, RESOLVED, IGNORED';
COMMENT ON COLUMN wf_exception_records.resolution_method IS 'Resolution method: AUTO_RETRY, MANUAL_FIX, IGNORED, COMPENSATED';
