-- =====================================================
-- Initialize Function Unit Versions
-- Sets version 1.0.0 for all existing function units and marks them as active
-- This ensures backward compatibility with existing data
-- Requirements: 9.1, 9.2, 9.3
-- =====================================================

-- =====================================================
-- 1. Initialize dw_function_units versions
-- =====================================================

-- Ensure all existing function units have version 1.0.0
UPDATE dw_function_units
SET version = '1.0.0'
WHERE version IS NULL OR version = '';

-- Ensure all existing function units are marked as active
UPDATE dw_function_units
SET is_active = TRUE
WHERE is_active IS NULL;

-- Ensure all existing function units have deployed_at timestamp
UPDATE dw_function_units
SET deployed_at = COALESCE(created_at, CURRENT_TIMESTAMP)
WHERE deployed_at IS NULL;

-- Set previous_version_id to NULL for all existing records (they are the first versions)
UPDATE dw_function_units
SET previous_version_id = NULL
WHERE previous_version_id IS NOT NULL;

-- =====================================================
-- 2. Initialize sys_function_units versions
-- =====================================================

-- Ensure all existing function units have proper semantic version format
-- If version doesn't match semantic versioning, set to 1.0.0
UPDATE sys_function_units
SET version = '1.0.0'
WHERE version IS NULL 
   OR version = '' 
   OR version !~ '^\d+\.\d+\.\d+$';

-- Ensure all existing function units are marked as active
UPDATE sys_function_units
SET is_active = TRUE
WHERE is_active IS NULL;

-- Ensure all existing function units have deployed_at timestamp
UPDATE sys_function_units
SET deployed_at = COALESCE(imported_at, created_at, CURRENT_TIMESTAMP)
WHERE deployed_at IS NULL;

-- Set previous_version_id to NULL for all existing records (they are the first versions)
UPDATE sys_function_units
SET previous_version_id = NULL
WHERE previous_version_id IS NOT NULL;

-- =====================================================
-- 3. Initialize dw_process_definitions version links
-- =====================================================

-- Link all existing process definitions to their function unit's current version
-- This assumes the function_unit_id points to the active version
UPDATE dw_process_definitions pd
SET function_unit_version_id = pd.function_unit_id
WHERE function_unit_version_id IS NULL;

-- =====================================================
-- 4. Initialize up_process_instance version links
-- =====================================================

-- Note: We cannot automatically link process instances to function unit versions
-- without additional information about which function unit they belong to.
-- This would require a mapping from process_definition_key to function_unit.
-- 
-- For now, we'll leave function_unit_version_id as NULL for existing process instances.
-- A separate migration script can be created if needed to populate this based on
-- business logic or process definition key patterns.

-- =====================================================
-- 5. Verify migration results
-- =====================================================

-- Count function units by version in dw_function_units
DO $$
DECLARE
    dw_count INTEGER;
    sys_count INTEGER;
    proc_def_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO dw_count FROM dw_function_units WHERE version = '1.0.0' AND is_active = TRUE;
    SELECT COUNT(*) INTO sys_count FROM sys_function_units WHERE version = '1.0.0' AND is_active = TRUE;
    SELECT COUNT(*) INTO proc_def_count FROM dw_process_definitions WHERE function_unit_version_id IS NOT NULL;
    
    RAISE NOTICE 'Migration verification:';
    RAISE NOTICE '  - dw_function_units with version 1.0.0 and active: %', dw_count;
    RAISE NOTICE '  - sys_function_units with version 1.0.0 and active: %', sys_count;
    RAISE NOTICE '  - dw_process_definitions with version link: %', proc_def_count;
    RAISE NOTICE 'Function unit version initialization completed successfully';
END $$;
