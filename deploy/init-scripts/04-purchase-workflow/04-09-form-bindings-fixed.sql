-- Form-Table Bindings for Purchase Request Function Unit
-- 表单和表的绑定关系

-- 1. Main Form (Purchase Request Main Form) -> purchase_request (PRIMARY)
INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at)
SELECT 
    fd.id,
    td.id,
    'PRIMARY',
    'EDITABLE',
    NULL,
    0,
    NOW()
FROM dw_form_definitions fd, dw_table_definitions td
WHERE fd.form_name = 'Purchase Request Main Form'
  AND td.table_name = 'purchase_request'
  AND fd.function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-purchase-request')
  AND td.function_unit_id = fd.function_unit_id
ON CONFLICT (form_id, table_id) DO NOTHING;

-- 2. Main Form -> purchase_item (SUB)
INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at)
SELECT 
    fd.id,
    td.id,
    'SUB',
    'EDITABLE',
    'request_id',
    1,
    NOW()
FROM dw_form_definitions fd, dw_table_definitions td
WHERE fd.form_name = 'Purchase Request Main Form'
  AND td.table_name = 'purchase_item'
  AND fd.function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-purchase-request')
  AND td.function_unit_id = fd.function_unit_id
ON CONFLICT (form_id, table_id) DO NOTHING;

-- 3. Main Form -> supplier_info (RELATED)
INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at)
SELECT 
    fd.id,
    td.id,
    'RELATED',
    'EDITABLE',
    'request_id',
    2,
    NOW()
FROM dw_form_definitions fd, dw_table_definitions td
WHERE fd.form_name = 'Purchase Request Main Form'
  AND td.table_name = 'supplier_info'
  AND fd.function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-purchase-request')
  AND td.function_unit_id = fd.function_unit_id
ON CONFLICT (form_id, table_id) DO NOTHING;

-- 4. Sub Form (Purchase Items Form) -> purchase_item (PRIMARY)
INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at)
SELECT 
    fd.id,
    td.id,
    'PRIMARY',
    'EDITABLE',
    NULL,
    0,
    NOW()
FROM dw_form_definitions fd, dw_table_definitions td
WHERE fd.form_name = 'Purchase Items Form'
  AND td.table_name = 'purchase_item'
  AND fd.function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-purchase-request')
  AND td.function_unit_id = fd.function_unit_id
ON CONFLICT (form_id, table_id) DO NOTHING;

-- 5. Approval Form -> purchase_request (PRIMARY, READONLY)
INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at)
SELECT 
    fd.id,
    td.id,
    'PRIMARY',
    'READONLY',
    NULL,
    0,
    NOW()
FROM dw_form_definitions fd, dw_table_definitions td
WHERE fd.form_name = 'Approval Form'
  AND td.table_name = 'purchase_request'
  AND fd.function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-purchase-request')
  AND td.function_unit_id = fd.function_unit_id
ON CONFLICT (form_id, table_id) DO NOTHING;

-- 6. Approval Form -> purchase_approval (SUB, EDITABLE)
INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at)
SELECT 
    fd.id,
    td.id,
    'SUB',
    'EDITABLE',
    'request_id',
    1,
    NOW()
FROM dw_form_definitions fd, dw_table_definitions td
WHERE fd.form_name = 'Approval Form'
  AND td.table_name = 'purchase_approval'
  AND fd.function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-purchase-request')
  AND td.function_unit_id = fd.function_unit_id
ON CONFLICT (form_id, table_id) DO NOTHING;

-- 7. Countersign Form -> purchase_request (PRIMARY, READONLY)
INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at)
SELECT 
    fd.id,
    td.id,
    'PRIMARY',
    'READONLY',
    NULL,
    0,
    NOW()
FROM dw_form_definitions fd, dw_table_definitions td
WHERE fd.form_name = 'Countersign Form'
  AND td.table_name = 'purchase_request'
  AND fd.function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-purchase-request')
  AND td.function_unit_id = fd.function_unit_id
ON CONFLICT (form_id, table_id) DO NOTHING;

-- 8. Countersign Form -> countersign_record (SUB, EDITABLE)
INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at)
SELECT 
    fd.id,
    td.id,
    'SUB',
    'EDITABLE',
    'request_id',
    1,
    NOW()
FROM dw_form_definitions fd, dw_table_definitions td
WHERE fd.form_name = 'Countersign Form'
  AND td.table_name = 'countersign_record'
  AND fd.function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-purchase-request')
  AND td.function_unit_id = fd.function_unit_id
ON CONFLICT (form_id, table_id) DO NOTHING;