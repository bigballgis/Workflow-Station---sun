-- =============================================================================
-- 21-fu-call-demo / 00: Callee Function Unit — "Vendor Qualification Check"
--
-- The unit that gets CALLED. Its Startup Mode is CALLABLE, so it does not appear
-- in the Portal catalog as something a user starts directly; it exists to be
-- invoked by another Function Unit's call activity.
--
-- Demo narrative:
--   A purchase request needs each proposed vendor checked. The check itself is a
--   small, self-contained process owned by a different team — exactly the thing
--   worth having as its own Function Unit rather than copying into every caller.
--
-- Dependencies: none (first script of this demo)
-- Execution order: 00 → 01 → 02 → 03 → 04 → 05 → 06 → 07
-- =============================================================================

DO $main$
DECLARE
    v_fu_id            BIGINT;
    v_review_form_id   BIGINT;
    v_action_approve   BIGINT;
    v_action_reject    BIGINT;
BEGIN
    -- =========================================================================
    -- Function Unit
    -- =========================================================================
    INSERT INTO dw_function_units (
        code, name, display_name, status, startup_mode,
        current_version, version, is_active, enabled,
        deployed_at, lock_version, created_by, created_at, updated_by, updated_at
    ) VALUES (
        'fu-call-demo-vendor',
        'Vendor Qualification Check',
        'Called sub-process: reviews one vendor''s qualification and returns a verdict. '
        'Startup Mode is Callable, so it runs only when another Function Unit calls it.',
        'PUBLISHED',
        'CALLABLE',
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

    RAISE NOTICE '[21-fu-call-demo] Callee function unit: id=%, code=fu-call-demo-vendor', v_fu_id;

    -- Workspace visibility is computed purely from dev-group membership, with no
    -- "unassigned means visible to everyone" fallback: a unit with no group is
    -- invisible the moment a user selects any workspace. 90-post-seed backfills
    -- orphans, but only on a fresh-volume init — so the demo declares its own
    -- membership rather than relying on that.
    INSERT INTO dw_function_unit_dev_groups (function_unit_id, virtual_group_id, created_by, created_at)
    SELECT v_fu_id, 'vg-dev-public', 'system', CURRENT_TIMESTAMP
    WHERE NOT EXISTS (
        SELECT 1 FROM dw_function_unit_dev_groups
        WHERE function_unit_id = v_fu_id AND virtual_group_id = 'vg-dev-public');

    -- Rebuild this unit's own design artefacts so re-running the seed is idempotent.
    DELETE FROM dw_form_stage_bindings WHERE form_id IN (
        SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id);
    DELETE FROM dw_form_table_bindings WHERE form_id IN (
        SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id);
    DELETE FROM dw_form_definitions WHERE function_unit_id = v_fu_id;
    DELETE FROM dw_action_definitions WHERE function_unit_id = v_fu_id;

    -- =========================================================================
    -- Form: the reviewer's verdict on one vendor
    --
    -- This is also the form the CALLER points its call activity at, so that the
    -- purchase request can show what the check concluded without the viewer
    -- needing access to this unit.
    -- =========================================================================
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, display_name, config_json, created_at, updated_at
    ) VALUES (
        v_fu_id,
        'Vendor Review Form',
        'PROCESS',
        'Vendor qualification review: the reviewer records a verdict and the reasoning behind it.',
        '{"rule": [
            {"name":"ref_vc_vendor","type":"input","field":"vendor_name","props":{"maxlength":200,"placeholder":"Vendor under review"},"title":"Vendor","_fc_id":"id_vc_vendor","hidden":false,"display":true,"validate":[{"message":"Vendor is required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
            {"name":"ref_vc_cat","type":"input","field":"category","props":{"maxlength":100,"placeholder":"Goods or services category"},"title":"Category","_fc_id":"id_vc_cat","hidden":false,"display":true,"_fc_drag_tag":"input"},
            {"name":"ref_vc_result","type":"select","field":"check_result","props":{"placeholder":"Select the verdict","options":[{"label":"Qualified","value":"QUALIFIED"},{"label":"Not qualified","value":"NOT_QUALIFIED"},{"label":"Conditionally qualified","value":"CONDITIONAL"}]},"title":"Check Result","_fc_id":"id_vc_result","hidden":false,"display":true,"validate":[{"message":"Check result is required","trigger":"change","required":true}],"_fc_drag_tag":"select"},
            {"name":"ref_vc_score","type":"inputNumber","field":"risk_score","props":{"max":100,"min":0,"placeholder":"0-100"},"title":"Risk Score","_fc_id":"id_vc_score","hidden":false,"display":true,"_fc_drag_tag":"inputNumber"},
            {"name":"ref_vc_comment","type":"input","field":"reviewer_comment","props":{"rows":3,"type":"textarea","placeholder":"Reasoning behind the verdict"},"title":"Reviewer Comment","_fc_id":"id_vc_comment","hidden":false,"display":true,"_fc_drag_tag":"input"}
        ],"options":{"form":{"size":"default","inline":false,"labelWidth":"140px","labelPosition":"left","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}},"subForms":{}}'::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_review_form_id;

    -- =========================================================================
    -- Actions
    --
    -- Both are needed for the demo's strong-binding story: approving lets the
    -- caller continue, rejecting fails the caller with it.
    -- =========================================================================
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, display_name, config_json, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Approve Vendor', 'APPROVE',
        'Vendor passes the qualification check; the calling request continues.',
        '{"buttonText":"Approve","buttonType":"primary"}'::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_action_approve;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, display_name, config_json, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Reject Vendor', 'REJECT',
        'Vendor fails the check. Caller and callee are bound together, so the calling '
        'purchase request is rejected along with it.',
        '{"buttonText":"Reject","buttonType":"danger"}'::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_action_reject;

    RAISE NOTICE '[21-fu-call-demo] Callee form id=%, actions approve=%, reject=%',
        v_review_form_id, v_action_approve, v_action_reject;
END $main$;
