-- 子表字段：leave_detail
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, is_primary_key, is_unique, description, sort_order)
SELECT t.id, 'id', 'BIGINT', false, true, true, '主键ID', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_detail';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'request_id', 'BIGINT', false, '关联主表ID', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_detail';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'leave_date', 'DATE', false, '请假日期', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_detail';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'time_period', 'VARCHAR', 20, false, '时段', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_detail';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, nullable, description, sort_order)
SELECT t.id, 'hours', 'DECIMAL', 4, 1, false, '请假小时数', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_detail';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'remark', 'VARCHAR', 500, true, '备注', 6
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_detail';

-- 关联表字段：leave_balance
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, is_primary_key, is_unique, description, sort_order)
SELECT t.id, 'id', 'BIGINT', false, true, true, '主键ID', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_balance';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'employee_id', 'VARCHAR', 64, false, '员工ID', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_balance';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'year', 'INTEGER', false, '年度', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_balance';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'leave_type', 'VARCHAR', 20, false, '假期类型', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_balance';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, nullable, description, sort_order)
SELECT t.id, 'total_days', 'DECIMAL', 5, 1, false, '总天数', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_balance';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, nullable, description, sort_order)
SELECT t.id, 'used_days', 'DECIMAL', 5, 1, false, '已用天数', 6
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_balance';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, nullable, description, sort_order)
SELECT t.id, 'remaining_days', 'DECIMAL', 5, 1, false, '剩余天数', 7
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_balance';

-- 动作表字段：approval_record
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, is_primary_key, is_unique, description, sort_order)
SELECT t.id, 'id', 'BIGINT', false, true, true, '主键ID', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'approval_record';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'request_id', 'BIGINT', false, '关联申请单ID', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'approval_record';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'approver_id', 'VARCHAR', 64, false, '审批人ID', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'approval_record';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'approver_name', 'VARCHAR', 100, false, '审批人姓名', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'approval_record';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'action', 'VARCHAR', 20, false, '审批动作', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'approval_record';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'comment', 'VARCHAR', 1000, true, '审批意见', 6
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'approval_record';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'action_time', 'TIMESTAMP', false, '审批时间', 7
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'approval_record';
