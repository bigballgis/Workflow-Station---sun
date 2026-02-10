-- =====================================================
-- 采购工作流 - 字段定义
-- =====================================================
-- 此脚本为采购工作流的表创建字段定义
-- 前置条件：需要先创建表定义 (02-create-purchase-tables.sql)

-- =====================================================
-- 采购申请主表 (purchase_request) 字段定义
-- =====================================================

-- 主键：ID
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, length, nullable, is_primary_key, is_unique, description, sort_order
)
SELECT id, 'id', 'BIGINT', NULL, false, true, true, '采购申请ID（主键）', 1
FROM dw_table_definitions WHERE table_name = 'purchase_request'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 申请编号
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, length, nullable, is_unique, description, sort_order
)
SELECT id, 'request_no', 'VARCHAR', 50, false, true, '采购申请编号（唯一）', 2
FROM dw_table_definitions WHERE table_name = 'purchase_request'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 申请标题
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, length, nullable, description, sort_order
)
SELECT id, 'title', 'VARCHAR', 200, false, '采购申请标题', 3
FROM dw_table_definitions WHERE table_name = 'purchase_request'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 申请部门
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, length, nullable, description, sort_order
)
SELECT id, 'department', 'VARCHAR', 100, false, '申请部门', 4
FROM dw_table_definitions WHERE table_name = 'purchase_request'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 申请人
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, length, nullable, description, sort_order
)
SELECT id, 'applicant', 'VARCHAR', 100, false, '申请人', 5
FROM dw_table_definitions WHERE table_name = 'purchase_request'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 申请日期
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, nullable, description, sort_order
)
SELECT id, 'apply_date', 'DATE', false, '申请日期', 6
FROM dw_table_definitions WHERE table_name = 'purchase_request'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 总金额
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, precision_value, scale, nullable, default_value, description, sort_order
)
SELECT id, 'total_amount', 'DECIMAL', 15, 2, false, '0.00', '采购总金额', 7
FROM dw_table_definitions WHERE table_name = 'purchase_request'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 状态
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, length, nullable, default_value, description, sort_order
)
SELECT id, 'status', 'VARCHAR', 20, false, 'DRAFT', '状态：DRAFT-草稿, PENDING-待审批, APPROVED-已批准, REJECTED-已拒绝', 8
FROM dw_table_definitions WHERE table_name = 'purchase_request'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 备注
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, nullable, description, sort_order
)
SELECT id, 'remarks', 'TEXT', true, '备注说明', 9
FROM dw_table_definitions WHERE table_name = 'purchase_request'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 创建时间
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, nullable, default_value, description, sort_order
)
SELECT id, 'created_at', 'TIMESTAMP', false, 'CURRENT_TIMESTAMP', '创建时间', 10
FROM dw_table_definitions WHERE table_name = 'purchase_request'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 更新时间
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, nullable, default_value, description, sort_order
)
SELECT id, 'updated_at', 'TIMESTAMP', true, 'CURRENT_TIMESTAMP', '更新时间', 11
FROM dw_table_definitions WHERE table_name = 'purchase_request'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- =====================================================
-- 采购明细表 (purchase_items) 字段定义
-- =====================================================

-- 主键：ID
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, nullable, is_primary_key, is_unique, description, sort_order
)
SELECT id, 'id', 'BIGINT', false, true, true, '明细ID（主键）', 1
FROM dw_table_definitions WHERE table_name = 'purchase_items'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 外键：采购申请ID
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, nullable, description, sort_order
)
SELECT id, 'request_id', 'BIGINT', false, '采购申请ID（外键）', 2
FROM dw_table_definitions WHERE table_name = 'purchase_items'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 物品名称
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, length, nullable, description, sort_order
)
SELECT id, 'item_name', 'VARCHAR', 200, false, '物品名称', 3
FROM dw_table_definitions WHERE table_name = 'purchase_items'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 规格型号
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, length, nullable, description, sort_order
)
SELECT id, 'specification', 'VARCHAR', 200, true, '规格型号', 4
FROM dw_table_definitions WHERE table_name = 'purchase_items'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 数量
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, nullable, default_value, description, sort_order
)
SELECT id, 'quantity', 'INTEGER', false, '1', '采购数量', 5
FROM dw_table_definitions WHERE table_name = 'purchase_items'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 单位
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, length, nullable, description, sort_order
)
SELECT id, 'unit', 'VARCHAR', 20, false, '计量单位', 6
FROM dw_table_definitions WHERE table_name = 'purchase_items'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 单价
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, precision_value, scale, nullable, default_value, description, sort_order
)
SELECT id, 'unit_price', 'DECIMAL', 15, 2, false, '0.00', '单价', 7
FROM dw_table_definitions WHERE table_name = 'purchase_items'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 小计金额
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, precision_value, scale, nullable, default_value, description, sort_order
)
SELECT id, 'subtotal', 'DECIMAL', 15, 2, false, '0.00', '小计金额（数量 × 单价）', 8
FROM dw_table_definitions WHERE table_name = 'purchase_items'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 备注
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, nullable, description, sort_order
)
SELECT id, 'remarks', 'TEXT', true, '备注说明', 9
FROM dw_table_definitions WHERE table_name = 'purchase_items'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- =====================================================
-- 供应商信息表 (suppliers) 字段定义
-- =====================================================

