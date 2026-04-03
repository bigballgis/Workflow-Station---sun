-- =============================================================================
-- 13-procurement-workflow: Create BPMN Process Definition
-- 基于数据库实际数据生成
-- BPMN 存储为 base64 编码，与源数据库一致
-- 流程: Start → Submit Request → First Review → Approve?
--       → Yes → Second Review → Total price > 10000?
--                → Yes → Manager Review → Approved? → Yes → Approved (end)
--                |                                   → No  → Rejected (end)
--                → No  → Auto Approved (end)
--       → No  → Rejected (end)
-- =============================================================================

DO $bpmn$
DECLARE
    v_function_unit_id      BIGINT;
    v_request_form_id       BIGINT;
    v_approval_form_id      BIGINT;
    v_action_submit_id      BIGINT;
    v_action_approve_id     BIGINT;
    v_action_reject_id      BIGINT;
    v_action_transfer_id    BIGINT;
    v_action_delegate_id    BIGINT;
    v_action_approve1st_id  BIGINT;
    v_action_reject1st_id   BIGINT;
    v_bpmn_xml              TEXT;
    v_bpmn_b64              TEXT;
BEGIN
    SELECT id INTO v_function_unit_id FROM dw_function_units WHERE code = 'fu-20260403-a1b2c2';
    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit fu-20260403-a1b2c2 not found. Run 00-create-function-unit.sql first.';
    END IF;

    SELECT id INTO v_request_form_id    FROM dw_form_definitions   WHERE function_unit_id = v_function_unit_id AND form_name   = 'Request Form';
    SELECT id INTO v_approval_form_id   FROM dw_form_definitions   WHERE function_unit_id = v_function_unit_id AND form_name   = 'Approval Form';
    SELECT id INTO v_action_submit_id   FROM dw_action_definitions WHERE function_unit_id = v_function_unit_id AND action_name = 'Submit Request';
    SELECT id INTO v_action_approve_id  FROM dw_action_definitions WHERE function_unit_id = v_function_unit_id AND action_name = 'Approve';
    SELECT id INTO v_action_reject_id   FROM dw_action_definitions WHERE function_unit_id = v_function_unit_id AND action_name = 'Reject';
    SELECT id INTO v_action_transfer_id FROM dw_action_definitions WHERE function_unit_id = v_function_unit_id AND action_name = 'Transfer';
    SELECT id INTO v_action_delegate_id FROM dw_action_definitions WHERE function_unit_id = v_function_unit_id AND action_name = 'Delegate';
    SELECT id INTO v_action_approve1st_id FROM dw_action_definitions WHERE function_unit_id = v_function_unit_id AND action_name = 'Approve First';
    SELECT id INTO v_action_reject1st_id  FROM dw_action_definitions WHERE function_unit_id = v_function_unit_id AND action_name = 'Rejected First';

    IF v_request_form_id IS NULL OR v_approval_form_id IS NULL THEN
        RAISE EXCEPTION 'Forms not found. Run 00-create-function-unit.sql first.';
    END IF;
    IF v_action_submit_id IS NULL OR v_action_approve_id IS NULL OR v_action_reject_id IS NULL THEN
        RAISE EXCEPTION 'Actions not found. Run 00-create-function-unit.sql first.';
    END IF;

    -- Build BPMN XML with actual IDs substituted
    -- format() placeholders: %1$s=action_submit, %2$s=request_form, %3$s=approve1st, %4$s=reject1st,
    --   %5$s=approve, %6$s=reject, %7$s=transfer, %8$s=delegate, %9$s=approval_form
    v_bpmn_xml := format(
        $xml$<?xml version="1.0" encoding="UTF-8"?><bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:custom_1="http://custom.bpmn.io/schema" xmlns:custom="http://workflow.platform/schema/custom" id="Definitions_ProcurementWorkflow" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="ProcurementWorkflowProcess" name="Procurement Workflow" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="Task_SubmitRequest" name="Submit Request">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[%1$s]"/>
          <custom_1:values name="actionNames" value="[&amp;#34;Submit Request&amp;#34;]"/>
          
          
        <custom_1:property name="formId" value="%2$s"/><custom_1:property name="formName" value="Request Form"/></custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_1nxntxt</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:exclusiveGateway id="Gateway_PriceCheck" name="Total price &gt; 10000?">
      <bpmn:incoming>Flow_046turt</bpmn:incoming>
      <bpmn:outgoing>Flow_ToManagerReview</bpmn:outgoing>
      <bpmn:outgoing>Flow_AutoApproved</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:userTask id="Task_ManagerApproval" name="Manager Review">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[%5$s,%6$s,%7$s,%8$s]"/>
          <custom_1:values name="actionNames" value="[&amp;#34;Approve&amp;#34;,&amp;#34;Reject&amp;#34;,&amp;#34;Transfer&amp;#34;,&amp;#34;Delegate&amp;#34;]"/>
          <custom_1:values name="formId" value="%9$s"/>
          <custom_1:values name="formName" value="Approval Form"/>
        </custom_1:properties>
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
      <bpmn:incoming>Flow_1ps06v3</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_SubmitRequest"/>
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
    <bpmn:exclusiveGateway id="Gateway_1xfbezt" name="Approve">
      <bpmn:incoming>Flow_0plzqlv</bpmn:incoming>
      <bpmn:outgoing>Flow_1ps06v3</bpmn:outgoing>
      <bpmn:outgoing>Flow_1v3fea6</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:userTask id="Activity_1wf0eko" name="First Review">
      <bpmn:extensionElements>
        <custom_1:properties>
          
          
          <custom_1:values name="actionIds" value="[%3$s,%4$s]"/>
          <custom_1:values name="actionNames" value="[&quot;Approve First&quot;,&quot;Rejected First&quot;]"/>
          
          
        <custom_1:property name="formId" value="%2$s"/><custom_1:property name="formName" value="Request Form"/></custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1nxntxt</bpmn:incoming>
      <bpmn:outgoing>Flow_0plzqlv</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:sequenceFlow id="Flow_0plzqlv" sourceRef="Activity_1wf0eko" targetRef="Gateway_1xfbezt"/>
    <bpmn:sequenceFlow id="Flow_1nxntxt" sourceRef="Task_SubmitRequest" targetRef="Activity_1wf0eko"/>
    <bpmn:sequenceFlow id="Flow_1ps06v3" name="No" sourceRef="Gateway_1xfbezt" targetRef="EndEvent_Rejected">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${decision != 'yes'}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_1v3fea6" name="Yes" sourceRef="Gateway_1xfbezt" targetRef="Activity_1a5pnid">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="conditionType" value="script"/>
        </custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${decision == 'yes'}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:userTask id="Activity_1a5pnid" name="Second Review">
      <bpmn:extensionElements>
        
      <custom_1:properties><custom_1:property name="formId" value="%2$s"/><custom_1:property name="formName" value="Request Form"/></custom_1:properties></bpmn:extensionElements>
      <bpmn:incoming>Flow_1v3fea6</bpmn:incoming>
      <bpmn:outgoing>Flow_046turt</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:sequenceFlow id="Flow_046turt" sourceRef="Activity_1a5pnid" targetRef="Gateway_PriceCheck"/>
  </bpmn:process>$xml$,
        v_action_submit_id,     -- %1$s -> actionIds for Submit Request [22]
        v_request_form_id,      -- %2$s -> formId for Submit/FirstReview/SecondReview [11]
        v_action_approve1st_id, -- %3$s -> actionIds[0] for First Review [35]
        v_action_reject1st_id,  -- %4$s -> actionIds[1] for First Review [36]
        v_action_approve_id,    -- %5$s -> actionIds[0] for Manager Review [23]
        v_action_reject_id,     -- %6$s -> actionIds[1] for Manager Review [24]
        v_action_transfer_id,   -- %7$s -> actionIds[2] for Manager Review [33]
        v_action_delegate_id,   -- %8$s -> actionIds[3] for Manager Review [34]
        v_approval_form_id      -- %9$s -> formId for Manager Review [12]
    );

    -- Append BPMN diagram (static layout, no dynamic IDs)
    v_bpmn_xml := v_bpmn_xml || $xml$
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="ProcurementWorkflowProcess">
      <bpmndi:BPMNShape id="BPMNShape_1e8g875" bpmnElement="StartEvent_1">
        <dc:Bounds x="-50" y="150" width="36" height="36"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="-45" y="185" width="24" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_0o4rau3" bpmnElement="Task_SubmitRequest">
        <dc:Bounds x="40" y="130" width="120" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_PriceCheck" bpmnElement="Gateway_PriceCheck" isMarkerVisible="true">
        <dc:Bounds x="495" y="65" width="50" height="50"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="455" y="34" width="60" height="27"/>
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
          <dc:Bounds x="915.5" y="161" width="47" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_0gt3x3e" bpmnElement="EndEvent_Rejected">
        <dc:Bounds x="757" y="262" width="36" height="36"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="703" y="273" width="44" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_1xfbezt_di" bpmnElement="Gateway_1xfbezt" isMarkerVisible="true">
        <dc:Bounds x="335" y="145" width="50" height="50"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="393" y="163" width="41" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_14q8thw_di" bpmnElement="Activity_1wf0eko">
        <dc:Bounds x="200" y="130" width="100" height="80"/>
        <bpmndi:BPMNLabel/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_1mq0hvk_di" bpmnElement="Activity_1a5pnid">
        <dc:Bounds x="310" y="-20" width="100" height="80"/>
        <bpmndi:BPMNLabel/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="BPMNEdge_0ltq2fg" bpmnElement="Flow_1">
        <di:waypoint x="-14" y="168"/>
        <di:waypoint x="40" y="168"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_ToManagerReview" bpmnElement="Flow_ToManagerReview">
        <di:waypoint x="520" y="115"/>
        <di:waypoint x="520" y="168"/>
        <di:waypoint x="590" y="168"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="522" y="134" width="18" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_AutoApproved" bpmnElement="Flow_AutoApproved">
        <di:waypoint x="545" y="90"/>
        <di:waypoint x="888" y="90"/>
        <di:waypoint x="888" y="150"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="568" y="65" width="15" height="14"/>
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
      <bpmndi:BPMNEdge id="Flow_0plzqlv_di" bpmnElement="Flow_0plzqlv">
        <di:waypoint x="300" y="170"/>
        <di:waypoint x="335" y="170"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1nxntxt_di" bpmnElement="Flow_1nxntxt">
        <di:waypoint x="160" y="170"/>
        <di:waypoint x="200" y="170"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1ps06v3_di" bpmnElement="Flow_1ps06v3">
        <di:waypoint x="360" y="195"/>
        <di:waypoint x="360" y="410"/>
        <di:waypoint x="775" y="410"/>
        <di:waypoint x="775" y="298"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="561" y="392" width="15" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1v3fea6_di" bpmnElement="Flow_1v3fea6">
        <di:waypoint x="360" y="145"/>
        <di:waypoint x="360" y="60"/>
        <bpmndi:BPMNLabel>
          <dc:Bounds x="367" y="100" width="18" height="14"/>
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_046turt_di" bpmnElement="Flow_046turt">
        <di:waypoint x="410" y="20"/>
        <di:waypoint x="520" y="20"/>
        <di:waypoint x="520" y="65"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>$xml$;

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
    RAISE NOTICE 'Function Unit ID    : %', v_function_unit_id;
    RAISE NOTICE 'Request Form ID     : %', v_request_form_id;
    RAISE NOTICE 'Approval Form ID    : %', v_approval_form_id;
    RAISE NOTICE 'Submit Action ID    : %', v_action_submit_id;
    RAISE NOTICE 'Approve Action ID   : %', v_action_approve_id;
    RAISE NOTICE 'Reject Action ID    : %', v_action_reject_id;
    RAISE NOTICE 'Transfer Action ID  : %', v_action_transfer_id;
    RAISE NOTICE 'Delegate Action ID  : %', v_action_delegate_id;
    RAISE NOTICE 'Approve1st Action ID: %', v_action_approve1st_id;
    RAISE NOTICE 'Reject1st Action ID : %', v_action_reject1st_id;
    RAISE NOTICE 'Next: run 03-form-table-bindings.sql';
    RAISE NOTICE '========================================';

END $bpmn$;
