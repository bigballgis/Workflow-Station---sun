-- 閲囪喘鐢宠 - 瀛愯〃/鍏宠仈琛?鍔ㄤ綔琛ㄥ瓧娈?

-- ========== 閲囪喘鏄庣粏瀛愯〃瀛楁 (purchase_item) ==========
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'item_name', 'VARCHAR', 200, false, '鐗╁搧鍚嶇О', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'specification', 'VARCHAR', 200, '瑙勬牸鍨嬪彿', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'unit', 'VARCHAR', 20, '鍗曚綅', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'quantity', 'INTEGER', false, '鏁伴噺', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, description, sort_order)
SELECT t.id, 'unit_price', 'DECIMAL', 18, 2, '鍗曚环', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, description, sort_order)
SELECT t.id, 'amount', 'DECIMAL', 18, 2, '閲戦', 6
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'item_remarks', 'TEXT', 500, '澶囨敞', 7
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

-- ========== 渚涘簲鍟嗕俊鎭叧鑱旇〃瀛楁 (supplier_info) ==========
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'supplier_name', 'VARCHAR', 200, false, '渚涘簲鍟嗗悕绉?, 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'supplier_code', 'VARCHAR', 50, '渚涘簲鍟嗙紪鐮?, 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'supplier_contact', 'VARCHAR', 50, '鑱旂郴浜?, 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'supplier_phone', 'VARCHAR', 20, '鑱旂郴鐢佃瘽', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'supplier_address', 'VARCHAR', 500, '鍦板潃', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

-- ========== 棰勭畻淇℃伅鍏宠仈琛ㄥ瓧娈?(budget_info) ==========
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'budget_code', 'VARCHAR', 50, false, '棰勭畻缂栫爜', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'budget_name', 'VARCHAR', 200, '棰勭畻鍚嶇О', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, description, sort_order)
SELECT t.id, 'budget_amount', 'DECIMAL', 18, 2, '棰勭畻閲戦', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, description, sort_order)
SELECT t.id, 'used_amount', 'DECIMAL', 18, 2, '宸茬敤閲戦', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, description, sort_order)
SELECT t.id, 'available_amount', 'DECIMAL', 18, 2, '鍙敤閲戦', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

-- ========== 瀹℃壒璁板綍鍔ㄤ綔琛ㄥ瓧娈?(purchase_approval) ==========
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'approver', 'VARCHAR', 50, false, '瀹℃壒浜?, 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'approve_time', 'TIMESTAMP', false, '瀹℃壒鏃堕棿', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'approve_result', 'VARCHAR', 20, false, '瀹℃壒缁撴灉', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'approve_comment', 'TEXT', 1000, '瀹℃壒鎰忚', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'task_name', 'VARCHAR', 100, '浠诲姟鑺傜偣', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

-- ========== 浼氱璁板綍鍔ㄤ綔琛ㄥ瓧娈?(countersign_record) ==========
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'signer', 'VARCHAR', 50, false, '浼氱浜?, 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'signer_dept', 'VARCHAR', 100, '浼氱閮ㄩ棬', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, description, sort_order)
SELECT t.id, 'sign_time', 'TIMESTAMP', '浼氱鏃堕棿', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'sign_result', 'VARCHAR', 20, '浼氱缁撴灉', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'sign_comment', 'TEXT', 1000, '浼氱鎰忚', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';