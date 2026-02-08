-- =====================================================
-- Rollback Function Unit Version Initialization
-- Resets version data to pre-migration state
-- This script reverses the changes made by 09-initialize-function-unit-versions.sql
-- WARNING: This will reset all version information to defaults
-- =====================================================

-- =====================================================
-- 1. Reset dw_function_units versions
-- =====================================================

-- Reset version to default
UPDATE dw_function_units
SET version = '1.0.0';

-- Reset is_active to default
UPDATE dw_function_units
SET is_active = TRUE;

-- Reset previous_version_id to NULL
UPDATE dw_function_units
SET previous_version_id = NULL;

-- Note: We don't reset deployed_at as it may have been set during initial creation

-- =====================================================
-- 2. Reset sys_function_units versions
-- =====================================================

-- Reset version to default
UPDATE sys_function_units
SET version = '1.0.0';

-- Reset is_active to default
UPDATE sys_function_units
SET is_active = TRUE;

-- Reset previous_version_id to NULL
UPDATE sys_function_units
SET previous_version_id = NULL;

-- Note: We don't reset deployed_at as it may have been set during initial creation

-- =====================================================
-- 3. Reset dw_process_definitions version links
-- =====================================================

-- Note: We keep function_unit_version_id pointing to function_unit_id
-- as this maintains referential integrity
-- If you need to clear these, you should drop the column using the schema rollback script

-- =====================================================
-- 4. Reset up_process_instance version links
-- =====================================================

-- Clear function_unit_version_id for all process instances
UPDATE up_process_instance
SET function_unit_version_id = NULL
WHERE function_unit_version_id IS NOT NULL;

-- =====================================================
-- Rollback Complete
-- =====================================================

-- Print success message
DO $$
BEGIN
    RAISE NOTICE 'Function unit version initialization rollback completed';
    RAISE NOTICE 'Reset all function units to version 1.0.0 and active status';
    RAISE NOTICE 'Cleared version links from process instances';
END $$;
