# 采购申请功能单元 - 生成外键字段和外键关系SQL

$outputDir = $PSScriptRoot

# 外键字段SQL - 为子表和关联表添加 request_id 字段
$fkFieldsSql = @"
-- Purchase Request - Foreign Key Fields

-- purchase_item 添加 request_id 外键字段
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'request_id', 'BIGINT', false, 'Reference to purchase_request', 0
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item'
AND NOT EXISTS (SELECT 1 FROM dw_field_definitions fd WHERE fd.table_id = t.id AND fd.field_name = 'request_id');

-- supplier_info 添加 request_id 外键字段
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'request_id', 'BIGINT', false, 'Reference to purchase_request', 0
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info'
AND NOT EXISTS (SELECT 1 FROM dw_field_definitions fd WHERE fd.table_id = t.id AND fd.field_name = 'request_id');

-- budget_info 添加 request_id 外键字段
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'request_id', 'BIGINT', false, 'Reference to purchase_request', 0
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info'
AND NOT EXISTS (SELECT 1 FROM dw_field_definitions fd WHERE fd.table_id = t.id AND fd.field_name = 'request_id');

-- purchase_approval 添加 request_id 外键字段
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'request_id', 'BIGINT', false, 'Reference to purchase_request', 0
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval'
AND NOT EXISTS (SELECT 1 FROM dw_field_definitions fd WHERE fd.table_id = t.id AND fd.field_name = 'request_id');

-- countersign_record 添加 request_id 外键字段
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'request_id', 'BIGINT', false, 'Reference to purchase_request', 0
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record'
AND NOT EXISTS (SELECT 1 FROM dw_field_definitions fd WHERE fd.table_id = t.id AND fd.field_name = 'request_id');

-- 主表添加 id 主键字段（如果不存在）
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, is_primary_key, description, sort_order)
SELECT t.id, 'id', 'BIGINT', false, true, 'Primary Key', 0
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request'
AND NOT EXISTS (SELECT 1 FROM dw_field_definitions fd WHERE fd.table_id = t.id AND fd.field_name = 'id');
"@

[System.IO.File]::WriteAllText("$outputDir\04-05-fk-fields.sql", $fkFieldsSql, [System.Text.Encoding]::UTF8)
Write-Host "Generated 04-05-fk-fields.sql"

# 外键关系SQL
$fkRelationsSql = @"
-- Purchase Request - Foreign Key Relations

-- purchase_item -> purchase_request
INSERT INTO dw_foreign_keys (table_id, field_id, ref_table_id, ref_field_id, on_delete, on_update)
SELECT 
    child.id as table_id,
    child_fk.id as field_id,
    parent.id as ref_table_id,
    parent_pk.id as ref_field_id,
    'CASCADE',
    'CASCADE'
FROM dw_table_definitions child
JOIN dw_function_units f ON child.function_unit_id = f.id
JOIN dw_field_definitions child_fk ON child_fk.table_id = child.id AND child_fk.field_name = 'request_id'
JOIN dw_table_definitions parent ON parent.function_unit_id = f.id AND parent.table_name = 'purchase_request'
JOIN dw_field_definitions parent_pk ON parent_pk.table_id = parent.id AND parent_pk.field_name = 'id'
WHERE f.code = 'fu-purchase-request' AND child.table_name = 'purchase_item'
AND NOT EXISTS (
    SELECT 1 FROM dw_foreign_keys fk 
    WHERE fk.table_id = child.id AND fk.field_id = child_fk.id
);

-- supplier_info -> purchase_request
INSERT INTO dw_foreign_keys (table_id, field_id, ref_table_id, ref_field_id, on_delete, on_update)
SELECT 
    child.id as table_id,
    child_fk.id as field_id,
    parent.id as ref_table_id,
    parent_pk.id as ref_field_id,
    'CASCADE',
    'CASCADE'
