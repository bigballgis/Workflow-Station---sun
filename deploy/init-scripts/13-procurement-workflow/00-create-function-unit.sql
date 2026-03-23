-- =============================================================================
-- 13-procurement-workflow: Create Function Unit (all-in-one)
-- 基于数据库实际数据生成 (source: Procurement Workflow, code PROCUREMENT_WORKFLOW)
-- 包含: Function Unit, Forms (Request/Approval/Review/sub), Actions (8个)
-- =============================================================================

DO $main$
DECLARE
    v_function_unit_id  BIGINT;
    v_request_form_id   BIGINT;
    v_approval_form_id  BIGINT;
    v_review_form_id    BIGINT;
    v_sub_form_id       BIGINT;
BEGIN
    -- =========================================================================
    -- Step 1: Function Unit
    -- =========================================================================
    INSERT INTO dw_function_units (
        code, name, description, status,
        current_version, version, is_active, enabled,
        deployed_at, lock_version, created_by, created_at, updated_by, updated_at
    ) VALUES (
        'PROCUREMENT_WORKFLOW',
        'Procurement Workflow',
        'Simple approval workflow with manager approval',
        'PUBLISHED',
        '1.0.6', '1.0.0',
        true, true,
        CURRENT_TIMESTAMP, 0,
        'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP
    )
    ON CONFLICT (code) DO UPDATE SET
        name             = EXCLUDED.name,
        description      = EXCLUDED.description,
        status           = EXCLUDED.status,
        current_version  = EXCLUDED.current_version,
        updated_by       = EXCLUDED.updated_by,
        updated_at       = CURRENT_TIMESTAMP
    RETURNING id INTO v_function_unit_id;

    RAISE NOTICE 'Function unit created/updated: id=%, code=PROCUREMENT_WORKFLOW', v_function_unit_id;

    -- =========================================================================
    -- Step 2: Forms (config_json 中的 subForms 在 03 脚本中填充)
    -- =========================================================================

    -- Request Form (MAIN) — rule 包含 5 个主表字段
    -- subForms 和 subTable placeholder 在 03-form-table-bindings.sql 中填充
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, description, config_json, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'Request Form',
        'MAIN',
        'Request submission form',
        '{"rule": [{"name": "ref_Fw7smm1s0a5gacc", "type": "input", "field": "request_number", "props": {"maxlength": 50, "placeholder": "Please input Request Number", "showWordLimit": true}, "title": "Request Number", "_fc_id": "id_Fkbfmm1s0a5gabc", "hidden": false, "display": true, "validate": [{"mode": "required", "message": "Request Number required", "trigger": "blur", "required": true}], "_fc_drag_tag": "input"}, {"name": "ref_Ftvqmm1s0a5gaec", "type": "datePicker", "field": "request_date", "props": {"type": "datetime", "placeholder": "Please input Request Date", "valueFormat": "YYYY-MM-DD HH:mm:ss"}, "title": "Request Date", "_fc_id": "id_Fjwimm1s0a5gadc", "hidden": false, "display": true, "validate": [{"message": "Request Date required", "trigger": "blur", "required": true}], "_fc_drag_tag": "datePicker"}, {"name": "ref_Fqk8mm1s0a5gagc", "type": "input", "field": "title", "props": {"maxlength": 200, "placeholder": "Please input Request Title", "showWordLimit": true}, "title": "Request Title", "_fc_id": "id_Faqomm1s0a5gafc", "hidden": false, "display": true, "validate": [{"message": "Request Title required", "trigger": "blur", "required": true}], "_fc_drag_tag": "input"}, {"name": "ref_Fjowmm1s0a5haic", "type": "input", "field": "description", "props": {"rows": 3, "type": "textarea", "placeholder": "Please input Request Description"}, "title": "Request Description", "_fc_id": "id_Ffhjmm1s0a5hahc", "hidden": false, "display": true, "validate": [{"mode": "required", "message": "Request Description required", "trigger": "blur", "required": true}], "_fc_drag_tag": "input"}, {"name": "ref_Fdsjmm3a7ibxacc", "type": "inputNumber", "field": "budget", "props": {"precision": 0, "placeholder": "Please input budget"}, "title": "budget", "_fc_id": "id_Ftbumm3a7ibxabc", "hidden": false, "display": true, "_fc_drag_tag": "inputNumber"}], "options": {"form": {"size": "default", "inline": false, "labelWidth": "125px", "labelPosition": "left", "hideRequiredAsterisk": false}, "resetBtn": {"show": false, "innerText": "Reset"}, "submitBtn": {"show": true, "innerText": "Submit"}}, "subForms": {}}',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, form_name) DO UPDATE SET
        config_json = EXCLUDED.config_json,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_request_form_id;

    RAISE NOTICE 'Request Form created/updated: id=%', v_request_form_id;

    -- Approval Form (MAIN)
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, description, config_json, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'Approval Form',
        'MAIN',
        'Manager approval form',
        '{"rule": [{"name": "ref_Flhmmm2udhnkaec", "type": "input", "field": "additional_information", "props": {"rows": 3, "type": "textarea", "placeholder": "Please input Additional Inforrmation"}, "title": "Additional Inforrmation", "_fc_id": "id_F59nmm2udhnkadc", "hidden": false, "display": true, "_fc_drag_tag": "input"}], "options": {"form": {"size": "default", "inline": false, "labelWidth": "125px", "labelPosition": "right", "hideRequiredAsterisk": false}, "resetBtn": {"show": false, "innerText": "Reset"}, "submitBtn": {"show": true, "innerText": "Submit"}}, "subForms": {}}',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, form_name) DO UPDATE SET
        config_json = EXCLUDED.config_json,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_approval_form_id;

    RAISE NOTICE 'Approval Form created/updated: id=%', v_approval_form_id;

    -- Review Form (MAIN)
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, description, config_json, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'Review Form',
        'MAIN',
        NULL,
        '{"rule": [{"name": "ref_Ftv7mmyxh928agc", "type": "inputNumber", "field": "id", "props": {"precision": 0, "placeholder": "Please input id"}, "title": "id", "_fc_id": "id_F913mmyxh928afc", "hidden": false, "display": true, "validate": [{"message": "id required", "trigger": "blur", "required": true}], "_fc_drag_tag": "inputNumber"}, {"name": "ref_Fx6tmmyxh928acc", "type": "input", "field": "Item", "props": {"rows": 3, "type": "textarea", "placeholder": "Please input item"}, "title": "item", "_fc_id": "id_F2z0mmyxh928abc", "hidden": false, "display": true, "_fc_drag_tag": "input"}, {"name": "ref_Fwdvmmyxh928aec", "type": "input", "field": "Comment", "props": {"maxlength": 255, "placeholder": "Please input comment", "showWordLimit": true}, "title": "comment", "_fc_id": "id_Fhhymmyxh928adc", "hidden": false, "display": true, "_fc_drag_tag": "input"}, {"info": "", "name": "ref_F5q1mmyxiftrajc", "type": "input", "field": "Fessmmyxiftrahc", "title": "Input", "_fc_id": "id_F4q8mmyxiftraic", "hidden": false, "display": true, "$required": false, "_fc_drag_tag": "input"}], "options": {"form": {"size": "default", "inline": false, "labelWidth": "125px", "labelPosition": "left", "hideRequiredAsterisk": false}, "resetBtn": {"show": false, "innerText": "Reset"}, "submitBtn": {"show": true, "innerText": "Submit"}}, "subForms": {}}',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, form_name) DO UPDATE SET
        config_json = EXCLUDED.config_json,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_review_form_id;

    RAISE NOTICE 'Review Form created/updated: id=%', v_review_form_id;

    -- sub form (SUB)
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, description, config_json, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'sub form',
        'SUB',
        NULL,
        '{"rule": [{"info": "", "name": "ref_F7simlyllz9dadc", "type": "input", "field": "Fayjmlyllz9dabc", "title": "Input", "_fc_id": "id_Fd7gmlyllz9dacc", "hidden": false, "display": true, "$required": false, "_fc_drag_tag": "input"}], "options": {"form": {"size": "default", "inline": false, "labelWidth": "125px", "labelPosition": "right", "hideRequiredAsterisk": false}, "resetBtn": {"show": false, "innerText": "Reset"}, "submitBtn": {"show": true, "innerText": "Submit"}}, "subForms": {}}',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, form_name) DO UPDATE SET
        config_json = EXCLUDED.config_json,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_sub_form_id;

    RAISE NOTICE 'sub form created/updated: id=%', v_sub_form_id;

    -- =========================================================================
    -- Step 3: Actions (8个)
    -- =========================================================================

    -- Submit Request
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'Submit Request', 'PROCESS_SUBMIT',
        '{"url":"","body":"","formId":null,"method":"POST","script":"","headers":"","targetStep":"","dialogTitle":"","dialogWidth":"600px","targetStatus":"","confirmMessage":"Confirm submitting this request?","requireComment":true,"successMessage":"Request submitted successfully","requireAssignee":false}',
        NULL, NULL, 'Submit request to start approval workflow', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    -- Approve
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'Approve', 'APPROVE',
        '{"targetStatus":"APPROVED","confirmMessage":"Confirm approving this request?","requireComment":true,"successMessage":"Request approved"}',
        'Check', 'success', 'Approve the request', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        icon = EXCLUDED.icon, button_color = EXCLUDED.button_color,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    -- Reject
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'Reject', 'REJECT',
        '{"targetStatus":"REJECTED","requireReason":true,"confirmMessage":"Confirm rejecting this request?","requireComment":true,"successMessage":"Request rejected"}',
        'Close', 'danger', 'Reject the request', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        icon = EXCLUDED.icon, button_color = EXCLUDED.button_color,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    -- Confirm
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'Confirm', 'APPROVE', '{}',
        NULL, NULL, NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        is_default = EXCLUDED.is_default, updated_at = CURRENT_TIMESTAMP;

    -- Transfer
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'Transfer', 'TRANSFER',
        '{"confirmMessage":"Confirm transferring this task?","requireComment":false,"successMessage":"Task transferred successfully"}',
        'Switch', NULL, 'Transfer task to another user', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        icon = EXCLUDED.icon, description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    -- Delegate
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'Delegate', 'DELEGATE',
        '{"confirmMessage":"Confirm delegating this task?","requireComment":false,"successMessage":"Task delegated successfully"}',
        'User', NULL, 'Delegate task to another user', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        icon = EXCLUDED.icon, description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    -- Approve First
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'Approve First', 'APPROVE', '{}',
        NULL, NULL, NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        is_default = EXCLUDED.is_default, updated_at = CURRENT_TIMESTAMP;

    -- Rejected First
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'Rejected First', 'REJECT',
        '{"url":"","body":"","formId":null,"method":"POST","script":"","headers":"","targetStep":"","webhookUrl":"","dialogTitle":"","dialogWidth":"600px","n8nConfigId":"","inputMapping":[],"targetStatus":"","n8nWorkflowId":"","outputMapping":[],"confirmMessage":"","requireComment":false,"timeoutSeconds":120,"requireAssignee":false}',
        NULL, NULL, NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        is_default = EXCLUDED.is_default, updated_at = CURRENT_TIMESTAMP;

    RAISE NOTICE '========================================';
    RAISE NOTICE 'Function Unit Setup Complete!';
    RAISE NOTICE 'Function Unit ID : %', v_function_unit_id;
    RAISE NOTICE 'Request Form ID  : %', v_request_form_id;
    RAISE NOTICE 'Approval Form ID : %', v_approval_form_id;
    RAISE NOTICE 'Review Form ID   : %', v_review_form_id;
    RAISE NOTICE 'sub form ID      : %', v_sub_form_id;
    RAISE NOTICE 'Next: run 01-create-tables.sql';
    RAISE NOTICE '========================================';

END $main$;
