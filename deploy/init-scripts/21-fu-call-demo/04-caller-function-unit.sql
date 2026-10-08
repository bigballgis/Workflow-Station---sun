-- =============================================================================
-- 21-fu-call-demo / 04: Caller Function Unit — "Purchase Request (with FU calls)"
--
-- The unit that DOES the calling, and the one a user starts. It exercises every
-- part of the cross-Function-Unit call feature in a single process:
--
--   1. Call once          — one vendor, one child instance.
--   2. Call once per row  — a sub-table of extra vendors, one child instance each
--                           (callActivity + multiInstanceLoopCharacteristics).
--   3. Co-existence       — a multi-instance SUB-PROCESS (the pre-existing MI
--                           sub-task mechanism) sits in the same diagram, to show
--                           the two mechanisms do not interfere.
--   4. Strong binding     — rejecting a child fails this request; withdrawing
--                           this request terminates its children.
--
-- Startup Mode stays STANDALONE: users start this one, nobody calls it.
--
-- Dependencies: 00–03 (the callee must exist so its code can be referenced)
-- =============================================================================

DO $main$
DECLARE
    v_fu_id                 BIGINT;
    v_request_form_id       BIGINT;
    v_approval_form_id      BIGINT;
    v_budget_form_id        BIGINT;
    v_action_submit         BIGINT;
    v_action_approve        BIGINT;
    v_action_reject         BIGINT;
    v_action_confirm_budget BIGINT;
