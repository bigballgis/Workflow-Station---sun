-- =============================================================================
-- Complete Demo: Employee Leave Management System
-- Creates a fully functional leave management system with all components
-- =============================================================================

DO $$
DECLARE
    v_function_unit_id BIGINT;
    v_main_table_id BIGINT;
    v_detail_table_id BIGINT;
    v_approval_table_id BIGINT;
    v_apply_form_id BIGINT;
    v_approve_form_id BIGINT;
    v_process_id BIGINT;
BEGIN
    -- =============================================================================
    -- 1. Get Function Unit ID
    -- =============================================================================
    SELECT id INTO v_function_unit_id 
    FROM public.dw_function_units 
    WHERE code = 'LEAVE_MGMT';

    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit LEAVE_MGMT not found. Please run 02-create-simple-demo.sql first.';
    END IF;

    RAISE NOTICE 'Creating demo for Function Unit ID: %', v_function_unit_id;

    -- =============================================================================
    -- 2. Create Tables
    -- =============================================================================
    
    -- 2.1 Main Table: Leave Request
    INSERT INTO public.dw_table_definitions (
        function_unit_id,
        table_name,
        table_type,
        description
    ) VALUES (
        v_function_unit_id,
        'Leave Request',
        'MAIN',
        'Main table for employee leave requests'
    ) RETURNING id INTO v_main_table_id;

    RAISE NOTICE 'Created main table with ID: %', v_main_table_id;

    -- Main table fields
    INSERT INTO public.dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_main_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, 'Primary key', 1),
    (v_main_table_id, 'request_number', 'VARCHAR', 50, NULL, FALSE, 'Unique request number', 2),
    (v_main_table_id, 'employee_name', 'VARCHAR', 100, NULL, FALSE, 'Name of employee', 3),
    (v_main_table_id, 'employee_id', 'VARCHAR', 20, NULL, FALSE, 'Employee ID', 4),
    (v_main_table_id, 'department', 'VARCHAR', 100, NULL, FALSE, 'Department', 5),
    (v_main_table_id, 'leave_type', 'VARCHAR', 50, NULL, FALSE, 'Leave type (Annual/Sick/Personal)', 6),
    (v_main_table_id, 'start_date', 'DATE', NULL, NULL, FALSE, 'Leave start date', 7),
    (v_main_table_id, 'end_date', 'DATE', NULL, NULL, FALSE, 'Leave end date', 8),
    (v_main_table_id, 'total_days', 'DECIMAL', 10, 1, FALSE, 'Total leave days', 9),
    (v_main_table_id, 'reason', 'TEXT', NULL, NULL, FALSE, 'Reason for leave', 10),
    (v_main_table_id, 'emergency_contact', 'VARCHAR', 100, NULL, TRUE, 'Emergency contact', 11),
    (v_main_table_id, 'emergency_phone', 'VARCHAR', 20, NULL, TRUE, 'Emergency phone', 12),
    (v_main_table_id, 'status', 'VARCHAR', 20, NULL, FALSE, 'Request status', 13),
    (v_main_table_id, 'submit_date', 'TIMESTAMP', NULL, NULL, TRUE, 'Submit date', 14),
    (v_main_table_id, 'manager_approval', 'VARCHAR', 20, NULL, TRUE, 'Manager approval status', 15),
    (v_main_table_id, 'hr_approval', 'VARCHAR', 20, NULL, TRUE, 'HR approval status', 16),
    (v_main_table_id, 'final_status', 'VARCHAR', 20, NULL, TRUE, 'Final status', 17),
    (v_main_table_id, 'created_at', 'TIMESTAMP', NULL, NULL, FALSE, 'Created timestamp', 18),
    (v_main_table_id, 'updated_at', 'TIMESTAMP', NULL, NULL, TRUE, 'Updated timestamp', 19);

    -- 2.2 Sub Table: Leave Details
    INSERT INTO public.dw_table_definitions (
        function_unit_id,
        table_name,
        table_type,
        description
    ) VALUES (
        v_function_unit_id,
        'Leave Details',
        'SUB',
        'Detailed breakdown of leave days'
    ) RETURNING id INTO v_detail_table_id;

    RAISE NOTICE 'Created detail table with ID: %', v_detail_table_id;

    -- Detail table fields
    INSERT INTO public.dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_detail_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, 'Primary key', 1),
    (v_detail_table_id, 'leave_request_id', 'BIGINT', NULL, NULL, FALSE, 'Foreign key to leave request', 2),
    (v_detail_table_id, 'leave_date', 'DATE', NULL, NULL, FALSE, 'Specific date of leave', 3),
    (v_detail_table_id, 'day_type', 'VARCHAR', 20, NULL, FALSE, 'Full day or half day', 4),
    (v_detail_table_id, 'hours', 'DECIMAL', 5, 2, FALSE, 'Number of hours', 5),
    (v_detail_table_id, 'notes', 'VARCHAR', 500, NULL, TRUE, 'Additional notes', 6);

    -- 2.3 Related Table: Approval Records
    INSERT INTO public.dw_table_definitions (
        function_unit_id,
        table_name,
        table_type,
        description
    ) VALUES (
        v_function_unit_id,
        'Approval Records',
        'RELATION',
        'History of all approval actions'
    ) RETURNING id INTO v_approval_table_id;

    RAISE NOTICE 'Created approval table with ID: %', v_approval_table_id;

    -- Approval records fields
    INSERT INTO public.dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_approval_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, 'Primary key', 1),
    (v_approval_table_id, 'leave_request_id', 'BIGINT', NULL, NULL, FALSE, 'Foreign key to leave request', 2),
    (v_approval_table_id, 'approver_name', 'VARCHAR', 100, NULL, FALSE, 'Name of approver', 3),
    (v_approval_table_id, 'approver_role', 'VARCHAR', 50, NULL, FALSE, 'Role of approver', 4),
    (v_approval_table_id, 'action', 'VARCHAR', 20, NULL, FALSE, 'Approval action', 5),
    (v_approval_table_id, 'action_date', 'TIMESTAMP', NULL, NULL, FALSE, 'Date of action', 6),
    (v_approval_table_id, 'comments', 'TEXT', NULL, NULL, TRUE, 'Approver comments', 7),
    (v_approval_table_id, 'decision', 'VARCHAR', 20, NULL, FALSE, 'Approve or Reject', 8);

    -- =============================================================================
    -- 3. Create Forms
    -- =============================================================================
    
    -- 3.1 Application Form
    INSERT INTO public.dw_form_definitions (
        function_unit_id,
        form_name,
        form_type,
        config_json,
        description,
        bound_table_id
    ) VALUES (
        v_function_unit_id,
        'Leave Application Form',
        'MAIN',
        '{
            "layout": "vertical",
            "labelWidth": "120px",
            "size": "default"
        }'::jsonb,
        'Form for employees to submit leave requests',
        v_main_table_id
    ) RETURNING id INTO v_apply_form_id;

    RAISE NOTICE 'Created application form with ID: %', v_apply_form_id;

    -- Bind tables to application form
    INSERT INTO public.dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, sort_order
    ) VALUES
    (v_apply_form_id, v_main_table_id, 'PRIMARY', 'EDITABLE', 1),
    (v_apply_form_id, v_detail_table_id, 'SUB', 'EDITABLE', 2);

    -- 3.2 Approval Form
    INSERT INTO public.dw_form_definitions (
        function_unit_id,
        form_name,
        form_type,
        config_json,
        description,
        bound_table_id
    ) VALUES (
        v_function_unit_id,
        'Leave Approval Form',
        'MAIN',
        '{
            "layout": "vertical",
            "labelWidth": "120px",
            "size": "default",
            "readonly": false
        }'::jsonb,
        'Form for managers and HR to approve leave requests',
        v_main_table_id
    ) RETURNING id INTO v_approve_form_id;

    RAISE NOTICE 'Created approval form with ID: %', v_approve_form_id;

    -- Bind tables to approval form
    INSERT INTO public.dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, sort_order
    ) VALUES
    (v_approve_form_id, v_main_table_id, 'PRIMARY', 'READONLY', 1),
    (v_approve_form_id, v_approval_table_id, 'RELATED', 'EDITABLE', 2);

    -- =============================================================================
    -- 4. Create Process Definition
    -- =============================================================================
    
    INSERT INTO public.dw_process_definitions (
        function_unit_id,
        bpmn_xml
    ) VALUES (
        v_function_unit_id,
        '<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:custom="http://workflow.platform/schema/custom"
  id="Definitions_LeaveApproval"
  targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="LeaveApprovalProcess" name="Leave Approval Process" isExecutable="true">
    
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    
    <bpmn:userTask id="Task_Submit" name="Submit Leave Application">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="' || v_apply_form_id || '" />
          <custom:property name="formName" value="Leave Application Form" />
          <custom:property name="formReadOnly" value="false" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:userTask id="Task_ManagerApproval" name="Manager Approval">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="' || v_approve_form_id || '" />
          <custom:property name="formName" value="Leave Approval Form" />
          <custom:property name="formReadOnly" value="false" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_2</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:exclusiveGateway id="Gateway_ManagerDecision" name="Manager Approved?">
      <bpmn:incoming>Flow_3</bpmn:incoming>
      <bpmn:outgoing>Flow_Approved</bpmn:outgoing>
      <bpmn:outgoing>Flow_Rejected</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    
    <bpmn:userTask id="Task_HRApproval" name="HR Approval">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="' || v_approve_form_id || '" />
          <custom:property name="formName" value="Leave Approval Form" />
          <custom:property name="formReadOnly" value="false" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_Approved</bpmn:incoming>
      <bpmn:outgoing>Flow_4</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:exclusiveGateway id="Gateway_HRDecision" name="HR Approved?">
      <bpmn:incoming>Flow_4</bpmn:incoming>
      <bpmn:outgoing>Flow_FinalApproved</bpmn:outgoing>
      <bpmn:outgoing>Flow_FinalRejected</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    
    <bpmn:endEvent id="EndEvent_Approved" name="Approved">
      <bpmn:incoming>Flow_FinalApproved</bpmn:incoming>
    </bpmn:endEvent>
    
    <bpmn:endEvent id="EndEvent_Rejected" name="Rejected">
      <bpmn:incoming>Flow_Rejected</bpmn:incoming>
      <bpmn:incoming>Flow_FinalRejected</bpmn:incoming>
    </bpmn:endEvent>
    
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_Submit" />
    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_Submit" targetRef="Task_ManagerApproval" />
    <bpmn:sequenceFlow id="Flow_3" sourceRef="Task_ManagerApproval" targetRef="Gateway_ManagerDecision" />
    <bpmn:sequenceFlow id="Flow_Approved" name="Yes" sourceRef="Gateway_ManagerDecision" targetRef="Task_HRApproval" />
    <bpmn:sequenceFlow id="Flow_Rejected" name="No" sourceRef="Gateway_ManagerDecision" targetRef="EndEvent_Rejected" />
    <bpmn:sequenceFlow id="Flow_4" sourceRef="Task_HRApproval" targetRef="Gateway_HRDecision" />
    <bpmn:sequenceFlow id="Flow_FinalApproved" name="Yes" sourceRef="Gateway_HRDecision" targetRef="EndEvent_Approved" />
    <bpmn:sequenceFlow id="Flow_FinalRejected" name="No" sourceRef="Gateway_HRDecision" targetRef="EndEvent_Rejected" />
    
  </bpmn:process>
  
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="LeaveApprovalProcess">
      
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="180" y="160" width="36" height="36" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="Task_Submit_di" bpmnElement="Task_Submit">
        <dc:Bounds x="280" y="138" width="100" height="80" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="Task_ManagerApproval_di" bpmnElement="Task_ManagerApproval">
        <dc:Bounds x="440" y="138" width="100" height="80" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="Gateway_ManagerDecision_di" bpmnElement="Gateway_ManagerDecision" isMarkerVisible="true">
        <dc:Bounds x="600" y="153" width="50" height="50" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="Task_HRApproval_di" bpmnElement="Task_HRApproval">
        <dc:Bounds x="720" y="138" width="100" height="80" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="Gateway_HRDecision_di" bpmnElement="Gateway_HRDecision" isMarkerVisible="true">
        <dc:Bounds x="880" y="153" width="50" height="50" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="EndEvent_Approved_di" bpmnElement="EndEvent_Approved">
        <dc:Bounds x="1000" y="160" width="36" height="36" />
      </bpmndi:BPMNShape>
      
      <bpmndi:BPMNShape id="EndEvent_Rejected_di" bpmnElement="EndEvent_Rejected">
        <dc:Bounds x="887" y="280" width="36" height="36" />
      </bpmndi:BPMNShape>
      
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
      
      <bpmndi:BPMNEdge id="Flow_Approved_di" bpmnElement="Flow_Approved">
        <di:waypoint x="650" y="178" />
        <di:waypoint x="720" y="178" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_Rejected_di" bpmnElement="Flow_Rejected">
        <di:waypoint x="625" y="203" />
        <di:waypoint x="625" y="298" />
        <di:waypoint x="887" y="298" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_4_di" bpmnElement="Flow_4">
        <di:waypoint x="820" y="178" />
        <di:waypoint x="880" y="178" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_FinalApproved_di" bpmnElement="Flow_FinalApproved">
        <di:waypoint x="930" y="178" />
        <di:waypoint x="1000" y="178" />
      </bpmndi:BPMNEdge>
      
      <bpmndi:BPMNEdge id="Flow_FinalRejected_di" bpmnElement="Flow_FinalRejected">
        <di:waypoint x="905" y="203" />
        <di:waypoint x="905" y="280" />
      </bpmndi:BPMNEdge>
      
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>'
    ) RETURNING id INTO v_process_id;

    RAISE NOTICE 'Created process with ID: %', v_process_id;

    -- =============================================================================
    -- 5. Create Actions
    -- =============================================================================
    
    INSERT INTO public.dw_action_definitions (
        function_unit_id,
        action_name,
        action_type,
        description,
        config_json,
        icon,
        button_color
    ) VALUES
    (v_function_unit_id, 'Submit Leave Request', 'PROCESS_SUBMIT', 'Submit a new leave request for approval', 
     '{"requireComment": false, "confirmMessage": "Submit this leave request?"}'::jsonb, 
     'Upload', 'primary'),
    (v_function_unit_id, 'Approve Leave Request', 'APPROVE', 'Approve a pending leave request', 
     '{"targetStatus": "APPROVED", "requireComment": true, "confirmMessage": "Approve this leave request?"}'::jsonb, 
     'Check', 'success'),
    (v_function_unit_id, 'Reject Leave Request', 'REJECT', 'Reject a pending leave request', 
     '{"targetStatus": "REJECTED", "requireComment": true, "confirmMessage": "Reject this leave request?"}'::jsonb, 
     'Close', 'danger'),
    (v_function_unit_id, 'Withdraw Leave Request', 'WITHDRAW', 'Withdraw a submitted leave request', 
     '{"targetStatus": "WITHDRAWN", "allowedFromStatus": ["PENDING", "IN_PROGRESS"]}'::jsonb, 
     'RefreshLeft', 'warning'),
    (v_function_unit_id, 'Query Leave Requests', 'API_CALL', 'Query leave requests with filters', 
     '{"url": "/api/leave-management/requests", "method": "GET"}'::jsonb, 
     'Search', 'info');

    RAISE NOTICE 'Created 5 actions';

    -- =============================================================================
    -- Summary
    -- =============================================================================
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Demo Leave Management System Created!';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Function Unit ID: %', v_function_unit_id;
    RAISE NOTICE 'Main Table ID: %', v_main_table_id;
    RAISE NOTICE 'Detail Table ID: %', v_detail_table_id;
    RAISE NOTICE 'Approval Table ID: %', v_approval_table_id;
    RAISE NOTICE 'Application Form ID: %', v_apply_form_id;
    RAISE NOTICE 'Approval Form ID: %', v_approve_form_id;
    RAISE NOTICE 'Process ID: %', v_process_id;
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Access Developer Workstation at http://localhost:3002';
    RAISE NOTICE 'to view and test the complete function unit!';
    RAISE NOTICE '========================================';
END $$;
