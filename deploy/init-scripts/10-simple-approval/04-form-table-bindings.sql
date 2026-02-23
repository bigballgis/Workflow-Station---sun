-- =============================================================================
-- Simple Approval Workflow - Form Table Bindings
-- =============================================================================
DO $bindings$
DECLARE
    v_function_unit_id  BIGINT;
    v_request_form_id   BIGINT;
    v_approval_form_id  BIGINT;
    v_main_table_id     BIGINT;
    v_sub_table_id      BIGINT;
    v_action_table_id   BIGINT;
    v_relation_table_id BIGINT;
BEGIN
    SELECT id INTO v_function_unit_id FROM dw_function_units WHERE code = 'SIMPLE_APPROVAL';
    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit SIMPLE_APPROVAL not found.';
    END IF;

    SELECT id INTO v_request_form_id  FROM dw_form_definitions WHERE function_unit_id = v_function_unit_id AND form_name = 'Request Form';
    SELECT id INTO v_approval_form_id FROM dw_form_definitions WHERE function_unit_id = v_function_unit_id AND form_name = 'Approval Form';
    SELECT id INTO v_main_table_id     FROM dw_table_definitions WHERE function_unit_id = v_function_unit_id AND table_name = 'Request';
    SELECT id INTO v_sub_table_id      FROM dw_table_definitions WHERE function_unit_id = v_function_unit_id AND table_name = 'RequestItems';
    SELECT id INTO v_action_table_id   FROM dw_table_definitions WHERE function_unit_id = v_function_unit_id AND table_name = 'ApprovalActions';
    SELECT id INTO v_relation_table_id FROM dw_table_definitions WHERE function_unit_id = v_function_unit_id AND table_name = 'RequestAttachments';

    DELETE FROM dw_form_table_bindings WHERE form_id IN (v_request_form_id, v_approval_form_id);

    -- Request Form: 申请人填写 (Task_SubmitRequest)
    INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at) VALUES
        (v_request_form_id, v_main_table_id,     'PRIMARY', 'EDITABLE', NULL,         1, NOW(), NOW()),
        (v_request_form_id, v_sub_table_id,      'SUB',     'EDITABLE', 'request_id', 2, NOW(), NOW()),
        (v_request_form_id, v_relation_table_id, 'RELATED', 'EDITABLE', 'request_id', 3, NOW(), NOW());

    -- Approval Form: 审批人操作 (Task_ManagerApproval)
    INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at) VALUES
        (v_approval_form_id, v_main_table_id,     'PRIMARY', 'READONLY', NULL,         1, NOW(), NOW()),
        (v_approval_form_id, v_sub_table_id,      'SUB',     'READONLY', 'request_id', 2, NOW(), NOW()),
        (v_approval_form_id, v_action_table_id,   'RELATED', 'EDITABLE', 'request_id', 3, NOW(), NOW()),
        (v_approval_form_id, v_relation_table_id, 'RELATED', 'READONLY', 'request_id', 4, NOW(), NOW());

    UPDATE dw_form_definitions SET bound_table_id = v_main_table_id WHERE id IN (v_request_form_id, v_approval_form_id);

    RAISE NOTICE 'Done: 7 bindings created for Request Form and Approval Form';
END $bindings$;
