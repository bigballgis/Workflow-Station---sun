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
        '会议参与人信息收集流程：演示多实例子流程动态任务分发。组织者创建会议并添加参与人，为每位参与人分配处理人后，系统自动创建并行子任务，各参与人独立填写参会信息，全部完成后流程自动推进。',
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
        '创建会议表单：填写会议基本信息并在子表中添加参与人',
        '{"rule": [
            {"name":"ref_mc_topic","type":"input","field":"topic","props":{"maxlength":200,"placeholder":"请输入会议主题","showWordLimit":true},"title":"会议主题","_fc_id":"id_mc_topic","hidden":false,"display":true,"validate":[{"message":"会议主题必填","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
            {"name":"ref_mc_time","type":"datePicker","field":"meeting_time","props":{"type":"datetime","placeholder":"请选择会议时间","valueFormat":"YYYY-MM-DD HH:mm:ss"},"title":"会议时间","_fc_id":"id_mc_time","hidden":false,"display":true,"validate":[{"message":"会议时间必填","trigger":"blur","required":true}],"_fc_drag_tag":"datePicker"},
            {"name":"ref_mc_location","type":"input","field":"location","props":{"maxlength":200,"placeholder":"请输入会议地点","showWordLimit":true},"title":"会议地点","_fc_id":"id_mc_location","hidden":false,"display":true,"validate":[{"message":"会议地点必填","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
            {"name":"ref_mc_organizer","type":"input","field":"organizer_name","props":{"maxlength":100,"placeholder":"请输入组织者姓名","showWordLimit":true},"title":"组织者","_fc_id":"id_mc_organizer","hidden":false,"display":true,"validate":[{"message":"组织者必填","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
            {"name":"ref_mc_desc","type":"input","field":"description","props":{"rows":3,"type":"textarea","placeholder":"请输入会议说明"},"title":"会议说明","_fc_id":"id_mc_desc","hidden":false,"display":true,"_fc_drag_tag":"input"}
        ],"options":{"form":{"size":"default","inline":false,"labelWidth":"125px","labelPosition":"left","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"重置"},"submitBtn":{"show":true,"innerText":"提交"}},"subForms":{}}'::jsonb,
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
        '参与人信息填写表单：子任务中参与人填写自己的参会信息（是否参会、饮食偏好、备注）',
        '{"rule": [{"name": "ref_pi_attend", "type": "select", "field": "attend_status", "props": {"options": [{"label": "是", "value": "YES"}, {"label": "否", "value": "NO"}, {"label": "待定", "value": "PENDING"}], "placeholder": "请选择是否参会"}, "title": "是否参会", "_fc_id": "id_pi_attend", "hidden": false, "display": true, "validate": [{"message": "请选择是否参会", "trigger": "change", "required": true}], "_fc_drag_tag": "select"}, {"name": "ref_pi_diet", "type": "select", "field": "dietary_preference", "props": {"options": [{"label": "无特殊要求", "value": "NONE"}, {"label": "素食", "value": "VEGETARIAN"}, {"label": "清真", "value": "HALAL"}, {"label": "其他", "value": "OTHER"}], "placeholder": "请选择饮食偏好"}, "title": "饮食偏好", "_fc_id": "id_pi_diet", "hidden": false, "display": true, "_fc_drag_tag": "select"}, {"name": "ref_pi_remark", "type": "input", "field": "remark", "props": {"rows": 3, "type": "textarea", "placeholder": "请输入备注信息（如需要投影仪、特殊座位等）"}, "title": "备注", "_fc_id": "id_pi_remark", "hidden": false, "display": true, "_fc_drag_tag": "input"}], "options": {"form": {"size": "default", "inline": false, "labelWidth": "125px", "labelPosition": "left", "hideRequiredAsterisk": false}, "resetBtn": {"show": false, "innerText": "重置"}, "submitBtn": {"show": true, "innerText": "提交"}}}'::jsonb,
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
        '提交会议',
        'PROCESS_SUBMIT',
        '{"confirmMessage":"确认提交此会议？提交后将进入参与人分配环节。","requireComment":false,"successMessage":"会议已提交，请分配参与人处理人"}'::jsonb,
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

    -- 3.2 完成分配 (Complete Assignment)
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        '完成分配',
        'PROCESS_SUBMIT',
        '{"confirmMessage":"确认所有参与人已分配处理人？确认后系统将自动为每位参与人创建填写任务。","requireComment":false,"successMessage":"分配完成，子任务已创建"}'::jsonb,
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

    -- 3.3 提交参会信息 (Submit Participant Info)
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        '提交参会信息',
        'PROCESS_SUBMIT',
        '{"confirmMessage":"确认提交您的参会信息？","requireComment":false,"successMessage":"参会信息已提交"}'::jsonb,
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
    RAISE NOTICE 'Actions: 提交会议 (id=%), 完成分配 (id=%), 提交参会信息 (id=%)',
        v_action_submit_id, v_action_complete_assign_id, v_action_submit_info_id;
    RAISE NOTICE 'Next: run 01-create-tables.sql';
    RAISE NOTICE '========================================';

END $main$;
