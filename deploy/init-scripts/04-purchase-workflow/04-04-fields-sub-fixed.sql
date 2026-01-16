-- Purchase Request - Sub/Relation/Action Table Fields

-- ========== purchase_item ==========
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'item_name', 'VARCHAR', 200, false, 'Item Name', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'specification', 'VARCHAR', 200, 'Specification', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'unit', 'VARCHAR', 20, 'Unit', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'quantity', 'INTEGER', false, 'Quantity', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, description, sort_order)
SELECT t.id, 'unit_price', 'DECIMAL', 18, 2, 'Unit Price', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, description, sort_order)
SELECT t.id, 'amount', 'DECIMAL', 18, 2, 'Amount', 6
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'item_remarks', 'TEXT', 500, 'Remarks', 7
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

-- ========== supplier_info ==========
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'supplier_name', 'VARCHAR', 200, false, 'Supplier Name', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'supplier_code', 'VARCHAR', 50, 'Supplier Code', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'supplier_contact', 'VARCHAR', 50, 'Contact Person', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'supplier_phone', 'VARCHAR', 20, 'Contact Phone', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'supplier_address', 'VARCHAR', 500, 'Address', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

-- ========== budget_info ==========
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'budget_code', 'VARCHAR', 50, false, 'Budget Code', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'budget_name', 'VARCHAR', 200, 'Budget Name', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, description, sort_order)
SELECT t.id, 'budget_amount', 'DECIMAL', 18, 2, 'Budget Amount', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, description, sort_order)
SELECT t.id, 'used_amount', 'DECIMAL', 18, 2, 'Used Amount', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, description, sort_order)
SELECT t.id, 'available_amount', 'DECIMAL', 18, 2, 'Available Amount', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

-- ========== purchase_approval ==========
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'approver', 'VARCHAR', 50, false, 'Approver', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'approve_time', 'TIMESTAMP', false, 'Approve Time', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'approve_result', 'VARCHAR', 20, false, 'Approve Result', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'approve_comment', 'TEXT', 1000, 'Approve Comment', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'task_name', 'VARCHAR', 100, 'Task Name', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

-- ========== countersign_record ==========
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'signer', 'VARCHAR', 50, false, 'Signer', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'signer_dept', 'VARCHAR', 100, 'Signer Department', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, description, sort_order)
SELECT t.id, 'sign_time', 'TIMESTAMP', 'Sign Time', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'sign_result', 'VARCHAR', 20, 'Sign Result', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'sign_comment', 'TEXT', 1000, 'Sign Comment', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';