-- =====================================================
-- Purchase Workflow - Table Fields
-- =====================================================
-- This script creates fields for purchase business tables
-- =====================================================

\echo '========================================='
\echo 'Creating Purchase Table Fields...'
\echo '========================================='

-- Main Table Fields
INSERT INTO dw_fields (id, table_id, code, name, type, length, required, default_value, description, sort_order, status, created_at, updated_at, created_by)
VALUES 
-- System fields
('fld-pr-id', 'tbl-purchase-main', 'id', 'ID', 'VARCHAR', 64, true, NULL, '主键ID', 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pr-request-no', 'tbl-purchase-main', 'request_no', '申请单号', 'VARCHAR', 50, true, NULL, '采购申请单号', 2, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pr-title', 'tbl-purchase-main', 'title', '申请标题', 'VARCHAR', 200, true, NULL, '采购申请标题', 3, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pr-dept', 'tbl-purchase-main', 'department_id', '申请部门', 'VARCHAR', 64, true, NULL, '申请部门ID', 4, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pr-applicant', 'tbl-purchase-main', 'applicant_id', '申请人', 'VARCHAR', 64, true, NULL, '申请人ID', 5, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pr-date', 'tbl-purchase-main', 'request_date', '申请日期', 'DATE', NULL, true, 'CURRENT_DATE', '申请日期', 6, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pr-amount', 'tbl-purchase-main', 'total_amount', '总金额', 'DECIMAL', NULL, true, '0', '采购总金额', 7, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pr-currency', 'tbl-purchase-main', 'currency', '币种', 'VARCHAR', 10, true, 'CNY', '币种', 8, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pr-urgency', 'tbl-purchase-main', 'urgency', '紧急程度', 'VARCHAR', 20, true, 'NORMAL', '紧急程度: URGENT, NORMAL, LOW', 9, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pr-reason', 'tbl-purchase-main', 'reason', '采购原因', 'TEXT', NULL, true, NULL, '采购原因说明', 10, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pr-status', 'tbl-purchase-main', 'status', '状态', 'VARCHAR', 20, true, 'DRAFT', '申请状态', 11, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pr-created-at', 'tbl-purchase-main', 'created_at', '创建时间', 'TIMESTAMP', NULL, true, 'CURRENT_TIMESTAMP', '创建时间', 12, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pr-updated-at', 'tbl-purchase-main', 'updated_at', '更新时间', 'TIMESTAMP', NULL, true, 'CURRENT_TIMESTAMP', '更新时间', 13, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (table_id, code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- Sub Table Fields (Purchase Items)
INSERT INTO dw_fields (id, table_id, code, name, type, length, required, default_value, description, sort_order, status, created_at, updated_at, created_by)
VALUES 
('fld-pi-id', 'tbl-purchase-items', 'id', 'ID', 'VARCHAR', 64, true, NULL, '主键ID', 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pi-request-id', 'tbl-purchase-items', 'request_id', '申请单ID', 'VARCHAR', 64, true, NULL, '关联的采购申请ID', 2, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pi-item-no', 'tbl-purchase-items', 'item_no', '项目编号', 'INTEGER', NULL, true, NULL, '明细项目编号', 3, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pi-name', 'tbl-purchase-items', 'item_name', '物品名称', 'VARCHAR', 200, true, NULL, '采购物品名称', 4, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pi-spec', 'tbl-purchase-items', 'specification', '规格型号', 'VARCHAR', 200, false, NULL, '物品规格型号', 5, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pi-unit', 'tbl-purchase-items', 'unit', '单位', 'VARCHAR', 20, true, '个', '计量单位', 6, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pi-quantity', 'tbl-purchase-items', 'quantity', '数量', 'DECIMAL', NULL, true, '1', '采购数量', 7, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pi-price', 'tbl-purchase-items', 'unit_price', '单价', 'DECIMAL', NULL, true, '0', '单价', 8, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pi-amount', 'tbl-purchase-items', 'amount', '金额', 'DECIMAL', NULL, true, '0', '小计金额', 9, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
('fld-pi-remark', 'tbl-purchase-items', 'remark', '备注', 'VARCHAR', 500, false, NULL, '备注说明', 10, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (table_id, code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

\echo '✓ Purchase table fields created'
\echo ''

\echo '========================================='
\echo 'Table Fields Summary'
\echo '========================================='
\echo 'Main Table (purchase_request): 13 fields'
\echo '  - System: id, request_no, status, timestamps'
\echo '  - Business: title, department, applicant, date'
\echo '  - Financial: total_amount, currency'
\echo '  - Details: urgency, reason'
\echo ''
\echo 'Sub Table (purchase_items): 10 fields'
\echo '  - System: id, request_id, item_no'
\echo '  - Item Info: item_name, specification, unit'
\echo '  - Financial: quantity, unit_price, amount'
\echo '  - Other: remark'
\echo '========================================='
