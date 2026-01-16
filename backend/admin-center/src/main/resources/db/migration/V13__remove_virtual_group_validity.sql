-- Remove validity period columns from sys_virtual_groups table
-- These columns are no longer needed as all virtual groups are permanent

ALTER TABLE sys_virtual_groups DROP COLUMN IF EXISTS valid_from;
ALTER TABLE sys_virtual_groups DROP COLUMN IF EXISTS valid_to;
