-- 閲囪喘鐢宠 - 琛ㄥ畾涔?

-- 涓昏〃: 閲囪喘鐢宠
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_label, table_type, description, created_by)
SELECT f.id, 'purchase_request', '閲囪喘鐢宠', 'MAIN', '閲囪喘鐢宠涓昏〃锛屽瓨鍌ㄧ敵璇峰熀鏈俊鎭?, 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 瀛愯〃: 閲囪喘鏄庣粏
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_label, table_type, description, created_by)
SELECT f.id, 'purchase_item', '閲囪喘鏄庣粏', 'SUB', '閲囪喘鐗╁搧鏄庣粏瀛愯〃', 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 鍏宠仈琛? 渚涘簲鍟嗕俊鎭?
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_label, table_type, description, created_by)
SELECT f.id, 'supplier_info', '渚涘簲鍟嗕俊鎭?, 'RELATION', '渚涘簲鍟嗕俊鎭叧鑱旇〃', 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 鍏宠仈琛? 棰勭畻淇℃伅
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_label, table_type, description, created_by)
SELECT f.id, 'budget_info', '棰勭畻淇℃伅', 'RELATION', '棰勭畻淇℃伅鍏宠仈琛?, 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 鍔ㄤ綔琛? 瀹℃壒璁板綍
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_label, table_type, description, created_by)
SELECT f.id, 'purchase_approval', '瀹℃壒璁板綍', 'ACTION', '瀹℃壒鎿嶄綔璁板綍琛?, 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 鍔ㄤ綔琛? 浼氱璁板綍
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_label, table_type, description, created_by)
SELECT f.id, 'countersign_record', '浼氱璁板綍', 'ACTION', '浼氱鎿嶄綔璁板綍琛?, 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';