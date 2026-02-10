-- =============================================================================
-- Insert BPMN Process Definition for Digital Lending V2
-- =============================================================================
-- Must run AFTER 01-create-digital-lending-complete.sql
-- Reads the function unit ID, form IDs, and action IDs from the database,
-- then inserts the BPMN XML with all placeholders replaced.
--
-- NOTE: BPMN is stored as raw XML (not base64). The backend entity
-- ProcessDefinition.bpmnXml is a TEXT column storing raw XML.
-- =============================================================================

DO $main$
DECLARE
    v_function_unit_id BIGINT;
    v_application_form_id BIGINT;
    v_credit_check_form_id BIGINT;
    v_risk_assessment_form_id BIGINT;
    v_approval_form_id BIGINT;
    v_disbursement_form_id BIGINT;
    v_action_submit BIGINT;
    v_action_withdraw BIGINT;
    v_action_credit_check BIGINT;
    v_action_view_credit BIGINT;
    v_action_assess_risk BIGINT;
    v_action_approve BIGINT;
    v_action_reject BIGINT;
    v_action_request_info BIGINT;
    v_action_verify_docs BIGINT;
    v_action_disburse BIGINT;
    v_action_verify_account BIGINT;
    v_action_low_risk BIGINT;
    v_action_high_risk BIGINT;
    v_bpmn_xml TEXT;
