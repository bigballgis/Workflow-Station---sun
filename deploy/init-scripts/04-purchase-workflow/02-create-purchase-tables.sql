-- =====================================================
-- 采购工作流 - 数据表定义
-- =====================================================
-- 此脚本为采购工作流创建业务数据表定义
-- 这些表定义将在 Developer Workstation 中使用

-- 前置条件：需要先创建功能单元 (function_unit)
-- 参考：01-create-purchase-function-unit.sql

-- 注意：dw_table_definitions 使用 bigint 类型的 id (自增序列)
--       function_unit_id 也是 bigint 类型，需要先查询功能单元的 id

-- =====================================================
-- 采购申请主表 (Purchase Request)
-- =====================================================
INSERT INTO dw_table_definitions (
    function_unit_id, 
    table_name, 
    table_type, 
    description, 
    created_at, 
    updated_at
) 
SELECT 
    fu.id,
    'purchase_request',
    'MAIN',
    '采购申请主表，记录采购申请的基本信息',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM dw_function_units fu
WHERE fu.code = 'PURCHASE'
ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 采购明细表 (Purchase Items)
-- =====================================================
INSERT INTO dw_table_definitions (
    function_unit_id, 
    table_name, 
    table_type, 
    description, 
    created_at, 
    updated_at
) 
SELECT 
    fu.id,
    'purchase_items',
    'SUB',
    '采购申请明细表，记录每个采购项目的详细信息',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM dw_function_units fu
WHERE fu.code = 'PURCHASE'
ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 供应商信息表 (Suppliers)
-- =====================================================
INSERT INTO dw_table_definitions (
    function_unit_id, 
    table_name, 
    table_type, 
    description, 
    created_at, 
    updated_at
) 
SELECT 
    fu.id,
    'suppliers',
    'RELATION',
    '供应商基础信息表，记录供应商的详细资料',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM dw_function_units fu
WHERE fu.code = 'PURCHASE'
ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 采购审批记录表 (Purchase Approvals)
-- =====================================================
INSERT INTO dw_table_definitions (
    function_unit_id, 
    table_name, 
    table_type, 
    description, 
    created_at, 
    updated_at
) 
SELECT 
    fu.id,
    'purchase_approvals',
    'ACTION',
    '采购审批历史记录表，记录每次审批的详细信息',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM dw_function_units fu
WHERE fu.code = 'PURCHASE'
ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 验证插入结果
-- =====================================================
-- 查询刚插入的表定义
-- SELECT td.id, td.table_name, td.table_type, td.description, fu.name as function_unit_name
-- FROM dw_table_definitions td
-- JOIN dw_function_units fu ON td.function_unit_id = fu.id
-- WHERE fu.code = 'PURCHASE'
-- ORDER BY td.created_at;
