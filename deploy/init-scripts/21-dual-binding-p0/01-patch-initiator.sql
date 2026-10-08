\set ON_ERROR_STOP on

-- Idempotent upgrade for an already-applied 21-dual-binding-p0 seed.
-- Looks up the Function Unit by code (never by generated id).
-- Converges BPMN to two INITIATOR user tasks so Fill auto-completes and Review
-- remains a To Do for Portal Save + subTableBindingScopes.

DO $patch$
DECLARE
    v_fu     BIGINT;
    v_form   BIGINT;
    v_submit BIGINT;
    v_save   BIGINT;
    v_bpmn   TEXT;
BEGIN
    SELECT id INTO v_fu FROM dw_function_units WHERE code = 'p0-dual-binding-test';
    IF v_fu IS NULL THEN
        RAISE NOTICE 'p0-dual-binding-test not present; skip';
        RETURN;
    END IF;

    SELECT id INTO v_form FROM dw_form_definitions
     WHERE function_unit_id = v_fu AND form_name = 'P0 Dual Case Form'
     ORDER BY id LIMIT 1;
    SELECT id INTO v_submit FROM dw_action_definitions
     WHERE function_unit_id = v_fu AND action_name = 'Submit'
     ORDER BY id LIMIT 1;
    SELECT id INTO v_save FROM dw_action_definitions
     WHERE function_unit_id = v_fu AND action_name = 'Save'
     ORDER BY id LIMIT 1;

    IF v_form IS NULL OR v_submit IS NULL OR v_save IS NULL THEN
        RAISE EXCEPTION 'p0-dual-binding-test is missing form or Submit/Save actions';
    END IF;

    v_bpmn := format(
        $xml$<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:custom_1="http://custom.bpmn.io/schema" xmlns:custom="http://workflow.platform/schema/custom" xmlns:flowable="http://flowable.org/bpmn" id="Definitions_P0Dual" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="p0-dual-binding-test" name="P0 Dual Binding Test" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="Task_FillDual" name="Fill Dual Binding Case">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[%s,%s]" />
          <custom_1:values name="actionNames" value="[&amp;#34;Submit&amp;#34;,&amp;#34;Save&amp;#34;]" />
          <custom_1:values name="formId" value="%s" />
          <custom_1:values name="formName" value="P0 Dual Case Form" />
          <custom:property name="assigneeType" value="INITIATOR" />
          <custom:property name="assigneeLabel" value="Process Initiator" />
        </custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:userTask id="Task_ReviewDual" name="Review Dual Binding Case">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[%s,%s]" />
          <custom_1:values name="actionNames" value="[&amp;#34;Submit&amp;#34;,&amp;#34;Save&amp;#34;]" />
          <custom_1:values name="formId" value="%s" />
          <custom_1:values name="formName" value="P0 Dual Case Form" />
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
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_FillDual" />
    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_FillDual" targetRef="Task_ReviewDual" />
    <bpmn:sequenceFlow id="Flow_3" sourceRef="Task_ReviewDual" targetRef="EndEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_P0Dual">
    <bpmndi:BPMNPlane id="BPMNPlane_P0Dual" bpmnElement="p0-dual-binding-test">
      <bpmndi:BPMNShape id="Shape_Start" bpmnElement="StartEvent_1">
        <dc:Bounds x="152" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_Fill" bpmnElement="Task_FillDual">
        <dc:Bounds x="240" y="80" width="140" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_Review" bpmnElement="Task_ReviewDual">
        <dc:Bounds x="430" y="80" width="150" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_End" bpmnElement="EndEvent_1">
        <dc:Bounds x="630" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Edge_1" bpmnElement="Flow_1">
        <di:waypoint x="188" y="120" />
        <di:waypoint x="240" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_2" bpmnElement="Flow_2">
        <di:waypoint x="380" y="120" />
        <di:waypoint x="430" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_3" bpmnElement="Flow_3">
        <di:waypoint x="580" y="120" />
        <di:waypoint x="630" y="120" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
$xml$,
        v_submit, v_save, v_form,
        v_submit, v_save, v_form
    );

    UPDATE dw_process_definitions
       SET bpmn_xml = encode(convert_to(v_bpmn, 'UTF8'), 'base64'),
           updated_at = CURRENT_TIMESTAMP
     WHERE function_unit_id = v_fu;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'p0-dual-binding-test has no process definition to patch';
    END IF;

    INSERT INTO dw_form_stage_bindings (form_id, stage_id, stage_name, scene)
    SELECT v_form, 'Task_ReviewDual', 'Review Dual Binding Case', 'TASK'
    WHERE NOT EXISTS (
        SELECT 1 FROM dw_form_stage_bindings
         WHERE form_id = v_form AND stage_id = 'Task_ReviewDual'
    );

    RAISE NOTICE 'p0-dual-binding-test BPMN now has Fill + Review INITIATOR tasks';
END
$patch$;
