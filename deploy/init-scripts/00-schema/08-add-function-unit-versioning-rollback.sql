-- =====================================================
-- Function Unit Versioning Migration Rollback
-- Removes version tracking columns from function unit tables
-- This script reverses the changes made by 08-add-function-unit-versioning.sql
-- =====================================================

-- =====================================================
-- 1. Rollback up_process_instance changes
-- =====================================================

-- Drop index
DROP INDEX IF EXISTS idx_up_process_instance_version;

-- Drop column
ALTER TABLE up_process_instance 
DROP COLUMN IF EXISTS function_unit_version_id;

-- =====================================================
-- 2. Rollback dw_process_definitions changes
-- =====================================================

-- Drop index
DROP INDEX IF EXISTS idx_dw_process_def_version;

-- Drop foreign key constraint
ALTER TABLE dw_process_definitions 
DROP CONSTRAINT IF EXISTS fk_dw_process_def_function_unit_version;

-- Drop column
ALTER TABLE dw_process_definitions 
DROP COLUMN IF EXISTS function_unit_version_id;

-- =====================================================
-- 3. Rollback sys_function_units changes
-- =====================================================

-- Drop indexes
DROP INDEX IF EXISTS idx_sys_function_unit_deployed_at;
DROP INDEX IF EXISTS idx_sys_function_unit_active;
DROP INDEX IF EXISTS idx_sys_function_unit_version;

-- Drop foreign key constraint
ALTER TABLE sys_function_units 
DROP CONSTRAINT IF EXISTS fk_sys_function_unit_previous_version;

-- Drop columns
ALTER TABLE sys_function_units 
DROP COLUMN IF EXISTS previous_version_id;

ALTER TABLE sys_function_units 
DROP COLUMN IF EXISTS deployed_at;

ALTER TABLE sys_function_units 
DROP COLUMN IF EXISTS is_active;

-- =====================================================
-- 4. Rollback dw_function_units changes
-- =====================================================

-- Drop indexes
DROP INDEX IF EXISTS idx_dw_function_unit_deployed_at;
DROP INDEX IF EXISTS idx_dw_function_unit_active;
DROP INDEX IF EXISTS idx_dw_function_unit_version;

-- Drop foreign key constraint
ALTER TABLE dw_function_units 
DROP CONSTRAINT IF EXISTS fk_dw_function_unit_previous_version;

-- Drop columns
ALTER TABLE dw_function_units 
DROP COLUMN IF EXISTS previous_version_id;

ALTER TABLE dw_function_units 
DROP COLUMN IF EXISTS deployed_at;

ALTER TABLE dw_function_units 
DROP COLUMN IF EXISTS is_active;

ALTER TABLE dw_function_units 
DROP COLUMN IF EXISTS version;

-- =====================================================
-- Rollback Complete
-- =====================================================

-- Print success message
DO $$
BEGIN
    RAISE NOTICE 'Function unit versioning migration rollback completed successfully';
    RAISE NOTICE 'Removed version tracking columns from dw_function_units and sys_function_units';
    RAISE NOTICE 'Removed function_unit_version_id from dw_process_definitions and up_process_instance';
    RAISE NOTICE 'Removed all indexes and constraints';
END $$;
