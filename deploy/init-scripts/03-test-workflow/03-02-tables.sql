-- 表定义 (Table Definitions)
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_type, description)
SELECT id, 'leave_request', 'MAIN', '请假申请主表' FROM dw_function_units WHERE name = '请假申请';

INSERT INTO dw_table_definitions (function_unit_id, table_name, table_type, description)
SELECT id, 'leave_detail', 'SUB', '请假明细子表' FROM dw_function_units WHERE name = '请假申请';

INSERT INTO dw_table_definitions (function_unit_id, table_name, table_type, description)
SELECT id, 'leave_balance', 'RELATION', '假期余额关联表' FROM dw_function_units WHERE name = '请假申请';

INSERT INTO dw_table_definitions (function_unit_id, table_name, table_type, description)
SELECT id, 'approval_record', 'ACTION', '审批记录表' FROM dw_function_units WHERE name = '请假申请';
