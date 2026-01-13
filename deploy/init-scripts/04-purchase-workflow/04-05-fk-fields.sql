-- Purchase Request - Foreign Key Fields

-- purchase_item 娣诲姞 request_id 澶栭敭瀛楁
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'request_id', 'BIGINT', false, 'Reference to purchase_request', 0
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item'
AND NOT EXISTS (SELECT 1 FROM dw_field_definitions fd WHERE fd.table_id = t.id AND fd.field_name = 'request_id');

-- supplier_info 娣诲姞 request_id 澶栭敭瀛楁
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'request_id', 'BIGINT', false, 'Reference to purchase_request', 0
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info'
AND NOT EXISTS (SELECT 1 FROM dw_field_definitions fd WHERE fd.table_id = t.id AND fd.field_name = 'request_id');

-- budget_info 娣诲姞 request_id 澶栭敭瀛楁
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'request_id', 'BIGINT', false, 'Reference to purchase_request', 0
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info'
AND NOT EXISTS (SELECT 1 FROM dw_field_definitions fd WHERE fd.table_id = t.id AND fd.field_name = 'request_id');

-- purchase_approval 娣诲姞 request_id 澶栭敭瀛楁
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'request_id', 'BIGINT', false, 'Reference to purchase_request', 0
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval'
AND NOT EXISTS (SELECT 1 FROM dw_field_definitions fd WHERE fd.table_id = t.id AND fd.field_name = 'request_id');

-- countersign_record 娣诲姞 request_id 澶栭敭瀛楁
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'request_id', 'BIGINT', false, 'Reference to purchase_request', 0
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record'
AND NOT EXISTS (SELECT 1 FROM dw_field_definitions fd WHERE fd.table_id = t.id AND fd.field_name = 'request_id');

-- 涓昏〃娣诲姞 id 涓婚敭瀛楁锛堝鏋滀笉瀛樺湪锛?
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, is_primary_key, description, sort_order)
SELECT t.id, 'id', 'BIGINT', false, true, 'Primary Key', 0
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request'
AND NOT EXISTS (SELECT 1 FROM dw_field_definitions fd WHERE fd.table_id = t.id AND fd.field_name = 'id');