-- =====================================================
-- Sample Function Unit: 请假申请 (Leave Request)
-- 覆盖所有低代码功能：功能单元、流程、表、字段、外键、表单、动作、版本
-- =====================================================

-- =====================================================
-- 1. 功能单元 (Function Unit)
-- =====================================================
INSERT INTO dw_function_units (name, description, icon_id, status, current_version, created_by)
VALUES (
    '请假申请',
    '员工请假申请流程，支持年假、病假、事假等多种假期类型，包含审批流程',
    (SELECT id FROM dw_icons WHERE name = 'approval-check'),
    'DRAFT',
    NULL,
    'system'
);

-- =====================================================
-- 2. 表定义 (Table Definitions)
-- =====================================================

-- 2.1 主表：请假申请单
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_type, description)
SELECT id, 'leave_request', 'MAIN', '请假申请主表，存储请假基本信息'
FROM dw_function_units WHERE name = '请假申请';

-- 2.2 子表：请假明细（支持跨天分段请假）
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_type, description)
SELECT id, 'leave_detail', 'SUB', '请假明细子表，支持分段请假'
FROM dw_function_units WHERE name = '请假申请';

-- 2.3 关联表：假期余额
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_type, description)
SELECT id, 'leave_balance', 'RELATION', '假期余额关联表，查询员工剩余假期'
FROM dw_function_units WHERE name = '请假申请';

-- 2.4 动作表：审批记录
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_type, description)
SELECT id, 'approval_record', 'ACTION', '审批记录表，记录每次审批操作'
FROM dw_function_units WHERE name = '请假申请';

-- =====================================================
-- 3. 字段定义 (Field Definitions)
-- =====================================================

-- 3.1 主表字段：leave_request
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, is_primary_key, is_unique, description, sort_order)
SELECT t.id, 'id', 'BIGINT', NULL, false, true, true, '主键ID', 1
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
SELECT t.id, 'leave_type', 'VARCHAR', 20, false, '请假类型：ANNUAL/SICK/PERSONAL/MARRIAGE/MATERNITY', 6
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_request';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'start_date', 'DATE', NULL, false, '开始日期', 7
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_request';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'end_date', 'DATE', NULL, false, '结束日期', 8
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_request';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, precision_value, scale, nullable, description, sort_order)
SELECT t.id, 'total_days', 'DECIMAL', NULL, 5, 1, false, '请假总天数', 9
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_request';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'reason', 'TEXT', false, '请假原因', 10
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.name = '请假申请' AND t.table_name = 'leave_request';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, default_value, description, sort_order)
SELECT t.id, 'status', 'VARCHAR', 20, false, 'DRAFT', '状态：DRAFT/PENDING/APPROVED/REJECTED/CANCELLED', 11
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
