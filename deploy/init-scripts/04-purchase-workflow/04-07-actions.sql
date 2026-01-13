-- 閲囪喘鐢宠 - 鍔ㄤ綔瀹氫箟
-- 瀹為檯琛ㄧ粨鏋? function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default

-- 1. 鎻愪氦娴佺▼
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default)
SELECT f.id, '鎻愪氦鐢宠', 'PROCESS_SUBMIT', '鎻愪氦閲囪喘鐢宠', 'primary', true
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 2. 鍚屾剰
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default)
SELECT f.id, '鍚屾剰', 'APPROVE', '鍚屾剰瀹℃壒', 'success', true
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 3. 鎷掔粷
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default)
SELECT f.id, '鎷掔粷', 'REJECT', '鎷掔粷瀹℃壒', 'danger', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 4. 杞姙
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default)
SELECT f.id, '杞姙', 'TRANSFER', '杞姙缁欏叾浠栦汉澶勭悊', 'warning', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 5. 鍥為€€
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default)
SELECT f.id, '鍥為€€', 'ROLLBACK', '鍥為€€鍒颁笂涓€鑺傜偣', 'warning', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 6. 鎾ゅ洖
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default)
SELECT f.id, '鎾ゅ洖', 'WITHDRAW', '鎾ゅ洖宸叉彁浜ょ殑鐢宠', 'default', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 7. API璋冪敤 - 鏌ヨ棰勭畻
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default, config_json)
SELECT f.id, '鏌ヨ棰勭畻', 'API_CALL', '璋冪敤API鏌ヨ棰勭畻淇℃伅', 'info', false, '{"url":"/api/budget/query","method":"GET"}'::jsonb
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 8. 鑴氭湰鎵ц - 璁＄畻閲戦
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default, config_json)
SELECT f.id, '璁＄畻閲戦', 'SCRIPT', '鎵ц鑴氭湰璁＄畻鎬婚噾棰?, 'default', false, '{"script":"calculateTotal"}'::jsonb
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 9. 淇濆瓨鑽夌
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default, config_json)
SELECT f.id, '淇濆瓨鑽夌', 'SCRIPT', '淇濆瓨涓鸿崏绋?, 'default', false, '{"script":"saveDraft"}'::jsonb
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 10. 鍙戣捣浼氱
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default, config_json)
SELECT f.id, '鍙戣捣浼氱', 'API_CALL', '鍙戣捣澶氶儴闂ㄤ細绛?, 'info', false, '{"url":"/api/countersign/start","method":"POST"}'::jsonb
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';