-- 閲囪喘鐢宠 - 涓昏〃瀛楁 (purchase_request)
-- 瀹為檯琛ㄧ粨鏋? table_id, field_name, data_type, length, precision_value, scale, nullable, default_value, is_primary_key, is_unique, description, sort_order

-- 鐢宠缂栧彿
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, is_unique, description, sort_order)
SELECT t.id, 'request_no', 'VARCHAR', 50, false, true, '鐢宠缂栧彿', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 鐢宠鏍囬
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'title', 'VARCHAR', 200, false, '鐢宠鏍囬', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 鐢宠浜?
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'applicant', 'VARCHAR', 50, false, '鐢宠浜?, 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 鐢宠閮ㄩ棬
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'department', 'VARCHAR', 100, false, '鐢宠閮ㄩ棬', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 鐢宠鏃ユ湡
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'apply_date', 'DATE', false, '鐢宠鏃ユ湡', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 閲囪喘绫诲瀷
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'purchase_type', 'VARCHAR', 50, false, '閲囪喘绫诲瀷', 6
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 绱ф€ョ▼搴?
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'urgency', 'VARCHAR', 20, false, '绱ф€ョ▼搴?, 7
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 棰勮鎬婚噾棰?
INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, nullable, description, sort_order)
SELECT t.id, 'total_amount', 'DECIMAL', 18, 2, false, '棰勮鎬婚噾棰?, 8
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 甯佺
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, default_value, description, sort_order)
SELECT t.id, 'currency', 'VARCHAR', 10, 'CNY', '甯佺', 9
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 閲囪喘鍘熷洜
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'reason', 'TEXT', 2000, false, '閲囪喘鍘熷洜', 10
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 鏈熸湜浜や粯鏃ユ湡
INSERT INTO dw_field_definitions (table_id, field_name, data_type, description, sort_order)
SELECT t.id, 'expected_delivery_date', 'DATE', '鏈熸湜浜や粯鏃ユ湡', 11
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 鏀惰揣鍦板潃
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'delivery_address', 'VARCHAR', 500, '鏀惰揣鍦板潃', 12
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 鑱旂郴浜?
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'contact_person', 'VARCHAR', 50, '鑱旂郴浜?, 13
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 鑱旂郴鐢佃瘽
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'contact_phone', 'VARCHAR', 20, '鑱旂郴鐢佃瘽', 14
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 闄勪欢
INSERT INTO dw_field_definitions (table_id, field_name, data_type, description, sort_order)
SELECT t.id, 'attachments', 'TEXT', '闄勪欢', 15
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 澶囨敞
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'remarks', 'TEXT', 1000, '澶囨敞', 16
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 鐘舵€?
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, default_value, description, sort_order)
SELECT t.id, 'status', 'VARCHAR', 20, 'DRAFT', '鐘舵€?, 17
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';