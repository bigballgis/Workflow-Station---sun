-- =============================================================================
-- 13-procurement-workflow: Form Table Bindings
-- 基于数据库实际数据生成
--
-- 绑定关系:
--   Request Form:
--     binding: table=Request,            PRIMARY, EDITABLE, fk=NULL,       sort=1
--     binding: table=RequestItems,       SUB,     EDITABLE, fk=request_id, sort=2
--     binding: table=RequestAttachments, SUB,     EDITABLE, fk=request_id, sort=3
--   Approval Form:
--     binding: table=Request,            PRIMARY, READONLY, fk=NULL,       sort=1
--
-- IMPORTANT: subForms keys in config_json must use form_table_binding IDs,
-- not table_definition IDs.
-- =============================================================================

DO $bindings$
DECLARE
    v_function_unit_id      BIGINT;
    v_request_form_id       BIGINT;
    v_approval_form_id      BIGINT;
    v_main_table_id         BIGINT;
    v_items_table_id        BIGINT;
    v_attach_table_id       BIGINT;
    v_binding_items_id      BIGINT;
    v_binding_attach_id     BIGINT;
BEGIN
    SELECT id INTO v_function_unit_id FROM dw_function_units WHERE code = 'PROCUREMENT_WORKFLOW';
    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit PROCUREMENT_WORKFLOW not found.';
    END IF;

    SELECT id INTO v_request_form_id   FROM dw_form_definitions  WHERE function_unit_id = v_function_unit_id AND form_name  = 'Request Form';
    SELECT id INTO v_approval_form_id  FROM dw_form_definitions  WHERE function_unit_id = v_function_unit_id AND form_name  = 'Approval Form';
    SELECT id INTO v_main_table_id     FROM dw_table_definitions WHERE function_unit_id = v_function_unit_id AND table_name = 'Request';
    SELECT id INTO v_items_table_id    FROM dw_table_definitions WHERE function_unit_id = v_function_unit_id AND table_name = 'RequestItems';
    SELECT id INTO v_attach_table_id   FROM dw_table_definitions WHERE function_unit_id = v_function_unit_id AND table_name = 'RequestAttachments';

    DELETE FROM dw_form_table_bindings WHERE form_id IN (v_request_form_id, v_approval_form_id);

    -- -------------------------------------------------------------------------
    -- Request Form bindings
    -- -------------------------------------------------------------------------
    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_request_form_id, v_main_table_id, 'PRIMARY', 'EDITABLE', NULL, 1, NOW(), NOW());

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_request_form_id, v_items_table_id, 'SUB', 'EDITABLE', 'request_id', 2, NOW(), NOW())
    RETURNING id INTO v_binding_items_id;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_request_form_id, v_attach_table_id, 'SUB', 'EDITABLE', 'request_id', 3, NOW(), NOW())
    RETURNING id INTO v_binding_attach_id;

    -- -------------------------------------------------------------------------
    -- Approval Form bindings
    -- -------------------------------------------------------------------------
    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_approval_form_id, v_main_table_id, 'PRIMARY', 'READONLY', NULL, 1, NOW(), NOW());

    -- -------------------------------------------------------------------------
    -- Update subForms in Request Form config_json
    -- Keys = form_table_binding IDs (not table_definition IDs)
    -- -------------------------------------------------------------------------
    UPDATE dw_form_definitions
    SET config_json = jsonb_set(
        config_json::jsonb,
        '{subForms}',
        jsonb_build_object(
            v_binding_items_id::text, '{
                "rule": [
                    {"name":"ref_Fj8smm1s0sq3akc","type":"input","field":"item_name","props":{"maxlength":200,"placeholder":"Please input Item Name","showWordLimit":true},"title":"Item Name","_fc_id":"id_F10nmm1s0sq3ajc","hidden":false,"display":true,"validate":[{"mode":"required","message":"Item Name required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
                    {"name":"ref_F1wbmm2wztutakc","type":"inputNumber","field":"count","props":{"precision":0,"placeholder":"Please input Count"},"title":"Count","_fc_id":"id_Ffkxmm2wztutajc","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"inputNumber"},
                    {"name":"ref_Fspimm1s0sq3amc","type":"inputNumber","field":"unit_price","props":{"precision":2,"placeholder":"Please input Unit Price"},"title":"Unit Price","_fc_id":"id_Fhv3mm1s0sq3alc","hidden":false,"display":true,"validate":[{"mode":"required","message":"Unit Price required","trigger":"blur","required":true}],"_fc_drag_tag":"inputNumber"},
                    {"name":"ref_F4lomm1s0sq3aoc","type":"inputNumber","field":"total_price","props":{"precision":2,"placeholder":"Please input Total Price"},"title":"Total Price","_fc_id":"id_Fz6vmm1s0sq3anc","hidden":false,"display":true,"validate":[{"message":"Total Price required","trigger":"blur","required":true}],"_fc_drag_tag":"inputNumber"}
                ],
                "options":{"form":{"size":"default","inline":false,"labelWidth":"125px","labelPosition":"left","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}}
            }'::jsonb,
            v_binding_attach_id::text, '{
                "rule": [
                    {"name":"ref_Fattach_file","type":"upload","field":"file","props":{"action":"/api/v1/upload","accept":".jpg,.jpeg,.png,.pdf,.docx,.xlsx","limit":1,"multiple":false,"listType":"text","tip":"Supported: jpg/png/pdf/docx/xlsx, max 10MB"},"title":"Attachment File","_fc_id":"id_Fattach_file","hidden":false,"display":true,"validate":[{"message":"Attachment File required","trigger":"change","required":true}],"_fc_drag_tag":"upload"},
                    {"name":"ref_Flpimm1s4ixyaqc","type":"input","field":"file_name","props":{"maxlength":255,"placeholder":"Please input Original File Name","showWordLimit":true},"title":"Original File Name","_fc_id":"id_Fci5mm1s4ixyapc","hidden":false,"display":true,"validate":[{"message":"Original File Name required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
                    {"name":"ref_Fv41mm1s4ixyauc","type":"input","field":"file_type","props":{"maxlength":100,"placeholder":"Please input Type","showWordLimit":true},"title":"Type","_fc_id":"id_F8xymm1s4ixyatc","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"input"},
                    {"name":"ref_Fattach_desc","type":"input","field":"description","props":{"type":"textarea","placeholder":"Please input description","rows":2},"title":"Description","_fc_id":"id_Fattach_desc","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"input"}
                ],
                "options":{"form":{"size":"default","inline":false,"labelWidth":"125px","labelPosition":"left","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}}
            }'::jsonb
        )
    )
    WHERE id = v_request_form_id;

    RAISE NOTICE '========================================';
    RAISE NOTICE 'Form Table Bindings Complete!';
    RAISE NOTICE 'Request Form (id=%) : 3 bindings, subForms keys: %, %',
        v_request_form_id, v_binding_items_id, v_binding_attach_id;
    RAISE NOTICE 'Approval Form (id=%) : 1 binding', v_approval_form_id;
    RAISE NOTICE '========================================';

END $bindings$;