-- 主键：ID
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, nullable, is_primary_key, is_unique, description, sort_order
)
SELECT id, 'id', 'BIGINT', false, true, true, '供应商ID（主键）', 1
FROM dw_table_definitions WHERE table_name = 'suppliers'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 供应商编号
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, length, nullable, is_unique, description, sort_order
)
SELECT id, 'supplier_code', 'VARCHAR', 50, false, true, '供应商编号（唯一）', 2
FROM dw_table_definitions WHERE table_name = 'suppliers'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 供应商名称
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, length, nullable, description, sort_order
)
SELECT id, 'supplier_name', 'VARCHAR', 200, false, '供应商名称', 3
FROM dw_table_definitions WHERE table_name = 'suppliers'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 联系人
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, length, nullable, description, sort_order
)
SELECT id, 'contact_person', 'VARCHAR', 100, true, '联系人', 4
FROM dw_table_definitions WHERE table_name = 'suppliers'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 联系电话
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, length, nullable, description, sort_order
)
SELECT id, 'contact_phone', 'VARCHAR', 50, true, '联系电话', 5
FROM dw_table_definitions WHERE table_name = 'suppliers'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 地址
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, length, nullable, description, sort_order
)
SELECT id, 'address', 'VARCHAR', 500, true, '供应商地址', 6
FROM dw_table_definitions WHERE table_name = 'suppliers'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 状态
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, length, nullable, default_value, description, sort_order
)
SELECT id, 'status', 'VARCHAR', 20, false, 'ACTIVE', '状态：ACTIVE-活跃, INACTIVE-停用', 7
FROM dw_table_definitions WHERE table_name = 'suppliers'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- =====================================================
-- 采购审批记录表 (purchase_approvals) 字段定义
-- =====================================================

-- 主键：ID
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, nullable, is_primary_key, is_unique, description, sort_order
)
SELECT id, 'id', 'BIGINT', false, true, true, '审批记录ID（主键）', 1
FROM dw_table_definitions WHERE table_name = 'purchase_approvals'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 外键：采购申请ID
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, nullable, description, sort_order
)
SELECT id, 'request_id', 'BIGINT', false, '采购申请ID（外键）', 2
FROM dw_table_definitions WHERE table_name = 'purchase_approvals'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 审批人
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, length, nullable, description, sort_order
)
SELECT id, 'approver', 'VARCHAR', 100, false, '审批人', 3
FROM dw_table_definitions WHERE table_name = 'purchase_approvals'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 审批结果
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, length, nullable, description, sort_order
)
SELECT id, 'result', 'VARCHAR', 20, false, '审批结果：APPROVED-批准, REJECTED-拒绝', 4
FROM dw_table_definitions WHERE table_name = 'purchase_approvals'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 审批意见
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, nullable, description, sort_order
)
SELECT id, 'comments', 'TEXT', true, '审批意见', 5
FROM dw_table_definitions WHERE table_name = 'purchase_approvals'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- 审批时间
INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, nullable, default_value, description, sort_order
)
SELECT id, 'approval_time', 'TIMESTAMP', false, 'CURRENT_TIMESTAMP', '审批时间', 6
FROM dw_table_definitions WHERE table_name = 'purchase_approvals'
ON CONFLICT (table_id, field_name) DO UPDATE SET description = EXCLUDED.description;

-- =====================================================
-- 验证插入结果
-- =====================================================
-- 查询所有字段定义
-- SELECT 
--     td.table_name,
--     fd.field_name,
--     fd.data_type,
--     fd.length,
--     fd.nullable,
--     fd.is_primary_key,
--     fd.description
-- FROM dw_field_definitions fd
-- JOIN dw_table_definitions td ON fd.table_id = td.id
-- JOIN dw_function_units fu ON td.function_unit_id = fu.id
-- WHERE fu.code = 'PURCHASE'
-- ORDER BY td.table_name, fd.sort_order;
