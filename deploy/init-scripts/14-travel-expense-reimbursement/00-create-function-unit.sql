-- =============================================================================
-- 14-travel-expense-reimbursement: Create Function Unit, Forms, and Actions
-- 差旅报销功能单元：创建功能单元、表单定义和动作定义
--
-- Dependencies: None (this is the first script)
-- Execution order: 00 → 01 → 02 → 03
-- =============================================================================

DO $main$
DECLARE
    v_function_unit_id      BIGINT;
    v_reimbursement_form_id BIGINT;
    v_approval_form_id      BIGINT;
    v_action_submit_id      BIGINT;
    v_action_n8n_id         BIGINT;
    v_action_approve_id     BIGINT;
    v_action_reject_id      BIGINT;
BEGIN
    -- =========================================================================
    -- Step 1: Function Unit
    -- =========================================================================
    INSERT INTO dw_function_units (
        code, name, description, status,
        current_version, version, is_active, enabled,
        deployed_at, lock_version, created_by, created_at, updated_by, updated_at
    ) VALUES (
        'TRAVEL_EXPENSE_REIMBURSEMENT',
        'Travel Expense Reimbursement',
        'Travel expense reimbursement workflow with AI invoice recognition via N8N',
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

    RAISE NOTICE 'Function unit created/updated: id=%, code=TRAVEL_EXPENSE_REIMBURSEMENT', v_function_unit_id;

    -- =========================================================================
    -- Step 2: Forms
    -- =========================================================================

    -- Reimbursement Form (MAIN)
    -- config_json includes 9 main fields: reimbursement_number, apply_date, applicant_name,
    -- department, travel_destination, travel_start_date, travel_end_date, travel_purpose, total_amount
    -- subForms are populated later in 03-form-table-bindings.sql after binding IDs are known
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, description, config_json, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'Reimbursement Form',
        'MAIN',
        'Travel expense reimbursement application form',
        '{"rule": [{"name": "ref_Ft1rmm1s0b1gacc", "type": "input", "field": "reimbursement_number", "props": {"maxlength": 50, "placeholder": "Please input Reimbursement Number", "showWordLimit": true}, "title": "Reimbursement Number", "_fc_id": "id_Ft1rmm1s0b1gabc", "hidden": false, "display": true, "validate": [{"message": "Reimbursement Number required", "trigger": "blur", "required": true}], "_fc_drag_tag": "input"}, {"name": "ref_Ft2rmm1s0b1gaec", "type": "datePicker", "field": "apply_date", "props": {"type": "datetime", "placeholder": "Please select Apply Date", "valueFormat": "YYYY-MM-DD HH:mm:ss"}, "title": "Apply Date", "_fc_id": "id_Ft2rmm1s0b1gadc", "hidden": false, "display": true, "validate": [{"message": "Apply Date required", "trigger": "blur", "required": true}], "_fc_drag_tag": "datePicker"}, {"name": "ref_Ft3rmm1s0b1gagc", "type": "input", "field": "applicant_name", "props": {"maxlength": 100, "placeholder": "Please input Applicant Name", "showWordLimit": true}, "title": "Applicant Name", "_fc_id": "id_Ft3rmm1s0b1gafc", "hidden": false, "display": true, "validate": [{"message": "Applicant Name required", "trigger": "blur", "required": true}], "_fc_drag_tag": "input"}, {"name": "ref_Ft4rmm1s0b1gaic", "type": "input", "field": "department", "props": {"maxlength": 100, "placeholder": "Please input Department", "showWordLimit": true}, "title": "Department", "_fc_id": "id_Ft4rmm1s0b1gahc", "hidden": false, "display": true, "validate": [{"message": "Department required", "trigger": "blur", "required": true}], "_fc_drag_tag": "input"}, {"name": "ref_Ft5rmm1s0b1gakc", "type": "input", "field": "travel_destination", "props": {"maxlength": 200, "placeholder": "Please input Travel Destination", "showWordLimit": true}, "title": "Travel Destination", "_fc_id": "id_Ft5rmm1s0b1gajc", "hidden": false, "display": true, "validate": [{"message": "Travel Destination required", "trigger": "blur", "required": true}], "_fc_drag_tag": "input"}, {"name": "ref_Ft6rmm1s0b1gamc", "type": "datePicker", "field": "travel_start_date", "props": {"type": "date", "placeholder": "Please select Travel Start Date", "valueFormat": "YYYY-MM-DD"}, "title": "Travel Start Date", "_fc_id": "id_Ft6rmm1s0b1galc", "hidden": false, "display": true, "validate": [{"message": "Travel Start Date required", "trigger": "blur", "required": true}], "_fc_drag_tag": "datePicker"}, {"name": "ref_Ft7rmm1s0b1gaoc", "type": "datePicker", "field": "travel_end_date", "props": {"type": "date", "placeholder": "Please select Travel End Date", "valueFormat": "YYYY-MM-DD"}, "title": "Travel End Date", "_fc_id": "id_Ft7rmm1s0b1ganc", "hidden": false, "display": true, "validate": [{"message": "Travel End Date required", "trigger": "blur", "required": true}], "_fc_drag_tag": "datePicker"}, {"name": "ref_Ft8rmm1s0b1gaqc", "type": "input", "field": "travel_purpose", "props": {"rows": 3, "type": "textarea", "placeholder": "Please input Travel Purpose"}, "title": "Travel Purpose", "_fc_id": "id_Ft8rmm1s0b1gapc", "hidden": false, "display": true, "validate": [{"message": "Travel Purpose required", "trigger": "blur", "required": true}], "_fc_drag_tag": "input"}, {"name": "ref_Ft9rmm1s0b1gasc", "type": "inputNumber", "field": "total_amount", "props": {"precision": 2, "placeholder": "Please input Total Amount"}, "title": "Total Amount", "_fc_id": "id_Ft9rmm1s0b1garc", "hidden": false, "display": true, "_fc_drag_tag": "inputNumber"}], "options": {"form": {"size": "default", "inline": false, "labelWidth": "125px", "labelPosition": "left", "hideRequiredAsterisk": false}, "resetBtn": {"show": false, "innerText": "Reset"}, "submitBtn": {"show": true, "innerText": "Submit"}}, "subForms": {}}',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, form_name) DO UPDATE SET
        config_json = EXCLUDED.config_json,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_reimbursement_form_id;

    RAISE NOTICE 'Reimbursement Form created/updated: id=%', v_reimbursement_form_id;

    -- Approval Form (MAIN)
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, description, config_json, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'Approval Form',
        'MAIN',
        'Manager approval form for travel expense reimbursement',
        '{"rule": [{"name": "ref_Fta1mm1s0b2gaec", "type": "input", "field": "approval_comment", "props": {"rows": 3, "type": "textarea", "placeholder": "Please input Approval Comment"}, "title": "Approval Comment", "_fc_id": "id_Fta1mm1s0b2gadc", "hidden": false, "display": true, "_fc_drag_tag": "input"}], "options": {"form": {"size": "default", "inline": false, "labelWidth": "125px", "labelPosition": "right", "hideRequiredAsterisk": false}, "resetBtn": {"show": false, "innerText": "Reset"}, "submitBtn": {"show": true, "innerText": "Submit"}}, "subForms": {}}',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, form_name) DO UPDATE SET
        config_json = EXCLUDED.config_json,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_approval_form_id;

    RAISE NOTICE 'Approval Form created/updated: id=%', v_approval_form_id;

    -- =========================================================================
    -- Step 3: Actions
    -- =========================================================================

    -- 提交报销 (Submit Reimbursement)
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        '提交报销',
        'PROCESS_SUBMIT',
        '{"url":"","body":"","formId":null,"method":"POST","script":"","headers":"","targetStep":"","dialogTitle":"","dialogWidth":"600px","targetStatus":"","confirmMessage":"确认提交此报销申请？","requireComment":true,"successMessage":"报销申请已提交","requireAssignee":false}',
        NULL, NULL,
        'Submit reimbursement to start approval workflow',
        false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type  = EXCLUDED.action_type,
        config_json  = EXCLUDED.config_json,
        description  = EXCLUDED.description,
        updated_at   = CURRENT_TIMESTAMP
    RETURNING id INTO v_action_submit_id;

    RAISE NOTICE 'Submit Reimbursement action created/updated: id=%', v_action_submit_id;

    -- AI 识别发票 (AI Invoice Recognition)
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        'AI 识别发票',
        'N8N_ACTION',
        '{"n8nConfigId":"travel-expense-invoice-recognition","n8nWorkflowId":"travel-expense-invoice-recognition","webhookUrl":"http://platform-n8n-dev:5678/webhook/invoice-recognition","timeoutSeconds":120,"inputMapping":[{"paramName":"invoiceFiles","paramLabel":"发票文件","paramType":"file_list","required":true}],"outputMapping":[{"source":"expenseItems","target":"ExpenseItems"},{"source":"summary.totalAmount","target":"total_amount"},{"source":"invoices","target":"InvoiceRecognitionResults"}]}',
        NULL, NULL,
        'AI invoice recognition via N8N workflow',
        false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type  = EXCLUDED.action_type,
        config_json  = EXCLUDED.config_json,
        description  = EXCLUDED.description,
        updated_at   = CURRENT_TIMESTAMP
    RETURNING id INTO v_action_n8n_id;

    RAISE NOTICE 'AI Invoice Recognition action created/updated: id=%', v_action_n8n_id;

    -- 审批通过 (Approve)
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        '审批通过',
        'APPROVE',
        '{"targetStatus":"APPROVED","confirmMessage":"确认审批通过此报销申请？","requireComment":true,"successMessage":"报销申请已审批通过"}',
        'Check', 'success',
        'Approve the reimbursement request',
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

    -- 审批驳回 (Reject)
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_function_unit_id,
        '审批驳回',
        'REJECT',
        '{"targetStatus":"REJECTED","requireReason":true,"confirmMessage":"确认驳回此报销申请？","requireComment":true,"successMessage":"报销申请已驳回"}',
        'Close', 'danger',
        'Reject the reimbursement request',
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
    RAISE NOTICE 'Function Unit ID       : %', v_function_unit_id;
    RAISE NOTICE 'Reimbursement Form ID  : %', v_reimbursement_form_id;
    RAISE NOTICE 'Approval Form ID       : %', v_approval_form_id;
    RAISE NOTICE 'Actions: Submit=%, N8N=%, Approve=%, Reject=%',
        v_action_submit_id, v_action_n8n_id, v_action_approve_id, v_action_reject_id;
    RAISE NOTICE 'Next: run 01-create-tables.sql';
    RAISE NOTICE '========================================';

END $main$;
