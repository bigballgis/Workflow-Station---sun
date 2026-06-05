-- =============================================================================
-- 12-simple-approval: Create Table Definitions & Field Definitions
-- 基于数据库实际数据生成 (source tables: 8/9/10/11/12 under SIMPLE_APPROVAL)
-- =============================================================================

DO $tables$
DECLARE
    v_function_unit_id  BIGINT;
    v_main_table_id     BIGINT;  -- Request (MAIN)
    v_sub_table_id      BIGINT;  -- RequestItems (SUB)
    v_action_table_id   BIGINT;  -- ApprovalActions (ACTION)
    v_relation_table_id BIGINT;  -- RequestAttachments (RELATION)
    v_test_table_id     BIGINT;  -- test (SUB)
BEGIN
    SELECT id INTO v_function_unit_id FROM dw_function_units WHERE code = 'fu-20260403-a1b2c1';
    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit fu-20260403-a1b2c1 not found. Run 00-create-function-unit.sql first.';
    END IF;

    -- =========================================================================
    -- Table 1: Request (MAIN) — source id=8
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, display_name, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'Request', 'MAIN', 'Main request table', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_type  = EXCLUDED.table_type,
        display_name = EXCLUDED.display_name,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_main_table_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_main_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, display_name, sort_order
    ) VALUES
    (v_main_table_id, 'id',                'BIGINT',    NULL, NULL, NULL, false, NULL, false, false, 'Primary key',           1),
    (v_main_table_id, 'request_number',    'VARCHAR',   50,   NULL, NULL, false, NULL, false, false, 'Request number',2),
    (v_main_table_id, 'request_date',      'TIMESTAMP', NULL, NULL, NULL, false, NULL, false, false, 'Request date',          3),
    (v_main_table_id, 'title',             'VARCHAR',   200,  NULL, NULL, false, NULL, false, false, 'Request title',         4),
    (v_main_table_id, 'description',       'TEXT',      NULL, NULL, NULL, false, NULL, false, false, 'Request description',   5),
    (v_main_table_id, 'status',            'VARCHAR',   30,   NULL, NULL, false, NULL, false, false, 'Request status',        6),
    (v_main_table_id, 'approval_comments', 'TEXT',      NULL, NULL, NULL, true,  NULL, false, false, 'Approval comments',     7),
    (v_main_table_id, 'created_by',        'VARCHAR',   100,  NULL, NULL, false, NULL, false, false, 'Created by',            8),
    (v_main_table_id, 'created_at',        'TIMESTAMP', NULL, NULL, NULL, false, NULL, false, false, 'Created at',            9),
    (v_main_table_id, 'updated_at',        'TIMESTAMP', NULL, NULL, NULL, true,  NULL, false, false, 'Updated at',            10);

    RAISE NOTICE 'Table Request (MAIN) created: id=%', v_main_table_id;

    -- =========================================================================
    -- Table 2: RequestItems (SUB) — source id=9
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, display_name, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'RequestItems', 'SUB', 'Sub table for request line items', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_type  = EXCLUDED.table_type,
        display_name = EXCLUDED.display_name,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_sub_table_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_sub_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, display_name, sort_order
    ) VALUES
    (v_sub_table_id, 'id',          'BIGINT',  NULL, NULL, NULL, false, NULL, false, false, 'Item ID',                     1),
    (v_sub_table_id, 'request_id',  'BIGINT',  NULL, NULL, NULL, false, NULL, false, false, 'Foreign key to Request table', 2),
    (v_sub_table_id, 'item_name',   'VARCHAR', 200,  NULL, NULL, false, NULL, false, false, 'Item name',                   3),
    (v_sub_table_id, 'quantity',    'INTEGER', NULL, NULL, NULL, false, NULL, false, false, 'Quantity',                    4),
    (v_sub_table_id, 'unit_price',  'DECIMAL', NULL, 10,   2,    false, NULL, false, false, 'Unit price',                  5),
    (v_sub_table_id, 'total_price', 'DECIMAL', NULL, 10,   2,    false, NULL, false, false, 'Total price',                 6),
    (v_sub_table_id, 'remarks',     'TEXT',    NULL, NULL, NULL, true,  NULL, false, false, 'Item remarks',                7),
    (v_sub_table_id, 'sort_order',  'INTEGER', NULL, NULL, NULL, false, NULL, false, false, 'Display order',               8);

    RAISE NOTICE 'Table RequestItems (SUB) created: id=%', v_sub_table_id;

    -- =========================================================================
    -- Table 3: ApprovalActions (ACTION) — source id=10
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, display_name, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'ApprovalActions', 'ACTION', 'Action table for tracking approval history', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_type  = EXCLUDED.table_type,
        display_name = EXCLUDED.display_name,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_action_table_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_action_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, display_name, sort_order
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
    -- Table 4: RequestAttachments (RELATION) — source id=11
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, display_name, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'RequestAttachments', 'RELATION', 'Relation table for request attachments', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_type  = EXCLUDED.table_type,
        display_name = EXCLUDED.display_name,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_relation_table_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_relation_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, display_name, sort_order
    ) VALUES
    (v_relation_table_id, 'id',          'BIGINT',    NULL, NULL, NULL, false, NULL, false, false, 'Attachment ID',                1),
    (v_relation_table_id, 'request_id',  'BIGINT',    NULL, NULL, NULL, false, NULL, false, false, 'Foreign key to Request table', 2),
    (v_relation_table_id, 'file_name',   'VARCHAR',   255,  NULL, NULL, false, NULL, false, false, 'Original file name',           3),
    (v_relation_table_id, 'file_path',   'VARCHAR',   500,  NULL, NULL, false, NULL, false, false, 'File storage path',            4),
    (v_relation_table_id, 'file_size',   'BIGINT',    NULL, NULL, NULL, false, NULL, false, false, 'File size in bytes',           5),
    (v_relation_table_id, 'file_type',   'VARCHAR',   100,  NULL, NULL, false, NULL, false, false, 'MIME type',                    6),
    (v_relation_table_id, 'uploaded_by', 'VARCHAR',   100,  NULL, NULL, false, NULL, false, false, 'User who uploaded',            7),
    (v_relation_table_id, 'uploaded_at', 'TIMESTAMP', NULL, NULL, NULL, false, NULL, false, false, 'Upload timestamp',             8),
    (v_relation_table_id, 'description', 'TEXT',      NULL, NULL, NULL, true,  NULL, false, false, 'Attachment description',       9);

    RAISE NOTICE 'Table RequestAttachments (RELATION) created: id=%', v_relation_table_id;

    -- =========================================================================
    -- Table 5: test (SUB) — source id=12
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, display_name, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'test', 'SUB', 'test', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_type  = EXCLUDED.table_type,
        display_name = EXCLUDED.display_name,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_test_table_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_test_table_id;

    -- source fields: test(VARCHAR 255, nullable, sort_order=0), request_id(INTEGER 255, nullable, sort_order=1)
    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, display_name, sort_order
    ) VALUES
    (v_test_table_id, 'test',       'VARCHAR', 255, NULL, NULL, true, NULL, false, false, 'e',  0),
    (v_test_table_id, 'request_id', 'INTEGER', 255, NULL, NULL, true, NULL, false, false, 'FK', 1);

    RAISE NOTICE 'Table test (SUB) created: id=%', v_test_table_id;

    -- =========================================================================
    -- Summary
    -- =========================================================================
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Tables Setup Complete!';
    RAISE NOTICE 'Request (MAIN)           : id=%', v_main_table_id;
    RAISE NOTICE 'RequestItems (SUB)       : id=%', v_sub_table_id;
    RAISE NOTICE 'ApprovalActions (ACTION) : id=%', v_action_table_id;
    RAISE NOTICE 'RequestAttachments (REL) : id=%', v_relation_table_id;
    RAISE NOTICE 'test (SUB)               : id=%', v_test_table_id;
    RAISE NOTICE 'Next: run 02-create-bpmn-process.sql';
    RAISE NOTICE '========================================';

END $tables$;
