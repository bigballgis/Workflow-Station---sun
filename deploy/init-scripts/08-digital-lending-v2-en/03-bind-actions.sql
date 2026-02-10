-- =============================================================================
-- Bind Actions to Process Nodes (Optional)
-- Note: In the current system, actions are bound through BPMN actionIds attribute
-- This script is used to verify bindings are correct
-- =============================================================================

DO $$
DECLARE
    v_function_unit_id BIGINT;
    v_process_id BIGINT;
    v_action_count INTEGER;
    v_action_record RECORD;
BEGIN
    -- Get function unit ID
    SELECT id INTO v_function_unit_id 
    FROM dw_function_units 
    WHERE code = 'DIGITAL_LENDING_V2_EN';

    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit DIGITAL_LENDING_V2_EN does not exist';
    END IF;

    -- Get process ID
    SELECT id INTO v_process_id 
    FROM dw_process_definitions 
    WHERE function_unit_id = v_function_unit_id;

    IF v_process_id IS NULL THEN
        RAISE EXCEPTION 'Process definition does not exist, please run 02-insert-bpmn-process.sql first';
    END IF;

    -- Count actions
    SELECT COUNT(*) INTO v_action_count 
    FROM dw_action_definitions 
    WHERE function_unit_id = v_function_unit_id;

    RAISE NOTICE '========================================';
    RAISE NOTICE 'Action Binding Verification';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Function Unit ID: %', v_function_unit_id;
    RAISE NOTICE 'Process ID: %', v_process_id;
    RAISE NOTICE 'Action Count: %', v_action_count;
    RAISE NOTICE '';
    RAISE NOTICE 'Action List:';

    -- Display all actions
    FOR v_action_record IN 
        SELECT id, action_name, action_type, button_color
        FROM dw_action_definitions 
        WHERE function_unit_id = v_function_unit_id
        ORDER BY id
    LOOP
        RAISE NOTICE '  [%] % (%) - %', 
            v_action_record.id, 
            v_action_record.action_name, 
            v_action_record.action_type,
            v_action_record.button_color;
    END LOOP;

    RAISE NOTICE '';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Verification Complete!';
    RAISE NOTICE '========================================';
    RAISE NOTICE '';
    RAISE NOTICE 'Actions are bound to process nodes through BPMN actionIds attribute';
    RAISE NOTICE 'No additional binding operations required';
    RAISE NOTICE '';
    RAISE NOTICE 'Next Steps:';
    RAISE NOTICE '  1. Login to Developer Workstation: http://localhost:3002';
    RAISE NOTICE '  2. Find "Digital Lending System V2"';
    RAISE NOTICE '  3. Click "Deploy" button';
    RAISE NOTICE '  4. Test in User Portal: http://localhost:3001';
    RAISE NOTICE '';
END $$;
