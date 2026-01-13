# 采购申请功能单元 - 生成所有SQL文件
# 使用UTF-8编码确保中文正确

# 1. 功能单元
$sql1 = @"
-- 采购申请功能单元
INSERT INTO dw_function_units (code, name, description, icon_id, status, current_version, created_by)
VALUES (
    'fu-purchase-request',
    '采购申请',
    '采购申请流程，支持多级审批、金额分级、部门会签等功能，覆盖所有7种任务分配类型和8种动作类型',
    (SELECT id FROM dw_icons WHERE name = 'credit-card'),
    'DRAFT',
    NULL,
    'system'
);
"@
[System.IO.File]::WriteAllText("04-01-function-unit.sql", $sql1, [System.Text.Encoding]::UTF8)

# 2. 表定义
$sql2 = @"
-- 采购申请 - 表定义

-- 主表: 采购申请
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_label, table_type, description, created_by)
SELECT f.id, 'purchase_request', '采购申请', 'MAIN', '采购申请主表，存储申请基本信息', 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 子表: 采购明细
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_label, table_type, description, created_by)
SELECT f.id, 'purchase_item', '采购明细', 'SUB', '采购物品明细子表', 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 关联表: 供应商信息
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_label, table_type, description, created_by)
SELECT f.id, 'supplier_info', '供应商信息', 'RELATION', '供应商信息关联表', 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 关联表: 预算信息
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_label, table_type, description, created_by)
SELECT f.id, 'budget_info', '预算信息', 'RELATION', '预算信息关联表', 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 动作表: 审批记录
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_label, table_type, description, created_by)
SELECT f.id, 'purchase_approval', '审批记录', 'ACTION', '审批操作记录表', 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 动作表: 会签记录
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_label, table_type, description, created_by)
SELECT f.id, 'countersign_record', '会签记录', 'ACTION', '会签操作记录表', 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';
"@
[System.IO.File]::WriteAllText("04-02-tables.sql", $sql2, [System.Text.Encoding]::UTF8)

# 3. 主表字段
$sql3 = @"
-- 采购申请 - 主表字段

-- 申请编号
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, is_required, is_unique, max_length, sort_order, created_by)
SELECT t.id, 'request_no', '申请编号', 'STRING', true, true, 50, 1, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 申请标题
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, is_required, max_length, sort_order, created_by)
SELECT t.id, 'title', '申请标题', 'STRING', true, 200, 2, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 申请人
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, is_required, max_length, sort_order, created_by)
SELECT t.id, 'applicant', '申请人', 'STRING', true, 50, 3, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 申请部门
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, is_required, max_length, sort_order, created_by)
SELECT t.id, 'department', '申请部门', 'STRING', true, 100, 4, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 申请日期
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, is_required, sort_order, created_by)
SELECT t.id, 'apply_date', '申请日期', 'DATE', true, 5, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 采购类型
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, is_required, max_length, sort_order, created_by)
SELECT t.id, 'purchase_type', '采购类型', 'ENUM', true, 50, 6, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 紧急程度
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, is_required, max_length, sort_order, created_by)
SELECT t.id, 'urgency', '紧急程度', 'ENUM', true, 20, 7, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 预计总金额
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, is_required, decimal_places, sort_order, created_by)
SELECT t.id, 'total_amount', '预计总金额', 'DECIMAL', true, 2, 8, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 币种
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, default_value, max_length, sort_order, created_by)
SELECT t.id, 'currency', '币种', 'STRING', 'CNY', 10, 9, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 采购原因
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, is_required, max_length, sort_order, created_by)
SELECT t.id, 'reason', '采购原因', 'TEXT', true, 2000, 10, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 期望交付日期
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, sort_order, created_by)
SELECT t.id, 'expected_delivery_date', '期望交付日期', 'DATE', 11, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 收货地址
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, max_length, sort_order, created_by)
SELECT t.id, 'delivery_address', '收货地址', 'STRING', 500, 12, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 联系人
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, max_length, sort_order, created_by)
SELECT t.id, 'contact_person', '联系人', 'STRING', 50, 13, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 联系电话
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, max_length, sort_order, created_by)
SELECT t.id, 'contact_phone', '联系电话', 'STRING', 20, 14, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 附件
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, sort_order, created_by)
SELECT t.id, 'attachments', '附件', 'FILE', 15, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 备注
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, max_length, sort_order, created_by)
SELECT t.id, 'remarks', '备注', 'TEXT', 1000, 16, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 状态
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, default_value, max_length, sort_order, created_by)
SELECT t.id, 'status', '状态', 'ENUM', 'DRAFT', 20, 17, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';
"@
[System.IO.File]::WriteAllText("04-03-fields-main.sql", $sql3, [System.Text.Encoding]::UTF8)

Write-Host "Generated: 04-01-function-unit.sql, 04-02-tables.sql, 04-03-fields-main.sql"
