-- =============================================================================
-- 13-procurement-workflow: Create BPMN Process Definition
-- 基于数据库实际数据生成
-- BPMN 存储为 base64 编码，与源数据库一致
-- 流程: Start → Submit Request → Price Check Gateway
--       → (>10000) Manager Review → Approved? → Yes → Approved / No → Rejected
--       → (<=10000) Auto Approved
-- =============================================================================

DO $bpmn$
DECLARE
    v_function_unit_id  BIGINT;
    v_request_form_id   BIGINT;
    v_approval_form_id  BIGINT;
    v_action_submit_id  BIGINT;
    v_action_approve_id BIGINT;
    v_action_reject_id  BIGINT;
    v_bpmn_xml          TEXT;
    v_bpmn_b64          TEXT;
BEGIN
    SELECT id INTO v_function_unit_id FROM dw_function_units WHERE code = 'PROCUREMENT_WORKFLOW';
    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit PROCUREMENT_WORKFLOW not found. Run 00-create-function-unit.sql first.';
    END IF;

    SELECT id INTO v_request_form_id   FROM dw_form_definitions   WHERE function_unit_id = v_function_unit_id AND form_name   = 'Request Form';
    SELECT id INTO v_approval_form_id  FROM dw_form_definitions   WHERE function_unit_id = v_function_unit_id AND form_name   = 'Approval Form';
    SELECT id INTO v_action_submit_id  FROM dw_action_definitions WHERE function_unit_id = v_function_unit_id AND action_name = 'Submit Request';
    SELECT id INTO v_action_approve_id FROM dw_action_definitions WHERE function_unit_id = v_function_unit_id AND action_name = 'Approve';
    SELECT id INTO v_action_reject_id  FROM dw_action_definitions WHERE function_unit_id = v_function_unit_id AND action_name = 'Reject';

    IF v_request_form_id IS NULL OR v_approval_form_id IS NULL THEN
        RAISE EXCEPTION 'Forms not found. Run 00-create-function-unit.sql first.';
    END IF;
    IF v_action_submit_id IS NULL OR v_action_approve_id IS NULL OR v_action_reject_id IS NULL THEN
        RAISE EXCEPTION 'Actions not found. Run 00-create-function-unit.sql first.';
    END IF;

    -- Build BPMN XML with actual IDs substituted
    v_bpmn_xml := format(
        $xml$<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:custom_1="http://custom.bpmn.io/schema" xmlns:custom="http://workflow.platform/schema/custom" id="Definitions_ProcurementWorkflow" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="ProcurementWorkflowProcess" name="Procurement Workflow" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="Task_SubmitRequest" name="Submit Request">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[%s]"/>
          <custom_1:values name="actionNames" value="[&amp;#34;Submit Request&amp;#34;]"/>
          <custom_1:values name="formId" value="%s"/>
          <custom_1:values name="formName" value="Request Form"/>
        </custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:exclusiveGateway id="Gateway_PriceCheck" name="Total price &gt; 10000?">
      <bpmn:incoming>Flow_2</bpmn:incoming>
      <bpmn:outgoing>Flow_ToManagerReview</bpmn:outgoing>
      <bpmn:outgoing>Flow_AutoApproved</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:userTask id="Task_ManagerApproval" name="Manager Review">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[%s,%s]"/>
          <custom_1:values name="actionNames" value="[&amp;#34;Approve&amp;#34;,&amp;#34;Reject&amp;#34;]"/>
        <custom_1:property name="formId" value="%s"/><custom_1:property name="formName" value="Approval Form"/></custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_ToManagerReview</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:exclusiveGateway id="Gateway_ManagerDecision" name="Approved?">
      <bpmn:incoming>Flow_3</bpmn:incoming>
      <bpmn:outgoing>Flow_Approved</bpmn:outgoing>
      <bpmn:outgoing>Flow_Rejected</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:endEvent id="EndEvent_Approved" name="Approved">
      <bpmn:incoming>Flow_Approved</bpmn:incoming>
      <bpmn:incoming>Flow_AutoApproved</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:endEvent id="EndEvent_Rejected" name="Rejected">
      <bpmn:incoming>Flow_Rejected</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_SubmitRequest"/>
    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_SubmitRequest" targetRef="Gateway_PriceCheck"/>
    <bpmn:sequenceFlow id="Flow_ToManagerReview" name="Yes" sourceRef="Gateway_PriceCheck" targetRef="Task_ManagerApproval">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${totalPrice &gt; 10000}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_AutoApproved" name="No" sourceRef="Gateway_PriceCheck" targetRef="EndEvent_Approved">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${totalPrice &lt;= 10000}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_3" sourceRef="Task_ManagerApproval" targetRef="Gateway_ManagerDecision"/>
    <bpmn:sequenceFlow id="Flow_Approved" name="Yes" sourceRef="Gateway_ManagerDecision" targetRef="EndEvent_Approved">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${decision == 'yes'}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_Rejected" name="No" sourceRef="Gateway_ManagerDecision" targetRef="EndEvent_Rejected">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${decision != 'yes'}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="ProcurementWorkflowProcess">
      <bpmndi:BPMNShape id="BPMNShape_1e8g875" bpmnElement="StartEvent_1">
        <dc:Bounds x="170" y="150" width="36" height="36"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="175" y="185" width="24" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_0o4rau3" bpmnElement="Task_SubmitRequest">
        <dc:Bounds x="270" y="130" width="120" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_PriceCheck" bpmnElement="Gateway_PriceCheck" isMarkerVisible="true">
        <dc:Bounds x="470" y="145" width="50" height="50"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="430" y="114" width="60" height="27"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_0o9yima" bpmnElement="Task_ManagerApproval">
        <dc:Bounds x="590" y="130" width="120" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_07neaj5" bpmnElement="Gateway_ManagerDecision" isMarkerVisible="true">
        <dc:Bounds x="750" y="145" width="50" height="50"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="748" y="121" width="53" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_1vyg15b" bpmnElement="EndEvent_Approved">
        <dc:Bounds x="870" y="150" width="36" height="36"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="865" y="185" width="47" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_0gt3x3e" bpmnElement="EndEvent_Rejected">
        <dc:Bounds x="757" y="262" width="36" height="36"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="752" y="297" width="44" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="BPMNEdge_0ltq2fg" bpmnElement="Flow_1">
        <di:waypoint x="206" y="168"/>
        <di:waypoint x="270" y="168"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_1e8h2d6" bpmnElement="Flow_2">
        <di:waypoint x="390" y="168"/>
        <di:waypoint x="472" y="168"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_ToManagerReview" bpmnElement="Flow_ToManagerReview">
        <di:waypoint x="520" y="168"/>
        <di:waypoint x="590" y="168"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="545" y="150" width="18" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_AutoApproved" bpmnElement="Flow_AutoApproved">
        <di:waypoint x="495" y="145"/>
        <di:waypoint x="495" y="50"/>
        <di:waypoint x="888" y="50"/>
        <di:waypoint x="888" y="150"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="469" y="43" width="15" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_1udmyi1" bpmnElement="Flow_3">
        <di:waypoint x="710" y="168"/>
        <di:waypoint x="750" y="168"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_0mqn3a0" bpmnElement="Flow_Approved">
        <di:waypoint x="800" y="168"/>
        <di:waypoint x="870" y="168"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="825" y="150" width="18" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_03qyogm" bpmnElement="Flow_Rejected">
        <di:waypoint x="775" y="195"/>
        <di:waypoint x="775" y="262"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="785" y="223" width="15" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
$xml$,
        v_action_submit_id,    -- %s -> actionIds for Submit Request
        v_request_form_id,     -- %s -> formId for Submit Request
        v_action_approve_id,   -- %s -> actionIds[0] for Manager Review
        v_action_reject_id,    -- %s -> actionIds[1] for Manager Review
        v_approval_form_id     -- %s -> formId for Manager Review
    );

    -- Encode to base64 (same storage format as source DB)
    v_bpmn_b64 := encode(convert_to(v_bpmn_xml, 'UTF8'), 'base64');

    -- Insert or update process definition
    DELETE FROM dw_process_definitions WHERE function_unit_id = v_function_unit_id;

    INSERT INTO dw_process_definitions (
        function_unit_id, function_unit_version_id, bpmn_xml, created_at, updated_at
    ) VALUES (
        v_function_unit_id, v_function_unit_id, v_bpmn_b64, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

    RAISE NOTICE '========================================';
    RAISE NOTICE 'BPMN Process Definition created!';
    RAISE NOTICE 'Function Unit ID  : %', v_function_unit_id;
    RAISE NOTICE 'Request Form ID   : %', v_request_form_id;
    RAISE NOTICE 'Approval Form ID  : %', v_approval_form_id;
    RAISE NOTICE 'Submit Action ID  : %', v_action_submit_id;
    RAISE NOTICE 'Approve Action ID : %', v_action_approve_id;
    RAISE NOTICE 'Reject Action ID  : %', v_action_reject_id;
    RAISE NOTICE 'Next: run 03-form-table-bindings.sql';
    RAISE NOTICE '========================================';

END $bpmn$;
