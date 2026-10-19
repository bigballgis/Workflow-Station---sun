-- Sub-Table Batch CRUD + Assign Demo / PostgreSQL 16+
-- Run against the platform database after all 00-schema migrations.
-- One atomic DO statement; no psql-only commands, credentials, fixed database IDs,
-- physical business tables, runtime tasks, catalog snapshots or permission grants.
-- Optional, in the same connection before running this file:
--   SET workflow.batch_demo_group = 'your development group name';
-- Default group: developer groups. The group must already exist and be ACTIVE.
-- Deploy through Developer Workstation afterwards; SQL cannot deploy Flowable.
-- Re-running preserves the existing seed, including subsequent designer edits.

DO $batch_demo$
DECLARE
    v_code CONSTANT text := 'sub-table-batch-demo';
    v_name CONSTANT text := 'Sub-Table Batch CRUD + Assign Demo';
    v_marker CONSTANT text := 'sub-table-batch-demo-seed-v1';
    v_group_name text := coalesce(nullif(current_setting('workflow.batch_demo_group', true), ''), 'developer groups');
    v_group_id text;
    v_fu bigint;
    v_existing_tags jsonb;
    v_main bigint;
    v_lines bigint;
    v_main_pk bigint;
    v_parent_fk bigint;
    v_forms bigint[] := ARRAY[]::bigint[];
    v_form bigint;
    v_primary bigint;
    v_sub bigint;
    v_view bigint;
    v_index integer;
    v_child boolean;
    v_row_rules jsonb;
    v_assignment jsonb;
    v_config jsonb;
    v_permissions jsonb;
    v_xml text;
