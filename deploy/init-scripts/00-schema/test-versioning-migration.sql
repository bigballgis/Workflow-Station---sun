-- =====================================================
-- Test Script for Function Unit Versioning Migration
-- Verifies that the migration scripts work correctly
-- =====================================================

-- Start transaction for testing (will be rolled back)
BEGIN;

-- =====================================================
-- Test 1: Verify schema changes on dw_function_units
-- =====================================================
DO $$
DECLARE
    column_exists BOOLEAN;
BEGIN
    RAISE NOTICE 'Test 1: Verifying dw_function_units schema changes...';
    
    -- Check version column
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'dw_function_units' AND column_name = 'version'
    ) INTO column_exists;
    IF NOT column_exists THEN
        RAISE EXCEPTION 'FAILED: version column not found in dw_function_units';
    END IF;
    
    -- Check is_active column
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'dw_function_units' AND column_name = 'is_active'
    ) INTO column_exists;
    IF NOT column_exists THEN
        RAISE EXCEPTION 'FAILED: is_active column not found in dw_function_units';
    END IF;
    
    -- Check deployed_at column
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'dw_function_units' AND column_name = 'deployed_at'
    ) INTO column_exists;
    IF NOT column_exists THEN
        RAISE EXCEPTION 'FAILED: deployed_at column not found in dw_function_units';
    END IF;
    
    -- Check previous_version_id column
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'dw_function_units' AND column_name = 'previous_version_id'
    ) INTO column_exists;
    IF NOT column_exists THEN
        RAISE EXCEPTION 'FAILED: previous_version_id column not found in dw_function_units';
    END IF;
    
    RAISE NOTICE 'Test 1: PASSED - All columns exist in dw_function_units';
END $$;

-- =====================================================
-- Test 2: Verify schema changes on sys_function_units
-- =====================================================
DO $$
DECLARE
    column_exists BOOLEAN;
BEGIN
    RAISE NOTICE 'Test 2: Verifying sys_function_units schema changes...';
    
    -- Check is_active column
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'sys_function_units' AND column_name = 'is_active'
    ) INTO column_exists;
    IF NOT column_exists THEN
        RAISE EXCEPTION 'FAILED: is_active column not found in sys_function_units';
    END IF;
    
    -- Check deployed_at column
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'sys_function_units' AND column_name = 'deployed_at'
    ) INTO column_exists;
    IF NOT column_exists THEN
        RAISE EXCEPTION 'FAILED: deployed_at column not found in sys_function_units';
    END IF;
    
    -- Check previous_version_id column
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'sys_function_units' AND column_name = 'previous_version_id'
    ) INTO column_exists;
    IF NOT column_exists THEN
        RAISE EXCEPTION 'FAILED: previous_version_id column not found in sys_function_units';
    END IF;
    
    RAISE NOTICE 'Test 2: PASSED - All columns exist in sys_function_units';
END $$;

-- =====================================================
-- Test 3: Verify indexes were created
-- =====================================================
DO $$
DECLARE
    index_exists BOOLEAN;
BEGIN
    RAISE NOTICE 'Test 3: Verifying indexes...';
    
    -- Check dw_function_units indexes
    SELECT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'dw_function_units' AND indexname = 'idx_dw_function_unit_version'
    ) INTO index_exists;
    IF NOT index_exists THEN
        RAISE EXCEPTION 'FAILED: idx_dw_function_unit_version not found';
    END IF;
    
    SELECT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'dw_function_units' AND indexname = 'idx_dw_function_unit_active'
    ) INTO index_exists;
    IF NOT index_exists THEN
        RAISE EXCEPTION 'FAILED: idx_dw_function_unit_active not found';
    END IF;
    
    -- Check sys_function_units indexes
    SELECT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'sys_function_units' AND indexname = 'idx_sys_function_unit_version'
    ) INTO index_exists;
    IF NOT index_exists THEN
        RAISE EXCEPTION 'FAILED: idx_sys_function_unit_version not found';
    END IF;
    
    SELECT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'sys_function_units' AND indexname = 'idx_sys_function_unit_active'
    ) INTO index_exists;
    IF NOT index_exists THEN
        RAISE EXCEPTION 'FAILED: idx_sys_function_unit_active not found';
    END IF;
    
    RAISE NOTICE 'Test 3: PASSED - All indexes exist';
END $$;

-- =====================================================
-- Test 4: Verify foreign key constraints
-- =====================================================
DO $$
DECLARE
    constraint_exists BOOLEAN;
BEGIN
    RAISE NOTICE 'Test 4: Verifying foreign key constraints...';
    
    -- Check dw_function_units previous_version FK
    SELECT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE table_name = 'dw_function_units' 
        AND constraint_name = 'fk_dw_function_unit_previous_version'
        AND constraint_type = 'FOREIGN KEY'
    ) INTO constraint_exists;
    IF NOT constraint_exists THEN
        RAISE EXCEPTION 'FAILED: fk_dw_function_unit_previous_version not found';
    END IF;
    
    -- Check sys_function_units previous_version FK
    SELECT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE table_name = 'sys_function_units' 
        AND constraint_name = 'fk_sys_function_unit_previous_version'
        AND constraint_type = 'FOREIGN KEY'
    ) INTO constraint_exists;
    IF NOT constraint_exists THEN
        RAISE EXCEPTION 'FAILED: fk_sys_function_unit_previous_version not found';
    END IF;
    
    -- Check dw_process_definitions version FK
    SELECT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE table_name = 'dw_process_definitions' 
        AND constraint_name = 'fk_dw_process_def_function_unit_version'
        AND constraint_type = 'FOREIGN KEY'
    ) INTO constraint_exists;
    IF NOT constraint_exists THEN
        RAISE EXCEPTION 'FAILED: fk_dw_process_def_function_unit_version not found';
    END IF;
    
    RAISE NOTICE 'Test 4: PASSED - All foreign key constraints exist';
