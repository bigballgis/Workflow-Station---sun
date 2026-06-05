-- =============================================================================
-- 12-simple-approval: Create Function Unit
-- 基于数据库实际数据生成 (source: SIMPLE_APPROVAL, version 1.0.13)
-- code 符合规范: fu-{yyyyMMdd}-{6位hex}
-- =============================================================================

DO $main$
DECLARE
    v_function_unit_id BIGINT;
    v_request_form_id  BIGINT;
    v_approval_form_id BIGINT;
    v_sub_form_id      BIGINT;
    v_action_submit_id BIGINT;
    v_action_approve_id BIGINT;
    v_action_reject_id BIGINT;
BEGIN
    -- =========================================================================
    -- Step 1: Function Unit
    -- =========================================================================
    INSERT INTO dw_function_units (
        code, name, display_name, status,
        current_version, version, is_active, enabled,
        deployed_at, lock_version, created_by, created_at, updated_by, updated_at
    ) VALUES (
        'fu-20260403-a1b2c1',
        'Simple Approval Workflow 12',
        'Simple approval workflow with manager approval',
        'DRAFT',
        '1.0.0', '1.0.0',
        true, true,
        NULL, 0,
        'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP
    )
    ON CONFLICT (code) DO UPDATE SET
        name             = EXCLUDED.name,
        display_name      = EXCLUDED.display_name,
        status           = EXCLUDED.status,
        current_version  = EXCLUDED.current_version,
        updated_by       = EXCLUDED.updated_by,
        updated_at       = CURRENT_TIMESTAMP
    RETURNING id INTO v_function_unit_id;

    RAISE NOTICE 'Function unit created/updated: id=%, code=fu-20260403-a1b2c1', v_function_unit_id;

    -- =========================================================================
    -- Step 2: Forms
    -- =========================================================================

    -- Request Form (id=6 in source) — config_json includes subForms for table 9 (RequestItems) and 11 (RequestAttachments)
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, display_name, config_json, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'Request Form',
        'PROCESS',
        'Request submission form',
        '{"rule":[{"name":"ref_Fvsumlhl019tacc","type":"inputNumber","field":"id","props":{"precision":0,"placeholder":"Please input Primary key"},"title":"Primary key","_fc_id":"id_Fky5mlhl019tabc","hidden":false,"display":true,"validate":[{"message":"Primary key required","trigger":"blur","required":true}],"_fc_drag_tag":"inputNumber"},{"name":"ref_Fi0smlhl019taec","type":"input","field":"request_number","props":{"maxlength":50,"placeholder":"Please input Request number (unique)","showWordLimit":true},"title":"Request number","_fc_id":"id_Fw4kmlhl019tadc","hidden":false,"display":true,"validate":[{"message":"Request number required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},{"name":"ref_F4zmmlhl019tagc","type":"datePicker","field":"request_date","props":{"type":"datetime","placeholder":"Please input Request date","valueFormat":"YYYY-MM-DD HH:mm:ss"},"title":"Request date","_fc_id":"id_Favemlhl019tafc","hidden":false,"display":true,"validate":[{"message":"Request date required","trigger":"blur","required":true}],"_fc_drag_tag":"datePicker"},{"name":"ref_Fuzcmlhl019taic","type":"input","field":"title","props":{"maxlength":200,"placeholder":"Please input Request title","showWordLimit":true},"title":"Request title","_fc_id":"id_Fzvrmlhl019tahc","hidden":false,"display":true,"validate":[{"message":"Request title required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},{"name":"ref_F606mlhl019takc","type":"input","field":"description","props":{"rows":3,"type":"textarea","placeholder":"Please input Request description"},"title":"Request description","_fc_id":"id_Ffgkmlhl019tajc","hidden":false,"display":true,"validate":[{"message":"Request description required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},{"name":"ref_Fbqemlhl019tamc","type":"input","field":"status","props":{"maxlength":30,"placeholder":"Please input Request status","showWordLimit":true},"title":"Request status","_fc_id":"id_Fno2mlhl019talc","hidden":false,"display":true,"validate":[{"message":"Request status required","trigger":"blur","required":true}],"_fc_drag_tag":"input"}],"options":{"form":{"size":"default","inline":false,"labelWidth":"125px","labelPosition":"right","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}},"subForms":{}}',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, form_name) DO UPDATE SET
        config_json = EXCLUDED.config_json,
        display_name = EXCLUDED.display_name,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_request_form_id;

    RAISE NOTICE 'Request Form created/updated: id=%', v_request_form_id;

    -- Approval Form (id=7 in source)
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, display_name, config_json, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'Approval Form',
        'TASK',
        'Manager approval form',
        '{"rule":[{"name":"ref_F4wwmlhl2zozawc","type":"input","field":"approval_comments","props":{"rows":3,"type":"textarea","placeholder":"Please input Approval comments"},"title":"Approval comments","_fc_id":"id_Fbdgmlhl2zozavc","hidden":false,"display":true,"_fc_drag_tag":"input"},{"name":"ref_Fmb1mlhl2zozayc","type":"input","field":"status","props":{"maxlength":30,"placeholder":"Please input Request status","showWordLimit":true},"title":"Request status","_fc_id":"id_Fjtlmlhl2zozaxc","hidden":false,"display":true,"validate":[{"mode":"required","message":"Request status required","trigger":"blur","required":true}],"_fc_drag_tag":"input"}],"options":{"form":{"size":"default","inline":false,"labelWidth":"125px","labelPosition":"right","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}},"subForms":{}}',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, form_name) DO UPDATE SET
        config_json = EXCLUDED.config_json,
        display_name = EXCLUDED.display_name,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_approval_form_id;

    RAISE NOTICE 'Approval Form created/updated: id=%', v_approval_form_id;

    -- sub form (id=8 in source)
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, display_name, config_json, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'sub form',
        'TASK',
        NULL,
        '{"rule":[{"info":"","name":"ref_F7simlyllz9dadc","type":"input","field":"Fayjmlyllz9dabc","title":"Input","_fc_id":"id_Fd7gmlyllz9dacc","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"input"}],"options":{"form":{"size":"default","inline":false,"labelWidth":"125px","labelPosition":"right","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}},"subForms":{}}',
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

    -- Submit Request (id=16 in source) — actual config from DB includes url/body/method fields
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, display_name, is_default, created_at, updated_at
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
        display_name  = EXCLUDED.display_name,
        updated_at   = CURRENT_TIMESTAMP
    RETURNING id INTO v_action_submit_id;

    RAISE NOTICE 'Submit Request action created/updated: id=%', v_action_submit_id;

    -- Approve (id=17 in source)
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, display_name, is_default, created_at, updated_at
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
        display_name  = EXCLUDED.display_name,
        updated_at   = CURRENT_TIMESTAMP
    RETURNING id INTO v_action_approve_id;

    RAISE NOTICE 'Approve action created/updated: id=%', v_action_approve_id;

    -- Reject (id=18 in source)
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, display_name, is_default, created_at, updated_at
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
        display_name  = EXCLUDED.display_name,
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
