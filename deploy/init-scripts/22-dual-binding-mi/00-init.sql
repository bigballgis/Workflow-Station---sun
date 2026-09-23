\set ON_ERROR_STOP on

-- =============================================================================
-- 22-dual-binding-mi: MI Complete vehicle, same-table two SUB bindings
-- Code: p3-mi-dual-binding-test
-- Tables: p3_mi_case (MAIN), p3_mi_party (MI collection), p3_mi_file (two FKs)
-- Not loaded by 00-init-all. Unique names so Demo attachment merger stays unambiguous.
-- =============================================================================

DO $p3$
DECLARE
    v_fu_id            BIGINT;
    v_start_form       BIGINT;
    v_task_form        BIGINT;
    v_case_id          BIGINT;
    v_party_id         BIGINT;
    v_file_id          BIGINT;
    v_case_pk          BIGINT;
    v_party_pk         BIGINT;
    v_file_pk          BIGINT;
    v_party_fk_case    BIGINT;
    v_file_fk_case     BIGINT;
    v_file_fk_party    BIGINT;
    v_bind_start_pri   BIGINT;
    v_bind_start_party BIGINT;
    v_bind_start_fc    BIGINT;
    v_bind_start_fp    BIGINT;
    v_bind_task_pri    BIGINT;
    v_bind_task_party  BIGINT;
    v_bind_task_fc     BIGINT;
    v_bind_task_fp     BIGINT;
    v_view_sp          BIGINT;
    v_view_sfc         BIGINT;
    v_view_sfp         BIGINT;
    v_view_tp          BIGINT;
    v_view_tfc         BIGINT;
    v_view_tfp         BIGINT;
    v_action_submit    BIGINT;
    v_action_save      BIGINT;
    v_action_approve   BIGINT;
    v_bpmn_xml         TEXT;
    v_pk_json          JSONB;
    v_party_subform    JSONB;
    v_file_subform     JSONB;
