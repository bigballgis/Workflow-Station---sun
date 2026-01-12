-- 动作定义 (Action Definitions)
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default)
SELECT f.id, '提交申请', 'PROCESS_SUBMIT', '{"targetStatus":"PENDING","validation":true}'::jsonb, 'approval-check', 'primary', '提交请假申请', true
FROM dw_function_units f WHERE f.name = '请假申请';

INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default)
SELECT f.id, '同意', 'APPROVE', '{"requireComment":false,"targetStatus":"APPROVED"}'::jsonb, 'approval-check', 'success', '审批通过', false
FROM dw_function_units f WHERE f.name = '请假申请';

INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default)
SELECT f.id, '拒绝', 'REJECT', '{"requireComment":true,"targetStatus":"REJECTED"}'::jsonb, 'compliance', 'danger', '审批拒绝', false
FROM dw_function_units f WHERE f.name = '请假申请';

INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default)
SELECT f.id, '转办', 'TRANSFER', '{"formRef":"转办表单","requireComment":true}'::jsonb, 'user', 'warning', '转办给其他人', false
FROM dw_function_units f WHERE f.name = '请假申请';

INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default)
SELECT f.id, '回退', 'ROLLBACK', '{"targetNode":"start","requireComment":true}'::jsonb, 'folder', 'default', '回退到申请人', false
FROM dw_function_units f WHERE f.name = '请假申请';

INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default)
SELECT f.id, '撤回', 'WITHDRAW', '{"allowedStatus":["PENDING"],"targetStatus":"DRAFT"}'::jsonb, 'operation', 'default', '撤回申请', false
FROM dw_function_units f WHERE f.name = '请假申请';

INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default)
SELECT f.id, '查看假期余额', 'API_CALL', '{"method":"GET","url":"/api/leave/balance/{applicant_id}"}'::jsonb, 'account', 'info', '查询假期余额', false
FROM dw_function_units f WHERE f.name = '请假申请';

INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default)
SELECT f.id, '计算天数', 'SCRIPT', '{"script":"calculateLeaveDays","inputs":["start_date","end_date"],"output":"total_days"}'::jsonb, 'settings', 'default', '自动计算请假天数', false
FROM dw_function_units f WHERE f.name = '请假申请';
