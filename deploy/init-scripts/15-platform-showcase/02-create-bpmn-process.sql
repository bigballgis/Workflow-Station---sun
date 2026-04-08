-- =============================================================================
-- 15-platform-showcase: BPMN（含 flowable DMN 服务任务，引用 showcase_amount_tier）
-- 顺序: 00 → 01 → 02 → 03 → 04 → 05（stage_id 与 userTask id 对齐）
-- =============================================================================

DO $bpmn$
DECLARE
    v_fu_id            BIGINT;
    v_request_form_id  BIGINT;
    v_approval_form_id BIGINT;
    v_a_submit         BIGINT;
    v_a_save           BIGINT;
    v_a_approve        BIGINT;
    v_a_reject         BIGINT;
    v_a_transfer       BIGINT;
    v_a_delegate       BIGINT;
    v_bpmn_xml         TEXT;
    v_bpmn_b64         TEXT;
BEGIN
    SELECT id INTO v_fu_id FROM dw_function_units WHERE code = 'fu-20260403-a1b2c4';
    IF v_fu_id IS NULL THEN
        RAISE EXCEPTION 'fu-20260403-a1b2c4 not found. Run 00-create-function-unit.sql first.';
    END IF;

    SELECT id INTO v_request_form_id FROM dw_form_definitions WHERE function_unit_id = v_fu_id AND form_name = 'Showcase Request Form';
    SELECT id INTO v_approval_form_id FROM dw_form_definitions WHERE function_unit_id = v_fu_id AND form_name = 'Showcase Approval Form';

    SELECT id INTO v_a_submit   FROM dw_action_definitions WHERE function_unit_id = v_fu_id AND action_name = '提交申请';
    SELECT id INTO v_a_save     FROM dw_action_definitions WHERE function_unit_id = v_fu_id AND action_name = '保存草稿';
    SELECT id INTO v_a_approve  FROM dw_action_definitions WHERE function_unit_id = v_fu_id AND action_name = '审批通过';
    SELECT id INTO v_a_reject   FROM dw_action_definitions WHERE function_unit_id = v_fu_id AND action_name = '审批拒绝';
    SELECT id INTO v_a_transfer FROM dw_action_definitions WHERE function_unit_id = v_fu_id AND action_name = '转办';
    SELECT id INTO v_a_delegate FROM dw_action_definitions WHERE function_unit_id = v_fu_id AND action_name = '委派';

    IF v_request_form_id IS NULL OR v_approval_form_id IS NULL THEN
        RAISE EXCEPTION 'Forms missing. Run 00-create-function-unit.sql first.';
    END IF;
    IF v_a_submit IS NULL OR v_a_save IS NULL OR v_a_approve IS NULL OR v_a_reject IS NULL
       OR v_a_transfer IS NULL OR v_a_delegate IS NULL THEN
        RAISE EXCEPTION 'Actions missing. Run 00-create-function-unit.sql first.';
    END IF;

    v_bpmn_xml := format(
        $xml$<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:custom_1="http://custom.bpmn.io/schema" xmlns:custom="http://workflow.platform/schema/custom" xmlns:flowable="http://flowable.org/bpmn" id="Definitions_PlatformShowcase" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="PlatformShowcaseProcess" name="Platform Showcase Process" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="Task_SubmitShowcase" name="Submit Application">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[%s,%s]" />
          <custom_1:values name="actionNames" value="[&amp;#34;提交申请&amp;#34;,&amp;#34;保存草稿&amp;#34;]" />
          <custom_1:values name="formId" value="%s" />
          <custom_1:values name="formName" value="Showcase Request Form" />
        </custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_to_dmn</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:serviceTask id="Task_DmnShowcase" name="Evaluate Tier (DMN)" flowable:type="dmn">
      <bpmn:extensionElements>
        <flowable:field name="decisionTableReferenceKey">
          <flowable:string><![CDATA[showcase_amount_tier]]></flowable:string>
        </flowable:field>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_to_dmn</bpmn:incoming>
      <bpmn:outgoing>Flow_after_dmn</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:userTask id="Task_ManagerShowcase" name="Manager Approval">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[%s,%s,%s,%s]" />
          <custom_1:values name="actionNames" value="[&amp;#34;审批通过&amp;#34;,&amp;#34;审批拒绝&amp;#34;,&amp;#34;转办&amp;#34;,&amp;#34;委派&amp;#34;]" />
          <custom_1:values name="formId" value="%s" />
          <custom_1:values name="formName" value="Showcase Approval Form" />
        </custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_after_dmn</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:exclusiveGateway id="Gateway_ManagerDecision" name="Approved?">
      <bpmn:incoming>Flow_3</bpmn:incoming>
      <bpmn:outgoing>Flow_Approved</bpmn:outgoing>
      <bpmn:outgoing>Flow_Rejected</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:endEvent id="EndEvent_Approved" name="Approved">
      <bpmn:incoming>Flow_Approved</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:endEvent id="EndEvent_Rejected" name="Rejected">
      <bpmn:incoming>Flow_Rejected</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_SubmitShowcase" />
    <bpmn:sequenceFlow id="Flow_to_dmn" sourceRef="Task_SubmitShowcase" targetRef="Task_DmnShowcase" />
    <bpmn:sequenceFlow id="Flow_after_dmn" sourceRef="Task_DmnShowcase" targetRef="Task_ManagerShowcase" />
    <bpmn:sequenceFlow id="Flow_3" sourceRef="Task_ManagerShowcase" targetRef="Gateway_ManagerDecision" />
    <bpmn:sequenceFlow id="Flow_Approved" name="Yes" sourceRef="Gateway_ManagerDecision" targetRef="EndEvent_Approved">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${decision == 'yes'}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_Rejected" name="No" sourceRef="Gateway_ManagerDecision" targetRef="EndEvent_Rejected">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${decision == 'no'}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_Showcase">
    <bpmndi:BPMNPlane id="BPMNPlane_Showcase" bpmnElement="PlatformShowcaseProcess">
      <bpmndi:BPMNShape id="Shape_Start" bpmnElement="StartEvent_1">
        <dc:Bounds x="152" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_Submit" bpmnElement="Task_SubmitShowcase">
        <dc:Bounds x="232" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_Dmn" bpmnElement="Task_DmnShowcase">
        <dc:Bounds x="382" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_Manager" bpmnElement="Task_ManagerShowcase">
        <dc:Bounds x="532" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_Gateway" bpmnElement="Gateway_ManagerDecision" isMarkerVisible="true">
        <dc:Bounds x="682" y="95" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_EndOk" bpmnElement="EndEvent_Approved">
        <dc:Bounds x="792" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_EndNo" bpmnElement="EndEvent_Rejected">
        <dc:Bounds x="689" y="222" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Edge_Flow1" bpmnElement="Flow_1">
        <di:waypoint x="188" y="120" />
        <di:waypoint x="232" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_toDmn" bpmnElement="Flow_to_dmn">
        <di:waypoint x="332" y="120" />
        <di:waypoint x="382" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_afterDmn" bpmnElement="Flow_after_dmn">
        <di:waypoint x="482" y="120" />
        <di:waypoint x="532" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Flow3" bpmnElement="Flow_3">
        <di:waypoint x="632" y="120" />
        <di:waypoint x="682" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Approved" bpmnElement="Flow_Approved">
        <di:waypoint x="732" y="120" />
        <di:waypoint x="792" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Rejected" bpmnElement="Flow_Rejected">
        <di:waypoint x="707" y="145" />
        <di:waypoint x="707" y="222" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
$xml$,
        v_a_submit,
        v_a_save,
        v_request_form_id,
        v_a_approve,
        v_a_reject,
        v_a_transfer,
        v_a_delegate,
        v_approval_form_id
    );

    v_bpmn_b64 := encode(convert_to(v_bpmn_xml, 'UTF8'), 'base64');

    DELETE FROM dw_process_definitions WHERE function_unit_id = v_fu_id;

    INSERT INTO dw_process_definitions (
        function_unit_id, function_unit_version_id, bpmn_xml, created_at, updated_at
    ) VALUES (
        v_fu_id, v_fu_id, v_bpmn_b64, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

    RAISE NOTICE 'fu-20260403-a1b2c4 BPMN written (base64). Next: 03-form-table-bindings.sql';

END $bpmn$;
