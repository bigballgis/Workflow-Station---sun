-- 同步 Action 定义到 Admin Center
-- 将 Developer Workstation 的 action 定义同步到 Admin Center 的 sys_function_unit_contents

-- 功能单元 ID
\set function_unit_id '4737ac68-42c5-4571-972e-e7ad0c6c7253'

-- 1. 清理已存在的 ACTION 内容
DELETE FROM sys_function_unit_contents 
WHERE function_unit_id = :'function_unit_id' 
AND content_type = 'ACTION';

-- 2. 同步所有 action 定义
INSERT INTO sys_function_unit_contents (
    id,
    function_unit_id,
    content_type,
    content_name,
    content_data,
    source_id,
    created_at
)
SELECT 
    gen_random_uuid()::text,
    :'function_unit_id',
    'ACTION',
    action_name,
    jsonb_build_object(
        'actionName', action_name,
        'actionType', action_type,
        'config', config_json,
        'icon', icon,
        'buttonColor', button_color,
        'description', description,
        'isDefault', is_default
    )::text,
    id::text,
    CURRENT_TIMESTAMP
FROM dw_action_definitions
WHERE function_unit_id = 10
ORDER BY id;

-- 3. 验证结果
SELECT 
    content_name,
    LENGTH(content_data) as data_size,
    (content_data::jsonb->>'actionType') as action_type
FROM sys_function_unit_contents 
WHERE function_unit_id = :'function_unit_id' 
AND content_type = 'ACTION'
ORDER BY content_name;
