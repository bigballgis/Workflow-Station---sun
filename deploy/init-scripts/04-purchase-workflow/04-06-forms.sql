-- 閲囪喘鐢宠 - 琛ㄥ崟瀹氫箟
-- 瀹為檯琛ㄧ粨鏋? function_unit_id, form_name, form_type, config_json, description, bound_table_id

-- 涓昏〃鍗? 閲囪喘鐢宠琛ㄥ崟
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description)
SELECT f.id, '閲囪喘鐢宠涓昏〃鍗?, 'MAIN', '閲囪喘鐢宠鐨勪富琛ㄥ崟锛屽寘鍚敵璇峰熀鏈俊鎭?
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 瀛愯〃鍗? 閲囪喘鏄庣粏琛ㄥ崟
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description)
SELECT f.id, '閲囪喘鏄庣粏琛ㄥ崟', 'SUB', '閲囪喘鐗╁搧鏄庣粏瀛愯〃鍗?
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 鍔ㄤ綔琛ㄥ崟: 瀹℃壒琛ㄥ崟
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description)
SELECT f.id, '瀹℃壒琛ㄥ崟', 'ACTION', '瀹℃壒鎿嶄綔琛ㄥ崟'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 寮瑰嚭琛ㄥ崟: 渚涘簲鍟嗛€夋嫨
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description)
SELECT f.id, '渚涘簲鍟嗛€夋嫨', 'POPUP', '渚涘簲鍟嗛€夋嫨寮瑰嚭琛ㄥ崟'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 寮瑰嚭琛ㄥ崟: 棰勭畻鏌ヨ
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description)
SELECT f.id, '棰勭畻鏌ヨ', 'POPUP', '棰勭畻淇℃伅鏌ヨ寮瑰嚭琛ㄥ崟'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 鍔ㄤ綔琛ㄥ崟: 浼氱琛ㄥ崟
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description)
SELECT f.id, '浼氱琛ㄥ崟', 'ACTION', '浼氱鎿嶄綔琛ㄥ崟'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';