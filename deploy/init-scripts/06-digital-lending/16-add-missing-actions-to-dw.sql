-- =====================================================
-- Add Missing Actions to dw_action_definitions
-- Date: 2026-02-06
-- Purpose: Add all missing actions that are referenced in BPMN
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
    
    -- Add missing actions
    -- These actions are referenced in BPMN but don't exist in dw_action_definitions
    
    -- Mark as Low Risk
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description, config_json, icon, button_color
    ) VALUES (
        v_function_unit_id, 'Mark as Low Risk', 'APPROVE', 'Mark loan as low risk', '{}'::jsonb, 'check', 'success'
    ) ON CONFLICT (function_unit_id, action_name) DO NOTHING;
    
    -- Mark as High Risk
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description, config_json, icon, button_color
    ) VALUES (
        v_function_unit_id, 'Mark as High Risk', 'REJECT', 'Mark loan as high risk', '{}'::jsonb, 'exclamation-triangle', 'danger'
    ) ON CONFLICT (function_unit_id, action_name) DO NOTHING;
    
    -- Manager Approve
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description, config_json, icon, button_color
    ) VALUES (
        v_function_unit_id, 'Approve', 'APPROVE', 'Approve loan application', '{}'::jsonb, 'check', 'success'
    ) ON CONFLICT (function_unit_id, action_name) DO NOTHING;
    
    -- Manager Reject
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description, config_json, icon, button_color
    ) VALUES (
        v_function_unit_id, 'Reject', 'REJECT', 'Reject loan application', '{}'::jsonb, 'times', 'danger'
    ) ON CONFLICT (function_unit_id, action_name) DO NOTHING;
    
    -- Request Revision
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description, config_json, icon, button_color
    ) VALUES (
        v_function_unit_id, 'Request Revision', 'FORM_POPUP', 'Request revisions to application', '{"formId": 12}'::jsonb, 'edit', 'warning'
    ) ON CONFLICT (function_unit_id, action_name) DO NOTHING;
    
    -- Final Approve
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description, config_json, icon, button_color
    ) VALUES (
        v_function_unit_id, 'Final Approve', 'APPROVE', 'Give final approval', '{}'::jsonb, 'check-circle', 'success'
    ) ON CONFLICT (function_unit_id, action_name) DO NOTHING;
    
    -- Final Reject
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description, config_json, icon, button_color
    ) VALUES (
        v_function_unit_id, 'Final Reject', 'REJECT', 'Give final rejection', '{}'::jsonb, 'times-circle', 'danger'
    ) ON CONFLICT (function_unit_id, action_name) DO NOTHING;
    
    -- Escalate
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description, config_json, icon, button_color
    ) VALUES (
        v_function_unit_id, 'Escalate', 'FORM_POPUP', 'Escalate to board', '{"formId": 13}'::jsonb, 'arrow-up', 'warning'
    ) ON CONFLICT (function_unit_id, action_name) DO NOTHING;
    
    -- Disburse Loan
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description, config_json, icon, button_color
    ) VALUES (
        v_function_unit_id, 'Disburse Loan', 'APPROVE', 'Process loan disbursement', '{}'::jsonb, 'money-bill', 'success'
    ) ON CONFLICT (function_unit_id, action_name) DO NOTHING;
    
    -- Hold Disbursement
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description, config_json, icon, button_color
    ) VALUES (
        v_function_unit_id, 'Hold Disbursement', 'REJECT', 'Hold disbursement pending review', '{}'::jsonb, 'pause', 'warning'
    ) ON CONFLICT (function_unit_id, action_name) DO NOTHING;
    
    -- Verify Account
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description, config_json, icon, button_color
    ) VALUES (
        v_function_unit_id, 'Verify Account', 'FORM_POPUP', 'Verify beneficiary account', '{"formId": 14}'::jsonb, 'bank', 'info'
    ) ON CONFLICT (function_unit_id, action_name) DO NOTHING;
    
    RAISE NOTICE 'Missing actions added successfully';
END $$;

-- Verify all actions
SELECT 
    id,
    action_name,
    action_type,
    icon,
    button_color
FROM dw_action_definitions
WHERE function_unit_id = 4
ORDER BY id;

-- Count total actions
SELECT COUNT(*) as total_actions
FROM dw_action_definitions
WHERE function_unit_id = 4;
