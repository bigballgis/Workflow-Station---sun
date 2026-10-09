-- =============================================================================
-- 21-fu-call-demo / 05: Caller tables
--
--   purchase_request (MAIN) — the request
--   extra_vendors    (SUB)  — one row per additional vendor → one CALL each
--   budget_lines     (SUB)  — one row per budget line → one MI SUB-TASK each
--
-- The two sub-tables exist to keep the demo's two mechanisms visibly separate:
-- extra_vendors feeds a callActivity, budget_lines feeds a multi-instance
-- sub-process. Same shape of data, deliberately different machinery.
--
-- Dependencies: 04-caller-function-unit.sql
-- =============================================================================

DO $tables$
DECLARE
    v_fu_id          BIGINT;
    v_main_id        BIGINT;
    v_vendors_id     BIGINT;
    v_budget_id      BIGINT;
BEGIN
    SELECT id INTO v_fu_id FROM dw_function_units WHERE code = 'fu-call-demo-purchase';
    IF v_fu_id IS NULL THEN
        RAISE EXCEPTION 'Function unit fu-call-demo-purchase not found. Run 04 first.';
    END IF;

    -- =========================================================================
    -- purchase_request (MAIN)
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_display_name, table_type, display_name, created_at, updated_at
    ) VALUES (
        v_fu_id, 'purchase_request', 'Purchase Request', 'MAIN',
        'The purchase request being raised.',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (table_name) DO UPDATE SET
        table_display_name = EXCLUDED.table_display_name,
        table_type         = EXCLUDED.table_type,
        display_name       = EXCLUDED.display_name,
        updated_at         = CURRENT_TIMESTAMP
    RETURNING id INTO v_main_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_main_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, display_name, sort_order
    ) VALUES
    (v_main_id, 'id',             'BIGINT',    NULL, NULL, NULL, false, NULL, true,  false, 'Primary key',    1),
    (v_main_id, 'title',          'VARCHAR',   200,  NULL, NULL, false, NULL, false, false, 'Request title',  2),
    (v_main_id, 'department',     'VARCHAR',   100,  NULL, NULL, false, NULL, false, false, 'Department',     3),
    (v_main_id, 'total_amount',   'DECIMAL',   NULL, 14,   2,    true,  NULL, false, false, 'Total amount',   4),
    (v_main_id, 'primary_vendor', 'VARCHAR',   200,  NULL, NULL, false, NULL, false, false, 'Primary vendor', 5),
    (v_main_id, 'justification',  'TEXT',      NULL, NULL, NULL, true,  NULL, false, false, 'Justification',  6),
    (v_main_id, 'approver_note',  'TEXT',      NULL, NULL, NULL, true,  NULL, false, false, 'Approver note',  7),
    -- Copied back from the primary vendor's check when it finishes (the call's output mapping).
    (v_main_id, 'primary_vendor_result', 'VARCHAR', 30, NULL, NULL, true, NULL, false, false, 'Primary vendor check result', 8),
    (v_main_id, 'created_at',     'TIMESTAMP', NULL, NULL, NULL, true,  NULL, false, false, 'Created at',     9);

    RAISE NOTICE '[21-fu-call-demo] Caller table purchase_request (MAIN): id=%', v_main_id;

    -- =========================================================================
    -- extra_vendors (SUB) — each row becomes its own CALL to the vendor check
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_display_name, table_type, display_name, created_at, updated_at
    ) VALUES (
        -- No description: the form's sub-table widget prints it after the name. What the rows
        -- are for is explained in README.md.
        v_fu_id, 'extra_vendors', 'Additional Vendors', 'SUB', NULL,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (table_name) DO UPDATE SET
        table_display_name = EXCLUDED.table_display_name,
        table_type         = EXCLUDED.table_type,
        display_name       = EXCLUDED.display_name,
        updated_at         = CURRENT_TIMESTAMP
    RETURNING id INTO v_vendors_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_vendors_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, display_name, sort_order,
        is_foreign_key, ref_table_id
    ) VALUES
    (v_vendors_id, 'id',           'VARCHAR',   64,   NULL, NULL, false, NULL, true,  false, 'Row id',      1, false, NULL),
    (v_vendors_id, 'request_id',   'BIGINT',    NULL, NULL, NULL, true,  NULL, false, false, 'Request',     2, true,  v_main_id),
    (v_vendors_id, 'vendor_name',  'VARCHAR',   200,  NULL, NULL, false, NULL, false, false, 'Vendor',      3, false, NULL),
    (v_vendors_id, 'category',     'VARCHAR',   100,  NULL, NULL, true,  NULL, false, false, 'Category',    4, false, NULL),
    (v_vendors_id, 'check_status', 'VARCHAR',   30,   NULL, NULL, true,  NULL, false, false, 'Check status',5, false, NULL);

    RAISE NOTICE '[21-fu-call-demo] Caller table extra_vendors (SUB): id=%', v_vendors_id;

    -- =========================================================================
    -- budget_lines (SUB) — each row becomes an MI SUB-TASK (not a call)
    --
    -- owner_user_id is the assignee field the multi-instance machinery reads,
    -- and task_status / task_current_node are the platform's progress columns.
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_display_name, table_type, display_name, created_at, updated_at
    ) VALUES (
        v_fu_id, 'budget_lines', 'Budget Lines', 'SUB', NULL,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (table_name) DO UPDATE SET
        table_display_name = EXCLUDED.table_display_name,
        table_type         = EXCLUDED.table_type,
        display_name       = EXCLUDED.display_name,
        updated_at         = CURRENT_TIMESTAMP
    RETURNING id INTO v_budget_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_budget_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, display_name, sort_order,
        is_foreign_key, ref_table_id
    ) VALUES
    (v_budget_id, 'id',                'VARCHAR',   64,   NULL, NULL, false, NULL, true,  false, 'Row id',           1, false, NULL),
    (v_budget_id, 'request_id',        'BIGINT',    NULL, NULL, NULL, true,  NULL, false, false, 'Request',          2, true,  v_main_id),
    (v_budget_id, 'line_item',         'VARCHAR',   200,  NULL, NULL, false, NULL, false, false, 'Line item',        3, false, NULL),
    (v_budget_id, 'amount',            'DECIMAL',   NULL, 14,   2,    true,  NULL, false, false, 'Amount',           4, false, NULL),
    (v_budget_id, 'owner_user_id',     'VARCHAR',   64,   NULL, NULL, true,  NULL, false, false, 'Budget owner',     5, false, NULL),
    (v_budget_id, 'owner_confirmed',   'VARCHAR',   10,   NULL, NULL, true,  NULL, false, false, 'Confirmed',        6, false, NULL),
    (v_budget_id, 'task_status',       'VARCHAR',   30,   NULL, NULL, true,  NULL, false, false, 'Sub-task status',  7, false, NULL),
    (v_budget_id, 'task_current_node', 'VARCHAR',   100,  NULL, NULL, true,  NULL, false, false, 'Current step',     8, false, NULL);

    RAISE NOTICE '[21-fu-call-demo] Caller table budget_lines (SUB): id=%', v_budget_id;
END $tables$;
