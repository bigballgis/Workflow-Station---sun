-- =============================================================================
-- 16-meeting-participant-collection: Translate All Chinese Data to English
-- Migrates all user-visible Chinese strings in function unit fu-20260403-a1b2c5
-- to English across: action names, form config, table display names, BPMN XML,
-- form stage bindings.
--
-- Safe to re-run: all updates are idempotent.
-- Dependencies: 00 → 01 → 02 → 03 must have been executed first.
-- =============================================================================

DO $translate$
DECLARE
    v_function_unit_id     BIGINT;
    v_create_form_id       BIGINT;
    v_participant_form_id  BIGINT;
    v_participant_table_id BIGINT;
    v_binding_sub_id       BIGINT;
BEGIN
    SELECT id INTO v_function_unit_id FROM dw_function_units
    WHERE code = 'fu-20260403-a1b2c5';
    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit fu-20260403-a1b2c5 not found.';
    END IF;

    SELECT id INTO v_create_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_function_unit_id AND form_name = 'Create Meeting Form';

    SELECT id INTO v_participant_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_function_unit_id AND form_name = 'Participant Info Form';

    SELECT id INTO v_participant_table_id FROM dw_table_definitions
    WHERE function_unit_id = v_function_unit_id AND table_name = 'participants';

    -- =========================================================================
    -- 1. Function Unit description
    -- =========================================================================
    UPDATE dw_function_units
    SET description = 'Meeting participant info collection workflow: demonstrates multi-instance subprocess with dynamic task distribution. The organizer creates a meeting and adds participants; after assigning a handler for each participant, the system auto-creates parallel sub-tasks so each participant independently fills in their attendance info, and the process advances once all are complete.',
        updated_at  = CURRENT_TIMESTAMP
    WHERE code = 'fu-20260403-a1b2c5';

    RAISE NOTICE 'Updated function unit description';

    -- =========================================================================
    -- 2. Action names and config_json messages
    -- =========================================================================
    UPDATE dw_action_definitions
    SET action_name = 'Submit Meeting',
        config_json = jsonb_set(
            jsonb_set(config_json::jsonb,
                '{confirmMessage}',
                '"Confirm submission of this meeting? Once submitted, the participant assignment stage will begin."'::jsonb),
            '{successMessage}',
            '"Meeting submitted. Please assign handlers for each participant."'::jsonb
        ),
        updated_at = CURRENT_TIMESTAMP
    WHERE function_unit_id = v_function_unit_id AND action_name = '提交会议';

    UPDATE dw_action_definitions
    SET action_name = 'Complete Assignment',
        config_json = jsonb_set(
            jsonb_set(config_json::jsonb,
                '{confirmMessage}',
                '"Confirm that all participants have been assigned a handler? The system will automatically create a fill-in task for each participant."'::jsonb),
            '{successMessage}',
            '"Assignment complete. Sub-tasks have been created."'::jsonb
        ),
        updated_at = CURRENT_TIMESTAMP
    WHERE function_unit_id = v_function_unit_id AND action_name = '完成分配';

    UPDATE dw_action_definitions
    SET action_name = 'Submit Participant Info',
        config_json = jsonb_set(
            jsonb_set(config_json::jsonb,
                '{confirmMessage}',
                '"Confirm submission of your attendance information?"'::jsonb),
            '{successMessage}',
            '"Attendance information submitted."'::jsonb
        ),
        updated_at = CURRENT_TIMESTAMP
    WHERE function_unit_id = v_function_unit_id AND action_name = '提交参会信息';

    RAISE NOTICE 'Updated action names and config messages';

    -- =========================================================================
    -- 3. Table display names
    -- =========================================================================
    UPDATE dw_table_definitions
    SET table_display_name = 'Meeting Info', updated_at = CURRENT_TIMESTAMP
    WHERE function_unit_id = v_function_unit_id AND table_name = 'meeting';

    UPDATE dw_table_definitions
    SET table_display_name = 'Participants', updated_at = CURRENT_TIMESTAMP
    WHERE function_unit_id = v_function_unit_id AND table_name = 'participants';

    RAISE NOTICE 'Updated table display names';

    -- =========================================================================
    -- 4. Form description column
    -- =========================================================================
    UPDATE dw_form_definitions
    SET description = 'Create meeting form: fill in basic meeting info and add participants in the sub-table',
        updated_at  = CURRENT_TIMESTAMP
    WHERE id = v_create_form_id;

    UPDATE dw_form_definitions
    SET description = 'Participant info form: used in sub-tasks for participants to fill in their attendance details (attendance status, dietary preference, remarks)',
        updated_at  = CURRENT_TIMESTAMP
    WHERE id = v_participant_form_id;

    RAISE NOTICE 'Updated form descriptions';

    -- =========================================================================
    -- 6. Form config_json — Create Meeting Form (rule + options; preserve subForms)
    -- =========================================================================
    UPDATE dw_form_definitions
    SET config_json = jsonb_set(
            jsonb_set(
                config_json::jsonb,
                '{rule}',
                '[
                    {"name":"ref_mc_topic","type":"input","field":"topic","props":{"maxlength":200,"placeholder":"Please enter meeting topic","showWordLimit":true},"title":"Meeting Topic","_fc_id":"id_mc_topic","hidden":false,"display":true,"validate":[{"message":"Meeting topic is required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
                    {"name":"ref_mc_time","type":"datePicker","field":"meeting_time","props":{"type":"datetime","placeholder":"Please select meeting time","valueFormat":"YYYY-MM-DD HH:mm:ss"},"title":"Meeting Time","_fc_id":"id_mc_time","hidden":false,"display":true,"validate":[{"message":"Meeting time is required","trigger":"blur","required":true}],"_fc_drag_tag":"datePicker"},
                    {"name":"ref_mc_location","type":"input","field":"location","props":{"maxlength":200,"placeholder":"Please enter meeting location","showWordLimit":true},"title":"Meeting Location","_fc_id":"id_mc_location","hidden":false,"display":true,"validate":[{"message":"Meeting location is required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
                    {"name":"ref_mc_organizer","type":"input","field":"organizer_name","props":{"maxlength":100,"placeholder":"Please enter organizer name","showWordLimit":true},"title":"Organizer","_fc_id":"id_mc_organizer","hidden":false,"display":true,"validate":[{"message":"Organizer is required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
                    {"name":"ref_mc_desc","type":"input","field":"description","props":{"rows":3,"type":"textarea","placeholder":"Please enter meeting description"},"title":"Meeting Description","_fc_id":"id_mc_desc","hidden":false,"display":true,"_fc_drag_tag":"input"}
                ]'::jsonb
            ),
            '{options}',
            '{"form":{"size":"default","inline":false,"labelWidth":"125px","labelPosition":"left","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}}'::jsonb
        ),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = v_create_form_id;

    RAISE NOTICE 'Updated Create Meeting Form config (rule + options)';

    -- =========================================================================
    -- 7. Form config_json — subForms (participants sub-form inside Create Meeting)
    --    Locate the binding id for the SUB binding of Create Meeting Form → participants
    -- =========================================================================
    SELECT id INTO v_binding_sub_id
    FROM dw_form_table_bindings
    WHERE form_id = v_create_form_id AND binding_type = 'SUB';

    IF v_binding_sub_id IS NOT NULL THEN
        UPDATE dw_form_definitions
        SET config_json = jsonb_set(
                config_json::jsonb,
                ARRAY['{subForms}', v_binding_sub_id::text],
                '{
                    "rule": [
                        {"name":"ref_p_name","type":"input","field":"name","props":{"maxlength":100,"placeholder":"Please enter participant name","showWordLimit":true},"title":"Name","_fc_id":"id_p_name","hidden":false,"display":true,"validate":[{"message":"Name is required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
                        {"name":"ref_p_dept","type":"input","field":"department","props":{"maxlength":100,"placeholder":"Please enter department","showWordLimit":true},"title":"Department","_fc_id":"id_p_dept","hidden":false,"display":true,"_fc_drag_tag":"input"},
                        {"name":"ref_p_email","type":"input","field":"email","props":{"maxlength":255,"placeholder":"Please enter email"},"title":"Email","_fc_id":"id_p_email","hidden":false,"display":true,"_fc_drag_tag":"input"}
                    ],
                    "options":{"form":{"size":"default","inline":false,"labelWidth":"100px","labelPosition":"left"},"resetBtn":{"show":false},"submitBtn":{"show":true,"innerText":"Confirm"}}
                }'::jsonb
            ),
            updated_at = CURRENT_TIMESTAMP
        WHERE id = v_create_form_id;
        RAISE NOTICE 'Updated Create Meeting Form subForms (binding id=%)', v_binding_sub_id;
    ELSE
        RAISE NOTICE 'WARNING: SUB binding for Create Meeting Form not found; subForms not updated';
    END IF;

    -- =========================================================================
    -- 8. Form config_json — Participant Info Form
    -- =========================================================================
    UPDATE dw_form_definitions
    SET config_json = '{
        "rule": [
            {"name":"ref_pi_attend","type":"select","field":"attend_status","props":{"options":[{"label":"Yes","value":"YES"},{"label":"No","value":"NO"},{"label":"Pending","value":"PENDING"}],"placeholder":"Please select attendance status"},"title":"Attendance","_fc_id":"id_pi_attend","hidden":false,"display":true,"validate":[{"message":"Please select attendance status","trigger":"change","required":true}],"_fc_drag_tag":"select"},
            {"name":"ref_pi_diet","type":"select","field":"dietary_preference","props":{"options":[{"label":"No special requirements","value":"NONE"},{"label":"Vegetarian","value":"VEGETARIAN"},{"label":"Halal","value":"HALAL"},{"label":"Other","value":"OTHER"}],"placeholder":"Please select dietary preference"},"title":"Dietary Preference","_fc_id":"id_pi_diet","hidden":false,"display":true,"_fc_drag_tag":"select"},
            {"name":"ref_pi_remark","type":"input","field":"remark","props":{"rows":3,"type":"textarea","placeholder":"Please enter remarks (e.g. projector needed, special seating, etc.)"},"title":"Remarks","_fc_id":"id_pi_remark","hidden":false,"display":true,"_fc_drag_tag":"input"}
        ],
        "options":{"form":{"size":"default","inline":false,"labelWidth":"125px","labelPosition":"left","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}}
    }'::jsonb,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = v_participant_form_id;

    RAISE NOTICE 'Updated Participant Info Form config';

    -- =========================================================================
    -- 9. BPMN XML — replace Chinese node names and actionNames in-place
    -- =========================================================================
    UPDATE dw_process_definitions
    SET bpmn_xml = replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(
            bpmn_xml,
            'name="开始"',           'name="Start"'),
            'name="创建会议"',        'name="Create Meeting"'),
            'name="分配参与人"',      'name="Assign Participants"'),
            'name="多实例-参与人列表"','name="Multi-instance: Participants"'),
            'name="填写参会信息"',    'name="Fill in Participant Info"'),
            'name="收集完成"',        'name="Collection Complete"'),
            '&quot;提交会议&quot;',   '&quot;Submit Meeting&quot;'),
            '&quot;完成分配&quot;',   '&quot;Complete Assignment&quot;'),
            '&quot;提交参会信息&quot;','&quot;Submit Participant Info&quot;'),
            -- Also cover plain-text variants (in case stored without XML encoding)
            '"提交参会信息"',         '"Submit Participant Info"'
        ),
        updated_at = CURRENT_TIMESTAMP
    WHERE function_unit_id = v_function_unit_id;

    RAISE NOTICE 'Updated BPMN XML node names and actionNames';

    -- =========================================================================
    -- 10. Form stage binding stage_name
    -- =========================================================================
    UPDATE dw_form_stage_bindings
    SET stage_name = 'Create Meeting'
    WHERE form_id = v_create_form_id AND stage_id = 'Task_CreateMeeting';

    UPDATE dw_form_stage_bindings
    SET stage_name = 'Assign Participants'
    WHERE form_id = v_create_form_id AND stage_id = 'Task_AssignParticipants';

    UPDATE dw_form_stage_bindings
    SET stage_name = 'Fill in Participant Info'
    WHERE form_id = v_participant_form_id
      AND stage_id = 'MI_UserTask_' || v_participant_table_id;

    RAISE NOTICE 'Updated form stage binding stage names';

    -- =========================================================================
    -- Summary
    -- =========================================================================
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Translation to English Complete!';
    RAISE NOTICE 'Function unit: fu-20260403-a1b2c5 (id=%)', v_function_unit_id;
    RAISE NOTICE 'Forms: Create Meeting (id=%), Participant Info (id=%)',
        v_create_form_id, v_participant_form_id;
    RAISE NOTICE '========================================';

END $translate$;
