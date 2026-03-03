-- =============================================================================
-- 13-procurement-workflow: Create Function Unit
-- 基于数据库实际数据生成 (source: Procurement Workflow, code PROCUREMENT_WORKFLOW)
-- =============================================================================

DO $main$
DECLARE
    v_function_unit_id  BIGINT;
    v_request_form_id   BIGINT;
    v_approval_form_id  BIGINT;
    v_sub_form_id       BIGINT;
    v_action_submit_id  BIGINT;
    v_action_approve_id BIGINT;
    v_action_reject_id  BIGINT;
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
        '1.0.0', '1.0.0',
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
    -- Step 2: Forms
    -- =========================================================================

    -- Request Form (MAIN)
    -- config_json includes 5 main fields: request_number, request_date, title, description, budget
    -- subForms are populated later in 03-form-table-bindings.sql after binding IDs are known
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, description, config_json, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'Request Form',
        'MAIN',
        'Request submission form',
        '{"rule": [{"name": "ref_Fw7smm1s0a5gacc", "type": "input", "field": "request_number", "props": {"maxlength": 50, "placeholder": "Please input Request Number", "showWordLimit": true}, "title": "Request Number", "_fc_id": "id_Fkbfmm1s0a5gabc", "hidden": false, "display": true, "validate": [{"message": "Request Number required", "trigger": "blur", "required": true}], "_fc_drag_tag": "input"}, {"name": "ref_Ftvqmm1s0a5gaec", "type": "datePicker", "field": "request_date", "props": {"type": "datetime", "placeholder": "Please input Request Date", "valueFormat": "YYYY-MM-DD HH:mm:ss"}, "title": "Request Date", "_fc_id": "id_Fjwimm1s0a5gadc", "hidden": false, "display": true, "validate": [{"message": "Request Date required", "trigger": "blur", "required": true}], "_fc_drag_tag": "datePicker"}, {"name": "ref_Fqk8mm1s0a5gagc", "type": "input", "field": "title", "props": {"maxlength": 200, "placeholder": "Please input Request Title", "showWordLimit": true}, "title": "Request Title", "_fc_id": "id_Faqomm1s0a5gafc", "hidden": false, "display": true, "validate": [{"message": "Request Title required", "trigger": "blur", "required": true}], "_fc_drag_tag": "input"}, {"name": "ref_Fjowmm1s0a5haic", "type": "input", "field": "description", "props": {"rows": 3, "type": "textarea", "placeholder": "Please input Request Description"}, "title": "Request Description", "_fc_id": "id_Ffhjmm1s0a5hahc", "hidden": false, "display": true, "validate": [{"message": "Request Description required", "trigger": "blur", "required": true}], "_fc_drag_tag": "input"}, {"name": "ref_Fdsjmm3a7ibxacc", "type": "inputNumber", "field": "budget", "props": {"precision": 0, "placeholder": "Please input budget"}, "title": "budget", "_fc_id": "id_Ftbumm3a7ibxabc", "hidden": false, "display": true, "_fc_drag_tag": "inputNumber"}], "options": {"form": {"size": "default", "inline": false, "labelWidth": "125px", "labelPosition": "left", "hideRequiredAsterisk": false}, "resetBtn": {"show": false, "innerText": "Reset"}, "submitBtn": {"show": true, "innerText": "Submit"}}, "subForms": {}}',
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
    -- Step 3: Actions
    -- =========================================================================

    -- Submit Request
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'Submit Request',
        'PROCESS_SUBMIT',
        '{"url":"","body":"","formId":null,"method":"POST","script":"","headers":"","targetStep":"","dialogTitle":"","dialogWidth":"600px","targetStatus":"","confirmMessage":"Confirm submitting this request?","requireComment":true,"successMessage":"Request submitted successfully","requireAssignee":false}',
        NULL, NULL,
        'Submit request to start approval workflow',
        false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type  = EXCLUDED.action_type,
        config_json  = EXCLUDED.config_json,
        description  = EXCLUDED.description,
        updated_at   = CURRENT_TIMESTAMP
    RETURNING id INTO v_action_submit_id;

    RAISE NOTICE 'Submit Request action created/updated: id=%', v_action_submit_id;

    -- Approve
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'Approve',
        'APPROVE',
        '{"targetStatus":"APPROVED","confirmMessage":"Confirm approving this request?","requireComment":true,"successMessage":"Request approved"}',
        'Check', 'success',
        'Approve the request',
        false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type  = EXCLUDED.action_type,
        config_json  = EXCLUDED.config_json,
        icon         = EXCLUDED.icon,
        button_color = EXCLUDED.button_color,
        description  = EXCLUDED.description,
        updated_at   = CURRENT_TIMESTAMP
    RETURNING id INTO v_action_approve_id;

    RAISE NOTICE 'Approve action created/updated: id=%', v_action_approve_id;

    -- Reject
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'Reject',
        'REJECT',
        '{"targetStatus":"REJECTED","requireReason":true,"confirmMessage":"Confirm rejecting this request?","requireComment":true,"successMessage":"Request rejected"}',
        'Close', 'danger',
        'Reject the request',
        false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type  = EXCLUDED.action_type,
        config_json  = EXCLUDED.config_json,
        icon         = EXCLUDED.icon,
        button_color = EXCLUDED.button_color,
        description  = EXCLUDED.description,
        updated_at   = CURRENT_TIMESTAMP
    RETURNING id INTO v_action_reject_id;

    RAISE NOTICE 'Reject action created/updated: id=%', v_action_reject_id;

    -- =========================================================================
    -- Summary
    -- =========================================================================
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Function Unit Setup Complete!';
    RAISE NOTICE 'Function Unit ID : %', v_function_unit_id;
    RAISE NOTICE 'Request Form ID  : %', v_request_form_id;
    RAISE NOTICE 'Approval Form ID : %', v_approval_form_id;
    RAISE NOTICE 'sub form ID      : %', v_sub_form_id;
    RAISE NOTICE 'Actions: Submit=%, Approve=%, Reject=%',
        v_action_submit_id, v_action_approve_id, v_action_reject_id;
    RAISE NOTICE 'Next: run 01-create-tables.sql';
    RAISE NOTICE '========================================';

END $main$;
