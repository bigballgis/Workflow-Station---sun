-- Purchase Request - Action Definitions

-- 1. Submit Process
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, description, button_color, is_default, created_at)
SELECT f.id, 'Submit', 'PROCESS_SUBMIT', '{}'::jsonb, 'Submit purchase request', 'primary', true, NOW()
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 2. Approve
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, description, button_color, is_default, created_at)
SELECT f.id, 'Approve', 'APPROVE', '{}'::jsonb, 'Approve request', 'success', true, NOW()
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 3. Reject
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, description, button_color, is_default, created_at)
SELECT f.id, 'Reject', 'REJECT', '{}'::jsonb, 'Reject request', 'danger', false, NOW()
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 4. Transfer
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, description, button_color, is_default, created_at)
SELECT f.id, 'Transfer', 'TRANSFER', '{}'::jsonb, 'Transfer task', 'warning', false, NOW()
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 5. Delegate
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, description, button_color, is_default, created_at)
SELECT f.id, 'Delegate', 'DELEGATE', '{}'::jsonb, 'Delegate task', 'info', false, NOW()
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 6. Rollback
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, description, button_color, is_default, created_at)
SELECT f.id, 'Rollback', 'ROLLBACK', '{}'::jsonb, 'Rollback to previous step', 'warning', false, NOW()
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 7. Withdraw
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, description, button_color, is_default, created_at)
SELECT f.id, 'Withdraw', 'WITHDRAW', '{}'::jsonb, 'Withdraw request', 'danger', false, NOW()
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 8. API Call: Budget Query
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, description, button_color, is_default, created_at)
SELECT f.id, 'Query Budget', 'API_CALL', '{}'::jsonb, 'Query budget information', 'info', false, NOW()
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 9. Form Popup: Supplier Selection
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, description, button_color, is_default, created_at)
SELECT f.id, 'Select Supplier', 'FORM_POPUP', '{}'::jsonb, 'Open supplier selection form', 'primary', false, NOW()
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 10. Custom Script: Calculate Total
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, description, button_color, is_default, created_at)
SELECT f.id, 'Calculate Total', 'CUSTOM_SCRIPT', '{}'::jsonb, 'Calculate total amount', 'info', false, NOW()
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';