-- =====================================================
-- 采购工作流 - 功能单元定义
-- =====================================================
-- 此脚本创建采购管理功能单元
-- 功能单元是 Developer Workstation 中的顶层组织单位

-- =====================================================
-- 创建采购功能单元
-- =====================================================
INSERT INTO dw_function_units (
    code,
    name,
    description,
    status,
    created_by,
    created_at,
    updated_at
) VALUES (
    'PURCHASE',
    '采购管理',
    '采购申请、审批和管理流程',
    'DRAFT',  -- 状态: DRAFT, PUBLISHED, ARCHIVED
    'admin',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 验证插入结果
-- =====================================================
-- 查询刚创建的功能单元
-- SELECT id, code, name, description, status, created_by, created_at
-- FROM dw_function_units
-- WHERE code = 'PURCHASE';
