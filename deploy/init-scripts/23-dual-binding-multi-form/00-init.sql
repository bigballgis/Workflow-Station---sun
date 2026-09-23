\set ON_ERROR_STOP on

-- =============================================================================
-- 23-dual-binding-multi-form
-- Same tables and dual file bindings as p0-dual-binding-test, on TWO forms.
-- PROCESS form is the start form. TASK form is the review task form.
-- Each form binds p0_mf_file twice (case_id vs party_id).
-- Not loaded by 00-init-all. Unique table names so Demo attachment stays unambiguous.
-- Code: p0-dual-binding-multi-form
-- =============================================================================

DO $mf$
DECLARE
    v_fu_id            BIGINT;
    v_fill_form        BIGINT;
    v_review_form      BIGINT;
    v_case_id          BIGINT;
    v_party_id         BIGINT;
    v_file_id          BIGINT;
    v_case_pk          BIGINT;
    v_party_pk         BIGINT;
    v_file_pk          BIGINT;
    v_party_fk_case    BIGINT;
    v_file_fk_case     BIGINT;
    v_file_fk_party    BIGINT;
    v_fill_primary     BIGINT;
    v_fill_party       BIGINT;
    v_fill_file_case   BIGINT;
    v_fill_file_party  BIGINT;
    v_rev_primary      BIGINT;
    v_rev_party        BIGINT;
    v_rev_file_case    BIGINT;
    v_rev_file_party   BIGINT;
    v_view             BIGINT;
    v_action_submit    BIGINT;
    v_action_save      BIGINT;
    v_bpmn_xml         TEXT;
    v_pk_json          JSONB;
    v_case_fill        JSONB;
    v_party_fill       JSONB;
