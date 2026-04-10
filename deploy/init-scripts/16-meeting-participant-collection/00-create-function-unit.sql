-- =============================================================================
-- 16-meeting-participant-collection: Create Function Unit, Forms, and Actions
-- 会议参与人信息收集功能单元：演示多实例子流程动态任务分发
--
-- 场景：会议组织者创建会议 → 添加参与人 → 分配处理人 → 
--       系统自动为每位参与人创建子任务 → 参与人填写信息 → 流程完成
--
-- Dependencies: None (this is the first script)
-- Execution order: 00 → 01 → 02 → 03 → 04 → 05 (05 optional if 03 already wrote stage bindings)
-- =============================================================================

DO $main$
DECLARE
    v_function_unit_id          BIGINT;
    v_create_meeting_form_id    BIGINT;
    v_participant_form_id       BIGINT;
    v_action_submit_id          BIGINT;
    v_action_complete_assign_id BIGINT;
    v_action_submit_info_id     BIGINT;
BEGIN
    -- =========================================================================
    -- Step 1: Function Unit
    -- =========================================================================
    INSERT INTO dw_function_units (
        code, name, description, status,
        current_version, version, is_active, enabled,
        deployed_at, lock_version, created_by, created_at, updated_by, updated_at
    ) VALUES (
        'fu-20260403-a1b2c5',
        'Meeting Participant Info Collection',
        'Meeting participant info collection workflow: demonstrates multi-instance subprocess with dynamic task distribution. The organizer creates a meeting and adds participants; after assigning a handler for each participant, the system auto-creates parallel sub-tasks so each participant independently fills in their attendance info, and the process advances once all are complete.',
        'PUBLISHED',
        '1.0.4', '1.0.0',
        true, true,
        CURRENT_TIMESTAMP, 0,
        'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP
    )
    ON CONFLICT (code) DO UPDATE SET
        name            = EXCLUDED.name,
        description     = EXCLUDED.description,
        status          = EXCLUDED.status,
        current_version = EXCLUDED.current_version,
        version         = EXCLUDED.version,
        is_active       = EXCLUDED.is_active,
        enabled         = EXCLUDED.enabled,
        updated_by      = EXCLUDED.updated_by,
        updated_at      = CURRENT_TIMESTAMP
    RETURNING id INTO v_function_unit_id;

    RAISE NOTICE 'Function unit created/updated: id=%, code=fu-20260403-a1b2c5', v_function_unit_id;

    -- 迁移：旧版「分配参与人」独立表单已废弃；BPMN 与「创建会议」共用同一张 PROCESS 表单
    DELETE FROM dw_form_stage_bindings WHERE form_id IN (
        SELECT id FROM dw_form_definitions
        WHERE function_unit_id = v_function_unit_id AND form_name = 'Assign Participants Form'
    );
    DELETE FROM dw_form_table_bindings WHERE form_id IN (
        SELECT id FROM dw_form_definitions
        WHERE function_unit_id = v_function_unit_id AND form_name = 'Assign Participants Form'
    );
    DELETE FROM dw_form_definitions
    WHERE function_unit_id = v_function_unit_id AND form_name = 'Assign Participants Form';

    -- =========================================================================
    -- Step 2: Forms
    -- =========================================================================

    -- 2.1 创建会议表单 (Create Meeting Form) - 主表单：创建会议与分配参与人节点共用
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, description, config_json, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'Create Meeting Form',
        'PROCESS',
        'Create meeting form: fill in basic meeting info and add participants in the sub-table',
        '{"rule": [
            {"name":"ref_mc_topic","type":"input","field":"topic","props":{"maxlength":200,"placeholder":"Please enter meeting topic","showWordLimit":true},"title":"Meeting Topic","_fc_id":"id_mc_topic","hidden":false,"display":true,"validate":[{"message":"Meeting topic is required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
            {"name":"ref_mc_time","type":"datePicker","field":"meeting_time","props":{"type":"datetime","placeholder":"Please select meeting time","valueFormat":"YYYY-MM-DD HH:mm:ss"},"title":"Meeting Time","_fc_id":"id_mc_time","hidden":false,"display":true,"validate":[{"message":"Meeting time is required","trigger":"blur","required":true}],"_fc_drag_tag":"datePicker"},
            {"name":"ref_mc_location","type":"input","field":"location","props":{"maxlength":200,"placeholder":"Please enter meeting location","showWordLimit":true},"title":"Meeting Location","_fc_id":"id_mc_location","hidden":false,"display":true,"validate":[{"message":"Meeting location is required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
            {"name":"ref_mc_organizer","type":"input","field":"organizer_name","props":{"maxlength":100,"placeholder":"Please enter organizer name","showWordLimit":true},"title":"Organizer","_fc_id":"id_mc_organizer","hidden":false,"display":true,"validate":[{"message":"Organizer is required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
            {"name":"ref_mc_desc","type":"input","field":"description","props":{"rows":3,"type":"textarea","placeholder":"Please enter meeting description"},"title":"Meeting Description","_fc_id":"id_mc_desc","hidden":false,"display":true,"_fc_drag_tag":"input"}
        ],"options":{"form":{"size":"default","inline":false,"labelWidth":"125px","labelPosition":"left","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}},"subForms":{}}'::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, form_name) DO UPDATE SET
        -- 保留 03-form-table-bindings.sql 写入的 subForms（participants 子表字段）；勿用 EXCLUDED 整包覆盖清空
        config_json = jsonb_set(
            EXCLUDED.config_json::jsonb,
            '{subForms}',
            COALESCE((dw_form_definitions.config_json::jsonb)->'subForms', '{}'::jsonb)
        ),
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_create_meeting_form_id;

    RAISE NOTICE 'Create Meeting Form created/updated: id=%', v_create_meeting_form_id;

    -- 2.2 填写参会信息表单 (Participant Info Form) - 子任务表单，参与人填写自己的信息
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, description, config_json, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'Participant Info Form',
        'TASK',
        'Participant info form: used in sub-tasks for participants to fill in their attendance details (attendance status, dietary preference, remarks)',
        '{"rule": [{"name": "ref_pi_attend", "type": "select", "field": "attend_status", "props": {"options": [{"label": "Yes", "value": "YES"}, {"label": "No", "value": "NO"}, {"label": "Pending", "value": "PENDING"}], "placeholder": "Please select attendance status"}, "title": "Attendance", "_fc_id": "id_pi_attend", "hidden": false, "display": true, "validate": [{"message": "Please select attendance status", "trigger": "change", "required": true}], "_fc_drag_tag": "select"}, {"name": "ref_pi_diet", "type": "select", "field": "dietary_preference", "props": {"options": [{"label": "No special requirements", "value": "NONE"}, {"label": "Vegetarian", "value": "VEGETARIAN"}, {"label": "Halal", "value": "HALAL"}, {"label": "Other", "value": "OTHER"}], "placeholder": "Please select dietary preference"}, "title": "Dietary Preference", "_fc_id": "id_pi_diet", "hidden": false, "display": true, "_fc_drag_tag": "select"}, {"name": "ref_pi_remark", "type": "input", "field": "remark", "props": {"rows": 3, "type": "textarea", "placeholder": "Please enter remarks (e.g. projector needed, special seating, etc.)"}, "title": "Remarks", "_fc_id": "id_pi_remark", "hidden": false, "display": true, "_fc_drag_tag": "input"}], "options": {"form": {"size": "default", "inline": false, "labelWidth": "125px", "labelPosition": "left", "hideRequiredAsterisk": false}, "resetBtn": {"show": false, "innerText": "Reset"}, "submitBtn": {"show": true, "innerText": "Submit"}}}'::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, form_name) DO UPDATE SET
        config_json = EXCLUDED.config_json,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_participant_form_id;

    RAISE NOTICE 'Participant Info Form created/updated: id=%', v_participant_form_id;

    -- =========================================================================
    -- Step 3: Actions
    -- =========================================================================

    -- 3.1 提交会议 (Submit Meeting)
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'Submit Meeting',
        'PROCESS_SUBMIT',
        '{"confirmMessage":"Confirm submission of this meeting? Once submitted, the participant assignment stage will begin.","requireComment":false,"successMessage":"Meeting submitted. Please assign handlers for each participant."}'::jsonb,
        'Upload', 'primary',
        'Submit meeting to start participant assignment',
        false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type,
        config_json = EXCLUDED.config_json,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_action_submit_id;

    RAISE NOTICE 'Submit Meeting action created/updated: id=%', v_action_submit_id;

    -- 3.2 Complete Assignment
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'Complete Assignment',
        'PROCESS_SUBMIT',
        '{"confirmMessage":"Confirm that all participants have been assigned a handler? The system will automatically create a fill-in task for each participant.","requireComment":false,"successMessage":"Assignment complete. Sub-tasks have been created."}'::jsonb,
        'Check', 'success',
        'Complete participant assignment and trigger multi-instance subprocess',
        false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type,
        config_json = EXCLUDED.config_json,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_action_complete_assign_id;

    RAISE NOTICE 'Complete Assignment action created/updated: id=%', v_action_complete_assign_id;

    -- 3.3 Submit Participant Info
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'Submit Participant Info',
        'PROCESS_SUBMIT',
        '{"confirmMessage":"Confirm submission of your attendance information?","requireComment":false,"successMessage":"Attendance information submitted."}'::jsonb,
        'Upload', 'primary',
        'Submit participant attendance info in multi-instance sub-task',
        false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type,
        config_json = EXCLUDED.config_json,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_action_submit_info_id;

    RAISE NOTICE 'Submit Participant Info action created/updated: id=%', v_action_submit_info_id;

    -- =========================================================================
    -- Summary
    -- =========================================================================
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Function Unit Setup Complete!';
    RAISE NOTICE 'Function Unit : id=%, code=fu-20260403-a1b2c5', v_function_unit_id;
    RAISE NOTICE 'Forms: Create Meeting (id=%), Participant Info (id=%)',
        v_create_meeting_form_id, v_participant_form_id;
    RAISE NOTICE 'Actions: Submit Meeting (id=%), Complete Assignment (id=%), Submit Participant Info (id=%)',
        v_action_submit_id, v_action_complete_assign_id, v_action_submit_info_id;
    RAISE NOTICE 'Next: run 01-create-tables.sql';
    RAISE NOTICE '========================================';

END $main$;
