-- =============================================================================
-- 14-travel-expense-reimbursement: Form Table Bindings
-- 差旅报销功能单元：表单-表绑定关系和子表单配置
--
-- Bindings:
--   Reimbursement Form:
--     binding: table=reimbursement,   PRIMARY, EDITABLE, fk=NULL,             sort=1
--     binding: table=expense_items,   SUB,     EDITABLE, fk=reimbursement_id, sort=2
--     binding: table=invoices,        SUB,     EDITABLE, fk=reimbursement_id, sort=3
--   Approval Form:
--     binding: table=reimbursement,   PRIMARY, READONLY, fk=NULL,             sort=1
--
-- IMPORTANT: subForms keys in config_json must use form_table_binding IDs,
-- not table_definition IDs.
--
-- Dependencies: 00-create-function-unit.sql (Function Unit, Forms)
--               01-create-tables.sql (Table Definitions)
-- Execution order: 00 → 01 → 02 → 03
-- =============================================================================

DO $bindings$
DECLARE
    v_function_unit_id      BIGINT;
    v_reimbursement_form_id BIGINT;
    v_approval_form_id      BIGINT;
    v_main_table_id         BIGINT;
    v_items_table_id        BIGINT;
    v_invoices_table_id     BIGINT;
    v_binding_items_id      BIGINT;
    v_binding_invoices_id   BIGINT;
