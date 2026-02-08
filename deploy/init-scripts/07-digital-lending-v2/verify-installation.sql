-- =============================================================================
-- 验证数字贷款系统 V2 的安装
-- =============================================================================

\echo '========================================'
\echo '数字贷款系统 V2 - 安装验证'
\echo '========================================'
\echo ''

-- 1. 检查功能单元
\echo '1. 功能单元检查'
\echo '----------------------------------------'
SELECT 
    id,
    code,
    name,
    status,
    version,
    is_active
FROM dw_function_units 
WHERE code = 'DIGITAL_LENDING_V2';

\echo ''

-- 2. 检查表定义
\echo '2. 表定义检查（应该有 7 个表）'
\echo '----------------------------------------'
SELECT 
    t.id,
    t.table_name,
    t.table_type,
    COUNT(f.id) as field_count
FROM dw_table_definitions t
LEFT JOIN dw_field_definitions f ON f.table_id = t.id
WHERE t.function_unit_id = (
    SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2'
)
GROUP BY t.id, t.table_name, t.table_type
ORDER BY 
    CASE t.table_type 
        WHEN 'MAIN' THEN 1 
        WHEN 'SUB' THEN 2 
        WHEN 'RELATION' THEN 3 
    END,
    t.id;

\echo ''

-- 3. 检查表单定义
\echo '3. 表单定义检查（应该有 5 个表单）'
\echo '----------------------------------------'
SELECT 
    f.id,
    f.form_name,
    f.form_type,
    COUNT(b.id) as table_binding_count
FROM dw_form_definitions f
LEFT JOIN dw_form_table_bindings b ON b.form_id = f.id
WHERE f.function_unit_id = (
    SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2'
)
GROUP BY f.id, f.form_name, f.form_type
ORDER BY f.id;

\echo ''

-- 4. 检查动作定义
\echo '4. 动作定义检查（应该有 15 个动作）'
\echo '----------------------------------------'
SELECT 
    id,
    action_name,
    action_type,
    button_color
FROM dw_action_definitions
WHERE function_unit_id = (
    SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2'
)
ORDER BY id;

\echo ''

-- 5. 检查流程定义
\echo '5. 流程定义检查（应该有 1 个流程）'
\echo '----------------------------------------'
SELECT 
    id,
    function_unit_id,
    LENGTH(bpmn_xml) as xml_length,
    created_at
FROM dw_process_definitions
WHERE function_unit_id = (
    SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2'
);

\echo ''

-- 6. 检查虚拟组
\echo '6. 虚拟组检查（应该有 4 个虚拟组）'
\echo '----------------------------------------'
SELECT 
    id,
    code,
    name,
    type,
    status
FROM sys_virtual_groups
WHERE code IN ('DOCUMENT_VERIFIERS', 'CREDIT_OFFICERS', 'RISK_OFFICERS', 'FINANCE_TEAM')
ORDER BY code;

\echo ''

-- 7. 汇总统计
\echo '7. 安装汇总'
\echo '----------------------------------------'
SELECT 
    '功能单元' as component,
    COUNT(*) as count
FROM dw_function_units 
WHERE code = 'DIGITAL_LENDING_V2'
UNION ALL
SELECT 
    '表定义',
    COUNT(*)
FROM dw_table_definitions
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2')
UNION ALL
SELECT 
    '字段定义',
    COUNT(*)
FROM dw_field_definitions
WHERE table_id IN (
    SELECT id FROM dw_table_definitions 
    WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2')
)
UNION ALL
SELECT 
    '表单定义',
    COUNT(*)
FROM dw_form_definitions
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2')
UNION ALL
SELECT 
    '表单绑定',
    COUNT(*)
FROM dw_form_table_bindings
WHERE form_id IN (
    SELECT id FROM dw_form_definitions 
    WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2')
)
UNION ALL
SELECT 
    '动作定义',
    COUNT(*)
FROM dw_action_definitions
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2')
UNION ALL
SELECT 
    '流程定义',
    COUNT(*)
FROM dw_process_definitions
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2')
UNION ALL
SELECT 
    '虚拟组',
    COUNT(*)
FROM sys_virtual_groups
WHERE code IN ('DOCUMENT_VERIFIERS', 'CREDIT_OFFICERS', 'RISK_OFFICERS', 'FINANCE_TEAM');

\echo ''
\echo '========================================'
\echo '验证完成！'
\echo '========================================'
\echo ''
\echo '预期结果：'
\echo '  - 功能单元: 1'
\echo '  - 表定义: 7'
\echo '  - 字段定义: 约 100+'
\echo '  - 表单定义: 5'
\echo '  - 表单绑定: 约 15+'
\echo '  - 动作定义: 15'
\echo '  - 流程定义: 1'
\echo '  - 虚拟组: 4'
\echo ''
