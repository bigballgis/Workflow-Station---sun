-- =====================================================
-- Sync String ID Actions to dw_action_definitions
-- Date: 2026-02-06
-- Purpose: Copy String ID actions from sys_action_definitions to dw_action_definitions
-- =====================================================

-- Get the function unit ID
DO $$
DECLARE
    v_function_unit_id BIGINT;
BEGIN
    -- Get function unit ID
    SELECT id INTO v_function_unit_id 
    FROM dw_function_units 
    WHERE code = 'DIGITAL_LENDING';
    
    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit DIGITAL_LENDING not found';
    END IF;
    
    RAISE NOTICE 'Function Unit ID: %', v_function_unit_id;
    
    -- Insert all String ID actions from sys_action_definitions to dw_action_definitions
    INSERT INTO dw_action_definitions (
        function_unit_id,
        action_name,
        action_type,
        description,
        action_config,
        icon,
        button_color,
        is_default,
        created_at,
        updated_at,
        created_by,
        updated_by
    )
    SELECT 
        v_function_unit_id,
        action_name,
        action_type,
        description,
        config_json::text,  -- Convert jsonb to text for action_config
        icon,
        button_color,
        is_default,
        created_at,
        updated_at,
        created_by,
        updated_by
    FROM sys_action_definitions
    WHERE function_unit_id = 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89'
    ON CONFLICT DO NOTHING;  -- Skip if already exists
    
    RAISE NOTICE 'Actions synced successfully';
END $$;

-- Verify the sync
SELECT 
    id,
    action_name,
    action_type,
    icon,
    button_color
FROM dw_action_definitions
WHERE function_unit_id = 4
ORDER BY action_name;

-- Count total actions
SELECT COUNT(*) as total_actions
FROM dw_action_definitions
WHERE function_unit_id = 4;
