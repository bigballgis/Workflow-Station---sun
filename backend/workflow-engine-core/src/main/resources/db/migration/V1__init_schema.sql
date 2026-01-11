-- =====================================================
-- Workflow Engine Core - Initial Schema
-- Version: 2.0.0
-- Note: Core sys_* tables are created by platform-security
-- This migration creates workflow-engine specific tables (wf_* prefix)
-- =====================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =====================================================
-- Workflow Engine Specific Tables (wf_* prefix)
-- =====================================================

-- Extended Task Info (workflow-specific task metadata)
CREATE TABLE IF NOT EXISTS wf_extended_task_info (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL UNIQUE,
    process_instance_id VARCHAR(64),
    assignment_type VARCHAR(20),
    original_assignee VARCHAR(64),
    delegated_from VARCHAR(64),
    priority INTEGER DEFAULT 50,
    due_date TIMESTAMP,
    reminder_sent BOOLEAN DEFAULT false,
    custom_data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wf_task_id ON wf_extended_task_info(task_id);
CREATE INDEX IF NOT EXISTS idx_wf_task_process ON wf_extended_task_info(process_instance_id);

-- Process Variables (workflow-specific variables)
CREATE TABLE IF NOT EXISTS wf_process_variables (
    id VARCHAR(64) PRIMARY KEY,
    process_instance_id VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50),
    value TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wf_var_process ON wf_process_variables(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_var_name ON wf_process_variables(name);

-- Workflow Audit Logs
CREATE TABLE IF NOT EXISTS wf_audit_logs (
    id VARCHAR(64) PRIMARY KEY,
    process_instance_id VARCHAR(64),
    task_id VARCHAR(64),
    user_id VARCHAR(64),
    operation_type VARCHAR(50) NOT NULL,
    operation_detail TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wf_audit_process ON wf_audit_logs(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_audit_user ON wf_audit_logs(user_id);

-- Exception Records
CREATE TABLE IF NOT EXISTS wf_exception_records (
    id VARCHAR(64) PRIMARY KEY,
    process_instance_id VARCHAR(64),
    task_id VARCHAR(64),
    exception_type VARCHAR(100),
    exception_message TEXT,
    stack_trace TEXT,
    handled BOOLEAN DEFAULT false,
    handled_by VARCHAR(64),
    handled_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wf_exception_process ON wf_exception_records(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_exception_handled ON wf_exception_records(handled);

-- Saga Transactions (for distributed transaction management)
CREATE TABLE IF NOT EXISTS wf_saga_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_type VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    context JSONB,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    version BIGINT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_wf_saga_status ON wf_saga_transactions(status);
CREATE INDEX IF NOT EXISTS idx_wf_saga_type ON wf_saga_transactions(saga_type);

-- Saga Steps
CREATE TABLE IF NOT EXISTS wf_saga_steps (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID NOT NULL REFERENCES wf_saga_transactions(id),
    step_name VARCHAR(100) NOT NULL,
    step_order INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    input_data JSONB,
    output_data JSONB,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wf_saga_steps_saga ON wf_saga_steps(saga_id);

-- Comments
COMMENT ON TABLE wf_extended_task_info IS 'Extended task information for workflow tasks';
COMMENT ON TABLE wf_process_variables IS 'Process instance variables';
COMMENT ON TABLE wf_audit_logs IS 'Workflow operation audit logs';
COMMENT ON TABLE wf_exception_records IS 'Workflow exception records';
COMMENT ON TABLE wf_saga_transactions IS 'Saga transaction management';
COMMENT ON TABLE wf_saga_steps IS 'Saga transaction steps';
