-- =============================================================================
-- Insert BPMN Process Definition for Simple Approval
-- Exported from database on 2026-02-12
-- =============================================================================

DO $main$
DECLARE
    v_function_unit_id BIGINT;
    v_request_form_id BIGINT;
    v_approval_form_id BIGINT;
    v_action_submit BIGINT;
    v_action_approve BIGINT;
    v_action_reject BIGINT;
    v_bpmn_xml TEXT;
BEGIN
    -- =========================================================================
    -- Step 1: Get function unit ID
    -- =========================================================================
    SELECT id INTO v_function_unit_id
    FROM dw_function_units
    WHERE code = 'SIMPLE_APPROVAL';

    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit SIMPLE_APPROVAL not found. Run 00-create-simple-approval.sql first.';
    END IF;

    -- Check if process definition already exists
    IF EXISTS (SELECT 1 FROM dw_process_definitions WHERE function_unit_id = v_function_unit_id) THEN
        RAISE NOTICE 'Process definition already exists for SIMPLE_APPROVAL, updating...';
        
        -- Get form and action IDs
        SELECT id INTO v_request_form_id FROM dw_form_definitions
        WHERE function_unit_id = v_function_unit_id AND form_name = 'Request Form';

        SELECT id INTO v_approval_form_id FROM dw_form_definitions
        WHERE function_unit_id = v_function_unit_id AND form_name = 'Approval Form';

        SELECT id INTO v_action_submit FROM dw_action_definitions
        WHERE function_unit_id = v_function_unit_id AND action_name = 'Submit Request';

        SELECT id INTO v_action_approve FROM dw_action_definitions
        WHERE function_unit_id = v_function_unit_id AND action_name = 'Approve';

        SELECT id INTO v_action_reject FROM dw_action_definitions
        WHERE function_unit_id = v_function_unit_id AND action_name = 'Reject';

        -- Build BPMN XML with actual IDs
        v_bpmn_xml := $bpmn$<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:custom_1="http://custom.bpmn.io/schema" xmlns:custom="http://workflow.platform/schema/custom" id="Definitions_SimpleApproval" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="SimpleApprovalProcess" name="Simple Approval Process" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="Task_SubmitRequest" name="Submit Request">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[{{ACTION_SUBMIT}}]" />
          <custom_1:values name="actionNames" value="[&#34;Submit Request&#34;]" />
          <custom_1:values name="formId" value="{{REQUEST_FORM_ID}}" />
          <custom_1:values name="formName" value="Request Form" />
        </custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:userTask id="Task_ManagerApproval" name="Manager Approval">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[{{ACTION_APPROVE}},{{ACTION_REJECT}}]" />
          <custom_1:values name="actionNames" value="[&#34;Approve&#34;,&#34;Reject&#34;]" />
          <custom_1:values name="formId" value="{{REQUEST_FORM_ID}}" />
          <custom_1:values name="formName" value="Request Form" />
          <custom_1:values name="formReadOnly" value="true" />
        </custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_2</bpmn:incoming>
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
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_SubmitRequest" />
    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_SubmitRequest" targetRef="Task_ManagerApproval" />
    <bpmn:sequenceFlow id="Flow_3" sourceRef="Task_ManagerApproval" targetRef="Gateway_ManagerDecision" />
    <bpmn:sequenceFlow id="Flow_Approved" name="Yes" sourceRef="Gateway_ManagerDecision" targetRef="EndEvent_Approved">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${decision == 'yes'}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_Rejected" name="No" sourceRef="Gateway_ManagerDecision" targetRef="EndEvent_Rejected">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${decision == 'no'}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="SimpleApprovalProcess">
      <bpmndi:BPMNShape id="BPMNShape_1e8g875" bpmnElement="StartEvent_1">
        <dc:Bounds x="162" y="102" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="168" y="138" width="24" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_0o4rau3" bpmnElement="Task_SubmitRequest">
        <dc:Bounds x="262" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_0o9yima" bpmnElement="Task_ManagerApproval">
        <dc:Bounds x="422" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_07neaj5" bpmnElement="Gateway_ManagerDecision" isMarkerVisible="true">
        <dc:Bounds x="582" y="95" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="581" y="145" width="53" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_1vyg15b" bpmnElement="EndEvent_Approved">
        <dc:Bounds x="702" y="102" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="697" y="138" width="47" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_0gt3x3e" bpmnElement="EndEvent_Rejected">
        <dc:Bounds x="589" y="222" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="585" y="258" width="44" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="BPMNEdge_0ltq2fg" bpmnElement="Flow_1">
        <di:waypoint x="198" y="120" />
        <di:waypoint x="262" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_1e8h2d6" bpmnElement="Flow_2">
        <di:waypoint x="362" y="120" />
        <di:waypoint x="422" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_1udmyi1" bpmnElement="Flow_3">
        <di:waypoint x="522" y="120" />
        <di:waypoint x="582" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_0mqn3a0" bpmnElement="Flow_Approved">
        <di:waypoint x="632" y="120" />
        <di:waypoint x="702" y="120" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="658" y="95" width="18" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_03qyogm" bpmnElement="Flow_Rejected">
        <di:waypoint x="607" y="145" />
        <di:waypoint x="607" y="222" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="615" y="174" width="15" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>$bpmn$;

        -- Replace placeholders with actual IDs
        v_bpmn_xml := replace(v_bpmn_xml, '{{REQUEST_FORM_ID}}', v_request_form_id::text);
        v_bpmn_xml := replace(v_bpmn_xml, '{{APPROVAL_FORM_ID}}', v_approval_form_id::text);
        v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_SUBMIT}}', v_action_submit::text);
        v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_APPROVE}}', v_action_approve::text);
        v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_REJECT}}', v_action_reject::text);

        -- Update existing process definition
        UPDATE dw_process_definitions
        SET bpmn_xml = v_bpmn_xml,
            updated_at = CURRENT_TIMESTAMP
        WHERE function_unit_id = v_function_unit_id;

        RAISE NOTICE 'Process definition updated successfully!';
        RETURN;
    END IF;

    -- =========================================================================
    -- Step 2: Get form IDs
    -- =========================================================================
    SELECT id INTO v_request_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_function_unit_id AND form_name = 'Request Form';

    SELECT id INTO v_approval_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_function_unit_id AND form_name = 'Approval Form';

    -- =========================================================================
    -- Step 3: Get action IDs
    -- =========================================================================
    SELECT id INTO v_action_submit FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Submit Request';

    SELECT id INTO v_action_approve FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Approve';

    SELECT id INTO v_action_reject FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Reject';

    -- Validate all IDs were found
    IF v_request_form_id IS NULL OR v_approval_form_id IS NULL THEN
        RAISE EXCEPTION 'One or more form definitions not found.';
    END IF;

    IF v_action_submit IS NULL OR v_action_approve IS NULL OR v_action_reject IS NULL THEN
        RAISE EXCEPTION 'One or more action definitions not found.';
    END IF;

    -- =========================================================================
    -- Step 4: Build BPMN XML
    -- =========================================================================
    v_bpmn_xml := $bpmn$<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:custom_1="http://custom.bpmn.io/schema" xmlns:custom="http://workflow.platform/schema/custom" id="Definitions_SimpleApproval" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="SimpleApprovalProcess" name="Simple Approval Process" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="Task_SubmitRequest" name="Submit Request">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[{{ACTION_SUBMIT}}]" />
          <custom_1:values name="actionNames" value="[&#34;Submit Request&#34;]" />
          <custom_1:values name="formId" value="{{REQUEST_FORM_ID}}" />
          <custom_1:values name="formName" value="Request Form" />
        </custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:userTask id="Task_ManagerApproval" name="Manager Approval">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[{{ACTION_APPROVE}},{{ACTION_REJECT}}]" />
          <custom_1:values name="actionNames" value="[&#34;Approve&#34;,&#34;Reject&#34;]" />
          <custom_1:values name="formId" value="{{REQUEST_FORM_ID}}" />
          <custom_1:values name="formName" value="Request Form" />
          <custom_1:values name="formReadOnly" value="true" />
        </custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_2</bpmn:incoming>
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
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_SubmitRequest" />
    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_SubmitRequest" targetRef="Task_ManagerApproval" />
    <bpmn:sequenceFlow id="Flow_3" sourceRef="Task_ManagerApproval" targetRef="Gateway_ManagerDecision" />
    <bpmn:sequenceFlow id="Flow_Approved" name="Yes" sourceRef="Gateway_ManagerDecision" targetRef="EndEvent_Approved">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${decision == 'yes'}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_Rejected" name="No" sourceRef="Gateway_ManagerDecision" targetRef="EndEvent_Rejected">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${decision == 'no'}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="SimpleApprovalProcess">
      <bpmndi:BPMNShape id="BPMNShape_1e8g875" bpmnElement="StartEvent_1">
        <dc:Bounds x="162" y="102" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="168" y="138" width="24" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_0o4rau3" bpmnElement="Task_SubmitRequest">
        <dc:Bounds x="262" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_0o9yima" bpmnElement="Task_ManagerApproval">
        <dc:Bounds x="422" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_07neaj5" bpmnElement="Gateway_ManagerDecision" isMarkerVisible="true">
        <dc:Bounds x="582" y="95" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="581" y="145" width="53" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_1vyg15b" bpmnElement="EndEvent_Approved">
        <dc:Bounds x="702" y="102" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="697" y="138" width="47" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_0gt3x3e" bpmnElement="EndEvent_Rejected">
        <dc:Bounds x="589" y="222" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="585" y="258" width="44" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="BPMNEdge_0ltq2fg" bpmnElement="Flow_1">
        <di:waypoint x="198" y="120" />
        <di:waypoint x="262" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_1e8h2d6" bpmnElement="Flow_2">
        <di:waypoint x="362" y="120" />
        <di:waypoint x="422" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_1udmyi1" bpmnElement="Flow_3">
        <di:waypoint x="522" y="120" />
        <di:waypoint x="582" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_0mqn3a0" bpmnElement="Flow_Approved">
        <di:waypoint x="632" y="120" />
        <di:waypoint x="702" y="120" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="658" y="95" width="18" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="BPMNEdge_03qyogm" bpmnElement="Flow_Rejected">
        <di:waypoint x="607" y="145" />
        <di:waypoint x="607" y="222" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="615" y="174" width="15" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>$bpmn$;

    -- =========================================================================
    -- Step 5: Replace placeholders with actual IDs
    -- =========================================================================
    v_bpmn_xml := replace(v_bpmn_xml, '{{REQUEST_FORM_ID}}', v_request_form_id::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{APPROVAL_FORM_ID}}', v_approval_form_id::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_SUBMIT}}', v_action_submit::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_APPROVE}}', v_action_approve::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_REJECT}}', v_action_reject::text);

    -- =========================================================================
    -- Step 6: Insert process definition
    -- =========================================================================
    INSERT INTO dw_process_definitions (
        function_unit_id, function_unit_version_id, bpmn_xml, created_at, updated_at
    ) VALUES (
        v_function_unit_id, v_function_unit_id, v_bpmn_xml, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

    RAISE NOTICE '========================================';
    RAISE NOTICE 'BPMN Process Definition inserted successfully!';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Function Unit ID: %', v_function_unit_id;
    RAISE NOTICE 'Request Form ID: %', v_request_form_id;
    RAISE NOTICE 'Approval Form ID: %', v_approval_form_id;
    RAISE NOTICE 'Action IDs: Submit=%, Approve=%, Reject=%', v_action_submit, v_action_approve, v_action_reject;
    RAISE NOTICE '========================================';

END $main$;
