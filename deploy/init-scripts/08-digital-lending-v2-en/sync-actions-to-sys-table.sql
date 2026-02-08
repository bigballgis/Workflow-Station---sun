-- 将 ACTION 内容从 sys_function_unit_contents 同步到 sys_action_definitions 表
-- 这样 User Portal 可以直接查询 action 定义

-- 功能单元 ID
\set function_unit_id '4737ac68-42c5-4571-972e-e7ad0c6c7253'

-- 1. 清理已存在的 action 定义
DELETE FROM sys_action_definitions 
WHERE function_unit_id = :'function_unit_id';

-- 2. 从 sys_function_unit_contents 提取并插入到 sys_action_definitions
INSERT INTO sys_action_definitions (
    id,
    function_unit_id,
    action_name,
    action_type,
    description,
    config_json,
    icon,
    button_color,
    is_default,
    created_at,
    updated_at
)
SELECT 
    source_id::text,
    :'function_unit_id',
    content_data::jsonb->>'actionName',
    content_data::jsonb->>'actionType',
    content_data::jsonb->>'description',
    content_data::jsonb->'config',
    content_data::jsonb->>'icon',
    content_data::jsonb->>'buttonColor',
    (content_data::jsonb->>'isDefault')::boolean,
    created_at,
    CURRENT_TIMESTAMP
FROM sys_function_unit_contents
WHERE function_unit_id = :'function_unit_id'
AND content_type = 'ACTION'
AND source_id IS NOT NULL;

-- 3. 验证结果
SELECT 
    id,
    action_name,
    action_type,
    icon,
    button_color
FROM sys_action_definitions
WHERE function_unit_id = :'function_unit_id'
ORDER BY action_name;
