# 采购申请功能单元 - 生成SQL文件 Part 2

# 4. 子表/关联表/动作表字段
$sql4 = @"
-- 采购申请 - 子表/关联表/动作表字段

-- ========== 采购明细子表字段 ==========
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, is_required, max_length, sort_order, created_by)
SELECT t.id, 'item_name', '物品名称', 'STRING', true, 200, 1, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, max_length, sort_order, created_by)
SELECT t.id, 'specification', '规格型号', 'STRING', 200, 2, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, max_length, sort_order, created_by)
SELECT t.id, 'unit', '单位', 'STRING', 20, 3, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, is_required, sort_order, created_by)
SELECT t.id, 'quantity', '数量', 'INTEGER', true, 4, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, decimal_places, sort_order, created_by)
SELECT t.id, 'unit_price', '单价', 'DECIMAL', 2, 5, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, decimal_places, sort_order, created_by)
SELECT t.id, 'amount', '金额', 'DECIMAL', 2, 6, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, max_length, sort_order, created_by)
SELECT t.id, 'item_remarks', '备注', 'TEXT', 500, 7, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

-- ========== 供应商信息关联表字段 ==========
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, is_required, max_length, sort_order, created_by)
SELECT t.id, 'supplier_name', '供应商名称', 'STRING', true, 200, 1, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, max_length, sort_order, created_by)
SELECT t.id, 'supplier_code', '供应商编码', 'STRING', 50, 2, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, max_length, sort_order, created_by)
SELECT t.id, 'supplier_contact', '联系人', 'STRING', 50, 3, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, max_length, sort_order, created_by)
SELECT t.id, 'supplier_phone', '联系电话', 'STRING', 20, 4, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, max_length, sort_order, created_by)
SELECT t.id, 'supplier_address', '地址', 'STRING', 500, 5, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

-- ========== 预算信息关联表字段 ==========
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, is_required, max_length, sort_order, created_by)
SELECT t.id, 'budget_code', '预算编码', 'STRING', true, 50, 1, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, max_length, sort_order, created_by)
SELECT t.id, 'budget_name', '预算名称', 'STRING', 200, 2, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, decimal_places, sort_order, created_by)
SELECT t.id, 'budget_amount', '预算金额', 'DECIMAL', 2, 3, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, decimal_places, sort_order, created_by)
SELECT t.id, 'used_amount', '已用金额', 'DECIMAL', 2, 4, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, decimal_places, sort_order, created_by)
SELECT t.id, 'available_amount', '可用金额', 'DECIMAL', 2, 5, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

-- ========== 审批记录动作表字段 ==========
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, is_required, max_length, sort_order, created_by)
SELECT t.id, 'approver', '审批人', 'STRING', true, 50, 1, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, is_required, sort_order, created_by)
SELECT t.id, 'approve_time', '审批时间', 'DATETIME', true, 2, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, is_required, max_length, sort_order, created_by)
SELECT t.id, 'approve_result', '审批结果', 'ENUM', true, 20, 3, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, max_length, sort_order, created_by)
SELECT t.id, 'approve_comment', '审批意见', 'TEXT', 1000, 4, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, max_length, sort_order, created_by)
SELECT t.id, 'task_name', '任务节点', 'STRING', 100, 5, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

-- ========== 会签记录动作表字段 ==========
INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, is_required, max_length, sort_order, created_by)
SELECT t.id, 'signer', '会签人', 'STRING', true, 50, 1, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, max_length, sort_order, created_by)
SELECT t.id, 'signer_dept', '会签部门', 'STRING', 100, 2, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, sort_order, created_by)
SELECT t.id, 'sign_time', '会签时间', 'DATETIME', 3, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, max_length, sort_order, created_by)
SELECT t.id, 'sign_result', '会签结果', 'ENUM', 20, 4, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';

INSERT INTO dw_field_definitions (table_id, field_name, field_label, field_type, max_length, sort_order, created_by)
SELECT t.id, 'sign_comment', '会签意见', 'TEXT', 1000, 5, 'system'
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';
"@
[System.IO.File]::WriteAllText("04-04-fields-sub.sql", $sql4, [System.Text.Encoding]::UTF8)

Write-Host "Generated: 04-04-fields-sub.sql"
