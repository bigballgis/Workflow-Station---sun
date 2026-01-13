# 采购申请功能单元 - 生成表单和动作SQL文件

$outputDir = $PSScriptRoot

# 表单SQL
$formsSql = @"
-- Purchase Request - Form Definitions

-- Main Form
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description)
SELECT f.id, 'Purchase Request Main Form', 'MAIN', 'Main form for purchase request'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- Sub Form: Purchase Items
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description)
SELECT f.id, 'Purchase Items Form', 'SUB', 'Sub form for purchase items'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- Action Form: Approval
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description)
SELECT f.id, 'Approval Form', 'ACTION', 'Approval action form'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- Popup Form: Supplier Selection
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description)
SELECT f.id, 'Supplier Selection', 'POPUP', 'Supplier selection popup form'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- Popup Form: Budget Query
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description)
SELECT f.id, 'Budget Query', 'POPUP', 'Budget query popup form'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- Action Form: Countersign
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description)
SELECT f.id, 'Countersign Form', 'ACTION', 'Countersign action form'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';
"@

[System.IO.File]::WriteAllText("$outputDir\04-06-forms.sql", $formsSql, [System.Text.Encoding]::UTF8)
Write-Host "Generated 04-06-forms.sql"

# 动作SQL
$actionsSql = @"
-- Purchase Request - Action Definitions

-- 1. Submit (PROCESS_SUBMIT)
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default)
SELECT f.id, 'Submit', 'PROCESS_SUBMIT', 'Submit purchase request', 'primary', true
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 2. Approve (APPROVE)
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default)
SELECT f.id, 'Approve', 'APPROVE', 'Approve the request', 'success', true
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 3. Reject (REJECT)
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default)
SELECT f.id, 'Reject', 'REJECT', 'Reject the request', 'danger', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 4. Transfer (TRANSFER)
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default)
SELECT f.id, 'Transfer', 'TRANSFER', 'Transfer to another person', 'warning', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 5. Rollback (ROLLBACK)
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default)
SELECT f.id, 'Rollback', 'ROLLBACK', 'Rollback to previous step', 'warning', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 6. Withdraw (WITHDRAW)
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default)
SELECT f.id, 'Withdraw', 'WITHDRAW', 'Withdraw submitted request', 'default', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 7. Query Budget (API_CALL)
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default, config_json)
SELECT f.id, 'Query Budget', 'API_CALL', 'Query budget information', 'info', false, '{"url":"/api/budget/query","method":"GET"}'::jsonb
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 8. Calculate Amount (SCRIPT)
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default, config_json)
SELECT f.id, 'Calculate Amount', 'SCRIPT', 'Calculate total amount', 'default', false, '{"script":"calculateTotal"}'::jsonb
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 9. Save Draft (SCRIPT)
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default, config_json)
SELECT f.id, 'Save Draft', 'SCRIPT', 'Save as draft', 'default', false, '{"script":"saveDraft"}'::jsonb
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 10. Start Countersign (API_CALL)
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default, config_json)
SELECT f.id, 'Start Countersign', 'API_CALL', 'Start multi-department countersign', 'info', false, '{"url":"/api/countersign/start","method":"POST"}'::jsonb
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';
"@

[System.IO.File]::WriteAllText("$outputDir\04-07-actions.sql", $actionsSql, [System.Text.Encoding]::UTF8)
Write-Host "Generated 04-07-actions.sql"

Write-Host "Form and Action SQL files generated successfully!"
