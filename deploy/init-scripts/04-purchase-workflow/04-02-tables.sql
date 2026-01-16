-- 采购申请 - 表定义

-- 主表: 采购申请
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_type, description, created_at, updated_at)
SELECT f.id, 'purchase_request', 'MAIN', '采购申请主表，存储申请基本信息', NOW(), NOW()
FROM dw_function_units f 
WHERE f.code = 'fu-purchase-request'
  AND NOT EXISTS (
    SELECT 1 FROM dw_table_definitions t 
    WHERE t.function_unit_id = f.id AND t.table_name = 'purchase_request'
  );

-- 子表: 采购明细
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_type, description, created_at, updated_at)
SELECT f.id, 'purchase_item', 'SUB', '采购物品明细子表', NOW(), NOW()
FROM dw_function_units f 
WHERE f.code = 'fu-purchase-request'
  AND NOT EXISTS (
    SELECT 1 FROM dw_table_definitions t 
    WHERE t.function_unit_id = f.id AND t.table_name = 'purchase_item'
  );

-- 关联表: 供应商信息
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_type, description, created_at, updated_at)
SELECT f.id, 'supplier_info', 'RELATION', '供应商信息关联表', NOW(), NOW()
FROM dw_function_units f 
WHERE f.code = 'fu-purchase-request'
  AND NOT EXISTS (
    SELECT 1 FROM dw_table_definitions t 
    WHERE t.function_unit_id = f.id AND t.table_name = 'supplier_info'
  );

-- 关联表: 预算信息
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_type, description, created_at, updated_at)
SELECT f.id, 'budget_info', 'RELATION', '预算信息关联表', NOW(), NOW()
FROM dw_function_units f 
WHERE f.code = 'fu-purchase-request'
  AND NOT EXISTS (
    SELECT 1 FROM dw_table_definitions t 
    WHERE t.function_unit_id = f.id AND t.table_name = 'budget_info'
  );

-- 动作表: 审批记录
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_type, description, created_at, updated_at)
SELECT f.id, 'purchase_approval', 'ACTION', '审批操作记录表', NOW(), NOW()
FROM dw_function_units f 
WHERE f.code = 'fu-purchase-request'
  AND NOT EXISTS (
    SELECT 1 FROM dw_table_definitions t 
    WHERE t.function_unit_id = f.id AND t.table_name = 'purchase_approval'
  );

-- 动作表: 会签记录
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_type, description, created_at, updated_at)
SELECT f.id, 'countersign_record', 'ACTION', '会签操作记录表', NOW(), NOW()
FROM dw_function_units f 
WHERE f.code = 'fu-purchase-request'
  AND NOT EXISTS (
    SELECT 1 FROM dw_table_definitions t 
    WHERE t.function_unit_id = f.id AND t.table_name = 'countersign_record'
  );
