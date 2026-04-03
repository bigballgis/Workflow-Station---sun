-- =============================================================================
-- 14-travel-expense-reimbursement: Create BPMN Process Definition
-- 差旅报销功能单元：创建 BPMN 流程定义
-- BPMN 存储为 base64 编码，与平台存储格式一致
-- 流程: Start → 填写报销申请 (Reimbursement Form, 提交报销 + AI识别发票)
--       → 主管审批 (Approval Form, 审批通过 + 审批驳回)
--       → ExclusiveGateway → 已通过 (decision=='yes') / 已驳回 (decision!='yes')
--
-- Dependencies: 00-create-function-unit.sql (Function Unit, Forms, Actions)
-- Execution order: 00 → 01 → 02 → 03
-- =============================================================================

DO $bpmn$
DECLARE
    v_function_unit_id      BIGINT;
    v_reimbursement_form_id BIGINT;
    v_approval_form_id      BIGINT;
    v_action_submit_id      BIGINT;
    v_action_n8n_id         BIGINT;
    v_action_approve_id     BIGINT;
    v_action_reject_id      BIGINT;
    v_bpmn_xml              TEXT;
    v_bpmn_b64              TEXT;
BEGIN
    SELECT id INTO v_function_unit_id FROM dw_function_units WHERE code = 'fu-20260403-a1b2c3';
    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit fu-20260403-a1b2c3 not found. Run 00-create-function-unit.sql first.';
    END IF;

    SELECT id INTO v_reimbursement_form_id FROM dw_form_definitions   WHERE function_unit_id = v_function_unit_id AND form_name   = 'Reimbursement Form';
    SELECT id INTO v_approval_form_id      FROM dw_form_definitions   WHERE function_unit_id = v_function_unit_id AND form_name   = 'Approval Form';
    SELECT id INTO v_action_submit_id      FROM dw_action_definitions WHERE function_unit_id = v_function_unit_id AND action_name = '提交报销';
    SELECT id INTO v_action_n8n_id         FROM dw_action_definitions WHERE function_unit_id = v_function_unit_id AND action_name = 'AI 识别发票';
    SELECT id INTO v_action_approve_id     FROM dw_action_definitions WHERE function_unit_id = v_function_unit_id AND action_name = '审批通过';
    SELECT id INTO v_action_reject_id      FROM dw_action_definitions WHERE function_unit_id = v_function_unit_id AND action_name = '审批驳回';

    IF v_reimbursement_form_id IS NULL OR v_approval_form_id IS NULL THEN
        RAISE EXCEPTION 'Forms not found. Run 00-create-function-unit.sql first.';
    END IF;
    IF v_action_submit_id IS NULL OR v_action_n8n_id IS NULL OR v_action_approve_id IS NULL OR v_action_reject_id IS NULL THEN
        RAISE EXCEPTION 'Actions not found. Run 00-create-function-unit.sql first.';
    END IF;

    -- Build BPMN XML with actual IDs substituted
    v_bpmn_xml := format(
        $xml$<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:custom_1="http://custom.bpmn.io/schema" xmlns:custom="http://workflow.platform/schema/custom" id="Definitions_TravelExpenseReimbursement" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="TravelExpenseReimburse" name="Travel Expense Reimbursement" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="Task_FillReimbursement" name="填写报销申请">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[%s,%s]"/>
          <custom_1:values name="actionNames" value="[&amp;#34;提交报销&amp;#34;,&amp;#34;AI 识别发票&amp;#34;]"/>
          <custom_1:values name="formId" value="%s"/>
          <custom_1:values name="formName" value="Reimbursement Form"/>
        </custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:userTask id="Task_ManagerApproval" name="主管审批">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[%s,%s]"/>
          <custom_1:values name="actionNames" value="[&amp;#34;审批通过&amp;#34;,&amp;#34;审批驳回&amp;#34;]"/>
          <custom_1:values name="formId" value="%s"/>
          <custom_1:values name="formName" value="Approval Form"/>
        </custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_2</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:exclusiveGateway id="Gateway_ApprovalResult" name="审批结果">
      <bpmn:incoming>Flow_3</bpmn:incoming>
      <bpmn:outgoing>Flow_Approved</bpmn:outgoing>
      <bpmn:outgoing>Flow_Rejected</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:endEvent id="EndEvent_Approved" name="已通过">
      <bpmn:incoming>Flow_Approved</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:endEvent id="EndEvent_Rejected" name="已驳回">
      <bpmn:incoming>Flow_Rejected</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_FillReimbursement"/>
    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_FillReimbursement" targetRef="Task_ManagerApproval"/>
    <bpmn:sequenceFlow id="Flow_3" sourceRef="Task_ManagerApproval" targetRef="Gateway_ApprovalResult"/>
    <bpmn:sequenceFlow id="Flow_Approved" name="Yes" sourceRef="Gateway_ApprovalResult" targetRef="EndEvent_Approved">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${decision == 'yes'}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_Rejected" name="No" sourceRef="Gateway_ApprovalResult" targetRef="EndEvent_Rejected">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${decision != 'yes'}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="TravelExpenseReimburse">
      <bpmndi:BPMNShape id="BPMNShape_StartEvent" bpmnElement="StartEvent_1">
        <dc:Bounds x="170" y="150" width="36" height="36"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="175" y="185" width="24" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_FillReimbursement" bpmnElement="Task_FillReimbursement">
        <dc:Bounds x="270" y="130" width="120" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_ManagerApproval" bpmnElement="Task_ManagerApproval">
        <dc:Bounds x="460" y="130" width="120" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_Gateway" bpmnElement="Gateway_ApprovalResult" isMarkerVisible="true">
        <dc:Bounds x="650" y="145" width="50" height="50"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="648" y="121" width="53" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_Approved" bpmnElement="EndEvent_Approved">
        <dc:Bounds x="770" y="150" width="36" height="36"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="765" y="185" width="47" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_Rejected" bpmnElement="EndEvent_Rejected">
        <dc:Bounds x="657" y="262" width="36" height="36"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="652" y="297" width="44" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="BPMNEdge_Flow1" bpmnElement="Flow_1">
        <di:waypoint x="206" y="168"/>
        <di:waypoint x="270" y="168"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_Flow2" bpmnElement="Flow_2">
        <di:waypoint x="390" y="168"/>
        <di:waypoint x="460" y="168"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_Flow3" bpmnElement="Flow_3">
        <di:waypoint x="580" y="168"/>
        <di:waypoint x="650" y="168"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_Approved" bpmnElement="Flow_Approved">
        <di:waypoint x="700" y="168"/>
        <di:waypoint x="770" y="168"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="725" y="150" width="18" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_Rejected" bpmnElement="Flow_Rejected">
        <di:waypoint x="675" y="195"/>
        <di:waypoint x="675" y="262"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="685" y="223" width="15" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
$xml$,
        v_action_submit_id,    -- %s -> actionIds[0] for 填写报销申请 (提交报销)
        v_action_n8n_id,       -- %s -> actionIds[1] for 填写报销申请 (AI 识别发票)
        v_reimbursement_form_id, -- %s -> formId for 填写报销申请
        v_action_approve_id,   -- %s -> actionIds[0] for 主管审批 (审批通过)
        v_action_reject_id,    -- %s -> actionIds[1] for 主管审批 (审批驳回)
        v_approval_form_id     -- %s -> formId for 主管审批
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
    RAISE NOTICE 'Function Unit ID       : %', v_function_unit_id;
    RAISE NOTICE 'Reimbursement Form ID  : %', v_reimbursement_form_id;
    RAISE NOTICE 'Approval Form ID       : %', v_approval_form_id;
    RAISE NOTICE 'Submit Action ID       : %', v_action_submit_id;
    RAISE NOTICE 'N8N Action ID          : %', v_action_n8n_id;
    RAISE NOTICE 'Approve Action ID      : %', v_action_approve_id;
    RAISE NOTICE 'Reject Action ID       : %', v_action_reject_id;
    RAISE NOTICE 'Next: run 03-form-table-bindings.sql';
    RAISE NOTICE '========================================';

END $bpmn$;
