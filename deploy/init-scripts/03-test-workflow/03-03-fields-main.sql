-- 主表字段：leave_request
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, is_primary_key, is_unique, description, sort_order)
SELECT t.id, 'id', 'BIGINT', false, true, true, '主键ID', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_request';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'request_no', 'VARCHAR', 50, false, '申请单号', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_request';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'applicant_id', 'VARCHAR', 64, false, '申请人ID', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_request';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'applicant_name', 'VARCHAR', 100, false, '申请人姓名', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_request';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'department_id', 'VARCHAR', 64, false, '部门ID', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_request';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'leave_type', 'VARCHAR', 20, false, '请假类型', 6
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_request';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'start_date', 'DATE', false, '开始日期', 7
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_request';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'end_date', 'DATE', false, '结束日期', 8
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_request';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, nullable, description, sort_order)
SELECT t.id, 'total_days', 'DECIMAL', 5, 1, false, '请假总天数', 9
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_request';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'reason', 'TEXT', false, '请假原因', 10
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_request';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, default_value, description, sort_order)
SELECT t.id, 'status', 'VARCHAR', 20, false, 'DRAFT', '状态', 11
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_request';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'created_at', 'TIMESTAMP', false, '创建时间', 12
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_request';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'updated_at', 'TIMESTAMP', true, '更新时间', 13
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_request';
