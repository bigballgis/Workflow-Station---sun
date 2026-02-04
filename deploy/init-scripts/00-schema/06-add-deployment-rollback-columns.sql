-- =====================================================
-- Add missing rollback columns to sys_function_unit_deployments
-- Date: 2026-02-04
-- =====================================================

-- Add rollback tracking columns
ALTER TABLE sys_function_unit_deployments 
ADD COLUMN IF NOT EXISTS started_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS rollback_reason TEXT,
ADD COLUMN IF NOT EXISTS rollback_by VARCHAR(64),
ADD COLUMN IF NOT EXISTS rollback_at TIMESTAMP;

-- Add comment for documentation
COMMENT ON COLUMN sys_function_unit_deployments.started_at IS 'Deployment start timestamp';
COMMENT ON COLUMN sys_function_unit_deployments.rollback_reason IS 'Reason for rollback';
COMMENT ON COLUMN sys_function_unit_deployments.rollback_by IS 'User ID who performed the rollback';
COMMENT ON COLUMN sys_function_unit_deployments.rollback_at IS 'Rollback timestamp';
