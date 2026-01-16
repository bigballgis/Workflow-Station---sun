-- Purchase Request - Main Table Fields (purchase_request)

-- Request Number
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, is_unique, description, sort_order)
SELECT t.id, 'request_no', 'VARCHAR', 50, false, true, 'Request Number', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- Request Title
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'title', 'VARCHAR', 200, false, 'Request Title', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- Applicant
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'applicant', 'VARCHAR', 50, false, 'Applicant', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- Department
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'department', 'VARCHAR', 100, false, 'Department', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- Apply Date
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'apply_date', 'DATE', false, 'Apply Date', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- Purchase Type
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'purchase_type', 'VARCHAR', 50, false, 'Purchase Type', 6
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- Urgency Level
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'urgency', 'VARCHAR', 20, false, 'Urgency Level', 7
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- Total Amount
INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, nullable, description, sort_order)
SELECT t.id, 'total_amount', 'DECIMAL', 18, 2, false, 'Total Amount', 8
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- Currency
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, default_value, description, sort_order)
SELECT t.id, 'currency', 'VARCHAR', 10, 'CNY', 'Currency', 9
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- Purchase Reason
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'reason', 'TEXT', 2000, false, 'Purchase Reason', 10
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- Expected Delivery Date
INSERT INTO dw_field_definitions (table_id, field_name, data_type, description, sort_order)
SELECT t.id, 'expected_delivery_date', 'DATE', 'Expected Delivery Date', 11
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- Delivery Address
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'delivery_address', 'VARCHAR', 500, 'Delivery Address', 12
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- Contact Person
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'contact_person', 'VARCHAR', 50, 'Contact Person', 13
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- Contact Phone
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'contact_phone', 'VARCHAR', 20, 'Contact Phone', 14
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- Attachments
INSERT INTO dw_field_definitions (table_id, field_name, data_type, description, sort_order)
SELECT t.id, 'attachments', 'TEXT', 'Attachments', 15
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- Remarks
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'remarks', 'TEXT', 1000, 'Remarks', 16
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- Status
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, default_value, description, sort_order)
SELECT t.id, 'status', 'VARCHAR', 20, 'DRAFT', 'Status', 17
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';