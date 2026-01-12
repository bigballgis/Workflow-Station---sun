-- 表单定义 (Form Definitions)
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, config_json, description, bound_table_id)
SELECT f.id, '请假申请表单', 'MAIN', '{"layout":"vertical","labelWidth":120}'::jsonb, '请假申请主表单', t.id
FROM dw_function_units f JOIN dw_table_definitions t ON t.function_unit_id = f.id AND t.table_name = 'leave_request'
WHERE f.name = '请假申请';

INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, config_json, description, bound_table_id)
SELECT f.id, '请假明细表单', 'SUB', '{"layout":"table","editable":true}'::jsonb, '请假明细子表单', t.id
FROM dw_function_units f JOIN dw_table_definitions t ON t.function_unit_id = f.id AND t.table_name = 'leave_detail'
WHERE f.name = '请假申请';

INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, config_json, description, bound_table_id)
SELECT f.id, '审批表单', 'POPUP', '{"layout":"vertical","width":500}'::jsonb, '审批弹出表单', t.id
FROM dw_function_units f JOIN dw_table_definitions t ON t.function_unit_id = f.id AND t.table_name = 'approval_record'
WHERE f.name = '请假申请';

INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, config_json, description)
SELECT f.id, '转办表单', 'ACTION', '{"layout":"vertical","width":400}'::jsonb, '转办操作表单'
FROM dw_function_units f WHERE f.name = '请假申请';

-- 表单表绑定 (Form Table Bindings)
INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, sort_order)
SELECT form.id, t.id, 'PRIMARY', 'EDITABLE', 1
FROM dw_form_definitions form JOIN dw_function_units f ON form.function_unit_id = f.id
JOIN dw_table_definitions t ON t.function_unit_id = f.id AND t.table_name = 'leave_request'
WHERE f.name = '请假申请' AND form.form_name = '请假申请表单';

INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order)
SELECT form.id, t.id, 'SUB', 'EDITABLE', 'request_id', 2
FROM dw_form_definitions form JOIN dw_function_units f ON form.function_unit_id = f.id
JOIN dw_table_definitions t ON t.function_unit_id = f.id AND t.table_name = 'leave_detail'
WHERE f.name = '请假申请' AND form.form_name = '请假申请表单';

INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, sort_order)
SELECT form.id, t.id, 'RELATED', 'READONLY', 3
FROM dw_form_definitions form JOIN dw_function_units f ON form.function_unit_id = f.id
JOIN dw_table_definitions t ON t.function_unit_id = f.id AND t.table_name = 'leave_balance'
WHERE f.name = '请假申请' AND form.form_name = '请假申请表单';