BEGIN
    -- =========================================================================
    -- Step 1: Get function unit ID
    -- =========================================================================
    SELECT id INTO v_function_unit_id
    FROM dw_function_units
    WHERE code = 'DIGITAL_LENDING_V2_EN';

    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit DIGITAL_LENDING_V2_EN not found. Run 01-create-digital-lending-complete.sql first.';
    END IF;

    -- Check if process definition already exists
    IF EXISTS (SELECT 1 FROM dw_process_definitions WHERE function_unit_id = v_function_unit_id) THEN
        RAISE NOTICE 'Process definition already exists for DIGITAL_LENDING_V2_EN, skipping.';
        RETURN;
    END IF;

    -- =========================================================================
    -- Step 2: Get form IDs
    -- =========================================================================
    SELECT id INTO v_application_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_function_unit_id AND form_name = 'Loan Application Form';

    SELECT id INTO v_approval_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_function_unit_id AND form_name = 'Loan Approval Form';

    SELECT id INTO v_credit_check_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_function_unit_id AND form_name = 'Credit Check Form';

    SELECT id INTO v_risk_assessment_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_function_unit_id AND form_name = 'Risk Assessment Form';

    SELECT id INTO v_disbursement_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_function_unit_id AND form_name = 'Loan Disbursement Form';

    -- =========================================================================
    -- Step 3: Get action IDs
    -- =========================================================================
    SELECT id INTO v_action_submit FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Submit Application';

    SELECT id INTO v_action_withdraw FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Withdraw Application';

    SELECT id INTO v_action_credit_check FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Perform Credit Check';

    SELECT id INTO v_action_view_credit FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = 'View Credit Report';

    SELECT id INTO v_action_assess_risk FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Assess Risk';

    SELECT id INTO v_action_approve FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Approve';

    SELECT id INTO v_action_reject FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Reject';

    SELECT id INTO v_action_request_info FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Request Additional Info';

    SELECT id INTO v_action_verify_docs FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Verify Documents';

    SELECT id INTO v_action_disburse FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Process Disbursement';

    SELECT id INTO v_action_verify_account FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Verify Account';

    SELECT id INTO v_action_low_risk FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Mark as Low Risk';

    SELECT id INTO v_action_high_risk FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Mark as High Risk';

    -- Validate all IDs were found
    IF v_application_form_id IS NULL OR v_approval_form_id IS NULL OR
       v_credit_check_form_id IS NULL OR v_risk_assessment_form_id IS NULL OR
       v_disbursement_form_id IS NULL THEN
        RAISE EXCEPTION 'One or more form definitions not found. Ensure 01-create-digital-lending-complete.sql ran successfully.';
    END IF;

    IF v_action_submit IS NULL OR v_action_withdraw IS NULL OR
       v_action_credit_check IS NULL OR v_action_view_credit IS NULL OR
       v_action_assess_risk IS NULL OR v_action_approve IS NULL OR
       v_action_reject IS NULL OR v_action_request_info IS NULL OR
       v_action_verify_docs IS NULL OR v_action_disburse IS NULL OR
       v_action_verify_account IS NULL OR v_action_low_risk IS NULL OR
       v_action_high_risk IS NULL THEN
        RAISE EXCEPTION 'One or more action definitions not found. Ensure 01-create-digital-lending-complete.sql ran successfully.';
    END IF;

    -- =========================================================================
    -- Step 4: Build BPMN XML with placeholders
    -- =========================================================================
    -- Using dollar-quoting $bpmn$...$bpmn$ to safely embed XML
    v_bpmn_xml := $bpmn$<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:custom="http://workflow.platform/schema/custom"
  id="Definitions_DigitalLendingV2"
  targetNamespace="http://bpmn.io/schema/bpmn">
  
  <bpmn:process id="DigitalLendingProcessV2" name="Digital Lending Process V2" isExecutable="true">
    
    <!-- Start Event -->
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    
    <!-- Task 1: Submit Application -->
    <bpmn:userTask id="Task_SubmitApplication" name="Submit Loan Application">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="assigneeType" value="INITIATOR" />
          <custom:property name="formId" value="{{APPLICATION_FORM_ID}}" />
          <custom:property name="formName" value="Loan Application Form" />
          <custom:property name="formReadOnly" value="false" />
          <custom:property name="actionIds" value="[{{ACTION_SUBMIT}},{{ACTION_WITHDRAW}}]" />
          <custom:property name="actionNames" value="[&quot;Submit Application&quot;,&quot;Withdraw Application&quot;]" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:incoming>Flow_NeedMoreInfo</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- Task 2: Document Verification -->
    <bpmn:userTask id="Task_DocumentVerification" name="Document Verification">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="assigneeType" value="VIRTUAL_GROUP" />
          <custom:property name="assigneeValue" value="DOCUMENT_VERIFIERS" />
          <custom:property name="formId" value="{{APPROVAL_FORM_ID}}" />
          <custom:property name="formName" value="Loan Approval Form" />
          <custom:property name="formReadOnly" value="false" />
          <custom:property name="actionIds" value="[{{ACTION_VERIFY_DOCS}},{{ACTION_APPROVE}},{{ACTION_REJECT}}]" />
          <custom:property name="actionNames" value="[&quot;Verify Documents&quot;,&quot;Approve&quot;,&quot;Reject&quot;]" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_2</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- Gateway: Documents OK? -->
    <bpmn:exclusiveGateway id="Gateway_DocumentsOK" name="Documents OK?">
      <bpmn:incoming>Flow_3</bpmn:incoming>
      <bpmn:outgoing>Flow_DocsApproved</bpmn:outgoing>
      <bpmn:outgoing>Flow_DocsRejected</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    
    <!-- Task 3: Credit Check -->
    <bpmn:userTask id="Task_CreditCheck" name="Credit Check">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="assigneeType" value="VIRTUAL_GROUP" />
          <custom:property name="assigneeValue" value="CREDIT_OFFICERS" />
          <custom:property name="formId" value="{{APPROVAL_FORM_ID}}" />
          <custom:property name="formName" value="Loan Approval Form" />
          <custom:property name="formReadOnly" value="false" />
          <custom:property name="actionIds" value="[{{ACTION_CREDIT_CHECK}},{{ACTION_VIEW_CREDIT}},{{ACTION_APPROVE}},{{ACTION_REJECT}}]" />
          <custom:property name="actionNames" value="[&quot;Perform Credit Check&quot;,&quot;View Credit Report&quot;,&quot;Approve&quot;,&quot;Reject&quot;]" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_DocsApproved</bpmn:incoming>
      <bpmn:outgoing>Flow_4</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- Task 4: Risk Assessment -->
    <bpmn:userTask id="Task_RiskAssessment" name="Risk Assessment">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="assigneeType" value="VIRTUAL_GROUP" />
          <custom:property name="assigneeValue" value="RISK_OFFICERS" />
          <custom:property name="formId" value="{{APPROVAL_FORM_ID}}" />
          <custom:property name="formName" value="Loan Approval Form" />
          <custom:property name="formReadOnly" value="false" />
          <custom:property name="actionIds" value="[{{ACTION_ASSESS_RISK}},{{ACTION_LOW_RISK}},{{ACTION_HIGH_RISK}},{{ACTION_REQUEST_INFO}}]" />
          <custom:property name="actionNames" value="[&quot;Assess Risk&quot;,&quot;Mark as Low Risk&quot;,&quot;Mark as High Risk&quot;,&quot;Request Additional Info&quot;]" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_4</bpmn:incoming>
      <bpmn:outgoing>Flow_5</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- Gateway: Risk Acceptable? -->
    <bpmn:exclusiveGateway id="Gateway_RiskAcceptable" name="Risk Acceptable?">
      <bpmn:incoming>Flow_5</bpmn:incoming>
      <bpmn:outgoing>Flow_LowRisk</bpmn:outgoing>
      <bpmn:outgoing>Flow_HighRisk</bpmn:outgoing>
      <bpmn:outgoing>Flow_NeedMoreInfo</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    
    <!-- Task 5: Manager Approval -->
    <bpmn:userTask id="Task_ManagerApproval" name="Manager Approval">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="assigneeType" value="ENTITY_MANAGER" />
          <custom:property name="formId" value="{{APPROVAL_FORM_ID}}" />
          <custom:property name="formName" value="Loan Approval Form" />
          <custom:property name="formReadOnly" value="false" />
          <custom:property name="actionIds" value="[{{ACTION_APPROVE}},{{ACTION_REJECT}},{{ACTION_REQUEST_INFO}}]" />
          <custom:property name="actionNames" value="[&quot;Approve&quot;,&quot;Reject&quot;,&quot;Request Additional Info&quot;]" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_LowRisk</bpmn:incoming>
      <bpmn:outgoing>Flow_6</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- Gateway: Manager Decision -->
    <bpmn:exclusiveGateway id="Gateway_ManagerDecision" name="Manager Approved?">
      <bpmn:incoming>Flow_6</bpmn:incoming>
      <bpmn:outgoing>Flow_ManagerApproved</bpmn:outgoing>
      <bpmn:outgoing>Flow_ManagerRejected</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    
    <!-- Task 6: Senior Manager Approval -->
    <bpmn:userTask id="Task_SeniorManagerApproval" name="Senior Manager Approval">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="assigneeType" value="FUNCTION_MANAGER" />
          <custom:property name="formId" value="{{APPROVAL_FORM_ID}}" />
          <custom:property name="formName" value="Loan Approval Form" />
          <custom:property name="formReadOnly" value="false" />
          <custom:property name="actionIds" value="[{{ACTION_APPROVE}},{{ACTION_REJECT}}]" />
          <custom:property name="actionNames" value="[&quot;Approve&quot;,&quot;Reject&quot;]" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_ManagerApproved</bpmn:incoming>
      <bpmn:outgoing>Flow_7</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- Gateway: Senior Manager Decision -->
    <bpmn:exclusiveGateway id="Gateway_SeniorManagerDecision" name="Senior Manager Approved?">
      <bpmn:incoming>Flow_7</bpmn:incoming>
      <bpmn:outgoing>Flow_SeniorApproved</bpmn:outgoing>
      <bpmn:outgoing>Flow_SeniorRejected</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    
    <!-- Task 7: Loan Disbursement -->
    <bpmn:userTask id="Task_Disbursement" name="Process Disbursement">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="assigneeType" value="VIRTUAL_GROUP" />
          <custom:property name="assigneeValue" value="FINANCE_TEAM" />
          <custom:property name="formId" value="{{DISBURSEMENT_FORM_ID}}" />
          <custom:property name="formName" value="Loan Disbursement Form" />
          <custom:property name="formReadOnly" value="false" />
          <custom:property name="actionIds" value="[{{ACTION_DISBURSE}},{{ACTION_VERIFY_ACCOUNT}}]" />
          <custom:property name="actionNames" value="[&quot;Process Disbursement&quot;,&quot;Verify Account&quot;]" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_SeniorApproved</bpmn:incoming>
      <bpmn:outgoing>Flow_8</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- End Events -->
    <bpmn:endEvent id="EndEvent_Approved" name="Loan Disbursed">
      <bpmn:incoming>Flow_8</bpmn:incoming>
    </bpmn:endEvent>
    
    <bpmn:endEvent id="EndEvent_Rejected" name="Loan Rejected">
      <bpmn:incoming>Flow_DocsRejected</bpmn:incoming>
      <bpmn:incoming>Flow_HighRisk</bpmn:incoming>
      <bpmn:incoming>Flow_ManagerRejected</bpmn:incoming>
      <bpmn:incoming>Flow_SeniorRejected</bpmn:incoming>
    </bpmn:endEvent>
    
    <!-- Sequence Flows -->
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_SubmitApplication" />
    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_SubmitApplication" targetRef="Task_DocumentVerification" />
    <bpmn:sequenceFlow id="Flow_3" sourceRef="Task_DocumentVerification" targetRef="Gateway_DocumentsOK" />
    <bpmn:sequenceFlow id="Flow_DocsApproved" name="Yes" sourceRef="Gateway_DocumentsOK" targetRef="Task_CreditCheck" />
    <bpmn:sequenceFlow id="Flow_DocsRejected" name="No" sourceRef="Gateway_DocumentsOK" targetRef="EndEvent_Rejected" />
    <bpmn:sequenceFlow id="Flow_4" sourceRef="Task_CreditCheck" targetRef="Task_RiskAssessment" />
    <bpmn:sequenceFlow id="Flow_5" sourceRef="Task_RiskAssessment" targetRef="Gateway_RiskAcceptable" />
    <bpmn:sequenceFlow id="Flow_LowRisk" name="Low/Medium Risk" sourceRef="Gateway_RiskAcceptable" targetRef="Task_ManagerApproval" />
    <bpmn:sequenceFlow id="Flow_HighRisk" name="High Risk - Reject" sourceRef="Gateway_RiskAcceptable" targetRef="EndEvent_Rejected" />
    <bpmn:sequenceFlow id="Flow_NeedMoreInfo" name="Need More Info" sourceRef="Gateway_RiskAcceptable" targetRef="Task_SubmitApplication" />
    <bpmn:sequenceFlow id="Flow_6" sourceRef="Task_ManagerApproval" targetRef="Gateway_ManagerDecision" />
    <bpmn:sequenceFlow id="Flow_ManagerApproved" name="Yes" sourceRef="Gateway_ManagerDecision" targetRef="Task_SeniorManagerApproval" />
    <bpmn:sequenceFlow id="Flow_ManagerRejected" name="No" sourceRef="Gateway_ManagerDecision" targetRef="EndEvent_Rejected" />
    <bpmn:sequenceFlow id="Flow_7" sourceRef="Task_SeniorManagerApproval" targetRef="Gateway_SeniorManagerDecision" />
    <bpmn:sequenceFlow id="Flow_SeniorApproved" name="Yes" sourceRef="Gateway_SeniorManagerDecision" targetRef="Task_Disbursement" />
    <bpmn:sequenceFlow id="Flow_SeniorRejected" name="No" sourceRef="Gateway_SeniorManagerDecision" targetRef="EndEvent_Rejected" />
    <bpmn:sequenceFlow id="Flow_8" sourceRef="Task_Disbursement" targetRef="EndEvent_Approved" />
    
  </bpmn:process>
  
  <!-- BPMN Diagram -->
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="DigitalLendingProcessV2">
      
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="180" y="160" width="36" height="36" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="Task_SubmitApplication_di" bpmnElement="Task_SubmitApplication">
        <dc:Bounds x="280" y="138" width="100" height="80" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="Task_DocumentVerification_di" bpmnElement="Task_DocumentVerification">
        <dc:Bounds x="440" y="138" width="100" height="80" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="Gateway_DocumentsOK_di" bpmnElement="Gateway_DocumentsOK" isMarkerVisible="true">
        <dc:Bounds x="600" y="153" width="50" height="50" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="Task_CreditCheck_di" bpmnElement="Task_CreditCheck">
        <dc:Bounds x="720" y="138" width="100" height="80" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="Task_RiskAssessment_di" bpmnElement="Task_RiskAssessment">
        <dc:Bounds x="880" y="138" width="100" height="80" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="Gateway_RiskAcceptable_di" bpmnElement="Gateway_RiskAcceptable" isMarkerVisible="true">
        <dc:Bounds x="1040" y="153" width="50" height="50" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="Task_ManagerApproval_di" bpmnElement="Task_ManagerApproval">
        <dc:Bounds x="1160" y="138" width="100" height="80" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="Gateway_ManagerDecision_di" bpmnElement="Gateway_ManagerDecision" isMarkerVisible="true">
        <dc:Bounds x="1320" y="153" width="50" height="50" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="Task_SeniorManagerApproval_di" bpmnElement="Task_SeniorManagerApproval">
        <dc:Bounds x="1440" y="138" width="100" height="80" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="Gateway_SeniorManagerDecision_di" bpmnElement="Gateway_SeniorManagerDecision" isMarkerVisible="true">
        <dc:Bounds x="1600" y="153" width="50" height="50" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="Task_Disbursement_di" bpmnElement="Task_Disbursement">
        <dc:Bounds x="1720" y="138" width="100" height="80" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="EndEvent_Approved_di" bpmnElement="EndEvent_Approved">
        <dc:Bounds x="1880" y="160" width="36" height="36" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="EndEvent_Rejected_di" bpmnElement="EndEvent_Rejected">
        <dc:Bounds x="1047" y="320" width="36" height="36" />
      </bpmndi:BPMNShape>
      
      <!-- Edges -->
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <di:waypoint x="216" y="178" />
        <di:waypoint x="280" y="178" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2">
        <di:waypoint x="380" y="178" />
        <di:waypoint x="440" y="178" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_3_di" bpmnElement="Flow_3">
        <di:waypoint x="540" y="178" />
        <di:waypoint x="600" y="178" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_DocsApproved_di" bpmnElement="Flow_DocsApproved">
        <di:waypoint x="650" y="178" />
        <di:waypoint x="720" y="178" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_DocsRejected_di" bpmnElement="Flow_DocsRejected">
        <di:waypoint x="625" y="203" />
        <di:waypoint x="625" y="338" />
        <di:waypoint x="1047" y="338" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_4_di" bpmnElement="Flow_4">
        <di:waypoint x="820" y="178" />
        <di:waypoint x="880" y="178" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_5_di" bpmnElement="Flow_5">
        <di:waypoint x="980" y="178" />
        <di:waypoint x="1040" y="178" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_LowRisk_di" bpmnElement="Flow_LowRisk">
        <di:waypoint x="1090" y="178" />
        <di:waypoint x="1160" y="178" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_HighRisk_di" bpmnElement="Flow_HighRisk">
        <di:waypoint x="1065" y="203" />
        <di:waypoint x="1065" y="320" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_NeedMoreInfo_di" bpmnElement="Flow_NeedMoreInfo">
        <di:waypoint x="1065" y="153" />
        <di:waypoint x="1065" y="80" />
        <di:waypoint x="330" y="80" />
        <di:waypoint x="330" y="138" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_6_di" bpmnElement="Flow_6">
        <di:waypoint x="1260" y="178" />
        <di:waypoint x="1320" y="178" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_ManagerApproved_di" bpmnElement="Flow_ManagerApproved">
        <di:waypoint x="1370" y="178" />
        <di:waypoint x="1440" y="178" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_ManagerRejected_di" bpmnElement="Flow_ManagerRejected">
        <di:waypoint x="1345" y="203" />
        <di:waypoint x="1345" y="338" />
        <di:waypoint x="1083" y="338" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_7_di" bpmnElement="Flow_7">
        <di:waypoint x="1540" y="178" />
        <di:waypoint x="1600" y="178" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_SeniorApproved_di" bpmnElement="Flow_SeniorApproved">
        <di:waypoint x="1650" y="178" />
        <di:waypoint x="1720" y="178" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_SeniorRejected_di" bpmnElement="Flow_SeniorRejected">
        <di:waypoint x="1625" y="203" />
        <di:waypoint x="1625" y="338" />
        <di:waypoint x="1083" y="338" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_8_di" bpmnElement="Flow_8">
        <di:waypoint x="1820" y="178" />
        <di:waypoint x="1880" y="178" />
      </bpmndi:BPMNEdge>
      
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>$bpmn$;

    -- =========================================================================
    -- Step 5: Replace placeholders with actual IDs
    -- =========================================================================
    v_bpmn_xml := replace(v_bpmn_xml, '{{APPLICATION_FORM_ID}}', v_application_form_id::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{APPROVAL_FORM_ID}}', v_approval_form_id::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{CREDIT_CHECK_FORM_ID}}', v_credit_check_form_id::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{RISK_ASSESSMENT_FORM_ID}}', v_risk_assessment_form_id::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{DISBURSEMENT_FORM_ID}}', v_disbursement_form_id::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_SUBMIT}}', v_action_submit::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_WITHDRAW}}', v_action_withdraw::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_CREDIT_CHECK}}', v_action_credit_check::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_VIEW_CREDIT}}', v_action_view_credit::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_ASSESS_RISK}}', v_action_assess_risk::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_APPROVE}}', v_action_approve::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_REJECT}}', v_action_reject::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_REQUEST_INFO}}', v_action_request_info::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_VERIFY_DOCS}}', v_action_verify_docs::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_DISBURSE}}', v_action_disburse::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_VERIFY_ACCOUNT}}', v_action_verify_account::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_LOW_RISK}}', v_action_low_risk::text);
    v_bpmn_xml := replace(v_bpmn_xml, '{{ACTION_HIGH_RISK}}', v_action_high_risk::text);

    -- =========================================================================
    -- Step 6: Insert process definition
    -- =========================================================================
    INSERT INTO dw_process_definitions (
        function_unit_id, bpmn_xml, created_at, updated_at
    ) VALUES (
        v_function_unit_id, v_bpmn_xml, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

    RAISE NOTICE '========================================';
    RAISE NOTICE 'BPMN Process Definition inserted successfully!';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Function Unit ID: %', v_function_unit_id;
    RAISE NOTICE 'Form IDs used:';
    RAISE NOTICE '  APPLICATION_FORM_ID: %', v_application_form_id;
    RAISE NOTICE '  APPROVAL_FORM_ID: %', v_approval_form_id;
    RAISE NOTICE '  CREDIT_CHECK_FORM_ID: %', v_credit_check_form_id;
    RAISE NOTICE '  RISK_ASSESSMENT_FORM_ID: %', v_risk_assessment_form_id;
    RAISE NOTICE '  DISBURSEMENT_FORM_ID: %', v_disbursement_form_id;
    RAISE NOTICE 'Action IDs used: 13 actions bound to BPMN nodes';
    RAISE NOTICE '========================================';

END $main$;
