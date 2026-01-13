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