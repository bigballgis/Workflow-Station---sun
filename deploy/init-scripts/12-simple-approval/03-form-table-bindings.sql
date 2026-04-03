-- =============================================================================
-- 12-simple-approval: Form Table Bindings
-- 基于数据库实际数据生成
--
-- 绑定关系:
--   Request Form:
--     binding: table=Request,            PRIMARY, EDITABLE, fk=NULL,       sort=1
--     binding: table=RequestItems,       SUB,     EDITABLE, fk=request_id, sort=2
--     binding: table=RequestAttachments, RELATED, EDITABLE, fk=request_id, sort=3
--   Approval Form:
--     binding: table=Request,            PRIMARY, READONLY, fk=NULL,       sort=1
-- =============================================================================

DO $bindings$
DECLARE
    v_function_unit_id  BIGINT;
    v_request_form_id   BIGINT;
    v_approval_form_id  BIGINT;
    v_main_table_id     BIGINT;
    v_sub_table_id      BIGINT;
    v_relation_table_id BIGINT;
    v_binding_sub_id    BIGINT;
    v_binding_rel_id    BIGINT;
BEGIN
    SELECT id INTO v_function_unit_id FROM dw_function_units WHERE code = 'fu-20260403-a1b2c1';
    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit fu-20260403-a1b2c1 not found.';
    END IF;

    SELECT id INTO v_request_form_id   FROM dw_form_definitions  WHERE function_unit_id = v_function_unit_id AND form_name  = 'Request Form';
    SELECT id INTO v_approval_form_id  FROM dw_form_definitions  WHERE function_unit_id = v_function_unit_id AND form_name  = 'Approval Form';
    SELECT id INTO v_main_table_id     FROM dw_table_definitions WHERE function_unit_id = v_function_unit_id AND table_name = 'Request';
    SELECT id INTO v_sub_table_id      FROM dw_table_definitions WHERE function_unit_id = v_function_unit_id AND table_name = 'RequestItems';
    SELECT id INTO v_relation_table_id FROM dw_table_definitions WHERE function_unit_id = v_function_unit_id AND table_name = 'RequestAttachments';

    -- Clear existing bindings for these forms
    DELETE FROM dw_form_table_bindings WHERE form_id IN (v_request_form_id, v_approval_form_id);

    -- -------------------------------------------------------------------------
    -- Request Form bindings (3 bindings)
    -- -------------------------------------------------------------------------
    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_request_form_id, v_main_table_id, 'PRIMARY', 'EDITABLE', NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_request_form_id, v_sub_table_id, 'SUB', 'EDITABLE', 'request_id', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_binding_sub_id;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_request_form_id, v_relation_table_id, 'RELATED', 'EDITABLE', 'request_id', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_binding_rel_id;

    -- -------------------------------------------------------------------------
    -- Approval Form bindings (1 binding)
    -- -------------------------------------------------------------------------
    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_approval_form_id, v_main_table_id, 'PRIMARY', 'READONLY', NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

    -- Update bound_table_id on Approval Form (Request Form 无 bound_table_id)
    UPDATE dw_form_definitions SET bound_table_id = v_main_table_id
    WHERE id = v_approval_form_id;

    -- -------------------------------------------------------------------------
    -- Update subForms in Request Form config_json
    -- IMPORTANT: keys = form_table_binding IDs (not table_definition IDs)
    -- This is consistent with 13-procurement-workflow and 14-travel-expense-reimbursement
    -- -------------------------------------------------------------------------
    UPDATE dw_form_definitions
    SET config_json = jsonb_set(
        config_json::jsonb,
        '{subForms}',
        jsonb_build_object(
            -- RequestItems 子表单字段配置（简化版，与 DB 一致）
            v_binding_sub_id::text, '{
                "rule": [
                    {"name":"ref_Fdobmlz9ogz1acc","type":"input","field":"item_name","props":{"maxlength":200,"placeholder":"Please input Item name","showWordLimit":true},"title":"Item name","_fc_id":"id_F0h8mlz9ogz1abc","hidden":false,"display":true,"validate":[{"message":"Item name required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
                    {"name":"ref_Fjjfmlz9ogz1aec","type":"inputNumber","field":"unit_price","props":{"precision":2,"placeholder":"Please input Unit price"},"title":"Unit price","_fc_id":"id_Fxfxmlz9ogz1adc","hidden":false,"display":true,"validate":[{"message":"Unit price required","trigger":"blur","required":true}],"_fc_drag_tag":"inputNumber"},
                    {"name":"ref_F953mlz9ogz1agc","type":"inputNumber","field":"total_price","props":{"precision":2,"placeholder":"Please input Total price"},"title":"Total price","_fc_id":"id_Fxgcmlz9ogz1afc","hidden":false,"display":true,"validate":[{"message":"Total price required","trigger":"blur","required":true}],"_fc_drag_tag":"inputNumber"}
                ],
                "options":{"form":{"size":"default","inline":false,"labelWidth":"125px","labelPosition":"right","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}}
            }'::jsonb,
            -- RequestAttachments 子表单字段配置（简化版，与 DB 一致）
            v_binding_rel_id::text, '{
                "rule": [
                    {"name":"ref_F3wsmlz9qczaaic","type":"input","field":"file_name","props":{"maxlength":255,"placeholder":"Please input Original file name","showWordLimit":true},"title":"Original file name","_fc_id":"id_Fgwnmlz9qczaahc","hidden":false,"display":true,"validate":[{"mode":"required","message":"Original file name required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
                    {"name":"ref_Flbumlz9qczaakc","type":"input","field":"description","props":{"rows":3,"type":"textarea","placeholder":"Please input Attachment description"},"title":"Attachment description","_fc_id":"id_F27nmlz9qczaajc","hidden":false,"display":true,"_fc_drag_tag":"input"},
                    {"name":"ref_F0p3mlz9qczaaqc","type":"input","field":"file_type","props":{"maxlength":100,"placeholder":"Please input MIME type","showWordLimit":true},"title":"MIME type","_fc_id":"id_Fo96mlz9qczaapc","hidden":false,"display":true,"validate":[{"message":"MIME type required","trigger":"blur","required":true}],"_fc_drag_tag":"input"}
                ],
                "options":{"form":{"size":"default","inline":false,"labelWidth":"125px","labelPosition":"right","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}}
            }'::jsonb
        )
    )
    WHERE id = v_request_form_id;

    RAISE NOTICE '========================================';
    RAISE NOTICE 'Form Table Bindings Complete!';
    RAISE NOTICE 'Request Form (id=%) : 3 bindings, subForms updated (keys: %, %)',
        v_request_form_id, v_binding_sub_id, v_binding_rel_id;
    RAISE NOTICE 'Approval Form (id=%) : 1 binding, bound_table_id=%', v_approval_form_id, v_main_table_id;
    RAISE NOTICE '========================================';

END $bindings$;
