-- =============================================================================
-- 15-platform-showcase: 表单-表绑定 + Request 表单 subForms
-- =============================================================================

DO $bindings$
DECLARE
    v_fu_id           BIGINT;
    v_request_form_id BIGINT;
    v_approval_form_id BIGINT;
    v_main_id         BIGINT;
    v_sub_id          BIGINT;
    v_rel_id          BIGINT;
    v_bind_sub_id     BIGINT;
    v_bind_rel_id     BIGINT;
BEGIN
    SELECT id INTO v_fu_id FROM dw_function_units WHERE code = 'PLATFORM_SHOWCASE';
    IF v_fu_id IS NULL THEN
        RAISE EXCEPTION 'PLATFORM_SHOWCASE not found.';
    END IF;

    SELECT id INTO v_request_form_id FROM dw_form_definitions WHERE function_unit_id = v_fu_id AND form_name = 'Showcase Request Form';
    SELECT id INTO v_approval_form_id FROM dw_form_definitions WHERE function_unit_id = v_fu_id AND form_name = 'Showcase Approval Form';
    SELECT id INTO v_main_id FROM dw_table_definitions WHERE function_unit_id = v_fu_id AND table_name = 'ShowcaseApp';
    SELECT id INTO v_sub_id FROM dw_table_definitions WHERE function_unit_id = v_fu_id AND table_name = 'ShowcaseLine';
    SELECT id INTO v_rel_id FROM dw_table_definitions WHERE function_unit_id = v_fu_id AND table_name = 'ShowcaseDoc';

    DELETE FROM dw_form_table_bindings WHERE form_id IN (v_request_form_id, v_approval_form_id);

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES (
        v_request_form_id, v_main_id, 'PRIMARY', 'EDITABLE', NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES (
        v_request_form_id, v_sub_id, 'SUB', 'EDITABLE', 'showcase_app_id', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) RETURNING id INTO v_bind_sub_id;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES (
        v_request_form_id, v_rel_id, 'RELATED', 'EDITABLE', 'showcase_app_id', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) RETURNING id INTO v_bind_rel_id;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES (
        v_approval_form_id, v_main_id, 'PRIMARY', 'READONLY', NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

    UPDATE dw_form_definitions SET bound_table_id = v_main_id, updated_at = CURRENT_TIMESTAMP
    WHERE id = v_approval_form_id;

    UPDATE dw_form_definitions
    SET config_json = jsonb_set(
            COALESCE(config_json::jsonb, '{}'::jsonb),
            '{subForms}',
            jsonb_build_object(
                v_bind_sub_id::text,
                '{"rule":[{"name":"ref_ps_line","type":"input","field":"line_desc","props":{"maxlength":500,"placeholder":"行说明"},"title":"行说明","_fc_id":"id_ps_line","hidden":false,"display":true,"_fc_drag_tag":"input"},{"name":"ref_ps_qty","type":"inputNumber","field":"quantity","props":{"precision":0,"placeholder":"数量"},"title":"数量","_fc_id":"id_ps_qty","hidden":false,"display":true,"validate":[{"message":"必填","trigger":"blur","required":true}],"_fc_drag_tag":"inputNumber"}],"options":{"form":{"size":"default","inline":false,"labelWidth":"120px","labelPosition":"right","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}}}'::jsonb,
                v_bind_rel_id::text,
                '{"rule":[{"name":"ref_ps_fn","type":"input","field":"file_name","props":{"maxlength":255,"placeholder":"文件名"},"title":"文件名","_fc_id":"id_ps_fn","hidden":false,"display":true,"validate":[{"message":"必填","trigger":"blur","required":true}],"_fc_drag_tag":"input"},{"name":"ref_ps_ft","type":"input","field":"file_type","props":{"maxlength":100,"placeholder":"MIME"},"title":"文件类型","_fc_id":"id_ps_ft","hidden":false,"display":true,"_fc_drag_tag":"input"}],"options":{"form":{"size":"default","inline":false,"labelWidth":"120px","labelPosition":"right","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}}}'::jsonb
            )
        ),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = v_request_form_id;

    RAISE NOTICE 'PLATFORM_SHOWCASE bindings OK. Next: 04-table-relations.sql';

END $bindings$;
