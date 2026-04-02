-- =============================================================================
-- 16-meeting-participant-collection: Form Table Bindings
-- 会议参与人信息收集：表单-表绑定关系和子表单配置
--
-- Bindings:
--   Create Meeting Form:
--     binding: table=meeting,       PRIMARY, EDITABLE, fk=NULL,       sort=1
--     binding: table=participants,  SUB,     EDITABLE, fk=meeting_id, sort=2
--   Assign Participants Form:
--     binding: table=meeting,       PRIMARY, READONLY, fk=NULL,       sort=1
--     binding: table=participants,  SUB,     EDITABLE, fk=meeting_id, sort=2
--   Participant Info Form:
--     binding: table=participants,  PRIMARY, EDITABLE, fk=NULL,       sort=1
--
-- Dependencies: 00, 01
-- Execution order: 00 → 01 → 02 → 03
-- =============================================================================

DO $bindings$
DECLARE
    v_function_unit_id       BIGINT;
    v_create_form_id         BIGINT;
    v_assign_form_id         BIGINT;
    v_participant_form_id    BIGINT;
    v_meeting_table_id       BIGINT;
    v_participant_table_id   BIGINT;
    v_binding_create_sub_id  BIGINT;
    v_binding_assign_sub_id  BIGINT;
BEGIN
    SELECT id INTO v_function_unit_id FROM dw_function_units
    WHERE code = 'MEETING_PARTICIPANT_COLLECTION';
    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit not found.';
    END IF;

    SELECT id INTO v_create_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_function_unit_id AND form_name = 'Create Meeting Form';
    SELECT id INTO v_assign_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_function_unit_id AND form_name = 'Assign Participants Form';
    SELECT id INTO v_participant_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_function_unit_id AND form_name = 'Participant Info Form';

    SELECT id INTO v_meeting_table_id FROM dw_table_definitions
    WHERE function_unit_id = v_function_unit_id AND table_name = 'meeting';
    SELECT id INTO v_participant_table_id FROM dw_table_definitions
    WHERE function_unit_id = v_function_unit_id AND table_name = 'participants';

    -- Clean existing bindings
    DELETE FROM dw_form_table_bindings
    WHERE form_id IN (v_create_form_id, v_assign_form_id, v_participant_form_id);

    -- -------------------------------------------------------------------------
    -- Create Meeting Form bindings
    -- -------------------------------------------------------------------------
    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_create_form_id, v_meeting_table_id, 'PRIMARY', 'EDITABLE', NULL, 1, NOW(), NOW());

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_create_form_id, v_participant_table_id, 'SUB', 'EDITABLE', 'meeting_id', 2, NOW(), NOW())
    RETURNING id INTO v_binding_create_sub_id;

    -- -------------------------------------------------------------------------
    -- Assign Participants Form bindings
    -- -------------------------------------------------------------------------
    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_assign_form_id, v_meeting_table_id, 'PRIMARY', 'READONLY', NULL, 1, NOW(), NOW());

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_assign_form_id, v_participant_table_id, 'SUB', 'EDITABLE', 'meeting_id', 2, NOW(), NOW())
    RETURNING id INTO v_binding_assign_sub_id;

    -- -------------------------------------------------------------------------
    -- Participant Info Form bindings (子任务表单绑定参与人子表)
    -- -------------------------------------------------------------------------
    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_participant_form_id, v_participant_table_id, 'PRIMARY', 'EDITABLE', NULL, 1, NOW(), NOW());

    -- -------------------------------------------------------------------------
    -- Update subForms in Create Meeting Form config_json
    -- -------------------------------------------------------------------------
    UPDATE dw_form_definitions
    SET config_json = jsonb_set(
        config_json::jsonb,
        '{subForms}',
        jsonb_build_object(
            v_binding_create_sub_id::text, '{
                "rule": [
                    {"name":"ref_p_name","type":"input","field":"name","props":{"maxlength":100,"placeholder":"请输入参与人姓名","showWordLimit":true},"title":"姓名","_fc_id":"id_p_name","hidden":false,"display":true,"validate":[{"message":"姓名必填","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
                    {"name":"ref_p_dept","type":"input","field":"department","props":{"maxlength":100,"placeholder":"请输入部门","showWordLimit":true},"title":"部门","_fc_id":"id_p_dept","hidden":false,"display":true,"_fc_drag_tag":"input"},
                    {"name":"ref_p_email","type":"input","field":"email","props":{"maxlength":255,"placeholder":"请输入邮箱"},"title":"邮箱","_fc_id":"id_p_email","hidden":false,"display":true,"_fc_drag_tag":"input"}
                ],
                "options":{"form":{"size":"default","inline":false,"labelWidth":"100px","labelPosition":"left"},"resetBtn":{"show":false},"submitBtn":{"show":true,"innerText":"确认"}}
            }'::jsonb
        )
    )
    WHERE id = v_create_form_id;

    -- -------------------------------------------------------------------------
    -- Update subForms in Assign Participants Form config_json
    -- -------------------------------------------------------------------------
    UPDATE dw_form_definitions
    SET config_json = jsonb_set(
        config_json::jsonb,
        '{subForms}',
        jsonb_build_object(
            v_binding_assign_sub_id::text, '{
                "rule": [
                    {"name":"ref_ap_name","type":"input","field":"name","props":{"placeholder":"姓名"},"title":"姓名","_fc_id":"id_ap_name","hidden":false,"display":true,"_fc_drag_tag":"input"},
                    {"name":"ref_ap_dept","type":"input","field":"department","props":{"placeholder":"部门"},"title":"部门","_fc_id":"id_ap_dept","hidden":false,"display":true,"_fc_drag_tag":"input"},
                    {"name":"ref_ap_email","type":"input","field":"email","props":{"placeholder":"邮箱"},"title":"邮箱","_fc_id":"id_ap_email","hidden":false,"display":true,"_fc_drag_tag":"input"}
                ],
                "options":{"form":{"size":"default","inline":false,"labelWidth":"100px","labelPosition":"left"},"resetBtn":{"show":false},"submitBtn":{"show":true,"innerText":"确认"}}
            }'::jsonb
        )
    )
    WHERE id = v_assign_form_id;

    RAISE NOTICE '========================================';
    RAISE NOTICE 'Form Table Bindings Complete!';
    RAISE NOTICE 'Create Meeting Form (id=%): 2 bindings, subForms key: %',
        v_create_form_id, v_binding_create_sub_id;
    RAISE NOTICE 'Assign Participants Form (id=%): 2 bindings, subForms key: %',
        v_assign_form_id, v_binding_assign_sub_id;
    RAISE NOTICE 'Participant Info Form (id=%): 1 binding', v_participant_form_id;
    RAISE NOTICE '========================================';

END $bindings$;