FROM dw_table_definitions child
JOIN dw_function_units f ON child.function_unit_id = f.id
JOIN dw_field_definitions child_fk ON child_fk.table_id = child.id AND child_fk.field_name = 'request_id'
JOIN dw_table_definitions parent ON parent.function_unit_id = f.id AND parent.table_name = 'purchase_request'
JOIN dw_field_definitions parent_pk ON parent_pk.table_id = parent.id AND parent_pk.field_name = 'id'
WHERE f.code = 'fu-purchase-request' AND child.table_name = 'supplier_info'
AND NOT EXISTS (
    SELECT 1 FROM dw_foreign_keys fk 
    WHERE fk.table_id = child.id AND fk.field_id = child_fk.id
);

-- budget_info -> purchase_request
INSERT INTO dw_foreign_keys (table_id, field_id, ref_table_id, ref_field_id, on_delete, on_update)
SELECT 
    child.id as table_id,
    child_fk.id as field_id,
    parent.id as ref_table_id,
    parent_pk.id as ref_field_id,
    'CASCADE',
    'CASCADE'
FROM dw_table_definitions child
JOIN dw_function_units f ON child.function_unit_id = f.id
JOIN dw_field_definitions child_fk ON child_fk.table_id = child.id AND child_fk.field_name = 'request_id'
JOIN dw_table_definitions parent ON parent.function_unit_id = f.id AND parent.table_name = 'purchase_request'
JOIN dw_field_definitions parent_pk ON parent_pk.table_id = parent.id AND parent_pk.field_name = 'id'
WHERE f.code = 'fu-purchase-request' AND child.table_name = 'budget_info'
AND NOT EXISTS (
    SELECT 1 FROM dw_foreign_keys fk 
    WHERE fk.table_id = child.id AND fk.field_id = child_fk.id
);

-- purchase_approval -> purchase_request
INSERT INTO dw_foreign_keys (table_id, field_id, ref_table_id, ref_field_id, on_delete, on_update)
SELECT 
    child.id as table_id,
    child_fk.id as field_id,
    parent.id as ref_table_id,
    parent_pk.id as ref_field_id,
    'CASCADE',
    'CASCADE'
FROM dw_table_definitions child
JOIN dw_function_units f ON child.function_unit_id = f.id
JOIN dw_field_definitions child_fk ON child_fk.table_id = child.id AND child_fk.field_name = 'request_id'
JOIN dw_table_definitions parent ON parent.function_unit_id = f.id AND parent.table_name = 'purchase_request'
JOIN dw_field_definitions parent_pk ON parent_pk.table_id = parent.id AND parent_pk.field_name = 'id'
WHERE f.code = 'fu-purchase-request' AND child.table_name = 'purchase_approval'
AND NOT EXISTS (
    SELECT 1 FROM dw_foreign_keys fk 
    WHERE fk.table_id = child.id AND fk.field_id = child_fk.id
);

-- countersign_record -> purchase_request
INSERT INTO dw_foreign_keys (table_id, field_id, ref_table_id, ref_field_id, on_delete, on_update)
SELECT 
    child.id as table_id,
    child_fk.id as field_id,
    parent.id as ref_table_id,
    parent_pk.id as ref_field_id,
    'CASCADE',
    'CASCADE'
FROM dw_table_definitions child
JOIN dw_function_units f ON child.function_unit_id = f.id
JOIN dw_field_definitions child_fk ON child_fk.table_id = child.id AND child_fk.field_name = 'request_id'
JOIN dw_table_definitions parent ON parent.function_unit_id = f.id AND parent.table_name = 'purchase_request'
JOIN dw_field_definitions parent_pk ON parent_pk.table_id = parent.id AND parent_pk.field_name = 'id'
WHERE f.code = 'fu-purchase-request' AND child.table_name = 'countersign_record'
AND NOT EXISTS (
    SELECT 1 FROM dw_foreign_keys fk 
    WHERE fk.table_id = child.id AND fk.field_id = child_fk.id
);
"@

[System.IO.File]::WriteAllText("$outputDir\04-05-fk-relations.sql", $fkRelationsSql, [System.Text.Encoding]::UTF8)
Write-Host "Generated 04-05-fk-relations.sql"

Write-Host "Foreign key SQL files generated successfully!"