BEGIN
    SELECT id INTO v_fu_id FROM dw_function_units WHERE code = 'p0-dual-binding-multi-form';
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
        'p0-dual-binding-multi-form',
        'P0 Dual Binding Multi Form',
        'Two forms (start + review) each bind p0_mf_file twice.',
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
    (v_fu_id, 'p0_mf_case', 'P0 MF Case', 'MAIN', 'Case (MAIN)', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_case_id;

    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_display_name, table_type, display_name, created_at, updated_at
    ) VALUES
    (v_fu_id, 'p0_mf_party', 'P0 MF Party', 'SUB', 'Parties of the case', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_party_id;

    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_display_name, table_type, display_name, created_at, updated_at
    ) VALUES
    (v_fu_id, 'p0_mf_file', 'P0 MF File', 'SUB', 'Files bound twice on each form', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_file_id;

    v_pk_json := '{"scope":"perTable","padWidth":6,"strategy":"prefixedSequence","startValue":1}'::jsonb;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, nullable, is_primary_key, is_unique,
        display_name, sort_order, is_foreign_key, ref_table_id, ref_primary_key_fields,
        pk_generation_json, fk_display_mode, relation_cardinality
    ) VALUES
    (v_case_id, 'id', 'VARCHAR', 64, false, true, true, 'Case ID', 0, false, NULL, NULL,
     v_pk_json || '{"prefix":"Case-"}'::jsonb, 'readonly', NULL),
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
     v_pk_json || '{"prefix":"Party-"}'::jsonb, 'readonly', NULL),
    (v_party_id, 'case_id', 'VARCHAR', 64, false, false, false, 'Case ID', 1, true, v_case_id, '["id"]'::jsonb, NULL, 'readonly', 'oneToMany'),
    (v_party_id, 'party_name', 'VARCHAR', 100, false, false, false, 'Party name', 2, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_party_id, 'created_at', 'TIMESTAMP', NULL, true, false, false, 'Created At', 3, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_party_id, 'created_by', 'VARCHAR', 64, true, false, false, 'Created By', 4, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_party_id, 'updated_at', 'TIMESTAMP', NULL, true, false, false, 'Updated At', 5, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_party_id, 'updated_by', 'VARCHAR', 64, true, false, false, 'Updated By', 6, false, NULL, NULL, NULL, 'readonly', NULL);

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, nullable, is_primary_key, is_unique,
        display_name, sort_order, is_foreign_key, ref_table_id, ref_primary_key_fields,
        pk_generation_json, fk_display_mode, relation_cardinality
    ) VALUES
    (v_file_id, 'id', 'VARCHAR', 64, false, true, true, 'File ID', 0, false, NULL, NULL,
     v_pk_json || '{"prefix":"File-"}'::jsonb, 'readonly', NULL),
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
    (v_case_id, 'id', 'perTable', '', 'Case-', 6, 0),
    (v_party_id, 'id', 'perTable', '', 'Party-', 6, 0),
    (v_file_id, 'id', 'perTable', '', 'File-', 6, 0);

    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, display_name, bound_table_id, scene, config_json, created_at, updated_at
    ) VALUES (
        v_fu_id, 'P0 MF Fill Form', 'PROCESS',
        'Start form: parties plus the file table bound twice',
        v_case_id, 'TASK',
        '{"rule":[],"options":{},"subForms":{}}'::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_fill_form;

    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, display_name, bound_table_id, scene, config_json, created_at, updated_at
    ) VALUES (
        v_fu_id, 'P0 MF Review Form', 'TASK',
        'Review task form: the same file table bound twice again',
        v_case_id, 'TASK',
        '{"rule":[],"options":{},"subForms":{}}'::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_review_form;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, sub_mode, filter_fk_field_id, created_at, updated_at
    ) VALUES
    (v_fill_form, v_case_id, 'PRIMARY', 'EDITABLE', NULL, 'structuralFk', 0, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_fill_primary;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, sub_mode, filter_fk_field_id, created_at, updated_at
    ) VALUES
    (v_fill_form, v_party_id, 'SUB', 'EDITABLE', 'case_id', 'structuralFk', 1, 'FULL', v_party_fk_case, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_fill_party;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, sub_mode, filter_fk_field_id, created_at, updated_at
    ) VALUES
    (v_fill_form, v_file_id, 'SUB', 'EDITABLE', 'case_id', 'structuralFk', 2, 'FULL', v_file_fk_case, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_fill_file_case;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, sub_mode, filter_fk_field_id, created_at, updated_at
    ) VALUES
    (v_fill_form, v_file_id, 'SUB', 'EDITABLE', 'party_id', 'structuralFk', 3, 'FULL', v_file_fk_party, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_fill_file_party;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, sub_mode, filter_fk_field_id, created_at, updated_at
    ) VALUES
    (v_review_form, v_case_id, 'PRIMARY', 'EDITABLE', NULL, 'structuralFk', 0, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_rev_primary;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, sub_mode, filter_fk_field_id, created_at, updated_at
    ) VALUES
    (v_review_form, v_party_id, 'SUB', 'EDITABLE', 'case_id', 'structuralFk', 1, 'FULL', v_party_fk_case, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_rev_party;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, sub_mode, filter_fk_field_id, created_at, updated_at
    ) VALUES
    (v_review_form, v_file_id, 'SUB', 'EDITABLE', 'case_id', 'structuralFk', 2, 'FULL', v_file_fk_case, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_rev_file_case;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field,
        binding_link_mode, sort_order, sub_mode, filter_fk_field_id, created_at, updated_at
    ) VALUES
    (v_review_form, v_file_id, 'SUB', 'EDITABLE', 'party_id', 'structuralFk', 3, 'FULL', v_file_fk_party, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    RETURNING id INTO v_rev_file_party;

    v_case_fill := jsonb_build_array(jsonb_build_object(
        'fieldId', v_party_fk_case, 'fieldName', 'case_id', 'kind', 'PRIMARY'));
    v_party_fill := jsonb_build_array(jsonb_build_object(
        'fieldId', v_file_fk_case, 'fieldName', 'case_id', 'kind', 'PRIMARY'));
    -- Party-file bindings stay undeclared so a list-level Add takes party_id from
    -- the single party already on the form. PARENT would demand a host dialog row.

    UPDATE dw_form_table_bindings SET fk_fill_sources = v_case_fill
     WHERE id IN (v_fill_party, v_rev_party);
    UPDATE dw_form_table_bindings SET fk_fill_sources = v_party_fill
     WHERE id IN (v_fill_file_case, v_rev_file_case);
    UPDATE dw_form_table_bindings SET fk_fill_sources = NULL
     WHERE id IN (v_fill_file_party, v_rev_file_party);

    INSERT INTO dw_sub_table_view_configs (binding_id, created_at, updated_at) VALUES (v_fill_party, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id INTO v_view;
    INSERT INTO dw_sub_table_view_fields (view_config_id, field_name, display_label, column_width, sort_order, visible) VALUES
    (v_view, 'id', 'Party ID', 150, 0, true),
    (v_view, 'party_name', 'Party name', 200, 1, true);
    UPDATE dw_form_table_bindings SET sub_list_view_id = v_view WHERE id = v_fill_party;

    INSERT INTO dw_sub_table_view_configs (binding_id, created_at, updated_at) VALUES (v_fill_file_case, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id INTO v_view;
    INSERT INTO dw_sub_table_view_fields (view_config_id, field_name, display_label, column_width, sort_order, visible) VALUES
    (v_view, 'id', 'File ID', 150, 0, true),
    (v_view, 'file_name', 'File name', 200, 1, true);
    UPDATE dw_form_table_bindings SET sub_list_view_id = v_view WHERE id = v_fill_file_case;

    INSERT INTO dw_sub_table_view_configs (binding_id, created_at, updated_at) VALUES (v_fill_file_party, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id INTO v_view;
    INSERT INTO dw_sub_table_view_fields (view_config_id, field_name, display_label, column_width, sort_order, visible) VALUES
    (v_view, 'id', 'File ID', 150, 0, true),
    (v_view, 'file_name', 'File name', 200, 1, true);
    UPDATE dw_form_table_bindings SET sub_list_view_id = v_view WHERE id = v_fill_file_party;

    INSERT INTO dw_sub_table_view_configs (binding_id, created_at, updated_at) VALUES (v_rev_party, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id INTO v_view;
    INSERT INTO dw_sub_table_view_fields (view_config_id, field_name, display_label, column_width, sort_order, visible) VALUES
    (v_view, 'id', 'Party ID', 150, 0, true),
    (v_view, 'party_name', 'Party name', 200, 1, true);
    UPDATE dw_form_table_bindings SET sub_list_view_id = v_view WHERE id = v_rev_party;

    INSERT INTO dw_sub_table_view_configs (binding_id, created_at, updated_at) VALUES (v_rev_file_case, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id INTO v_view;
    INSERT INTO dw_sub_table_view_fields (view_config_id, field_name, display_label, column_width, sort_order, visible) VALUES
    (v_view, 'id', 'File ID', 150, 0, true),
    (v_view, 'file_name', 'File name', 200, 1, true);
    UPDATE dw_form_table_bindings SET sub_list_view_id = v_view WHERE id = v_rev_file_case;

    INSERT INTO dw_sub_table_view_configs (binding_id, created_at, updated_at) VALUES (v_rev_file_party, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id INTO v_view;
    INSERT INTO dw_sub_table_view_fields (view_config_id, field_name, display_label, column_width, sort_order, visible) VALUES
    (v_view, 'id', 'File ID', 150, 0, true),
    (v_view, 'file_name', 'File name', 200, 1, true);
    UPDATE dw_form_table_bindings SET sub_list_view_id = v_view WHERE id = v_rev_file_party;

    UPDATE dw_form_definitions
    SET config_json = jsonb_build_object(
            'rule', jsonb_build_array(
                jsonb_build_object('name', 'ref_mf_title', 'type', 'input', 'field', 'title', 'title', 'Title',
                    '_fc_id', 'id_mf_title', 'hidden', false, 'display', true, '_fc_drag_tag', 'input',
                    'props', jsonb_build_object('maxlength', 200, 'placeholder', 'Case title'),
                    'validate', jsonb_build_array(jsonb_build_object('message', 'Title is required', 'trigger', 'blur', 'required', true))),
                jsonb_build_object('name', 'ref_mf_party', 'type', 'subTable', 'title', 'Parties',
                    '_fc_id', 'id_mf_party', 'hidden', false, 'display', true, '_fc_drag_tag', 'subTable',
                    '_bindingId', v_fill_party, 'props', '{}'::jsonb),
                jsonb_build_object('name', 'ref_mf_file_case', 'type', 'subTable', 'title', 'Case files',
                    '_fc_id', 'id_mf_file_case', 'hidden', false, 'display', true, '_fc_drag_tag', 'subTable',
                    '_bindingId', v_fill_file_case, 'props', '{}'::jsonb),
                jsonb_build_object('name', 'ref_mf_file_party', 'type', 'subTable', 'title', 'Party files',
                    '_fc_id', 'id_mf_file_party', 'hidden', false, 'display', true, '_fc_drag_tag', 'subTable',
                    '_bindingId', v_fill_file_party, 'props', '{}'::jsonb)
            ),
            'options', jsonb_build_object(
                'form', jsonb_build_object('size', 'default', 'inline', false, 'labelWidth', '125px', 'labelPosition', 'left', 'hideRequiredAsterisk', false),
                'resetBtn', jsonb_build_object('show', false, 'innerText', 'Reset'),
                'submitBtn', jsonb_build_object('show', true, 'innerText', 'Submit')
            ),
            'subForms', jsonb_build_object(
                v_fill_party::text, '{"rule":[{"name":"ref_mf_pn","type":"input","field":"party_name","title":"Party name","_fc_id":"id_mf_pn","hidden":false,"display":true,"_fc_drag_tag":"input","props":{"maxlength":100}}],"options":{"form":{"labelWidth":"125px","labelPosition":"left"},"submitBtn":{"show":true,"innerText":"Submit"},"resetBtn":{"show":false}}}'::jsonb,
                v_fill_file_case::text, '{"rule":[{"name":"ref_mf_fn_c","type":"input","field":"file_name","title":"File name","_fc_id":"id_mf_fn_c","hidden":false,"display":true,"_fc_drag_tag":"input","props":{"maxlength":200}}],"options":{"form":{"labelWidth":"125px","labelPosition":"left"},"submitBtn":{"show":true,"innerText":"Submit"},"resetBtn":{"show":false}}}'::jsonb,
                v_fill_file_party::text, '{"rule":[{"name":"ref_mf_fn_p","type":"input","field":"file_name","title":"File name","_fc_id":"id_mf_fn_p","hidden":false,"display":true,"_fc_drag_tag":"input","props":{"maxlength":200}}],"options":{"form":{"labelWidth":"125px","labelPosition":"left"},"submitBtn":{"show":true,"innerText":"Submit"},"resetBtn":{"show":false}}}'::jsonb
            )
        ),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = v_fill_form;

    UPDATE dw_form_definitions
    SET config_json = jsonb_build_object(
            'rule', jsonb_build_array(
                jsonb_build_object('name', 'ref_mf_rtitle', 'type', 'input', 'field', 'title', 'title', 'Title',
                    '_fc_id', 'id_mf_rtitle', 'hidden', false, 'display', true, '_fc_drag_tag', 'input',
                    'props', jsonb_build_object('maxlength', 200, 'placeholder', 'Case title')),
                jsonb_build_object('name', 'ref_mf_rparty', 'type', 'subTable', 'title', 'Review parties',
                    '_fc_id', 'id_mf_rparty', 'hidden', false, 'display', true, '_fc_drag_tag', 'subTable',
                    '_bindingId', v_rev_party, 'props', '{}'::jsonb),
                jsonb_build_object('name', 'ref_mf_rfile_case', 'type', 'subTable', 'title', 'Review case files',
                    '_fc_id', 'id_mf_rfile_case', 'hidden', false, 'display', true, '_fc_drag_tag', 'subTable',
                    '_bindingId', v_rev_file_case, 'props', '{}'::jsonb),
                jsonb_build_object('name', 'ref_mf_rfile_party', 'type', 'subTable', 'title', 'Review party files',
                    '_fc_id', 'id_mf_rfile_party', 'hidden', false, 'display', true, '_fc_drag_tag', 'subTable',
                    '_bindingId', v_rev_file_party, 'props', '{}'::jsonb)
            ),
            'options', jsonb_build_object(
                'form', jsonb_build_object('size', 'default', 'inline', false, 'labelWidth', '125px', 'labelPosition', 'left', 'hideRequiredAsterisk', false),
                'resetBtn', jsonb_build_object('show', false, 'innerText', 'Reset'),
                'submitBtn', jsonb_build_object('show', true, 'innerText', 'Submit')
            ),
            'subForms', jsonb_build_object(
                v_rev_party::text, '{"rule":[{"name":"ref_mf_rpn","type":"input","field":"party_name","title":"Party name","_fc_id":"id_mf_rpn","hidden":false,"display":true,"_fc_drag_tag":"input","props":{"maxlength":100}}],"options":{"form":{"labelWidth":"125px","labelPosition":"left"},"submitBtn":{"show":true,"innerText":"Submit"},"resetBtn":{"show":false}}}'::jsonb,
                v_rev_file_case::text, '{"rule":[{"name":"ref_mf_rfn_c","type":"input","field":"file_name","title":"File name","_fc_id":"id_mf_rfn_c","hidden":false,"display":true,"_fc_drag_tag":"input","props":{"maxlength":200}}],"options":{"form":{"labelWidth":"125px","labelPosition":"left"},"submitBtn":{"show":true,"innerText":"Submit"},"resetBtn":{"show":false}}}'::jsonb,
                v_rev_file_party::text, '{"rule":[{"name":"ref_mf_rfn_p","type":"input","field":"file_name","title":"File name","_fc_id":"id_mf_rfn_p","hidden":false,"display":true,"_fc_drag_tag":"input","props":{"maxlength":200}}],"options":{"form":{"labelWidth":"125px","labelPosition":"left"},"submitBtn":{"show":true,"innerText":"Submit"},"resetBtn":{"show":false}}}'::jsonb
            )
        ),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = v_review_form;

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

    v_bpmn_xml := format(
        $xml$<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:custom_1="http://custom.bpmn.io/schema" xmlns:custom="http://workflow.platform/schema/custom" xmlns:flowable="http://flowable.org/bpmn" id="Definitions_P0Mf" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="p0-dual-binding-multi-form" name="P0 Dual Binding Multi Form" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="Task_FillMf" name="Fill Multi Form Case">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[%s,%s]" />
          <custom_1:values name="actionNames" value="[&amp;#34;Submit&amp;#34;,&amp;#34;Save&amp;#34;]" />
          <custom_1:values name="formId" value="%s" />
          <custom_1:values name="formName" value="P0 MF Fill Form" />
          <custom:property name="assigneeType" value="INITIATOR" />
          <custom:property name="assigneeLabel" value="Process Initiator" />
        </custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:userTask id="Task_ReviewMf" name="Review Multi Form Case">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[%s,%s]" />
          <custom_1:values name="actionNames" value="[&amp;#34;Submit&amp;#34;,&amp;#34;Save&amp;#34;]" />
          <custom_1:values name="formId" value="%s" />
          <custom_1:values name="formName" value="P0 MF Review Form" />
          <custom:property name="assigneeType" value="INITIATOR" />
          <custom:property name="assigneeLabel" value="Process Initiator" />
        </custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_2</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:endEvent id="EndEvent_1" name="End">
      <bpmn:incoming>Flow_3</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_FillMf" />
    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_FillMf" targetRef="Task_ReviewMf" />
    <bpmn:sequenceFlow id="Flow_3" sourceRef="Task_ReviewMf" targetRef="EndEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_P0Mf">
    <bpmndi:BPMNPlane id="BPMNPlane_P0Mf" bpmnElement="p0-dual-binding-multi-form">
      <bpmndi:BPMNShape id="Shape_Start" bpmnElement="StartEvent_1"><dc:Bounds x="152" y="102" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_Fill" bpmnElement="Task_FillMf"><dc:Bounds x="240" y="80" width="160" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_Review" bpmnElement="Task_ReviewMf"><dc:Bounds x="450" y="80" width="170" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_End" bpmnElement="EndEvent_1"><dc:Bounds x="670" y="102" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Edge_1" bpmnElement="Flow_1"><di:waypoint x="188" y="120" /><di:waypoint x="240" y="120" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_2" bpmnElement="Flow_2"><di:waypoint x="400" y="120" /><di:waypoint x="450" y="120" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_3" bpmnElement="Flow_3"><di:waypoint x="620" y="120" /><di:waypoint x="670" y="120" /></bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
$xml$,
        v_action_submit, v_action_save, v_fill_form,
        v_action_submit, v_action_save, v_review_form
    );

    INSERT INTO dw_process_definitions (
        function_unit_id, function_unit_version_id, bpmn_xml, created_at, updated_at
    ) VALUES (
        v_fu_id, v_fu_id, encode(convert_to(v_bpmn_xml, 'UTF8'), 'base64'), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

    INSERT INTO dw_form_stage_bindings (form_id, stage_id, stage_name, scene) VALUES
    (v_fill_form, 'Task_FillMf', 'Fill Multi Form Case', 'TASK'),
    (v_review_form, 'Task_ReviewMf', 'Review Multi Form Case', 'TASK');

    IF (SELECT COUNT(*) FROM dw_form_table_bindings b
        JOIN dw_form_definitions f ON f.id = b.form_id
        WHERE f.function_unit_id = v_fu_id AND b.table_id = v_file_id AND b.binding_type = 'SUB') <> 4 THEN
        RAISE EXCEPTION 'multi-form seed failed: expected 4 file SUB bindings (2 forms x 2 filters)';
    END IF;

    RAISE NOTICE 'p0-dual-binding-multi-form ready: fu=% fill_form=% review_form=% file_table=%',
        v_fu_id, v_fill_form, v_review_form, v_file_id;
END $mf$;
