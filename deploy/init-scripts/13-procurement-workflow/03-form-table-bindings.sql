-- =============================================================================
-- 13-procurement-workflow: Form Table Bindings + subForms (完整版)
-- 基于数据库实际数据生成，包含所有控件类型的 subForm rule
-- =============================================================================

DO $bindings$
DECLARE
    v_fu_id             BIGINT;
    v_request_form_id   BIGINT;
    v_approval_form_id  BIGINT;
    v_main_table_id     BIGINT;
    v_items_table_id    BIGINT;
    v_attach_table_id   BIGINT;
    v_bind_items_id     BIGINT;
    v_bind_attach_id    BIGINT;
    v_items_rule        JSONB;
    v_attach_rule       JSONB;
BEGIN
    SELECT id INTO v_fu_id FROM dw_function_units WHERE code = 'fu-20260403-a1b2c2';
    IF v_fu_id IS NULL THEN RAISE EXCEPTION 'fu-20260403-a1b2c2 not found'; END IF;

    SELECT id INTO v_request_form_id  FROM dw_form_definitions  WHERE function_unit_id=v_fu_id AND form_name='Request Form';
    SELECT id INTO v_approval_form_id FROM dw_form_definitions  WHERE function_unit_id=v_fu_id AND form_name='Approval Form';
    SELECT id INTO v_main_table_id    FROM dw_table_definitions WHERE function_unit_id=v_fu_id AND table_name='Request';
    SELECT id INTO v_items_table_id   FROM dw_table_definitions WHERE function_unit_id=v_fu_id AND table_name='RequestItems';
    SELECT id INTO v_attach_table_id  FROM dw_table_definitions WHERE function_unit_id=v_fu_id AND table_name='RequestAttachments';

    DELETE FROM dw_form_table_bindings WHERE form_id IN (v_request_form_id, v_approval_form_id);

    -- Request Form: PRIMARY (Request)
    INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at)
    VALUES (v_request_form_id, v_main_table_id, 'PRIMARY', 'EDITABLE', NULL, 1, NOW(), NOW());

    -- Request Form: SUB (RequestItems)
    INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at)
    VALUES (v_request_form_id, v_items_table_id, 'SUB', 'EDITABLE', 'request_id', 2, NOW(), NOW())
    RETURNING id INTO v_bind_items_id;

    -- Request Form: SUB (RequestAttachments)
    INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at)
    VALUES (v_request_form_id, v_attach_table_id, 'SUB', 'EDITABLE', 'request_id', 3, NOW(), NOW())
    RETURNING id INTO v_bind_attach_id;

    -- Approval Form: PRIMARY (Request, READONLY)
    INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at)
    VALUES (v_approval_form_id, v_main_table_id, 'PRIMARY', 'READONLY', NULL, 1, NOW(), NOW());

    -- =========================================================================
    -- RequestItems subForm rule (完整 24 个字段，覆盖所有控件类型)
    -- =========================================================================
    v_items_rule := '[
        {"name":"ref_Fj8smm1s0sq3akc","type":"input","field":"item_name","props":{"maxlength":200,"placeholder":"Please input Item Name","showWordLimit":true},"title":"Item Name","_fc_id":"id_F10nmm1s0sq3ajc","hidden":false,"display":true,"validate":[{"mode":"required","message":"Item Name required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
        {"name":"ref_F1wbmm2wztutakc","type":"inputNumber","field":"count","props":{"precision":0,"placeholder":"Please input Count"},"title":"Count","_fc_id":"id_Ffkxmm2wztutajc","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"inputNumber"},
        {"name":"ref_Fspimm1s0sq3amc","type":"inputNumber","field":"unit_price","props":{"precision":2,"placeholder":"Please input Unit Price"},"title":"Unit Price","_fc_id":"id_Fhv3mm1s0sq3alc","hidden":false,"display":true,"validate":[{"mode":"required","message":"Unit Price required","trigger":"blur","required":true}],"_fc_drag_tag":"inputNumber"},
        {"name":"ref_F4lomm1s0sq3aoc","type":"inputNumber","field":"total_price","props":{"precision":2,"placeholder":"Please input Total Price"},"title":"Total Price","_fc_id":"id_Fz6vmm1s0sq3anc","hidden":false,"display":true,"validate":[{"message":"Total Price required","trigger":"blur","required":true}],"_fc_drag_tag":"inputNumber"},
        {"name":"ref_Fitemcode001","type":"input","field":"item_code","props":{"maxlength":255,"placeholder":"Please input Item Code","showWordLimit":true},"title":"Item Code","_fc_id":"id_Fitemcode001","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"input"},
        {"name":"ref_Fdesc001","type":"input","field":"description","props":{"rows":3,"type":"textarea","placeholder":"Please input Description"},"title":"Description","_fc_id":"id_Fdesc001","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"input"},
        {"name":"ref_Fstockqty001","type":"inputNumber","field":"stock_qty","props":{"precision":0,"placeholder":"Please input Stock Quantity"},"title":"Stock Quantity","_fc_id":"id_Fstockqty001","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"inputNumber"},
        {"name":"ref_Fdiscountrate001","type":"inputNumber","field":"discount_rate","props":{"max":100,"min":0,"precision":2,"placeholder":"Please input Discount Rate"},"title":"Discount Rate","_fc_id":"id_Fdiscountrate001","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"inputNumber"},
        {"name":"ref_Fcategory001","type":"select","field":"category","props":{"options":[{"label":"Electronics","value":"electronics"},{"label":"Office Supplies","value":"office"},{"label":"Furniture","value":"furniture"},{"label":"Other","value":"other"}],"placeholder":"Please select Category"},"title":"Category","_fc_id":"id_Fcategory001","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"select"},
        {"name":"ref_Fpriority001","type":"select","field":"priority","props":{"options":[{"label":"Low","value":1},{"label":"Medium","value":2},{"label":"High","value":3}],"placeholder":"Please select Priority"},"title":"Priority","_fc_id":"id_Fpriority001","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"select"},
        {"name":"ref_Ftags001","type":"select","field":"tags","props":{"options":[{"label":"Fragile","value":"fragile"},{"label":"Perishable","value":"perishable"},{"label":"Hazardous","value":"hazardous"},{"label":"Bulk","value":"bulk"}],"multiple":true,"placeholder":"Please select Tags"},"title":"Tags","_fc_id":"id_Ftags001","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"select"},
        {"name":"ref_Fisurgent001","type":"switch","field":"is_urgent","title":"Is Urgent","_fc_id":"id_Fisurgent001","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"switch"},
        {"name":"ref_Fdeliverydate001","type":"datePicker","field":"delivery_date","props":{"type":"date","placeholder":"Please select Delivery Date","valueFormat":"YYYY-MM-DD"},"title":"Delivery Date","_fc_id":"id_Fdeliverydate001","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"datePicker"},
        {"name":"ref_Fexpectedat001","type":"datePicker","field":"expected_at","props":{"type":"datetime","placeholder":"Please select Expected At","valueFormat":"YYYY-MM-DD HH:mm:ss"},"title":"Expected At","_fc_id":"id_Fexpectedat001","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"datePicker"},
        {"name":"ref_Fitemimage001","type":"upload","field":"item_image","props":{"tip":"Supported: jpg/png/gif/webp, max 5MB","limit":1,"accept":".jpg,.jpeg,.png,.gif,.webp","action":"/api/v1/upload","listType":"text","multiple":false},"title":"Item Image","_fc_id":"id_Fitemimage001","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"upload"},
        {"name":"ref_Fassignedto001","type":"input","field":"assigned_to","props":{"placeholder":"Please select Assigned To (user)"},"title":"Assigned To","_fc_id":"id_Fassignedto001","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"input"},
        {"name":"ref_Fdepartment001","type":"input","field":"department","props":{"placeholder":"Please select Department"},"title":"Department","_fc_id":"id_Fdepartment001","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"input"},
        {"name":"ref_Fitem_status001","type":"radio","field":"item_status","props":{"options":[{"label":"Pending","value":"pending"},{"label":"Approved","value":"approved"},{"label":"Rejected","value":"rejected"}]},"title":"Item Status","_fc_id":"id_item_status","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"radio"},
        {"name":"ref_Frating001","type":"rate","field":"rating","props":{"max":5,"allowHalf":false},"title":"Rating","value":0,"_fc_id":"id_rating","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"rate"},
        {"name":"ref_Flabelcolor001","type":"colorPicker","field":"label_color","props":{"showAlpha":false},"title":"Label Color","_fc_id":"id_label_color","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"colorPicker"},
        {"name":"ref_Fworktimerange001","type":"timePicker","field":"work_time_range","props":{"isRange":true,"valueFormat":"HH:mm:ss","endPlaceholder":"End time","startPlaceholder":"Start time"},"title":"Work Time Range","_fc_id":"id_work_time_range","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"timePicker"},
        {"name":"ref_Fproductcategory001","type":"elTreeSelect","field":"product_category","props":{"data":[{"label":"Electronics","value":"electronics","children":[{"label":"Computers","value":"computers"},{"label":"Phones","value":"phones"}]},{"label":"Office Supplies","value":"office","children":[{"label":"Stationery","value":"stationery"},{"label":"Furniture","value":"furniture"}]},{"label":"Other","value":"other"}],"placeholder":"Please select Product Category","checkStrictly":true},"title":"Product Category","_fc_id":"id_product_category","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"elTreeSelect"},
        {"name":"ref_Fselected_nodes001","type":"tree","field":"selected_nodes","props":{"data":[{"id":1,"label":"Category A","children":[{"id":2,"label":"Sub A1"},{"id":3,"label":"Sub A2"}]},{"id":4,"label":"Category B","children":[{"id":5,"label":"Sub B1"},{"id":6,"label":"Sub B2"}]}],"props":{"label":"label","children":"children"},"nodeKey":"id","showCheckbox":true},"title":"Selected Nodes","_fc_id":"id_selected_nodes","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"tree"},
        {"name":"ref_Fapplicable_tags001","type":"checkbox","field":"applicable_tags","props":{"options":[{"label":"Urgent","value":"urgent"},{"label":"Fragile","value":"fragile"},{"label":"Bulk Order","value":"bulk"},{"label":"Recurring","value":"recurring"}]},"title":"Applicable Tags","_fc_id":"id_applicable_tags","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"checkbox"},
        {"name":"ref_Fspecnotes001","type":"editor","field":"spec_notes","props":{"rows":5,"placeholder":"Please input Spec Notes"},"title":"Spec Notes","_fc_id":"id_spec_notes","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"editor"},
        {"name":"ref_Fapproversign001","type":"signature","field":"approver_sign","props":{},"title":"Approver Signature","_fc_id":"id_approver_sign","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"signature"},
        {"name":"ref_Fassignedteams001","type":"transfer","field":"assigned_teams","props":{"options":[{"label":"Team Alpha","value":"alpha"},{"label":"Team Beta","value":"beta"},{"label":"Team Gamma","value":"gamma"},{"label":"Team Delta","value":"delta"}],"leftTitle":"Available","rightTitle":"Assigned"},"title":"Assigned Teams","_fc_id":"id_assigned_teams","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"transfer"},
        {"name":"ref_Flocation001","type":"cascader","field":"location","props":{"options":[{"label":"North America","value":"na","children":[{"label":"USA","value":"usa","children":[{"label":"New York","value":"ny"},{"label":"California","value":"ca"}]},{"label":"Canada","value":"canada"}]},{"label":"Asia","value":"asia","children":[{"label":"China","value":"china","children":[{"label":"Beijing","value":"bj"},{"label":"Shanghai","value":"sh"}]},{"label":"Japan","value":"japan"}]}],"placeholder":"Please select Location"},"title":"Location","_fc_id":"id_location","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"cascader"},
        {"name":"ref_Fprogress001","type":"slider","field":"progress","props":{"min":0,"max":100,"step":5},"title":"Progress","value":0,"_fc_id":"id_progress_slider","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"slider"},
        {"name":"ref_Fsecretcode001","type":"input","field":"secret_code","props":{"type":"password","placeholder":"Please input Secret Code"},"title":"Secret Code","_fc_id":"id_secret_code","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"input"}
    ]'::jsonb;

    -- RequestAttachments subForm rule
    v_attach_rule := '[
        {"name":"ref_Fattach_file","type":"upload","field":"file","props":{"tip":"Supported: jpg/png/pdf/docx/xlsx, max 10MB","limit":1,"accept":".jpg,.jpeg,.png,.pdf,.docx,.xlsx","action":"/api/v1/upload","listType":"text","multiple":false},"title":"Attachment File","_fc_id":"id_Fattach_file","hidden":false,"display":true,"validate":[{"message":"Attachment File required","trigger":"change","required":true}],"_fc_drag_tag":"upload"},
        {"name":"ref_Flpimm1s4ixyaqc","type":"input","field":"file_name","props":{"maxlength":255,"placeholder":"Please input Original File Name","showWordLimit":true},"title":"Original File Name","_fc_id":"id_Fci5mm1s4ixyapc","hidden":false,"display":true,"validate":[{"message":"Original File Name required","trigger":"blur","required":true}],"_fc_drag_tag":"input"},
        {"name":"ref_Fv41mm1s4ixyauc","type":"input","field":"file_type","props":{"maxlength":100,"placeholder":"Please input Type","showWordLimit":true},"title":"Type","_fc_id":"id_F8xymm1s4ixyatc","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"input"},
        {"name":"ref_Fattach_desc","type":"input","field":"description","props":{"rows":2,"type":"textarea","placeholder":"Please input description"},"title":"Description","_fc_id":"id_Fattach_desc","hidden":false,"display":true,"$required":false,"_fc_drag_tag":"input"}
    ]'::jsonb;

    -- =========================================================================
    -- 写入 subForms 到 Request Form config_json
    -- =========================================================================
    UPDATE dw_form_definitions
    SET config_json = jsonb_set(
        config_json::jsonb,
        '{subForms}',
        jsonb_build_object(
            v_bind_items_id::text,  jsonb_build_object(
                'rule', v_items_rule,
                'options', '{"form":{"size":"default","inline":false,"labelWidth":"125px","labelPosition":"left","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}}'::jsonb
            ),
            v_bind_attach_id::text, jsonb_build_object(
                'rule', v_attach_rule,
                'options', '{"form":{"size":"default","inline":false,"labelWidth":"125px","labelPosition":"left","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}}'::jsonb
            )
        )
    )
    WHERE id = v_request_form_id;

    -- =========================================================================
    -- 在 rule 数组中插入 subTable placeholder
    -- =========================================================================

    -- 先移除已有的 subTable entries
    UPDATE dw_form_definitions
    SET config_json = jsonb_set(
        config_json, '{rule}',
        (SELECT jsonb_agg(elem ORDER BY ordinality)
         FROM jsonb_array_elements(config_json->'rule') WITH ORDINALITY AS t(elem, ordinality)
         WHERE elem->>'type' <> 'subTable')
    ) WHERE id = v_request_form_id;

    -- 在 description 字段后插入 RequestItems subTable placeholder
    UPDATE dw_form_definitions
    SET config_json = jsonb_set(
        config_json, '{rule}',
        (SELECT jsonb_agg(elem ORDER BY ordinality) FROM (
            SELECT elem, ordinality FROM jsonb_array_elements(config_json->'rule') WITH ORDINALITY AS t(elem, ordinality) WHERE ordinality <= 4
            UNION ALL
            SELECT jsonb_build_object('name','ref_Fpgmmn1jxdw5acc','type','subTable','title','Sub-Table','_fc_id','id_Ftjumn1jxdw5abc','hidden',false,'display',true,'_bindingId',v_bind_items_id,'_fc_drag_tag','subTable'), 4.5
            UNION ALL
            SELECT elem, ordinality FROM jsonb_array_elements(config_json->'rule') WITH ORDINALITY AS t(elem, ordinality) WHERE ordinality > 4
        ) sub)
    ) WHERE id = v_request_form_id;

    -- 追加 RequestAttachments subTable placeholder
    UPDATE dw_form_definitions
    SET config_json = jsonb_set(
        config_json, '{rule}',
        (config_json->'rule') || jsonb_build_array(jsonb_build_object(
            'name','ref_F49dmn1jzy3pagc','type','subTable','title','Sub-Table',
            '_fc_id','id_Fx5amn1jzy3pafc','hidden',false,'display',true,
            '_bindingId',v_bind_attach_id,'_fc_drag_tag','subTable'
        ))
    ) WHERE id = v_request_form_id;

    RAISE NOTICE '========================================';
    RAISE NOTICE 'Form Table Bindings Complete!';
    RAISE NOTICE 'RequestItems binding  : id=%', v_bind_items_id;
    RAISE NOTICE 'Attachments binding   : id=%', v_bind_attach_id;
    RAISE NOTICE '========================================';

END $bindings$;
