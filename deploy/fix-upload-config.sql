-- Fix RequestAttachments sub-form upload action URL in Request Form (id=8, binding=23)
DO $$
DECLARE
    v_request_form_id BIGINT;
    v_binding_items_id BIGINT;
    v_binding_attach_id BIGINT;
BEGIN
    SELECT id INTO v_request_form_id FROM dw_form_definitions
    WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'PROCUREMENT_WORKFLOW')
      AND form_name = 'Request Form';

    SELECT id INTO v_binding_items_id FROM dw_form_table_bindings
    WHERE form_id = v_request_form_id AND sort_order = 2;

    SELECT id INTO v_binding_attach_id FROM dw_form_table_bindings
    WHERE form_id = v_request_form_id AND sort_order = 3;

    UPDATE dw_form_definitions
    SET config_json = jsonb_set(
        config_json::jsonb,
        '{subForms}',
        jsonb_build_object(
            v_binding_items_id::text, config_json->'subForms'->(v_binding_items_id::text),
            v_binding_attach_id::text, jsonb_build_object(
                'rule', jsonb_build_array(
                    jsonb_build_object(
                        'name', 'ref_Fattach_file',
                        'type', 'upload',
                        'field', 'file',
                        'props', jsonb_build_object(
                            'action', '/api/v1/upload',
                            'accept', '.jpg,.jpeg,.png,.pdf,.docx,.xlsx',
                            'limit', 1,
                            'multiple', false,
                            'listType', 'text',
                            'tip', 'Supported: jpg/png/pdf/docx/xlsx, max 10MB'
                        ),
                        'title', 'Attachment File',
                        '_fc_id', 'id_Fattach_file',
                        'hidden', false,
                        'display', true,
                        'validate', jsonb_build_array(
                            jsonb_build_object('message', 'Attachment File required', 'trigger', 'change', 'required', true)
                        ),
                        '_fc_drag_tag', 'upload'
                    ),
                    jsonb_build_object(
                        'name', 'ref_Flpimm1s4ixyaqc',
                        'type', 'input',
                        'field', 'file_name',
                        'props', jsonb_build_object('maxlength', 255, 'placeholder', 'Please input Original File Name', 'showWordLimit', true),
                        'title', 'Original File Name',
                        '_fc_id', 'id_Fci5mm1s4ixyapc',
                        'hidden', false,
                        'display', true,
                        'validate', jsonb_build_array(
                            jsonb_build_object('message', 'Original File Name required', 'trigger', 'blur', 'required', true)
                        ),
                        '_fc_drag_tag', 'input'
                    ),
                    jsonb_build_object(
                        'name', 'ref_Fv41mm1s4ixyauc',
                        'type', 'input',
                        'field', 'file_type',
                        'props', jsonb_build_object('maxlength', 100, 'placeholder', 'Please input Type', 'showWordLimit', true),
                        'title', 'Type',
                        '_fc_id', 'id_F8xymm1s4ixyatc',
                        'hidden', false,
                        'display', true,
                        '_fc_drag_tag', 'input'
                    ),
                    jsonb_build_object(
                        'name', 'ref_Fattach_desc',
                        'type', 'input',
                        'field', 'description',
                        'props', jsonb_build_object('type', 'textarea', 'placeholder', 'Please input description', 'rows', 2),
                        'title', 'Description',
                        '_fc_id', 'id_Fattach_desc',
                        'hidden', false,
                        'display', true,
                        '_fc_drag_tag', 'input'
                    )
                ),
                'options', jsonb_build_object(
                    'form', jsonb_build_object('size','default','inline',false,'labelWidth','125px','labelPosition','left','hideRequiredAsterisk',false),
                    'resetBtn', jsonb_build_object('show',false,'innerText','Reset'),
                    'submitBtn', jsonb_build_object('show',true,'innerText','Submit')
                )
            )
        )
    )
    WHERE id = v_request_form_id;

    RAISE NOTICE 'Updated Request Form (id=%) subForms: items_binding=%, attach_binding=%',
        v_request_form_id, v_binding_items_id, v_binding_attach_id;
END $$;
