-- =====================================================
-- Insert Digital Lending Actions to sys_action_definitions
-- Creates action definitions for Digital Lending in production table
-- =====================================================

-- Insert actions for Digital Lending function unit
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
) VALUES
-- Action 1: Verify Documents (APPROVE)
(
    'action-dl-verify-docs',
    'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89',
    'Verify Documents',
    'APPROVE',
    'Verify applicant documents',
    '{}',
    'check-circle',
    'primary',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'system',
    'system'
),
-- Action 2: Approve Loan (APPROVE)
(
    'action-dl-approve-loan',
    'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89',
    'Approve Loan',
    'APPROVE',
    'Approve the loan application',
    '{}',
    'check',
    'success',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'system',
    'system'
),
-- Action 3: Reject Loan (REJECT)
(
    'action-dl-reject-loan',
    'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89',
    'Reject Loan',
    'REJECT',
    'Reject the loan application',
    '{}',
    'times-circle',
    'danger',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'system',
    'system'
),
-- Action 4: Request Additional Info (FORM_POPUP)
(
    'action-dl-request-info',
    'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89',
    'Request Additional Info',
    'FORM_POPUP',
    'Request additional information from applicant',
    '{"formId": 7}',
    'file-alt',
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
