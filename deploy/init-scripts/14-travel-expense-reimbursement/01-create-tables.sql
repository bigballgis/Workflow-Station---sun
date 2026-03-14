-- =============================================================================
-- 14-travel-expense-reimbursement: Create Table Definitions & Field Definitions
-- 差旅报销功能单元：创建数据表定义和字段定义
-- Tables: Reimbursement(MAIN), ExpenseItems(SUB), Invoices(SUB),
--         ApprovalActions(ACTION)
--
-- Dependencies: 00-create-function-unit.sql (requires TRAVEL_EXPENSE_REIMBURSEMENT function unit)
-- Execution order: 00 → 01 → 02 → 03
-- =============================================================================

DO $tables$
DECLARE
    v_function_unit_id       BIGINT;
    v_reimbursement_table_id BIGINT;  -- Reimbursement (MAIN)
    v_items_table_id         BIGINT;  -- ExpenseItems (SUB)
    v_invoices_table_id      BIGINT;  -- Invoices (SUB)
    v_action_table_id        BIGINT;  -- ApprovalActions (ACTION)
BEGIN
    SELECT id INTO v_function_unit_id FROM dw_function_units WHERE code = 'TRAVEL_EXPENSE_REIMBURSEMENT';
    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit TRAVEL_EXPENSE_REIMBURSEMENT not found. Run 00-create-function-unit.sql first.';
    END IF;

    -- =========================================================================
    -- Table 1: Reimbursement (MAIN)
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_display_name, table_type, description, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'reimbursement', 'Reimbursement', 'MAIN', 'Main reimbursement request table', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_display_name = EXCLUDED.table_display_name,
        table_type  = EXCLUDED.table_type,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_reimbursement_table_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_reimbursement_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, description, sort_order
    ) VALUES
    (v_reimbursement_table_id, 'id',                     'BIGINT',    NULL, NULL, NULL, false, NULL, false, false, 'Primary key',              1),
    (v_reimbursement_table_id, 'reimbursement_number',  'VARCHAR',   50,   NULL, NULL, false, NULL, false, false, 'Reimbursement Number',     2),
    (v_reimbursement_table_id, 'apply_date',            'TIMESTAMP', NULL, NULL, NULL, false, NULL, false, false, 'Apply Date',               3),
    (v_reimbursement_table_id, 'applicant_name',        'VARCHAR',   100,  NULL, NULL, false, NULL, false, false, 'Applicant Name',           4),
    (v_reimbursement_table_id, 'department',            'VARCHAR',   100,  NULL, NULL, false, NULL, false, false, 'Department',               5),
    (v_reimbursement_table_id, 'travel_destination',    'VARCHAR',   200,  NULL, NULL, false, NULL, false, false, 'Travel Destination',       6),
    (v_reimbursement_table_id, 'travel_start_date',     'DATE',      NULL, NULL, NULL, false, NULL, false, false, 'Travel Start Date',        7),
    (v_reimbursement_table_id, 'travel_end_date',       'DATE',      NULL, NULL, NULL, false, NULL, false, false, 'Travel End Date',          8),
    (v_reimbursement_table_id, 'travel_purpose',        'TEXT',      NULL, NULL, NULL, false, NULL, false, false, 'Travel Purpose',           9),
    (v_reimbursement_table_id, 'total_amount',          'DECIMAL',   NULL, 15,   2,    true,  NULL, false, false, 'Total Reimbursement Amount', 10),
    (v_reimbursement_table_id, 'status',                'VARCHAR',   30,   NULL, NULL, false, NULL, false, false, 'Request Status',           11),
    (v_reimbursement_table_id, 'approval_comment',      'TEXT',      NULL, NULL, NULL, true,  NULL, false, false, 'Approval Comment',         12),
    (v_reimbursement_table_id, 'created_by',            'VARCHAR',   100,  NULL, NULL, false, NULL, false, false, 'Created by',               13),
    (v_reimbursement_table_id, 'created_at',            'TIMESTAMP', NULL, NULL, NULL, false, NULL, false, false, 'Created at',               14),
    (v_reimbursement_table_id, 'updated_at',            'TIMESTAMP', NULL, NULL, NULL, true,  NULL, false, false, 'Updated at',               15);

    RAISE NOTICE 'Table Reimbursement (MAIN) created: id=%', v_reimbursement_table_id;

    -- =========================================================================
    -- Table 2: ExpenseItems (SUB)
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_display_name, table_type, description, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'expense_items', 'Expense Items', 'SUB', 'Sub table for expense line items', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_display_name = EXCLUDED.table_display_name,
        table_type  = EXCLUDED.table_type,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_items_table_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_items_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, description, sort_order
    ) VALUES
    (v_items_table_id, 'id',               'BIGINT',  NULL, NULL, NULL, false, NULL, false, false, 'Item ID',                              1),
    (v_items_table_id, 'reimbursement_id', 'BIGINT',  NULL, NULL, NULL, false, NULL, false, false, 'Foreign key to Reimbursement table',   2),
    (v_items_table_id, 'expense_type',     'VARCHAR', 50,   NULL, NULL, false, NULL, false, false, 'Expense type (交通/住宿/餐饮/其他)',    3),
    (v_items_table_id, 'expense_date',     'DATE',    NULL, NULL, NULL, false, NULL, false, false, 'Expense Date',                         4),
    (v_items_table_id, 'amount',           'DECIMAL', NULL, 15,   2,    false, NULL, false, false, 'Amount',                               5),
    (v_items_table_id, 'description',      'TEXT',    NULL, NULL, NULL, true,  NULL, false, false, 'Expense Description',                  6),
    (v_items_table_id, 'sort_order',       'INTEGER', NULL, NULL, NULL, false, NULL, false, false, 'Display Order',                        7);

    RAISE NOTICE 'Table ExpenseItems (SUB) created: id=%', v_items_table_id;

    -- =========================================================================
    -- Table 3: Invoices (SUB)
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_display_name, table_type, description, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'invoices', 'Invoices', 'SUB', 'Sub table for invoice attachments and recognition results', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_display_name = EXCLUDED.table_display_name,
        table_type  = EXCLUDED.table_type,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_invoices_table_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_invoices_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, description, sort_order
    ) VALUES
    (v_invoices_table_id, 'id',                  'BIGINT',    NULL, NULL, NULL, false, NULL, false, false, 'Invoice ID',                          1),
    (v_invoices_table_id, 'reimbursement_id',    'BIGINT',    NULL, NULL, NULL, false, NULL, false, false, 'Foreign key to Reimbursement table',  2),
    (v_invoices_table_id, 'file',                'FILE',      NULL, NULL, NULL, false, NULL, false, false, 'Invoice File',                        3),
    (v_invoices_table_id, 'file_name',           'VARCHAR',   255,  NULL, NULL, false, NULL, false, false, 'Original File Name',                  4),
    (v_invoices_table_id, 'file_path',           'VARCHAR',   500,  NULL, NULL, false, NULL, false, false, 'File Storage Path',                   5),
    (v_invoices_table_id, 'file_size',           'BIGINT',    NULL, NULL, NULL, false, NULL, false, false, 'File size in bytes',                  6),
    (v_invoices_table_id, 'file_type',           'VARCHAR',   100,  NULL, NULL, false, NULL, false, false, 'File Type',                           7),
    (v_invoices_table_id, 'invoice_type',        'VARCHAR',   50,   NULL, NULL, true,  NULL, false, false, 'Invoice type (N8N recognition)',      8),
    (v_invoices_table_id, 'invoice_amount',      'DECIMAL',   NULL, 15,   2,    true,  NULL, false, false, 'Invoice amount (N8N recognition)',    9),
    (v_invoices_table_id, 'invoice_date',        'VARCHAR',   50,   NULL, NULL, true,  NULL, false, false, 'Invoice date (N8N recognition)',      10),
    (v_invoices_table_id, 'recognition_status',  'VARCHAR',   20,   NULL, NULL, true,  NULL, false, false, 'Recognition status (PENDING/SUCCESS/FAILED)', 11),
    (v_invoices_table_id, 'recognition_result',  'TEXT',      NULL, NULL, NULL, true,  NULL, false, false, 'Recognition raw result JSON',         12),
    (v_invoices_table_id, 'uploaded_by',         'VARCHAR',   100,  NULL, NULL, false, NULL, false, false, 'User who uploaded',                   13),
    (v_invoices_table_id, 'uploaded_at',         'TIMESTAMP', NULL, NULL, NULL, false, NULL, false, false, 'Upload timestamp',                    14),
    (v_invoices_table_id, 'description',         'TEXT',      NULL, NULL, NULL, true,  NULL, false, false, 'Invoice description',                 15);

    RAISE NOTICE 'Table Invoices (SUB) created: id=%', v_invoices_table_id;

    -- =========================================================================
    -- Table 4: ApprovalActions (ACTION)
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_display_name, table_type, description, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'approval_actions', 'Approval Actions', 'ACTION', 'Action table for tracking approval history', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_display_name = EXCLUDED.table_display_name,
        table_type  = EXCLUDED.table_type,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_action_table_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_action_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, description, sort_order
    ) VALUES
    (v_action_table_id, 'id',              'BIGINT',    NULL, NULL, NULL, false, NULL, false, false, 'Action ID',                              1),
    (v_action_table_id, 'reimbursement_id','BIGINT',    NULL, NULL, NULL, false, NULL, false, false, 'Foreign key to Reimbursement table',     2),
    (v_action_table_id, 'action_type',     'VARCHAR',   50,   NULL, NULL, false, NULL, false, false, 'Action type',                            3),
    (v_action_table_id, 'action_by',       'VARCHAR',   100,  NULL, NULL, false, NULL, false, false, 'User who performed action',              4),
    (v_action_table_id, 'action_at',       'TIMESTAMP', NULL, NULL, NULL, false, NULL, false, false, 'Action timestamp',                       5),
    (v_action_table_id, 'comments',        'TEXT',      NULL, NULL, NULL, true,  NULL, false, false, 'Action comments',                        6),
    (v_action_table_id, 'previous_status', 'VARCHAR',   30,   NULL, NULL, true,  NULL, false, false, 'Status before action',                   7),
    (v_action_table_id, 'new_status',      'VARCHAR',   30,   NULL, NULL, false, NULL, false, false, 'Status after action',                    8);

    RAISE NOTICE 'Table ApprovalActions (ACTION) created: id=%', v_action_table_id;

    -- =========================================================================
    -- Summary
    -- =========================================================================
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Tables Setup Complete!';
    RAISE NOTICE 'Reimbursement (MAIN)     : id=%', v_reimbursement_table_id;
    RAISE NOTICE 'ExpenseItems (SUB)       : id=%', v_items_table_id;
    RAISE NOTICE 'Invoices (SUB)           : id=%', v_invoices_table_id;
    RAISE NOTICE 'ApprovalActions (ACTION)  : id=%', v_action_table_id;
    RAISE NOTICE 'Next: run 02-create-bpmn-process.sql';
    RAISE NOTICE '========================================';

END $tables$;
