-- =============================================================================
-- 21-fu-call-demo / 01: Callee tables — vendor_check (MAIN)
--
-- Row data lives in JSON on the process instance (see the
-- json-row-storage-no-physical-tables rule); these are design definitions only,
-- no physical tables are created.
--
-- Dependencies: 00-callee-function-unit.sql
-- =============================================================================

DO $tables$
DECLARE
    v_fu_id    BIGINT;
    v_table_id BIGINT;
BEGIN
    SELECT id INTO v_fu_id FROM dw_function_units WHERE code = 'fu-call-demo-vendor';
    IF v_fu_id IS NULL THEN
        RAISE EXCEPTION 'Function unit fu-call-demo-vendor not found. Run 00-callee-function-unit.sql first.';
    END IF;

    -- =========================================================================
    -- vendor_check (MAIN) — one row per call, i.e. per vendor reviewed
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_display_name, table_type, display_name, created_at, updated_at
    ) VALUES (
        v_fu_id, 'vendor_check', 'Vendor Check', 'MAIN',
        'One qualification check: the vendor reviewed and the verdict reached.',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (table_name) DO UPDATE SET
        table_display_name = EXCLUDED.table_display_name,
        table_type         = EXCLUDED.table_type,
        display_name       = EXCLUDED.display_name,
        updated_at         = CURRENT_TIMESTAMP
    RETURNING id INTO v_table_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, display_name, sort_order
    ) VALUES
    (v_table_id, 'id',               'BIGINT',    NULL, NULL, NULL, false, NULL, true,  false, 'Primary key',      1),
    (v_table_id, 'vendor_name',      'VARCHAR',   200,  NULL, NULL, false, NULL, false, false, 'Vendor',           2),
    (v_table_id, 'category',         'VARCHAR',   100,  NULL, NULL, true,  NULL, false, false, 'Category',         3),
    (v_table_id, 'check_result',     'VARCHAR',   30,   NULL, NULL, true,  NULL, false, false, 'Check result',     4),
    (v_table_id, 'risk_score',       'INTEGER',   NULL, NULL, NULL, true,  NULL, false, false, 'Risk score',       5),
    (v_table_id, 'reviewer_comment', 'TEXT',      NULL, NULL, NULL, true,  NULL, false, false, 'Reviewer comment', 6),
    (v_table_id, 'created_at',       'TIMESTAMP', NULL, NULL, NULL, true,  NULL, false, false, 'Created at',       7);

    RAISE NOTICE '[21-fu-call-demo] Callee table vendor_check (MAIN): id=%', v_table_id;
END $tables$;