BEGIN
    -- Serialize concurrent installers of this seed without locking unrelated FUs.
    PERFORM pg_advisory_xact_lock(hashtext(v_code));
    SELECT id, tags INTO v_fu, v_existing_tags FROM dw_function_units WHERE code = v_code;
    IF v_fu IS NOT NULL THEN
        IF NOT coalesce(v_existing_tags ? v_marker, false) THEN
            RAISE EXCEPTION 'Code % belongs to a different FU; refusing to overwrite it.', v_code;
        END IF;
        RAISE NOTICE 'Demo FU % already exists (id %); preserved without changes.', v_code, v_fu;
        RETURN;
    END IF;

    IF (SELECT count(*) FROM sys_virtual_groups
        WHERE name = v_group_name AND type IN ('CUSTOM', 'DEVELOPER') AND status = 'ACTIVE') <> 1 THEN
        RAISE EXCEPTION 'Expected one ACTIVE development group (CUSTOM/DEVELOPER) named "%".', v_group_name
            USING HINT = 'Create the group in Admin Center, or SET workflow.batch_demo_group to an existing group name in this connection.';
    END IF;
    SELECT id INTO STRICT v_group_id FROM sys_virtual_groups
        WHERE name = v_group_name AND type IN ('CUSTOM', 'DEVELOPER') AND status = 'ACTIVE';

    INSERT INTO dw_function_units
        (code, name, display_name, status, startup_mode, tags, created_by, updated_by, deployed_at)
    VALUES (v_code, v_name,
        'Synthetic demo: batch add/edit/delete rows, assign people or BU + Role, then create parallel row-scoped sub-tasks.',
        'DRAFT', 'STANDALONE', jsonb_build_array(v_marker, 'demo', 'batch'), 'system', 'system', NULL)
    RETURNING id INTO v_fu;
    INSERT INTO dw_function_unit_dev_groups (function_unit_id, virtual_group_id, created_by)
        VALUES (v_fu, v_group_id, 'system');

    INSERT INTO dw_table_definitions (function_unit_id, table_name, table_display_name, table_type)
        VALUES (v_fu, 'batch_crud_assign_case', 'Batch demo case', 'MAIN') RETURNING id INTO v_main;
    INSERT INTO dw_table_definitions (function_unit_id, table_name, table_display_name, table_type)
        VALUES (v_fu, 'batch_crud_assign_line', 'Batch demo lines', 'SUB') RETURNING id INTO v_lines;

    -- Case key is deliberately manual: enter a new unique value for each request.
    -- Line key is UUID-generated: users must NOT type or batch-assign primary keys.
    INSERT INTO dw_field_definitions
        (table_id, field_name, data_type, length, nullable, is_primary_key, is_unique,
         display_name, sort_order, pk_generation_json)
    VALUES (v_main, 'case_key', 'VARCHAR', 64, false, true, true,
        'Case key', 0, '{"strategy":"manual"}') RETURNING id INTO v_main_pk;
    INSERT INTO dw_field_definitions
        (table_id, field_name, data_type, length, nullable, display_name, sort_order)
    VALUES (v_main, 'title', 'VARCHAR', 200, false, 'Request title', 1);
    INSERT INTO dw_field_definitions
        (table_id, field_name, data_type, length, nullable, is_primary_key, is_unique,
         display_name, sort_order, pk_generation_json)
    VALUES (v_lines, 'line_key', 'VARCHAR', 64, false, true, true,
        'Line key', 0, '{"strategy":"uuid"}');
    INSERT INTO dw_field_definitions
        (table_id, field_name, data_type, length, nullable, display_name, sort_order,
         is_foreign_key, ref_table_id, ref_primary_key_fields, fk_display_mode, relation_cardinality)
    VALUES (v_lines, 'case_ref', 'VARCHAR', 64, false, 'Case reference', 1,
        true, v_main, '["case_key"]', 'readonly', 'oneToMany') RETURNING id INTO v_parent_fk;
    INSERT INTO dw_foreign_keys (table_id, field_id, ref_table_id, ref_field_id)
        VALUES (v_lines, v_parent_fk, v_main, v_main_pk);
    INSERT INTO dw_field_definitions
        (table_id, field_name, data_type, length, nullable, display_name, sort_order, default_value)
    VALUES
        (v_lines, 'caption', 'VARCHAR', 100, false, 'Caption', 2, 'New line'),
        (v_lines, 'amount', 'INTEGER', NULL, false, 'Amount', 3, '0'),
        (v_lines, 'assignee_id', 'VARCHAR', 100, true, 'Assignee', 4, NULL),
        (v_lines, 'bu_code', 'VARCHAR', 100, true, 'Business unit', 5, NULL),
        (v_lines, 'role_code', 'VARCHAR', 100, true, 'Role', 6, NULL);

    v_assignment := '{"type":"miAssignment","name":"ref_batch_demo_assignment","children":[
        {"type":"input","field":"assignee_id","title":"Assignee"},
        {"type":"input","field":"bu_code","title":"Business unit"},
        {"type":"input","field":"role_code","title":"Role"}
    ]}'::jsonb;
    v_row_rules := '[
        {"type":"input","field":"caption","title":"Caption","value":"New line",
         "props":{"maxlength":100},"validate":[{"required":true,"message":"Enter a caption"}]},
        {"type":"inputNumber","field":"amount","title":"Amount","value":0,
         "props":{"min":0,"precision":0},"validate":[{"required":true,"message":"Enter an amount"}]}
    ]'::jsonb;

    -- Separate source forms and child form; every binding ID is generated locally.
    FOR v_index IN 1..3 LOOP
        v_child := v_index = 3;
        INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, scene)
        VALUES (v_fu, (ARRAY['01 - Create batch rows', '02 - Batch CRUD and Assign', '03 - Handle assigned row'])[v_index],
            CASE WHEN v_index = 1 THEN 'PROCESS' ELSE 'TASK' END, 'TASK') RETURNING id INTO v_form;
        v_forms := array_append(v_forms, v_form);
        INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, sort_order)
            VALUES (v_form, v_main, 'PRIMARY', CASE WHEN v_child THEN 'READONLY' ELSE 'EDITABLE' END, 0)
            RETURNING id INTO v_primary;
        INSERT INTO dw_form_table_bindings
            (form_id, table_id, binding_type, binding_mode, foreign_key_field,
             binding_link_mode, filter_fk_field_id, sub_mode, sort_order)
        VALUES (v_form, v_lines, 'SUB', 'EDITABLE', CASE WHEN v_child THEN 'line_key' ELSE 'case_ref' END,
            CASE WHEN v_child THEN 'miParticipantRow' ELSE 'structuralFk' END,
            CASE WHEN v_child THEN NULL ELSE v_parent_fk END, 'FULL', 1) RETURNING id INTO v_sub;
        INSERT INTO dw_sub_table_view_configs (binding_id) VALUES (v_sub) RETURNING id INTO v_view;
        UPDATE dw_form_table_bindings SET sub_list_view_id = v_view WHERE id = v_sub;
        INSERT INTO dw_sub_table_view_fields
            (view_config_id, field_name, display_label, column_width, sort_order, visible)
        VALUES
            (v_view, 'line_key', 'Line key', 280, 0, true),
            (v_view, 'caption', 'Caption', 220, 1, true),
            (v_view, 'amount', 'Amount', 120, 2, true),
            (v_view, 'assignee_id', 'Assignee', 180, 3, true),
            (v_view, 'bu_code', 'Business unit', 160, 4, true),
            (v_view, 'role_code', 'Role', 180, 5, true);

        -- Palette-standard Sub-Table: NO field/value/validate. Binding + title at
        -- rule root; only operation/presentation options in props. The designer
        -- supplies its own transient _fc_id/_menu/_on data when loading.
        v_config := jsonb_build_object(
            'rule', jsonb_build_array(
                jsonb_build_object('type', 'input', 'field', 'case_key', 'title', 'Case key',
                    'props', jsonb_build_object('_bindingId', v_primary, 'readonly', v_child, 'maxlength', 64),
                    'validate', '[{"required":true,"message":"Enter a unique case key"}]'::jsonb),
                jsonb_build_object('type', 'input', 'field', 'title', 'title', 'Request title',
                    'props', jsonb_build_object('_bindingId', v_primary, 'readonly', v_child, 'maxlength', 200),
                    'validate', '[{"required":true,"message":"Enter a request title"}]'::jsonb),
                jsonb_build_object('type', 'subTable', 'name', 'ref_batch_demo_lines_' || v_index,
                    'title', CASE WHEN v_child THEN 'My assigned row' ELSE 'Batch demo lines' END,
                    '_bindingId', v_sub, 'batchControlId', 'batch-demo-' || v_form,
                    'props', jsonb_build_object('allowAdd', NOT v_child, 'allowEdit', true,
                        'allowDelete', NOT v_child, 'allowBatchAdd', NOT v_child,
                        'allowBatchEdit', NOT v_child, 'allowBatchDelete', NOT v_child,
                        'allowBatchAssign', NOT v_child, 'compactCells', false))),
            'options', '{"form":{"labelPosition":"top","inline":false},"submitBtn":{"show":false},"resetBtn":{"show":false}}'::jsonb,
            'subForms', jsonb_build_object(v_sub::text, jsonb_build_object(
                'rule', v_row_rules || jsonb_build_array(v_assignment), 'options', '{}'::jsonb)),
            'batchForms', CASE WHEN v_child THEN '{}'::jsonb ELSE jsonb_build_object(v_sub::text,
                jsonb_build_object('schemaVersion', 1, 'rule', v_row_rules || '[{"type":"miAssignment","name":"ref_batch_demo_batch_assignment"}]'::jsonb,
                    'options', '{}'::jsonb)) END);
        v_permissions := jsonb_build_object('case_key', CASE WHEN v_child THEN 'READONLY' ELSE 'EDITABLE' END,
            'title', CASE WHEN v_child THEN 'READONLY' ELSE 'EDITABLE' END,
            v_sub || ':line_key', 'READONLY', v_sub || ':case_ref', 'READONLY',
            v_sub || ':caption', 'EDITABLE', v_sub || ':amount', 'EDITABLE',
            v_sub || ':assignee_id', CASE WHEN v_child THEN 'READONLY' ELSE 'EDITABLE' END,
            v_sub || ':bu_code', CASE WHEN v_child THEN 'READONLY' ELSE 'EDITABLE' END,
            v_sub || ':role_code', CASE WHEN v_child THEN 'READONLY' ELSE 'EDITABLE' END);
        UPDATE dw_form_definitions SET config_json = v_config, field_permissions = v_permissions WHERE id = v_form;
    END LOOP;

    v_xml := format($xml$<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" xmlns:custom="http://workflow.platform/schema/custom" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" targetNamespace="BatchDemo">
  <bpmn:process id="sub-table-batch-demo" name="Sub-Table Batch CRUD and Assign Demo" isExecutable="true">
    <bpmn:startEvent id="Start" name="Start"><bpmn:extensionElements><custom:properties><custom:property name="formId" value="%1$s"/></custom:properties></bpmn:extensionElements></bpmn:startEvent>
    <bpmn:sequenceFlow id="StartToIntake" sourceRef="Start" targetRef="Intake"/>
    <bpmn:userTask id="Intake" name="Create batch rows" flowable:assignee="${initiator}"><bpmn:extensionElements><custom:properties><custom:property name="formId" value="%1$s"/><custom:property name="assigneeType" value="INITIATOR"/></custom:properties></bpmn:extensionElements></bpmn:userTask>
    <bpmn:sequenceFlow id="IntakeToReview" sourceRef="Intake" targetRef="Review"/>
    <bpmn:userTask id="Review" name="Batch CRUD and Assign" flowable:assignee="${initiator}"><bpmn:extensionElements><custom:properties><custom:property name="formId" value="%2$s"/><custom:property name="assigneeType" value="INITIATOR"/></custom:properties></bpmn:extensionElements></bpmn:userTask>
    <bpmn:sequenceFlow id="ReviewToAssigned" sourceRef="Review" targetRef="AssignedRows"/>
    <bpmn:subProcess id="AssignedRows" name="Parallel assigned sub-tasks">
      <bpmn:multiInstanceLoopCharacteristics isSequential="false" flowable:collection="multiInstance_batch_crud_assign_line_collection" flowable:elementVariable="currentItem"/>
      <bpmn:startEvent id="AssignedStart"/>
      <bpmn:sequenceFlow id="AssignedStartToWork" sourceRef="AssignedStart" targetRef="AssignedWork"/>
      <bpmn:userTask id="AssignedWork" name="Handle assigned row"><bpmn:extensionElements><custom:properties>
        <custom:property name="formId" value="%3$s"/>
        <custom:property name="subTableName" value="batch_crud_assign_line"/><custom:property name="subTableId" value="%4$s"/>
        <custom:property name="assigneeType" value="ELEMENT_VARIABLE"/><custom:property name="assigneeMode" value="both"/>
        <custom:property name="assigneeField" value="assignee_id"/><custom:property name="buField" value="bu_code"/><custom:property name="roleField" value="role_code"/>
        <custom:property name="rowIdVariable" value="${currentItem.rowId}"/>
      </custom:properties></bpmn:extensionElements></bpmn:userTask>
      <bpmn:sequenceFlow id="AssignedWorkToEnd" sourceRef="AssignedWork" targetRef="AssignedEnd"/>
      <bpmn:endEvent id="AssignedEnd"/>
    </bpmn:subProcess>
    <bpmn:sequenceFlow id="AssignedToEnd" sourceRef="AssignedRows" targetRef="End"/>
    <bpmn:endEvent id="End" name="End"/>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BatchDemoDiagram"><bpmndi:BPMNPlane id="BatchDemoPlane" bpmnElement="sub-table-batch-demo">
    <bpmndi:BPMNShape id="StartShape" bpmnElement="Start"><dc:Bounds x="100" y="172" width="36" height="36"/></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="IntakeShape" bpmnElement="Intake"><dc:Bounds x="190" y="150" width="120" height="80"/></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="ReviewShape" bpmnElement="Review"><dc:Bounds x="360" y="150" width="120" height="80"/></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="AssignedShape" bpmnElement="AssignedRows" isExpanded="true"><dc:Bounds x="530" y="80" width="400" height="220"/></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="AssignedStartShape" bpmnElement="AssignedStart"><dc:Bounds x="565" y="172" width="36" height="36"/></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="AssignedWorkShape" bpmnElement="AssignedWork"><dc:Bounds x="650" y="150" width="130" height="80"/></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="AssignedEndShape" bpmnElement="AssignedEnd"><dc:Bounds x="850" y="172" width="36" height="36"/></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="EndShape" bpmnElement="End"><dc:Bounds x="990" y="172" width="36" height="36"/></bpmndi:BPMNShape>
    <bpmndi:BPMNEdge id="StartEdge" bpmnElement="StartToIntake"><di:waypoint x="136" y="190"/><di:waypoint x="190" y="190"/></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="IntakeEdge" bpmnElement="IntakeToReview"><di:waypoint x="310" y="190"/><di:waypoint x="360" y="190"/></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="ReviewEdge" bpmnElement="ReviewToAssigned"><di:waypoint x="480" y="190"/><di:waypoint x="530" y="190"/></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="AssignedStartEdge" bpmnElement="AssignedStartToWork"><di:waypoint x="601" y="190"/><di:waypoint x="650" y="190"/></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="AssignedWorkEdge" bpmnElement="AssignedWorkToEnd"><di:waypoint x="780" y="190"/><di:waypoint x="850" y="190"/></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="AssignedEndEdge" bpmnElement="AssignedToEnd"><di:waypoint x="930" y="190"/><di:waypoint x="990" y="190"/></bpmndi:BPMNEdge>
  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
</bpmn:definitions>$xml$, v_forms[1], v_forms[2], v_forms[3], v_lines);
    INSERT INTO dw_process_definitions (function_unit_id, function_unit_version_id, bpmn_xml)
        VALUES (v_fu, v_fu, encode(convert_to(v_xml, 'UTF8'), 'base64'));
    INSERT INTO dw_form_stage_bindings (form_id, stage_id, stage_name, scene) VALUES
        (v_forms[1], 'Intake', 'Create batch rows', 'TASK'),
        (v_forms[2], 'Review', 'Batch CRUD and Assign', 'TASK'),
        (v_forms[3], 'AssignedWork', 'Handle assigned row', 'TASK');

    -- Fail atomically if a nonstandard wrapper or mismatched batch binding slips in.
    IF EXISTS (SELECT 1 FROM dw_form_definitions f,
        LATERAL jsonb_array_elements(f.config_json->'rule') r
        WHERE f.function_unit_id = v_fu AND r->>'type' = 'subTable'
        AND (r ? 'field' OR r ? 'value' OR r ? 'validate' OR NOT r ? '_bindingId' OR r->'props' ? '_bindingId')) THEN
        RAISE EXCEPTION 'Demo Sub-Table must use palette-standard root binding and no field/value/validate.';
    END IF;
    IF NOT xml_is_well_formed_document(v_xml) THEN
        RAISE EXCEPTION 'Demo BPMN XML is not well formed.';
    END IF;
    RAISE NOTICE 'Created % (FU id %), group %. Next: Validate and Deploy in Developer Workstation.', v_name, v_fu, v_group_name;
END
$batch_demo$;
