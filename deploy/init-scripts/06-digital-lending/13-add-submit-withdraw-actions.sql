-- =====================================================
-- Add Submit and Withdraw Actions with String IDs
-- Date: 2026-02-06
-- Purpose: Complete the migration to String action IDs
-- =====================================================

-- Insert Submit Loan Application action
INSERT INTO sys_action_definitions (
    id,
    function_unit_id,
    action_name,
    action_type,
    description,
    config_json,
    icon,
    button_color,
    is_default,
    created_at,
    updated_at,
    created_by,
    updated_by
) VALUES (
    'action-dl-submit-application',
    'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89',
    'Submit Loan Application',
    'PROCESS_SUBMIT',
    'Submit a new loan application for processing',
    '{
        "requireComment": false,
        "confirmMessage": "Submit this loan application?",
        "successMessage": "Loan application submitted successfully"
    }'::jsonb,
    'send',
    'primary',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'system',
    'system'
)
ON CONFLICT (id) DO UPDATE SET
    action_name = EXCLUDED.action_name,
    action_type = EXCLUDED.action_type,
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    icon = EXCLUDED.icon,
    button_color = EXCLUDED.button_color,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'system';

-- Insert Withdraw Application action
INSERT INTO sys_action_definitions (
    id,
    function_unit_id,
    action_name,
    action_type,
    description,
    config_json,
    icon,
    button_color,
    is_default,
    created_at,
    updated_at,
    created_by,
    updated_by
) VALUES (
    'action-dl-withdraw-application',
    'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89',
    'Withdraw Application',
    'WITHDRAW',
    'Withdraw the loan application',
    '{
        "targetStatus": "WITHDRAWN",
        "requireComment": true,
        "requireReason": true,
        "allowedFromStatus": ["SUBMITTED", "IN_REVIEW", "INFO_REQUIRED"],
        "confirmMessage": "Withdraw this loan application?",
        "successMessage": "Loan application withdrawn"
    }'::jsonb,
    'undo',
    'warning',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'system',
    'system'
)
ON CONFLICT (id) DO UPDATE SET
    action_name = EXCLUDED.action_name,
    action_type = EXCLUDED.action_type,
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    icon = EXCLUDED.icon,
    button_color = EXCLUDED.button_color,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'system';

-- Verify the insert
SELECT 
    id,
    action_name,
    action_type,
    icon,
    button_color
FROM sys_action_definitions
WHERE function_unit_id = 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89'
ORDER BY action_name;

-- Expected: 21 rows (19 existing + 2 new)
SELECT COUNT(*) as total_actions
FROM sys_action_definitions
WHERE function_unit_id = 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89';
