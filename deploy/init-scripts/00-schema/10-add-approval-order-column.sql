-- Add approval_order column to sys_function_unit_approvals table
-- Supports approval sequence in multi-level approval workflows

-- Add column (if not exists)
ALTER TABLE sys_function_unit_approvals 
ADD COLUMN IF NOT EXISTS approval_order INTEGER DEFAULT 1;

-- Make approver_id nullable (may not be assigned when approval is created)
ALTER TABLE sys_function_unit_approvals 
ALTER COLUMN approver_id DROP NOT NULL;

-- Add comments
COMMENT ON COLUMN sys_function_unit_approvals.approval_order IS 'Approval sequence order for multi-level approval workflows';
COMMENT ON COLUMN sys_function_unit_approvals.approver_id IS 'Approver ID, nullable at creation, required at approval time';

-- Set default value for existing records
UPDATE sys_function_unit_approvals 
SET approval_order = 1 
WHERE approval_order IS NULL;
