-- 为请假流程添加完整的动作定义
-- 使用现有的动作类型

-- 删除已存在的动作（如果有）
DELETE FROM dw_action_definitions WHERE id IN (12, 13, 14, 15) AND function_unit_id = 1;

-- 添加提交动作 (使用PROCESS_SUBMIT类型)
INSERT INTO dw_action_definitions (id, action_name, action_type, button_color, config_json, description, icon, is_default, function_unit_id, created_at, updated_at)
VALUES (12, '提交', 'PROCESS_SUBMIT', 'primary', '{"requireComment": false, "confirmMessage": "确定要提交申请吗？"}', '提交请假申请', 'upload', true, 1, NOW(), NOW());

-- 添加撤回动作
INSERT INTO dw_action_definitions (id, action_name, action_type, button_color, config_json, description, icon, is_default, function_unit_id, created_at, updated_at)
VALUES (13, '撤回', 'WITHDRAW', 'warning', '{"targetStatus": "CANCELLED", "allowedFromStatus": ["PENDING", "IN_PROGRESS"]}', '撤回已提交的申请', 'refresh-left', true, 1, NOW(), NOW());

-- 添加驳回动作
INSERT INTO dw_action_definitions (id, action_name, action_type, button_color, config_json, description, icon, is_default, function_unit_id, created_at, updated_at)
VALUES (14, '驳回', 'ROLLBACK', 'warning', '{"targetStep": "previous", "requireComment": true}', '驳回到上一步', 'back', true, 1, NOW(), NOW());

-- 添加取消动作
INSERT INTO dw_action_definitions (id, action_name, action_type, button_color, config_json, description, icon, is_default, function_unit_id, created_at, updated_at)
VALUES (15, '取消', 'WITHDRAW', 'info', '{"targetStatus": "CANCELLED", "confirmMessage": "确定要取消申请吗？"}', '取消请假申请', 'close', true, 1, NOW(), NOW());

-- 重置序列
SELECT setval('dw_action_definitions_id_seq', (SELECT MAX(id) FROM dw_action_definitions));