BEGIN
    INSERT INTO dw_function_units (
        code, name, display_name, status, startup_mode,
        current_version, version, is_active, enabled,
        deployed_at, lock_version, created_by, created_at, updated_by, updated_at
    ) VALUES (
        'fu-call-demo-purchase',
        'Purchase Request (Function Unit Calls)',
        'Demonstrates one Function Unit calling another: a single call for the primary vendor, '
        'one call per row for additional vendors, and a multi-instance sub-task block alongside '
        'them to show the two mechanisms coexist. Rejecting a called check fails this request.',
        'PUBLISHED',
        'STANDALONE',
        '1.0.0', '1.0.0',
        true, true,
        CURRENT_TIMESTAMP, 0,
        'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP
    )
    ON CONFLICT (code) DO UPDATE SET
        name            = EXCLUDED.name,
        display_name    = EXCLUDED.display_name,
        status          = EXCLUDED.status,
        startup_mode    = EXCLUDED.startup_mode,
        current_version = EXCLUDED.current_version,
        is_active       = EXCLUDED.is_active,
        enabled         = EXCLUDED.enabled,
        updated_by      = EXCLUDED.updated_by,
        updated_at      = CURRENT_TIMESTAMP
    RETURNING id INTO v_fu_id;

    RAISE NOTICE '[21-fu-call-demo] Caller function unit: id=%, code=fu-call-demo-purchase', v_fu_id;

    -- Same reasoning as the callee: declare the dev-group membership here instead
    -- of leaning on the 90-post-seed orphan backfill, which only runs on a
    -- fresh-volume init. Without a group the unit is invisible in every workspace
    -- except "All Groups".
    INSERT INTO dw_function_unit_dev_groups (function_unit_id, virtual_group_id, created_by, created_at)
    SELECT v_fu_id, 'vg-dev-public', 'system', CURRENT_TIMESTAMP
    WHERE NOT EXISTS (
        SELECT 1 FROM dw_function_unit_dev_groups
        WHERE function_unit_id = v_fu_id AND virtual_group_id = 'vg-dev-public');

    DELETE FROM dw_form_stage_bindings WHERE form_id IN (
        SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id);
    DELETE FROM dw_form_table_bindings WHERE form_id IN (
        SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id);
    DELETE FROM dw_form_definitions WHERE function_unit_id = v_fu_id;
    DELETE FROM dw_action_definitions WHERE function_unit_id = v_fu_id;

    -- =========================================================================
    -- Form 1: the request itself, with two sub-tables
    --   extra_vendors  → drives the per-row calls
    --   budget_lines   → drives the multi-instance sub-task block
    -- =========================================================================
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, display_name, config_json, created_at, updated_at
    ) VALUES (
        v_fu_id,
        'Purchase Request Form',
        'PROCESS',
        'Purchase request: what is being bought, the primary vendor, any additional vendors to '
        'check, and the budget lines that need owner sign-off.',
        '{"rule": [
            {"name":"ref_pr_title","type":"input","field":"title","props":{"maxlength":200,"placeholder":"What is being purchased"},"title":"Request Title","_fc_id":"id_pr_title","hidden":false,"display":true,"validate":[{"message":"Request title is required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
            {"name":"ref_pr_dept","type":"input","field":"department","props":{"maxlength":100,"placeholder":"Requesting department"},"title":"Department","_fc_id":"id_pr_dept","hidden":false,"display":true,"validate":[{"message":"Department is required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
            {"name":"ref_pr_amount","type":"inputNumber","field":"total_amount","props":{"min":0,"precision":2,"placeholder":"Total amount"},"title":"Total Amount","_fc_id":"id_pr_amount","hidden":false,"display":true,"validate":[{"message":"Total amount is required","trigger":"blur","required":true}],"_fc_drag_tag":"inputNumber"},
            {"name":"ref_pr_vendor","type":"input","field":"primary_vendor","props":{"maxlength":200,"placeholder":"Primary vendor to qualify"},"title":"Primary Vendor","_fc_id":"id_pr_vendor","hidden":false,"display":true,"validate":[{"message":"Primary vendor is required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
            {"name":"ref_pr_reason","type":"input","field":"justification","props":{"rows":3,"type":"textarea","placeholder":"Why this purchase is needed"},"title":"Justification","_fc_id":"id_pr_reason","hidden":false,"display":true,"_fc_drag_tag":"input"}
        ],"options":{"form":{"size":"default","inline":false,"labelWidth":"140px","labelPosition":"left","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}},"subForms":{}}'::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_request_form_id;

    -- =========================================================================
    -- Form 2: final approval, after every called check has returned
    -- =========================================================================
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, display_name, config_json, created_at, updated_at
    ) VALUES (
        v_fu_id,
        'Purchase Approval Form',
        'TASK',
        'Final approval: the approver sees the request plus every vendor check that came back.',
        '{"rule": [
            {"name":"ref_pa_title","type":"input","field":"title","props":{"disabled":true},"title":"Request Title","_fc_id":"id_pa_title","hidden":false,"display":true,"_fc_drag_tag":"input"},
            {"name":"ref_pa_amount","type":"inputNumber","field":"total_amount","props":{"disabled":true},"title":"Total Amount","_fc_id":"id_pa_amount","hidden":false,"display":true,"_fc_drag_tag":"inputNumber"},
            {"name":"ref_pa_vendor_result","type":"select","field":"primary_vendor_result","props":{"disabled":true,"options":[{"label":"Qualified","value":"QUALIFIED"},{"label":"Not qualified","value":"NOT_QUALIFIED"},{"label":"Conditionally qualified","value":"CONDITIONAL"}]},"title":"Primary Vendor Check","_fc_id":"id_pa_vendor_result","hidden":false,"display":true,"_fc_drag_tag":"select"},
            {"name":"ref_pa_decision","type":"input","field":"approver_note","props":{"rows":3,"type":"textarea","placeholder":"Approval note"},"title":"Approver Note","_fc_id":"id_pa_decision","hidden":false,"display":true,"_fc_drag_tag":"input"}
        ],"options":{"form":{"size":"default","inline":false,"labelWidth":"140px","labelPosition":"left","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}},"subForms":{}}'::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_approval_form_id;

    -- =========================================================================
    -- Form 3: one budget line, filled by its owner inside the MI sub-process
    --
    -- This is the PRE-EXISTING multi-instance sub-task mechanism, included so the
    -- demo proves cross-FU calls and MI sub-tasks run side by side untouched.
    -- =========================================================================
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, display_name, config_json, created_at, updated_at
    ) VALUES (
        v_fu_id,
        'Budget Line Form',
        'TASK',
        'One budget line, confirmed by its owner. Runs as a multi-instance sub-task — not a '
        'Function Unit call.',
        -- The line itself is an Inline Form over the budget_lines participant binding, added
        -- in 07-caller-bindings.sql once that binding (and its id) exists.
        '{"rule": [
            {"name":"ref_bl_title","type":"input","field":"title","props":{"readonly":true},"title":"Request Title","_fc_id":"id_bl_title","hidden":false,"display":true,"_fc_drag_tag":"input"}
        ],"options":{"form":{"size":"default","inline":false,"labelWidth":"150px","labelPosition":"left","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}},"subForms":{}}'::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_budget_form_id;

    -- =========================================================================
    -- Actions
    -- =========================================================================
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, display_name, config_json, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Submit Request', 'PROCESS_SUBMIT', 'Submit the purchase request.',
        '{"buttonText":"Submit","buttonType":"primary"}'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) RETURNING id INTO v_action_submit;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, display_name, config_json, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Confirm Budget Line', 'APPROVE', 'Budget owner confirms one line.',
        '{"buttonText":"Confirm","buttonType":"primary"}'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) RETURNING id INTO v_action_confirm_budget;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, display_name, config_json, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Approve Purchase', 'APPROVE', 'Approve the purchase request.',
        '{"buttonText":"Approve","buttonType":"primary"}'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) RETURNING id INTO v_action_approve;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, display_name, config_json, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Reject Purchase', 'REJECT', 'Reject the purchase request.',
        '{"buttonText":"Reject","buttonType":"danger"}'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) RETURNING id INTO v_action_reject;

    RAISE NOTICE '[21-fu-call-demo] Caller forms: request=%, approval=%, budget=%',
        v_request_form_id, v_approval_form_id, v_budget_form_id;
END $main$;
