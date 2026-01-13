-- =====================================================
-- 采购申请 - 外键关系定义
-- =====================================================

-- purchase_item -> purchase_request
INSERT INTO dw_foreign_key_definitions (source_table_id, source_field_id, target_table_id, target_field_id, constraint_name, on_delete, on_update)
SELECT 
    st.id, sf.id, tt.id, tf.id,
    'fk_purchase_item_request', 'CASCADE', 'CASCADE'
FROM dw_table_definitions st
JOIN dw_function_units f ON st.function_unit_id = f.id
JOIN dw_field_definitions sf ON sf.table_id = st.id AND sf.field_name = 'request_id'
JOIN dw_table_definitions tt ON tt.function_unit_id = f.id AND tt.table_name = 'purchase_request'
JOIN dw_field_definitions tf ON tf.table_id = tt.id AND tf.field_name = 'id'
WHERE f.code = 'fu-purchase-request' AND st.table_name = 'purchase_item';

-- purchase_approval -> purchase_request
INSERT INTO dw_foreign_key_definitions (source_table_id, source_field_id, target_table_id, target_field_id, constraint_name, on_delete, on_update)
SELECT 
    st.id, sf.id, tt.id, tf.id,
    'fk_purchase_approval_request', 'CASCADE', 'CASCADE'
FROM dw_table_definitions st
JOIN dw_function_units f ON st.function_unit_id = f.id
JOIN dw_field_definitions sf ON sf.table_id = st.id AND sf.field_name = 'request_id'
JOIN dw_table_definitions tt ON tt.function_unit_id = f.id AND tt.table_name = 'purchase_request'
JOIN dw_field_definitions tf ON tf.table_id = tt.id AND tf.field_name = 'id'
WHERE f.code = 'fu-purchase-request' AND st.table_name = 'purchase_approval';

-- countersign_record -> purchase_request
INSERT INTO dw_foreign_key_definitions (source_table_id, source_field_id, target_table_id, target_field_id, constraint_name, on_delete, on_update)
SELECT 
    st.id, sf.id, tt.id, tf.id,
    'fk_countersign_request', 'CASCADE', 'CASCADE'
FROM dw_table_definitions st
JOIN dw_function_units f ON st.function_unit_id = f.id
JOIN dw_field_definitions sf ON sf.table_id = st.id AND sf.field_name = 'request_id'
JOIN dw_table_definitions tt ON tt.function_unit_id = f.id AND tt.table_name = 'purchase_request'
JOIN dw_field_definitions tf ON tf.table_id = tt.id AND tf.field_name = 'id'
WHERE f.code = 'fu-purchase-request' AND st.table_name = 'countersign_record';
