-- =============================================================================
-- 21-fu-call-demo / 03: Callee form↔table bindings and stage binding
--
-- Dependencies: 00, 01, 02
-- =============================================================================

DO $bindings$
DECLARE
    v_fu_id          BIGINT;
    v_form_id        BIGINT;
    v_table_id       BIGINT;
BEGIN
    SELECT id INTO v_fu_id FROM dw_function_units WHERE code = 'fu-call-demo-vendor';
    IF v_fu_id IS NULL THEN
        RAISE EXCEPTION 'Function unit fu-call-demo-vendor not found.';
    END IF;

    SELECT id INTO v_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_fu_id AND form_name = 'Vendor Review Form';

    SELECT id INTO v_table_id FROM dw_table_definitions
    WHERE function_unit_id = v_fu_id AND table_name = 'vendor_check';

    DELETE FROM dw_form_table_bindings WHERE form_id = v_form_id;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_form_id, v_table_id, 'PRIMARY', 'EDITABLE', NULL, 1, NOW(), NOW());

    -- Bind the form to the review step so the Portal renders it there.
    DELETE FROM dw_form_stage_bindings WHERE form_id = v_form_id;

    INSERT INTO dw_form_stage_bindings (form_id, stage_id, stage_name, read_only, scene, created_at)
    VALUES (v_form_id, 'VC_Task_Review', 'Review Vendor', false, 'TASK', NOW());

    -- Main table view so the unit's rows are listable under Data → Views.
    DELETE FROM dw_main_table_view_fields WHERE view_config_id IN (
        SELECT id FROM dw_main_table_view_configs WHERE function_unit_id = v_fu_id);
    DELETE FROM dw_main_table_view_configs WHERE function_unit_id = v_fu_id;

    RAISE NOTICE '[21-fu-call-demo] Callee bindings written (form=%, table=%)', v_form_id, v_table_id;
END $bindings$;
