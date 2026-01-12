-- 外键关系 (Foreign Keys)
INSERT INTO dw_foreign_keys (table_id, field_id, ref_table_id, ref_field_id, on_delete, on_update)
SELECT detail_table.id, detail_field.id, main_table.id, main_field.id, 'CASCADE', 'CASCADE'
FROM dw_table_definitions detail_table
JOIN dw_function_units f ON detail_table.function_unit_id = f.id
JOIN dw_field_definitions detail_field ON detail_field.table_id = detail_table.id AND detail_field.field_name = 'request_id'
JOIN dw_table_definitions main_table ON main_table.function_unit_id = f.id AND main_table.table_name = 'leave_request'
JOIN dw_field_definitions main_field ON main_field.table_id = main_table.id AND main_field.field_name = 'id'
WHERE f.name = '请假申请' AND detail_table.table_name = 'leave_detail';

INSERT INTO dw_foreign_keys (table_id, field_id, ref_table_id, ref_field_id, on_delete, on_update)
SELECT action_table.id, action_field.id, main_table.id, main_field.id, 'CASCADE', 'CASCADE'
FROM dw_table_definitions action_table
JOIN dw_function_units f ON action_table.function_unit_id = f.id
JOIN dw_field_definitions action_field ON action_field.table_id = action_table.id AND action_field.field_name = 'request_id'
JOIN dw_table_definitions main_table ON main_table.function_unit_id = f.id AND main_table.table_name = 'leave_request'
JOIN dw_field_definitions main_field ON main_field.table_id = main_table.id AND main_field.field_name = 'id'
WHERE f.name = '请假申请' AND action_table.table_name = 'approval_record';