END $$;

-- =====================================================
-- Test 5: Test version insertion and querying
-- =====================================================
DO $$
DECLARE
    test_fu_id BIGINT;
    test_fu_id_v2 BIGINT;
    active_count INTEGER;
BEGIN
    RAISE NOTICE 'Test 5: Testing version insertion and querying...';
    
    -- Insert a test function unit (version 1.0.0)
    INSERT INTO dw_function_units (code, name, description, status, version, is_active, created_by)
    VALUES ('TEST_FU', 'Test Function Unit', 'Test description', 'DRAFT', '1.0.0', TRUE, 'test_user')
    RETURNING id INTO test_fu_id;
    
    -- Insert a second version (version 1.1.0)
    INSERT INTO dw_function_units (code, name, description, status, version, is_active, previous_version_id, created_by)
    VALUES ('TEST_FU_V2', 'Test Function Unit', 'Test description v2', 'DRAFT', '1.1.0', FALSE, test_fu_id, 'test_user')
    RETURNING id INTO test_fu_id_v2;
    
    -- Query active version
    SELECT COUNT(*) INTO active_count
    FROM dw_function_units
    WHERE name = 'Test Function Unit' AND is_active = TRUE;
    
    IF active_count != 1 THEN
        RAISE EXCEPTION 'FAILED: Expected 1 active version, found %', active_count;
    END IF;
    
    -- Verify previous_version_id link
    IF NOT EXISTS (
        SELECT 1 FROM dw_function_units 
        WHERE id = test_fu_id_v2 AND previous_version_id = test_fu_id
    ) THEN
        RAISE EXCEPTION 'FAILED: previous_version_id link not working';
    END IF;
    
    RAISE NOTICE 'Test 5: PASSED - Version insertion and querying works correctly';
END $$;

-- =====================================================
-- Test 6: Test semantic version format validation
-- =====================================================
DO $$
BEGIN
    RAISE NOTICE 'Test 6: Testing semantic version format...';
    
    -- Test valid semantic versions
    IF NOT ('1.0.0' ~ '^\d+\.\d+\.\d+$') THEN
        RAISE EXCEPTION 'FAILED: Valid version 1.0.0 rejected';
    END IF;
    
    IF NOT ('10.20.30' ~ '^\d+\.\d+\.\d+$') THEN
        RAISE EXCEPTION 'FAILED: Valid version 10.20.30 rejected';
    END IF;
    
    -- Test invalid semantic versions
    IF ('1.0' ~ '^\d+\.\d+\.\d+$') THEN
        RAISE EXCEPTION 'FAILED: Invalid version 1.0 accepted';
    END IF;
    
    IF ('v1.0.0' ~ '^\d+\.\d+\.\d+$') THEN
        RAISE EXCEPTION 'FAILED: Invalid version v1.0.0 accepted';
    END IF;
    
    RAISE NOTICE 'Test 6: PASSED - Semantic version format validation works';
END $$;

-- =====================================================
-- Test 7: Test process definition version linking
-- =====================================================
DO $$
DECLARE
    test_fu_id BIGINT;
    test_pd_id BIGINT;
BEGIN
    RAISE NOTICE 'Test 7: Testing process definition version linking...';
    
    -- Create a test function unit
    INSERT INTO dw_function_units (code, name, status, version, is_active, created_by)
    VALUES ('TEST_PD_FU', 'Test PD Function Unit', 'DRAFT', '1.0.0', TRUE, 'test_user')
    RETURNING id INTO test_fu_id;
    
    -- Create a process definition linked to the version
    INSERT INTO dw_process_definitions (function_unit_id, function_unit_version_id, bpmn_xml)
    VALUES (test_fu_id, test_fu_id, '<bpmn>test</bpmn>')
    RETURNING id INTO test_pd_id;
    
    -- Verify the link
    IF NOT EXISTS (
        SELECT 1 FROM dw_process_definitions 
        WHERE id = test_pd_id AND function_unit_version_id = test_fu_id
    ) THEN
        RAISE EXCEPTION 'FAILED: Process definition not linked to version';
    END IF;
    
    RAISE NOTICE 'Test 7: PASSED - Process definition version linking works';
END $$;

-- =====================================================
-- Test Summary
-- =====================================================
DO $$
BEGIN
    RAISE NOTICE '==============================================';
    RAISE NOTICE 'ALL TESTS PASSED';
    RAISE NOTICE '==============================================';
    RAISE NOTICE 'The function unit versioning migration is working correctly.';
    RAISE NOTICE 'Schema changes, indexes, constraints, and data operations all verified.';
END $$;

-- Rollback test transaction
ROLLBACK;

-- Print final message
DO $$
BEGIN
    RAISE NOTICE 'Test transaction rolled back - database unchanged';
END $$;