BEGIN
    SELECT id INTO v_function_unit_id FROM dw_function_units WHERE code = 'TRAVEL_EXPENSE_REIMBURSEMENT';
    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit TRAVEL_EXPENSE_REIMBURSEMENT not found. Run 00-create-function-unit.sql first.';
    END IF;

    SELECT id INTO v_reimbursement_form_id FROM dw_form_definitions  WHERE function_unit_id = v_function_unit_id AND form_name  = 'Reimbursement Form';
    SELECT id INTO v_approval_form_id      FROM dw_form_definitions  WHERE function_unit_id = v_function_unit_id AND form_name  = 'Approval Form';
    SELECT id INTO v_main_table_id         FROM dw_table_definitions WHERE function_unit_id = v_function_unit_id AND table_name = 'reimbursement';
    SELECT id INTO v_items_table_id        FROM dw_table_definitions WHERE function_unit_id = v_function_unit_id AND table_name = 'expense_items';
    SELECT id INTO v_invoices_table_id     FROM dw_table_definitions WHERE function_unit_id = v_function_unit_id AND table_name = 'invoices';

    IF v_reimbursement_form_id IS NULL OR v_approval_form_id IS NULL THEN
        RAISE EXCEPTION 'Forms not found. Run 00-create-function-unit.sql first.';
    END IF;

    IF v_main_table_id IS NULL OR v_items_table_id IS NULL OR v_invoices_table_id IS NULL THEN
        RAISE EXCEPTION 'Table definitions not found. Run 01-create-tables.sql first.';
    END IF;

    DELETE FROM dw_form_table_bindings WHERE form_id IN (v_reimbursement_form_id, v_approval_form_id);

    -- -------------------------------------------------------------------------
    -- Reimbursement Form bindings
    -- -------------------------------------------------------------------------
    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_reimbursement_form_id, v_main_table_id, 'PRIMARY', 'EDITABLE', NULL, 1, NOW(), NOW());

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_reimbursement_form_id, v_items_table_id, 'SUB', 'EDITABLE', 'reimbursement_id', 2, NOW(), NOW())
    RETURNING id INTO v_binding_items_id;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_reimbursement_form_id, v_invoices_table_id, 'SUB', 'EDITABLE', 'reimbursement_id', 3, NOW(), NOW())
    RETURNING id INTO v_binding_invoices_id;

    -- -------------------------------------------------------------------------
    -- Approval Form bindings
    -- -------------------------------------------------------------------------
    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_approval_form_id, v_main_table_id, 'PRIMARY', 'READONLY', NULL, 1, NOW(), NOW());

    -- -------------------------------------------------------------------------
    -- Update subForms in Reimbursement Form config_json
    -- Keys = form_table_binding IDs (not table_definition IDs)
    -- -------------------------------------------------------------------------
    UPDATE dw_form_definitions
    SET config_json = jsonb_set(
        config_json::jsonb,
        '{subForms}',
        jsonb_build_object(
            v_binding_items_id::text, '{
                "rule": [
                    {"name":"ref_Fei1mm1s0c1gacc","type":"select","field":"expense_type","props":{"placeholder":"Please select Expense Type","options":[{"label":"交通","value":"交通"},{"label":"住宿","value":"住宿"},{"label":"餐饮","value":"餐饮"},{"label":"其他","value":"其他"}]},"title":"Expense Type","_fc_id":"id_Fei1mm1s0c1gabc","hidden":false,"display":true,"validate":[{"mode":"required","message":"Expense Type required","trigger":"change","required":true}],"_fc_drag_tag":"select"},
                    {"name":"ref_Fei2mm1s0c1gaec","type":"datePicker","field":"expense_date","props":{"type":"date","placeholder":"Please select Expense Date","valueFormat":"YYYY-MM-DD"},"title":"Expense Date","_fc_id":"id_Fei2mm1s0c1gadc","hidden":false,"display":true,"validate":[{"mode":"required","message":"Expense Date required","trigger":"blur","required":true}],"_fc_drag_tag":"datePicker"},
                    {"name":"ref_Fei3mm1s0c1gagc","type":"inputNumber","field":"amount","props":{"precision":2,"placeholder":"Please input Amount"},"title":"Amount","_fc_id":"id_Fei3mm1s0c1gafc","hidden":false,"display":true,"validate":[{"mode":"required","message":"Amount required","trigger":"blur","required":true}],"_fc_drag_tag":"inputNumber"},
                    {"name":"ref_Fei4mm1s0c1gaic","type":"input","field":"description","props":{"placeholder":"Please input Description"},"title":"Description","_fc_id":"id_Fei4mm1s0c1gahc","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"input"}
                ],
                "options":{"form":{"size":"default","inline":false,"labelWidth":"125px","labelPosition":"left","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}}
            }'::jsonb,
            v_binding_invoices_id::text, '{
                "rule": [
                    {"name":"ref_Fin1mm1s0c2gacc","type":"upload","field":"file","props":{"action":"/api/v1/upload","accept":".jpg,.jpeg,.png,.pdf","limit":1,"multiple":false,"listType":"text","tip":"Supported: jpg/png/pdf, max 10MB"},"title":"Invoice File","_fc_id":"id_Fin1mm1s0c2gabc","hidden":false,"display":true,"_fc_drag_tag":"upload"},
                    {"name":"ref_Fin2mm1s0c2gaec","type":"input","field":"file_name","props":{"placeholder":"Please input File Name"},"title":"File Name","_fc_id":"id_Fin2mm1s0c2gadc","hidden":false,"display":true,"_fc_drag_tag":"input"},
                    {"name":"ref_Fin3mm1s0c2gagc","type":"input","field":"description","props":{"placeholder":"Please input Description"},"title":"Description","_fc_id":"id_Fin3mm1s0c2gafc","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"input"}
                ],
                "options":{"form":{"size":"default","inline":false,"labelWidth":"125px","labelPosition":"left","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}}
            }'::jsonb
        )
    )
    WHERE id = v_reimbursement_form_id;

    RAISE NOTICE '========================================';
    RAISE NOTICE 'Form Table Bindings Complete!';
    RAISE NOTICE 'Reimbursement Form (id=%) : 3 bindings, subForms keys: %, %',
        v_reimbursement_form_id, v_binding_items_id, v_binding_invoices_id;
    RAISE NOTICE 'Approval Form (id=%) : 1 binding', v_approval_form_id;
    RAISE NOTICE '========================================';

END $bindings$;
