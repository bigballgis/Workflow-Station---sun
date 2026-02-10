DELETE FROM dw_action_definitions WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'PURCHASE');

INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default)
SELECT id, 'submit', 'PROCESS_SUBMIT', 'Submit Purchase Request', '{"processKey":"purchase_approval_process","requireComment":false}'::jsonb, 'send', 'primary', true FROM dw_function_units WHERE code = 'PURCHASE';

INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default)
SELECT id, 'save_draft', 'SAVE', 'Save as Draft', '{"requireComment":false}'::jsonb, 'save', 'default', false FROM dw_function_units WHERE code = 'PURCHASE';

INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default)
SELECT id, 'dept_approve', 'APPROVE', 'Department Manager Approve', '{"targetStatus":"DEPT_APPROVED","requireComment":true}'::jsonb, 'check', 'success', false FROM dw_function_units WHERE code = 'PURCHASE';

INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default)
SELECT id, 'dept_reject', 'REJECT', 'Department Manager Reject', '{"targetStatus":"REJECTED","requireComment":true}'::jsonb, 'close', 'danger', false FROM dw_function_units WHERE code = 'PURCHASE';

INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default)
SELECT id, 'finance_approve', 'APPROVE', 'Finance Approve', '{"targetStatus":"APPROVED","requireComment":true}'::jsonb, 'check-circle', 'success', false FROM dw_function_units WHERE code = 'PURCHASE';

INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default)
SELECT id, 'finance_reject', 'REJECT', 'Finance Reject', '{"targetStatus":"REJECTED","requireComment":true}'::jsonb, 'close-circle', 'danger', false FROM dw_function_units WHERE code = 'PURCHASE';

INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default)
SELECT id, 'withdraw', 'CANCEL', 'Withdraw Request', '{"targetStatus":"WITHDRAWN","requireComment":true}'::jsonb, 'rollback', 'warning', false FROM dw_function_units WHERE code = 'PURCHASE';

INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default)
SELECT id, 'print', 'EXPORT', 'Print Purchase Order', '{"exportType":"PDF"}'::jsonb, 'printer', 'default', false FROM dw_function_units WHERE code = 'PURCHASE';

INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default)
SELECT id, 'export', 'EXPORT', 'Export to Excel', '{"exportType":"EXCEL"}'::jsonb, 'download', 'default', false FROM dw_function_units WHERE code = 'PURCHASE';

SELECT a.id, a.action_name, a.action_type, a.icon, a.button_color FROM dw_action_definitions a JOIN dw_function_units fu ON a.function_unit_id = fu.id WHERE fu.code = 'PURCHASE' ORDER BY a.id;
