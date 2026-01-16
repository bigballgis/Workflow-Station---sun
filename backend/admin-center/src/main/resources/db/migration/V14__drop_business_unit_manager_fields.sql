-- V5: Drop deprecated manager fields from sys_business_units
-- These fields are no longer used as approvers are now managed via sys_approvers table

-- Drop manager_id and secondary_manager_id columns
ALTER TABLE sys_business_units DROP COLUMN IF EXISTS manager_id;
ALTER TABLE sys_business_units DROP COLUMN IF EXISTS secondary_manager_id;

-- Note: Approvers are now managed through:
-- 1. sys_approvers table (for business unit approvers)
-- 2. sys_virtual_group_approvers table (for virtual group approvers)