BEGIN
    SELECT id INTO v_fu_id FROM dw_function_units WHERE code = 'p3-mi-dual-binding-test';
    IF v_fu_id IS NOT NULL THEN
        DELETE FROM dw_form_table_bindings
        WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id);
        DELETE FROM dw_function_units WHERE id = v_fu_id;
    END IF;

    INSERT INTO dw_function_units (
        code, name, display_name, status,
        current_version, version, is_active, enabled,
        deployed_at, lock_version, created_by, created_at, updated_by, updated_at
    ) VALUES (
        'p3-mi-dual-binding-test',
        'P3 MI Dual Binding Test',
        'Local P3 vehicle: MI subtask form binds p3_mi_file twice (case_id vs party_id).',
        'DRAFT',
        '1.0.0', '1.0.0',
        true, true,
        CURRENT_TIMESTAMP, 0,
        'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_fu_id;

    INSERT INTO dw_function_unit_dev_groups (function_unit_id, virtual_group_id, created_at, created_by)
    VALUES (v_fu_id, 'vg-dev-public', CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (function_unit_id, virtual_group_id) DO NOTHING;

    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_display_name, table_type, display_name, created_at, updated_at
    ) VALUES
    (v_fu_id, 'p3_mi_case', 'P3 MI Case', 'MAIN', 'Case (MAIN)', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_case_id;

    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_display_name, table_type, display_name, created_at, updated_at
    ) VALUES
    (v_fu_id, 'p3_mi_party', 'P3 MI Party', 'SUB', 'MI collection parties', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_party_id;

    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_display_name, table_type, display_name, created_at, updated_at
    ) VALUES
    (v_fu_id, 'p3_mi_file', 'P3 MI File', 'SUB', 'Files bound twice (case vs party)', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_file_id;

    v_pk_json := '{"scope":"perTable","padWidth":6,"strategy":"prefixedSequence","startValue":1}'::jsonb;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, nullable, is_primary_key, is_unique,
        display_name, sort_order, is_foreign_key, ref_table_id, ref_primary_key_fields,
        pk_generation_json, fk_display_mode, relation_cardinality
    ) VALUES
    (v_case_id, 'id', 'VARCHAR', 64, false, true, true, 'Case ID', 0, false, NULL, NULL,
     v_pk_json || '{"prefix":"MiCase-"}'::jsonb, 'readonly', NULL),
    (v_case_id, 'title', 'VARCHAR', 200, false, false, false, 'Title', 1, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_case_id, 'created_at', 'TIMESTAMP', NULL, true, false, false, 'Created At', 2, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_case_id, 'created_by', 'VARCHAR', 64, true, false, false, 'Created By', 3, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_case_id, 'updated_at', 'TIMESTAMP', NULL, true, false, false, 'Updated At', 4, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_case_id, 'updated_by', 'VARCHAR', 64, true, false, false, 'Updated By', 5, false, NULL, NULL, NULL, 'readonly', NULL);

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, nullable, is_primary_key, is_unique,
        display_name, sort_order, is_foreign_key, ref_table_id, ref_primary_key_fields,
        pk_generation_json, fk_display_mode, relation_cardinality
    ) VALUES
    (v_party_id, 'id', 'VARCHAR', 64, false, true, true, 'Party ID', 0, false, NULL, NULL,
     v_pk_json || '{"prefix":"MiParty-"}'::jsonb, 'readonly', NULL),
    (v_party_id, 'case_id', 'VARCHAR', 64, false, false, false, 'Case ID', 1, true, v_case_id, '["id"]'::jsonb, NULL, 'readonly', 'oneToMany'),
    (v_party_id, 'party_name', 'VARCHAR', 100, false, false, false, 'Party name', 2, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_party_id, 'assignee_user_id', 'VARCHAR', 64, false, false, false, 'Assignee user ID', 3, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_party_id, 'created_at', 'TIMESTAMP', NULL, true, false, false, 'Created At', 4, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_party_id, 'created_by', 'VARCHAR', 64, true, false, false, 'Created By', 5, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_party_id, 'updated_at', 'TIMESTAMP', NULL, true, false, false, 'Updated At', 6, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_party_id, 'updated_by', 'VARCHAR', 64, true, false, false, 'Updated By', 7, false, NULL, NULL, NULL, 'readonly', NULL);

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, nullable, is_primary_key, is_unique,
        display_name, sort_order, is_foreign_key, ref_table_id, ref_primary_key_fields,
        pk_generation_json, fk_display_mode, relation_cardinality
    ) VALUES
    (v_file_id, 'id', 'VARCHAR', 64, false, true, true, 'File ID', 0, false, NULL, NULL,
     v_pk_json || '{"prefix":"MiFile-"}'::jsonb, 'readonly', NULL),
    (v_file_id, 'case_id', 'VARCHAR', 64, true, false, false, 'Case ID', 1, true, v_case_id, '["id"]'::jsonb, NULL, 'readonly', 'oneToMany'),
    (v_file_id, 'party_id', 'VARCHAR', 64, true, false, false, 'Party ID', 2, true, v_party_id, '["id"]'::jsonb, NULL, 'readonly', 'oneToMany'),
    (v_file_id, 'file_name', 'VARCHAR', 200, false, false, false, 'File name', 3, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_file_id, 'created_at', 'TIMESTAMP', NULL, true, false, false, 'Created At', 4, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_file_id, 'created_by', 'VARCHAR', 64, true, false, false, 'Created By', 5, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_file_id, 'updated_at', 'TIMESTAMP', NULL, true, false, false, 'Updated At', 6, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_file_id, 'updated_by', 'VARCHAR', 64, true, false, false, 'Updated By', 7, false, NULL, NULL, NULL, 'readonly', NULL);

    SELECT id INTO v_case_pk FROM dw_field_definitions WHERE table_id = v_case_id AND field_name = 'id';
    SELECT id INTO v_party_pk FROM dw_field_definitions WHERE table_id = v_party_id AND field_name = 'id';
    SELECT id INTO v_file_pk FROM dw_field_definitions WHERE table_id = v_file_id AND field_name = 'id';
    SELECT id INTO v_party_fk_case FROM dw_field_definitions WHERE table_id = v_party_id AND field_name = 'case_id';
    SELECT id INTO v_file_fk_case FROM dw_field_definitions WHERE table_id = v_file_id AND field_name = 'case_id';
    SELECT id INTO v_file_fk_party FROM dw_field_definitions WHERE table_id = v_file_id AND field_name = 'party_id';

    INSERT INTO dw_foreign_keys (table_id, field_id, ref_table_id, ref_field_id, on_delete, on_update) VALUES
    (v_party_id, v_party_fk_case, v_case_id, v_case_pk, 'NO ACTION', 'NO ACTION'),
    (v_file_id, v_file_fk_case, v_case_id, v_case_pk, 'NO ACTION', 'NO ACTION'),
    (v_file_id, v_file_fk_party, v_party_id, v_party_pk, 'NO ACTION', 'NO ACTION');

    INSERT INTO dw_table_relations (
        function_unit_id, source_table_id, source_field_name, relation_type,
        target_table_id, target_field_name, created_at, updated_at
    ) VALUES
    (v_fu_id, v_case_id, 'id', 'ONE_TO_MANY', v_party_id, 'case_id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (v_fu_id, v_case_id, 'id', 'ONE_TO_MANY', v_file_id, 'case_id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (v_fu_id, v_party_id, 'id', 'ONE_TO_MANY', v_file_id, 'party_id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

    INSERT INTO dw_pk_sequences (table_id, field_name, scope_type, scope_key, prefix, pad_width, current_value)
    VALUES
    (v_case_id, 'id', 'perTable', '', 'MiCase-', 6, 0),
    (v_party_id, 'id', 'perTable', '', 'MiParty-', 6, 0),
    (v_file_id, 'id', 'perTable', '', 'MiFile-', 6, 0);

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, display_name, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Submit', 'PROCESS_SUBMIT',
        '{"confirmMessage":"Submit this case?","requireComment":false,"successMessage":"Submitted."}'::jsonb,
        'Upload', 'primary', 'Submit case', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_action_submit;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, display_name, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Save', 'SAVE',
        '{"confirmMessage":"","requireComment":false,"successMessage":"Saved."}'::jsonb,
        'Document', 'default', 'Save draft', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_action_save;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, display_name, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Approve', 'APPROVE',
        '{"confirmMessage":"","requireComment":false,"successMessage":"Approved."}'::jsonb,
        'Check', 'success', 'Approve', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_action_approve;

    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, display_name, bound_table_id, scene, config_json, created_at, updated_at
    ) VALUES (
        v_fu_id, 'P3 MI Start Form', 'PROCESS',
        'Start form: parties plus the same file table bound twice',
        v_case_id, 'TASK',
        '{"rule":[],"options":{"form":{"labelWidth":"125px","labelPosition":"left"}},"subForms":{}}'::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_start_form;

    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, display_name, bound_table_id, scene, config_json, created_at, updated_at
    ) VALUES (
        v_fu_id, 'P3 MI Subtask Form', 'TASK',
        'MI subtask: case files vs party files on p3_mi_file',
        v_case_id, 'TASK',
        '{"rule":[],"options":{"form":{"labelWidth":"125px","labelPosition":"left"}},"subForms":{}}'::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_task_form;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, sub_mode, filter_fk_field_id, created_at, updated_at
    ) VALUES
    (v_start_form, v_case_id, 'PRIMARY', 'EDITABLE', NULL, 'structuralFk', 0, NULL, NULL,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_bind_start_pri;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, sub_mode, filter_fk_field_id, created_at, updated_at
    ) VALUES
    (v_start_form, v_party_id, 'SUB', 'EDITABLE', 'case_id', 'miParticipantRow', 1, 'FULL', v_party_fk_case,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_bind_start_party;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, sub_mode, filter_fk_field_id, created_at, updated_at
    ) VALUES
    (v_start_form, v_file_id, 'SUB', 'EDITABLE', 'case_id', 'structuralFk', 2, 'FULL', v_file_fk_case,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_bind_start_fc;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, sub_mode, filter_fk_field_id, created_at, updated_at
    ) VALUES
    (v_start_form, v_file_id, 'SUB', 'EDITABLE', 'party_id', 'structuralFk', 3, 'FULL', v_file_fk_party,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_bind_start_fp;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, sub_mode, filter_fk_field_id, created_at, updated_at
    ) VALUES
    (v_task_form, v_case_id, 'PRIMARY', 'READONLY', NULL, 'structuralFk', 0, NULL, NULL,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_bind_task_pri;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, sub_mode, filter_fk_field_id, created_at, updated_at
    ) VALUES
    (v_task_form, v_party_id, 'SUB', 'READONLY', 'case_id', 'miParticipantRow', 1, 'FULL', v_party_fk_case,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_bind_task_party;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, sub_mode, filter_fk_field_id, created_at, updated_at
    ) VALUES
    (v_task_form, v_file_id, 'SUB', 'EDITABLE', 'case_id', 'structuralFk', 2, 'FULL', v_file_fk_case,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_bind_task_fc;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, sub_mode, filter_fk_field_id, created_at, updated_at
    ) VALUES
    (v_task_form, v_file_id, 'SUB', 'EDITABLE', 'party_id', 'structuralFk', 3, 'FULL', v_file_fk_party,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_bind_task_fp;

    INSERT INTO dw_sub_table_view_configs (binding_id, created_at, updated_at) VALUES
    (v_bind_start_party, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id INTO v_view_sp;
    INSERT INTO dw_sub_table_view_configs (binding_id, created_at, updated_at) VALUES
    (v_bind_start_fc, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id INTO v_view_sfc;
    INSERT INTO dw_sub_table_view_configs (binding_id, created_at, updated_at) VALUES
    (v_bind_start_fp, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id INTO v_view_sfp;
    INSERT INTO dw_sub_table_view_configs (binding_id, created_at, updated_at) VALUES
    (v_bind_task_party, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id INTO v_view_tp;
    INSERT INTO dw_sub_table_view_configs (binding_id, created_at, updated_at) VALUES
    (v_bind_task_fc, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id INTO v_view_tfc;
    INSERT INTO dw_sub_table_view_configs (binding_id, created_at, updated_at) VALUES
    (v_bind_task_fp, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id INTO v_view_tfp;

    INSERT INTO dw_sub_table_view_fields (view_config_id, field_name, display_label, column_width, sort_order, visible) VALUES
    (v_view_sp, 'id', 'Party ID', 150, 0, true),
    (v_view_sp, 'party_name', 'Party name', 160, 1, true),
    (v_view_sp, 'assignee_user_id', 'Assignee', 200, 2, true),
    (v_view_sfc, 'id', 'File ID', 150, 0, true),
    (v_view_sfc, 'file_name', 'File name', 200, 1, true),
    (v_view_sfp, 'id', 'File ID', 150, 0, true),
    (v_view_sfp, 'file_name', 'File name', 200, 1, true),
    (v_view_tp, 'id', 'Party ID', 150, 0, true),
    (v_view_tp, 'party_name', 'Party name', 160, 1, true),
    (v_view_tfc, 'id', 'File ID', 150, 0, true),
    (v_view_tfc, 'file_name', 'File name', 200, 1, true),
    (v_view_tfp, 'id', 'File ID', 150, 0, true),
    (v_view_tfp, 'file_name', 'File name', 200, 1, true);

    UPDATE dw_form_table_bindings SET sub_list_view_id = v_view_sp WHERE id = v_bind_start_party;
    UPDATE dw_form_table_bindings SET sub_list_view_id = v_view_sfc WHERE id = v_bind_start_fc;
    UPDATE dw_form_table_bindings SET sub_list_view_id = v_view_sfp WHERE id = v_bind_start_fp;
    UPDATE dw_form_table_bindings SET sub_list_view_id = v_view_tp WHERE id = v_bind_task_party;
    UPDATE dw_form_table_bindings SET sub_list_view_id = v_view_tfc WHERE id = v_bind_task_fc;
    UPDATE dw_form_table_bindings SET sub_list_view_id = v_view_tfp WHERE id = v_bind_task_fp;

    v_party_subform := '{"rule":[{"name":"ref_p3_pn","type":"input","field":"party_name","title":"Party name","_fc_id":"id_p3_pn","hidden":false,"display":true,"_fc_drag_tag":"input","props":{"maxlength":100}},{"name":"ref_p3_as","type":"input","field":"assignee_user_id","title":"Assignee user ID","_fc_id":"id_p3_as","hidden":false,"display":true,"_fc_drag_tag":"input","props":{"maxlength":64}}],"options":{"form":{"labelWidth":"125px","labelPosition":"left"},"submitBtn":{"show":true,"innerText":"Submit"},"resetBtn":{"show":false}}}'::jsonb;
    v_file_subform := '{"rule":[{"name":"ref_p3_fn","type":"input","field":"file_name","title":"File name","_fc_id":"id_p3_fn","hidden":false,"display":true,"_fc_drag_tag":"input","props":{"maxlength":200}}],"options":{"form":{"labelWidth":"125px","labelPosition":"left"},"submitBtn":{"show":true,"innerText":"Submit"},"resetBtn":{"show":false}}}'::jsonb;

    UPDATE dw_form_definitions
    SET config_json = jsonb_build_object(
            'rule', jsonb_build_array(
                jsonb_build_object(
                    'name', 'ref_p3_title', 'type', 'input', 'field', 'title',
                    'title', 'Title', '_fc_id', 'id_p3_title', 'hidden', false, 'display', true,
                    '_fc_drag_tag', 'input',
                    'props', jsonb_build_object('maxlength', 200, 'placeholder', 'Case title'),
                    'validate', jsonb_build_array(jsonb_build_object('message', 'Title is required', 'trigger', 'blur', 'required', true))
                ),
                jsonb_build_object(
                    'name', 'ref_p3_party', 'type', 'subTable', 'title', 'Parties',
                    '_fc_id', 'id_p3_party', 'hidden', false, 'display', true,
                    '_fc_drag_tag', 'subTable', '_bindingId', v_bind_start_party, 'props', '{}'::jsonb
                ),
                jsonb_build_object(
                    'name', 'ref_p3_file_case', 'type', 'subTable', 'title', 'Case files',
                    '_fc_id', 'id_p3_file_case', 'hidden', false, 'display', true,
                    '_fc_drag_tag', 'subTable', '_bindingId', v_bind_start_fc, 'props', '{}'::jsonb
                ),
                jsonb_build_object(
                    'name', 'ref_p3_file_party', 'type', 'subTable', 'title', 'Party files',
                    '_fc_id', 'id_p3_file_party', 'hidden', false, 'display', true,
                    '_fc_drag_tag', 'subTable', '_bindingId', v_bind_start_fp, 'props', '{}'::jsonb
                )
            ),
            'options', jsonb_build_object(
                'form', jsonb_build_object('size', 'default', 'inline', false, 'labelWidth', '125px', 'labelPosition', 'left', 'hideRequiredAsterisk', false),
                'resetBtn', jsonb_build_object('show', false, 'innerText', 'Reset'),
                'submitBtn', jsonb_build_object('show', true, 'innerText', 'Submit')
            ),
            'subForms', jsonb_build_object(
                v_bind_start_party::text, v_party_subform,
                v_bind_start_fc::text, v_file_subform,
                v_bind_start_fp::text, v_file_subform
            )
        ),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = v_start_form;

    UPDATE dw_form_definitions
    SET config_json = jsonb_build_object(
            'rule', jsonb_build_array(
                jsonb_build_object(
                    'name', 'ref_p3t_title', 'type', 'input', 'field', 'title',
                    'title', 'Title', '_fc_id', 'id_p3t_title', 'hidden', false, 'display', true,
                    'readonly', true, '_fc_drag_tag', 'input',
                    'props', jsonb_build_object('readonly', true)
                ),
                jsonb_build_object(
                    'name', 'ref_p3t_party', 'type', 'subTable', 'title', 'Parties',
                    '_fc_id', 'id_p3t_party', 'hidden', false, 'display', true,
                    '_fc_drag_tag', 'subTable', '_bindingId', v_bind_task_party, 'props', '{}'::jsonb
                ),
                jsonb_build_object(
                    'name', 'ref_p3t_file_case', 'type', 'subTable', 'title', 'Case files',
                    '_fc_id', 'id_p3t_file_case', 'hidden', false, 'display', true,
                    '_fc_drag_tag', 'subTable', '_bindingId', v_bind_task_fc, 'props', '{}'::jsonb
                ),
                jsonb_build_object(
                    'name', 'ref_p3t_file_party', 'type', 'subTable', 'title', 'Party files',
                    '_fc_id', 'id_p3t_file_party', 'hidden', false, 'display', true,
                    '_fc_drag_tag', 'subTable', '_bindingId', v_bind_task_fp, 'props', '{}'::jsonb
                )
            ),
            'options', jsonb_build_object(
                'form', jsonb_build_object('size', 'default', 'inline', false, 'labelWidth', '125px', 'labelPosition', 'left', 'hideRequiredAsterisk', false),
                'resetBtn', jsonb_build_object('show', false, 'innerText', 'Reset'),
                'submitBtn', jsonb_build_object('show', true, 'innerText', 'Submit')
            ),
            'subForms', jsonb_build_object(
                v_bind_task_party::text, v_party_subform,
                v_bind_task_fc::text, v_file_subform,
                v_bind_task_fp::text, v_file_subform
            )
        ),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = v_task_form;

    v_bpmn_xml := format(
        $xml$<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:custom_1="http://custom.bpmn.io/schema" xmlns:custom="http://workflow.platform/schema/custom" xmlns:flowable="http://flowable.org/bpmn" id="Definitions_P3MiDual" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="p3-mi-dual-binding-test" name="P3 MI Dual Binding Test" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="Task_FillMiDual" name="Fill MI Dual Binding Case">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[%s,%s]" />
          <custom_1:values name="actionNames" value="[&amp;#34;Submit&amp;#34;,&amp;#34;Save&amp;#34;]" />
          <custom_1:values name="formId" value="%s" />
          <custom_1:values name="formName" value="P3 MI Start Form" />
          <custom:property name="assigneeType" value="INITIATOR" />
          <custom:property name="assigneeLabel" value="Process Initiator" />
        </custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:userTask id="Task_ReleaseMiDual" name="Release MI Dual Binding">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[%s,%s]" />
          <custom_1:values name="actionNames" value="[&amp;#34;Approve&amp;#34;,&amp;#34;Save&amp;#34;]" />
          <custom_1:values name="formId" value="%s" />
          <custom_1:values name="formName" value="P3 MI Start Form" />
          <custom:property name="assigneeType" value="INITIATOR" />
          <custom:property name="assigneeLabel" value="Process Initiator" />
        </custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_2</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:subProcess id="SubProcess_MiDual" name="multi">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="multiInstance" value="true" />
          <custom:property name="subTableName" value="p3_mi_party" />
          <custom:property name="subTableId" value="%s" />
          <custom:property name="assigneeField" value="assignee_user_id" />
          <custom:property name="assigneeMode" value="user" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_3</bpmn:incoming>
      <bpmn:outgoing>Flow_4</bpmn:outgoing>
      <bpmn:multiInstanceLoopCharacteristics isSequential="false" flowable:collection="multiInstance_p3_mi_party_collection" flowable:elementVariable="currentItem" />
      <bpmn:startEvent id="MI_Start">
        <bpmn:outgoing>Flow_MI_1</bpmn:outgoing>
      </bpmn:startEvent>
      <bpmn:userTask id="Task_MiSubDual" name="MI Dual Binding Subtask">
        <bpmn:extensionElements>
          <custom:properties>
            <custom:property name="assigneeType" value="ELEMENT_VARIABLE" />
            <custom:property name="subTableName" value="p3_mi_party" />
            <custom:property name="subTableId" value="%s" />
            <custom:property name="assigneeField" value="assignee_user_id" />
            <custom:property name="assigneeMode" value="user" />
            <custom:property name="rowIdVariable" value="currentItem.rowId" />
            <custom:property name="actionIds" value="[%s,%s]" />
            <custom:property name="actionNames" value="[&amp;#34;Approve&amp;#34;,&amp;#34;Save&amp;#34;]" />
            <custom:property name="formId" value="%s" />
            <custom:property name="formName" value="P3 MI Subtask Form" />
          </custom:properties>
        </bpmn:extensionElements>
        <bpmn:incoming>Flow_MI_1</bpmn:incoming>
        <bpmn:outgoing>Flow_MI_2</bpmn:outgoing>
      </bpmn:userTask>
      <bpmn:endEvent id="MI_End">
        <bpmn:incoming>Flow_MI_2</bpmn:incoming>
      </bpmn:endEvent>
      <bpmn:sequenceFlow id="Flow_MI_1" sourceRef="MI_Start" targetRef="Task_MiSubDual" />
      <bpmn:sequenceFlow id="Flow_MI_2" sourceRef="Task_MiSubDual" targetRef="MI_End" />
    </bpmn:subProcess>
    <bpmn:endEvent id="EndEvent_1" name="End">
      <bpmn:incoming>Flow_4</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_FillMiDual" />
    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_FillMiDual" targetRef="Task_ReleaseMiDual" />
    <bpmn:sequenceFlow id="Flow_3" sourceRef="Task_ReleaseMiDual" targetRef="SubProcess_MiDual" />
    <bpmn:sequenceFlow id="Flow_4" sourceRef="SubProcess_MiDual" targetRef="EndEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_P3MiDual">
    <bpmndi:BPMNPlane id="BPMNPlane_P3MiDual" bpmnElement="p3-mi-dual-binding-test">
      <bpmndi:BPMNShape id="Shape_Start" bpmnElement="StartEvent_1"><dc:Bounds x="152" y="102" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_Fill" bpmnElement="Task_FillMiDual"><dc:Bounds x="240" y="80" width="140" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_Release" bpmnElement="Task_ReleaseMiDual"><dc:Bounds x="430" y="80" width="150" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_MI" bpmnElement="SubProcess_MiDual" isExpanded="true"><dc:Bounds x="630" y="40" width="360" height="160" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_MI_Start" bpmnElement="MI_Start"><dc:Bounds x="660" y="92" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_MI_Task" bpmnElement="Task_MiSubDual"><dc:Bounds x="740" y="70" width="150" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_MI_End" bpmnElement="MI_End"><dc:Bounds x="930" y="92" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_End" bpmnElement="EndEvent_1"><dc:Bounds x="1040" y="102" width="36" height="36" /></bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
$xml$,
        v_action_submit, v_action_save, v_start_form,
        v_action_approve, v_action_save, v_start_form,
        v_party_id, v_party_id,
        v_action_approve, v_action_save, v_task_form
    );

    INSERT INTO dw_process_definitions (
        function_unit_id, function_unit_version_id, bpmn_xml, created_at, updated_at
    ) VALUES (
        v_fu_id, v_fu_id, encode(convert_to(v_bpmn_xml, 'UTF8'), 'base64'),
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

    INSERT INTO dw_form_stage_bindings (form_id, stage_id, stage_name, scene) VALUES
    (v_start_form, 'Task_FillMiDual', 'Fill MI Dual Binding Case', 'TASK'),
    (v_start_form, 'Task_ReleaseMiDual', 'Release MI Dual Binding', 'TASK'),
    (v_task_form, 'Task_MiSubDual', 'MI Dual Binding Subtask', 'TASK');

    IF (SELECT COUNT(*) FROM dw_form_table_bindings
        WHERE form_id = v_task_form AND table_id = v_file_id AND binding_type = 'SUB') <> 2 THEN
        RAISE EXCEPTION 'P3 MI dual-binding seed failed: expected 2 SUB bindings on p3_mi_file task form';
    END IF;
    IF v_file_fk_case = v_file_fk_party THEN
        RAISE EXCEPTION 'P3 MI dual-binding seed failed: filter FK field ids must differ';
    END IF;

    RAISE NOTICE 'p3-mi-dual-binding-test ready: fu=% start=% task=% party_table=% file_table=% bind_fc=% bind_fp=%',
        v_fu_id, v_start_form, v_task_form, v_party_id, v_file_id, v_bind_task_fc, v_bind_task_fp;
END $p3$;
