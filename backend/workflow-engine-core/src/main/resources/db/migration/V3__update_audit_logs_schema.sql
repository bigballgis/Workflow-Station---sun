-- =====================================================
-- V3: Update wf_audit_logs table to match AuditLog entity
-- This migration adds missing columns and indexes
-- =====================================================

-- Add missing columns to wf_audit_logs
ALTER TABLE wf_audit_logs ADD COLUMN IF NOT EXISTS resource_type VARCHAR(50);
ALTER TABLE wf_audit_logs ADD COLUMN IF NOT EXISTS resource_id VARCHAR(64);
ALTER TABLE wf_audit_logs ADD COLUMN IF NOT EXISTS resource_name VARCHAR(255);
ALTER TABLE wf_audit_logs ADD COLUMN IF NOT EXISTS operation_description TEXT;
ALTER TABLE wf_audit_logs ADD COLUMN IF NOT EXISTS before_data JSONB;
ALTER TABLE wf_audit_logs ADD COLUMN IF NOT EXISTS after_data JSONB;
ALTER TABLE wf_audit_logs ADD COLUMN IF NOT EXISTS operation_result VARCHAR(20);
ALTER TABLE wf_audit_logs ADD COLUMN IF NOT EXISTS error_message TEXT;
ALTER TABLE wf_audit_logs ADD COLUMN IF NOT EXISTS timestamp TIMESTAMP;
ALTER TABLE wf_audit_logs ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45);
ALTER TABLE wf_audit_logs ADD COLUMN IF NOT EXISTS user_agent VARCHAR(500);
ALTER TABLE wf_audit_logs ADD COLUMN IF NOT EXISTS session_id VARCHAR(128);
ALTER TABLE wf_audit_logs ADD COLUMN IF NOT EXISTS request_id VARCHAR(64);
ALTER TABLE wf_audit_logs ADD COLUMN IF NOT EXISTS duration_ms BIGINT;
ALTER TABLE wf_audit_logs ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
ALTER TABLE wf_audit_logs ADD COLUMN IF NOT EXISTS context_data JSONB;
ALTER TABLE wf_audit_logs ADD COLUMN IF NOT EXISTS risk_level VARCHAR(20);
ALTER TABLE wf_audit_logs ADD COLUMN IF NOT EXISTS is_sensitive BOOLEAN DEFAULT false;

-- Migrate existing data: copy created_at to timestamp if timestamp is null
UPDATE wf_audit_logs SET timestamp = created_at WHERE timestamp IS NULL;

-- Set default values for required columns
UPDATE wf_audit_logs SET resource_type = 'UNKNOWN' WHERE resource_type IS NULL;
UPDATE wf_audit_logs SET resource_id = COALESCE(process_instance_id, task_id, 'UNKNOWN') WHERE resource_id IS NULL;
UPDATE wf_audit_logs SET operation_result = 'SUCCESS' WHERE operation_result IS NULL;
UPDATE wf_audit_logs SET is_sensitive = false WHERE is_sensitive IS NULL;

-- Now add NOT NULL constraints where needed
ALTER TABLE wf_audit_logs ALTER COLUMN resource_type SET NOT NULL;
ALTER TABLE wf_audit_logs ALTER COLUMN resource_id SET NOT NULL;
ALTER TABLE wf_audit_logs ALTER COLUMN operation_result SET NOT NULL;
ALTER TABLE wf_audit_logs ALTER COLUMN is_sensitive SET NOT NULL;
ALTER TABLE wf_audit_logs ALTER COLUMN timestamp SET NOT NULL;

-- Create indexes for the new columns (use IF NOT EXISTS pattern)
CREATE INDEX IF NOT EXISTS idx_audit_user_id ON wf_audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_operation_type ON wf_audit_logs(operation_type);
CREATE INDEX IF NOT EXISTS idx_audit_resource_type ON wf_audit_logs(resource_type);
CREATE INDEX IF NOT EXISTS idx_audit_resource_id ON wf_audit_logs(resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON wf_audit_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_ip_address ON wf_audit_logs(ip_address);
CREATE INDEX IF NOT EXISTS idx_audit_session_id ON wf_audit_logs(session_id);
CREATE INDEX IF NOT EXISTS idx_audit_tenant_id ON wf_audit_logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_composite ON wf_audit_logs(user_id, operation_type, timestamp);

-- Add comments
COMMENT ON COLUMN wf_audit_logs.resource_type IS 'Resource type: PROCESS_DEFINITION, PROCESS_INSTANCE, TASK, VARIABLE, FORM, USER, ROLE';
COMMENT ON COLUMN wf_audit_logs.operation_result IS 'Operation result: SUCCESS, FAILURE, PARTIAL';
COMMENT ON COLUMN wf_audit_logs.risk_level IS 'Risk level: LOW, MEDIUM, HIGH, CRITICAL';
COMMENT ON COLUMN wf_audit_logs.is_sensitive IS 'Whether this is a sensitive operation';
