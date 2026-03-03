-- =============================================================================
-- 13-procurement-workflow: Create Table Definitions & Field Definitions
-- 基于数据库实际数据生成 (source: Procurement Workflow)
-- Tables: Request(MAIN), RequestItems(SUB), ApprovalActions(ACTION),
--         RequestAttachments(SUB)
-- =============================================================================

DO $tables$
DECLARE
    v_function_unit_id    BIGINT;
    v_main_table_id       BIGINT;  -- Request (MAIN)
    v_items_table_id      BIGINT;  -- RequestItems (SUB)
    v_action_table_id     BIGINT;  -- ApprovalActions (ACTION)
    v_attach_table_id     BIGINT;  -- RequestAttachments (SUB)
BEGIN
    SELECT id INTO v_function_unit_id FROM dw_function_units WHERE code = 'PROCUREMENT_WORKFLOW';
    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit PROCUREMENT_WORKFLOW not found. Run 00-create-function-unit.sql first.';
    END IF;

    -- =========================================================================
    -- Table 1: Request (MAIN)
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'Request', 'MAIN', 'Main request table', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_type  = EXCLUDED.table_type,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_main_table_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_main_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, description, sort_order
    ) VALUES
    (v_main_table_id, 'id',                     'BIGINT',    NULL, NULL, NULL, false, NULL, false, false, 'Primary key',              1),
    (v_main_table_id, 'request_number',         'VARCHAR',   50,   NULL, NULL, false, NULL, false, false, 'Request Number',           2),
    (v_main_table_id, 'request_date',           'TIMESTAMP', NULL, NULL, NULL, false, NULL, false, false, 'Request Date',             3),
    (v_main_table_id, 'title',                  'VARCHAR',   200,  NULL, NULL, false, NULL, false, false, 'Request Title',            4),
    (v_main_table_id, 'description',            'TEXT',      NULL, NULL, NULL, false, NULL, false, false, 'Request Description',      5),
    (v_main_table_id, 'status',                 'VARCHAR',   30,   NULL, NULL, false, NULL, false, false, 'Request Status',           6),
    (v_main_table_id, 'additional_information', 'TEXT',      NULL, NULL, NULL, true,  NULL, false, false, 'Additional Inforrmation',  7),
    (v_main_table_id, 'created_by',             'VARCHAR',   100,  NULL, NULL, false, NULL, false, false, 'Created by',               8),
    (v_main_table_id, 'created_at',             'TIMESTAMP', NULL, NULL, NULL, false, NULL, false, false, 'Created at',               9),
    (v_main_table_id, 'updated_at',             'TIMESTAMP', NULL, NULL, NULL, true,  NULL, false, false, 'Updated at',               10),
    (v_main_table_id, 'budget',                 'INTEGER',   NULL, NULL, NULL, true,  NULL, false, false, 'budget',                   10);

    RAISE NOTICE 'Table Request (MAIN) created: id=%', v_main_table_id;

    -- =========================================================================
    -- Table 2: RequestItems (SUB)
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'RequestItems', 'SUB', 'Sub table for request line items', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_type  = EXCLUDED.table_type,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_items_table_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_items_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, description, sort_order
    ) VALUES
    (v_items_table_id, 'id',          'BIGINT',  NULL, NULL, NULL, false, NULL, false, false, 'Item ID',                     1),
    (v_items_table_id, 'request_id',  'BIGINT',  NULL, NULL, NULL, false, NULL, false, false, 'Foreign key to Request table', 2),
    (v_items_table_id, 'item_name',   'VARCHAR', 200,  NULL, NULL, false, NULL, false, false, 'Item Name',                   3),
    (v_items_table_id, 'quantity',    'INTEGER', NULL, NULL, NULL, false, NULL, false, false, 'Quantity',                    4),
    (v_items_table_id, 'unit_price',  'DECIMAL', NULL, 10,   2,    false, NULL, false, false, 'Unit Price',                  5),
    (v_items_table_id, 'total_price', 'DECIMAL', NULL, 10,   2,    false, NULL, false, false, 'Total Price',                 6),
    (v_items_table_id, 'remarks',     'TEXT',    NULL, NULL, NULL, true,  NULL, false, false, 'Item Remarks',                7),
    (v_items_table_id, 'count',       'INTEGER', NULL, NULL, NULL, true,  NULL, false, false, 'Count',                       8),
    (v_items_table_id, 'sort_order',  'INTEGER', NULL, NULL, NULL, false, NULL, false, false, 'Display Order',               8);

    RAISE NOTICE 'Table RequestItems (SUB) created: id=%', v_items_table_id;

    -- =========================================================================
    -- Table 3: ApprovalActions (ACTION)
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'ApprovalActions', 'ACTION', 'Action table for tracking approval history', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_type  = EXCLUDED.table_type,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_action_table_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_action_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, description, sort_order
    ) VALUES
    (v_action_table_id, 'id',              'BIGINT',    NULL, NULL, NULL, false, NULL, false, false, 'Action ID',                          1),
    (v_action_table_id, 'request_id',      'BIGINT',    NULL, NULL, NULL, false, NULL, false, false, 'Foreign key to Request table',       2),
    (v_action_table_id, 'action_type',     'VARCHAR',   50,   NULL, NULL, false, NULL, false, false, 'Action type (SUBMIT/APPROVE/REJECT)', 3),
    (v_action_table_id, 'action_by',       'VARCHAR',   100,  NULL, NULL, false, NULL, false, false, 'User who performed action',          4),
    (v_action_table_id, 'action_at',       'TIMESTAMP', NULL, NULL, NULL, false, NULL, false, false, 'Action timestamp',                   5),
    (v_action_table_id, 'comments',        'TEXT',      NULL, NULL, NULL, true,  NULL, false, false, 'Action comments',                    6),
    (v_action_table_id, 'previous_status', 'VARCHAR',   30,   NULL, NULL, true,  NULL, false, false, 'Status before action',               7),
    (v_action_table_id, 'new_status',      'VARCHAR',   30,   NULL, NULL, false, NULL, false, false, 'Status after action',                8),
    (v_action_table_id, 'ip_address',      'VARCHAR',   50,   NULL, NULL, true,  NULL, false, false, 'IP address of action',               9);

    RAISE NOTICE 'Table ApprovalActions (ACTION) created: id=%', v_action_table_id;

    -- =========================================================================
    -- Table 4: RequestAttachments (SUB)
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'RequestAttachments', 'SUB', 'Relation table for request attachments', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_type  = EXCLUDED.table_type,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_attach_table_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_attach_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, description, sort_order
    ) VALUES
    (v_attach_table_id, 'id',          'BIGINT',    NULL, NULL, NULL, false, NULL, false, false, 'Attachment ID',                1),
    (v_attach_table_id, 'request_id',  'BIGINT',    NULL, NULL, NULL, false, NULL, false, false, 'Foreign key to Request table', 2),
    (v_attach_table_id, 'file_name',   'VARCHAR',   255,  NULL, NULL, false, NULL, false, false, 'Original File Name',           3),
    (v_attach_table_id, 'file_path',   'VARCHAR',   500,  NULL, NULL, false, NULL, false, false, 'File Storage Path',            4),
    (v_attach_table_id, 'file_size',   'BIGINT',    NULL, NULL, NULL, false, NULL, false, false, 'File size in bytes',           5),
    (v_attach_table_id, 'file_type',   'VARCHAR',   100,  NULL, NULL, false, NULL, false, false, 'Type',                         6),
    (v_attach_table_id, 'uploaded_by', 'VARCHAR',   100,  NULL, NULL, false, NULL, false, false, 'User who uploaded',            7),
    (v_attach_table_id, 'uploaded_at', 'TIMESTAMP', NULL, NULL, NULL, false, NULL, false, false, 'Upload timestamp',             8),
    (v_attach_table_id, 'description', 'TEXT',      NULL, NULL, NULL, true,  NULL, false, false, 'Attachment description',       9);

    RAISE NOTICE 'Table RequestAttachments (SUB) created: id=%', v_attach_table_id;

    -- =========================================================================
    -- Summary
    -- =========================================================================
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Tables Setup Complete!';
    RAISE NOTICE 'Request (MAIN)           : id=%', v_main_table_id;
    RAISE NOTICE 'RequestItems (SUB)       : id=%', v_items_table_id;
    RAISE NOTICE 'ApprovalActions (ACTION)  : id=%', v_action_table_id;
    RAISE NOTICE 'RequestAttachments (SUB) : id=%', v_attach_table_id;
    RAISE NOTICE 'Next: run 02-create-bpmn-process.sql';
    RAISE NOTICE '========================================';

END $tables$;
