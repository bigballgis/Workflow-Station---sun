-- =============================================================================
-- 21-fu-call-demo / 07: Caller form↔table bindings, sub-forms and stage bindings
--
-- budget_lines is bound with binding_link_mode = 'miParticipantRow': that is the
-- explicit declaration the multi-instance machinery reads to know "one row here
-- is one participant". extra_vendors is a plain structural-FK sub-table — it
-- feeds call activities, which have nothing to do with MI participants.
--
-- Dependencies: 04, 05, 06
-- =============================================================================

DO $bindings$
DECLARE
    v_fu_id             BIGINT;
    v_request_form_id   BIGINT;
    v_approval_form_id  BIGINT;
    v_budget_form_id    BIGINT;
    v_main_id           BIGINT;
    v_vendors_id        BIGINT;
    v_budget_id         BIGINT;
    v_binding_vendors   BIGINT;
    v_binding_budget    BIGINT;
    v_binding_budget_line BIGINT;
BEGIN
    SELECT id INTO v_fu_id FROM dw_function_units WHERE code = 'fu-call-demo-purchase';
    IF v_fu_id IS NULL THEN
        RAISE EXCEPTION 'Function unit fu-call-demo-purchase not found.';
    END IF;

    SELECT id INTO v_request_form_id  FROM dw_form_definitions
        WHERE function_unit_id = v_fu_id AND form_name = 'Purchase Request Form';
    SELECT id INTO v_approval_form_id FROM dw_form_definitions
        WHERE function_unit_id = v_fu_id AND form_name = 'Purchase Approval Form';
    SELECT id INTO v_budget_form_id   FROM dw_form_definitions
        WHERE function_unit_id = v_fu_id AND form_name = 'Budget Line Form';

    SELECT id INTO v_main_id    FROM dw_table_definitions
        WHERE function_unit_id = v_fu_id AND table_name = 'purchase_request';
    SELECT id INTO v_vendors_id FROM dw_table_definitions
        WHERE function_unit_id = v_fu_id AND table_name = 'extra_vendors';
    SELECT id INTO v_budget_id  FROM dw_table_definitions
        WHERE function_unit_id = v_fu_id AND table_name = 'budget_lines';

    DELETE FROM dw_form_table_bindings
    WHERE form_id IN (v_request_form_id, v_approval_form_id, v_budget_form_id);

    -- ---- Purchase Request Form ---------------------------------------------
    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_request_form_id, v_main_id, 'PRIMARY', 'EDITABLE', NULL, 1, NOW(), NOW());

    -- Additional vendors: plain sub-table. Each row drives one Function Unit call.
    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, created_at, updated_at
    ) VALUES
    (v_request_form_id, v_vendors_id, 'SUB', 'EDITABLE', 'request_id',
     'structuralFk', 2, NOW(), NOW())
    RETURNING id INTO v_binding_vendors;

    -- Budget lines: declared as the MI participant collection. This is the
    -- authoritative signal for "one row = one sub-task participant" — never
    -- inferred from column names.
    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, created_at, updated_at
    ) VALUES
    (v_request_form_id, v_budget_id, 'SUB', 'EDITABLE', 'request_id',
     'miParticipantRow', 3, NOW(), NOW())
    RETURNING id INTO v_binding_budget;

    -- ---- Purchase Approval Form --------------------------------------------
    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_approval_form_id, v_main_id, 'PRIMARY', 'READONLY', NULL, 1, NOW(), NOW());

    -- ---- Budget Line Form (one MI sub-task) --------------------------------
    -- The same shape as any MI sub-task form: the request as the read-only PRIMARY, and the
    -- participant table as a SUB binding (miParticipantRow), which the portal scopes to the
    -- sub-task's own row. Binding budget_lines as PRIMARY instead left the form empty: PRIMARY
    -- fields are filled from the request's own fields, not from a row.
    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, binding_link_mode, sort_order, created_at, updated_at
    ) VALUES
    (v_budget_form_id, v_main_id, 'PRIMARY', 'READONLY', NULL, 'structuralFk', 1, NOW(), NOW());

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, created_at, updated_at
    ) VALUES
    (v_budget_form_id, v_budget_id, 'SUB', 'EDITABLE', 'request_id',
     'miParticipantRow', 2, NOW(), NOW())
    RETURNING id INTO v_binding_budget_line;

    UPDATE dw_form_definitions
    SET config_json = jsonb_set(
            jsonb_set(
                config_json,
                '{rule}',
                (config_json->'rule') || jsonb_build_array(jsonb_build_object(
                    'name','ref_bl_line','type','inlineSubForm','title','Budget Line',
                    '_fc_id','id_bl_line','hidden',false,'display',true,
                    '_bindingId', v_binding_budget_line, '_fc_drag_tag','inlineSubForm','props','{}'::jsonb))),
            '{subForms}',
            jsonb_build_object(v_binding_budget_line::text, jsonb_build_object(
                'rule', jsonb_build_array(
                    jsonb_build_object('name','ref_bl_item','type','input','field','line_item','title','Line Item',
                        '_fc_id','id_bl_item','hidden',false,'display',true,'_fc_drag_tag','input',
                        'props', jsonb_build_object('maxlength',200,'placeholder','Line item')),
                    jsonb_build_object('name','ref_bl_amount','type','inputNumber','field','amount','title','Amount',
                        '_fc_id','id_bl_amount','hidden',false,'display',true,'_fc_drag_tag','inputNumber',
                        'props', jsonb_build_object('min',0,'precision',2)),
                    jsonb_build_object('name','ref_bl_confirm','type','select','field','owner_confirmed','title','Owner Confirmation',
                        '_fc_id','id_bl_confirm','hidden',false,'display',true,'_fc_drag_tag','select',
                        'props', jsonb_build_object('placeholder','Confirm this line','options', jsonb_build_array(
                            jsonb_build_object('label','Confirmed','value','YES'),
                            jsonb_build_object('label','Needs revision','value','NO'))))),
                'options', jsonb_build_object('form', jsonb_build_object('labelPosition','left')))),
            true)
    WHERE id = v_budget_form_id;

    -- ---- sub-forms on the request form -------------------------------------
    UPDATE dw_form_definitions
    SET config_json = jsonb_set(
            config_json,
            '{subForms}',
            jsonb_build_object(
                v_binding_vendors::text, jsonb_build_object(
                    'rule', jsonb_build_array(
                        jsonb_build_object(
                            'name','ref_ev_vendor','type','input','field','vendor_name',
                            'title','Vendor','_fc_id','id_ev_vendor','display',true,'hidden',false,
                            'props', jsonb_build_object('maxlength',200,'placeholder','Vendor name'),
                            '_fc_drag_tag','input'),
                        jsonb_build_object(
                            'name','ref_ev_cat','type','input','field','category',
                            'title','Category','_fc_id','id_ev_cat','display',true,'hidden',false,
                            'props', jsonb_build_object('maxlength',100,'placeholder','Category'),
                            '_fc_drag_tag','input')
                    ),
                    'options', jsonb_build_object('form', jsonb_build_object('labelPosition','left'))
                ),
                v_binding_budget::text, jsonb_build_object(
                    'rule', jsonb_build_array(
                        jsonb_build_object(
                            'name','ref_bl_item2','type','input','field','line_item',
                            'title','Line Item','_fc_id','id_bl_item2','display',true,'hidden',false,
                            'props', jsonb_build_object('maxlength',200,'placeholder','Line item'),
                            '_fc_drag_tag','input'),
                        jsonb_build_object(
                            'name','ref_bl_amt2','type','inputNumber','field','amount',
                            'title','Amount','_fc_id','id_bl_amt2','display',true,'hidden',false,
                            'props', jsonb_build_object('min',0,'precision',2),
                            '_fc_drag_tag','inputNumber'),
                        jsonb_build_object(
                            'name','ref_bl_owner2','type','input','field','owner_user_id',
                            'title','Budget Owner','_fc_id','id_bl_owner2','display',true,'hidden',false,
                            'props', jsonb_build_object('placeholder','User id of the budget owner'),
                            '_fc_drag_tag','input')
                    ),
                    'options', jsonb_build_object('form', jsonb_build_object('labelPosition','left'))
                )
            ),
            true)
    WHERE id = v_request_form_id;

    -- ---- the two sub-tables on the request form ----------------------------
    -- Without these the requester has nowhere to add rows: both per-row steps (the
    -- vendor calls and the budget-line sub-tasks) then run for zero rows and the
    -- request goes straight past them. They reference the bindings above by id,
    -- so they can only be placed here, after the bindings exist.
    UPDATE dw_form_definitions
    SET config_json = jsonb_set(
            config_json,
            '{rule}',
            (config_json->'rule') || jsonb_build_array(
                jsonb_build_object(
                    'name','ref_pr_extra_vendors','type','subTable','title','Additional Vendors',
                    '_fc_id','id_pr_extra_vendors','hidden',false,'display',true,
                    '_bindingId', v_binding_vendors, '_fc_drag_tag','subTable',
                    'props', jsonb_build_object('allowAdd',true,'allowEdit',true,'allowDelete',true)),
                jsonb_build_object(
                    'name','ref_pr_budget_lines','type','subTable','title','Budget Lines',
                    '_fc_id','id_pr_budget_lines','hidden',false,'display',true,
                    '_bindingId', v_binding_budget, '_fc_drag_tag','subTable',
                    'props', jsonb_build_object('allowAdd',true,'allowEdit',true,'allowDelete',true))))
    WHERE id = v_request_form_id;

    -- ---- stage bindings ----------------------------------------------------
    DELETE FROM dw_form_stage_bindings
    WHERE form_id IN (v_request_form_id, v_approval_form_id, v_budget_form_id);

    INSERT INTO dw_form_stage_bindings (form_id, stage_id, stage_name, read_only, scene, created_at)
    VALUES
    (v_request_form_id,  'PR_Task_Raise',      'Raise Request',       false, 'TASK', NOW()),
    (v_budget_form_id,   'PR_MI_Task_Confirm', 'Confirm Budget Line', false, 'TASK', NOW()),
    (v_approval_form_id, 'PR_Task_Approve',    'Approve Purchase',    false, 'TASK', NOW());

    RAISE NOTICE '[21-fu-call-demo] Caller bindings: vendors=%, budget=% (miParticipantRow)',
        v_binding_vendors, v_binding_budget;
END $bindings$;
