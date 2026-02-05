-- =============================================================================
-- Demo: Employee Leave Management System
-- Complete function unit with tables, forms, process, and actions
-- =============================================================================

-- =============================================================================
-- 1. Create Function Unit
-- =============================================================================
INSERT INTO public.dw_function_units (
    function_unit_name,
    function_unit_code,
    description,
    icon,
    category,
    status,
    created_by,
    updated_by
) VALUES (
    'Employee Leave Management',
    'LEAVE_MGMT',
    'Complete employee leave request and approval system with multi-level approval workflow',
    'Calendar',
    'HR',
    'ACTIVE',
    1,
    1
) ON CONFLICT (function_unit_code) DO NOTHING;

-- Get the function unit ID
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
    -- Get function unit ID
    SELECT id INTO v_function_unit_id 
    FROM public.dw_function_units 
    WHERE function_unit_code = 'LEAVE_MGMT';

    -- =============================================================================
    -- 2. Create Tables
    -- =============================================================================
    
    -- 2.1 Main Table: Leave Request
    INSERT INTO public.dw_table_definitions (
        function_unit_id,
        table_name,
        table_code,
        table_type,
        description,
        created_by,
        updated_by
    ) VALUES (
        v_function_unit_id,
        'Leave Request',
        'leave_request',
        'MAIN',
        'Main table for employee leave requests',
        1,
        1
    ) RETURNING id INTO v_main_table_id;

    -- Main table fields
    INSERT INTO public.dw_field_definitions (
        table_id,
        field_name,
        field_code,
        data_type,
        length,
        scale,
        nullable,
        default_value,
        description,
        display_order,
        created_by,
        updated_by
    ) VALUES
    (v_main_table_id, 'Request Number', 'request_number', 'VARCHAR', 50, NULL, FALSE, NULL, 'Unique request number', 1, 1, 1),
    (v_main_table_id, 'Employee Name', 'employee_name', 'VARCHAR', 100, NULL, FALSE, NULL, 'Name of employee requesting leave', 2, 1, 1),
    (v_main_table_id, 'Employee ID', 'employee_id', 'VARCHAR', 20, NULL, FALSE, NULL, 'Employee identification number', 3, 1, 1),
    (v_main_table_id, 'Department', 'department', 'VARCHAR', 100, NULL, FALSE, NULL, 'Employee department', 4, 1, 1),
    (v_main_table_id, 'Leave Type', 'leave_type', 'VARCHAR', 50, NULL, FALSE, NULL, 'Type of leave (Annual/Sick/Personal)', 5, 1, 1),
    (v_main_table_id, 'Start Date', 'start_date', 'DATE', NULL, NULL, FALSE, NULL, 'Leave start date', 6, 1, 1),
    (v_main_table_id, 'End Date', 'end_date', 'DATE', NULL, NULL, FALSE, NULL, 'Leave end date', 7, 1, 1),
    (v_main_table_id, 'Total Days', 'total_days', 'DECIMAL', 10, 1, FALSE, NULL, 'Total number of leave days', 8, 1, 1),
    (v_main_table_id, 'Reason', 'reason', 'TEXT', NULL, NULL, FALSE, NULL, 'Reason for leave', 9, 1, 1),
    (v_main_table_id, 'Emergency Contact', 'emergency_contact', 'VARCHAR', 100, NULL, TRUE, NULL, 'Emergency contact during leave', 10, 1, 1),
    (v_main_table_id, 'Emergency Phone', 'emergency_phone', 'VARCHAR', 20, NULL, TRUE, NULL, 'Emergency contact phone number', 11, 1, 1),
    (v_main_table_id, 'Status', 'status', 'VARCHAR', 20, NULL, FALSE, 'DRAFT', 'Request status', 12, 1, 1),
    (v_main_table_id, 'Submit Date', 'submit_date', 'TIMESTAMP', NULL, NULL, TRUE, NULL, 'Date when request was submitted', 13, 1, 1),
    (v_main_table_id, 'Manager Approval', 'manager_approval', 'VARCHAR', 20, NULL, TRUE, NULL, 'Manager approval status', 14, 1, 1),
    (v_main_table_id, 'HR Approval', 'hr_approval', 'VARCHAR', 20, NULL, TRUE, NULL, 'HR approval status', 15, 1, 1),
    (v_main_table_id, 'Final Status', 'final_status', 'VARCHAR', 20, NULL, TRUE, NULL, 'Final approval status', 16, 1, 1);

    -- 2.2 Sub Table: Leave Details
    INSERT INTO public.dw_table_definitions (
        function_unit_id,
        table_name,
        table_code,
        table_type,
        description,
        created_by,
        updated_by
    ) VALUES (
        v_function_unit_id,
        'Leave Details',
        'leave_details',
        'SUB',
        'Detailed breakdown of leave days',
        1,
        1
    ) RETURNING id INTO v_detail_table_id;

    -- Detail table fields
    INSERT INTO public.dw_field_definitions (
        table_id,
        field_name,
        field_code,
        data_type,
        length,
        scale,
        nullable,
        default_value,
        description,
        display_order,
        created_by,
        updated_by
    ) VALUES
    (v_detail_table_id, 'Leave Date', 'leave_date', 'DATE', NULL, NULL, FALSE, NULL, 'Specific date of leave', 1, 1, 1),
    (v_detail_table_id, 'Day Type', 'day_type', 'VARCHAR', 20, NULL, FALSE, NULL, 'Full day or half day', 2, 1, 1),
    (v_detail_table_id, 'Hours', 'hours', 'DECIMAL', 5, 2, FALSE, NULL, 'Number of hours', 3, 1, 1),
    (v_detail_table_id, 'Notes', 'notes', 'VARCHAR', 500, NULL, TRUE, NULL, 'Additional notes for this day', 4, 1, 1);

    -- 2.3 Related Table: Approval Records
    INSERT INTO public.dw_table_definitions (
        function_unit_id,
        table_name,
        table_code,
        table_type,
        description,
        created_by,
        updated_by
    ) VALUES (
        v_function_unit_id,
        'Approval Records',
        'approval_records',
        'RELATION',
        'History of all approval actions',
        1,
        1
    ) RETURNING id INTO v_approval_table_id;

    -- Approval records fields
    INSERT INTO public.dw_field_definitions (
        table_id,
        field_name,
        field_code,
        data_type,
        length,
        scale,
        nullable,
        default_value,
        description,
        display_order,
        created_by,
        updated_by
    ) VALUES
    (v_approval_table_id, 'Approver Name', 'approver_name', 'VARCHAR', 100, NULL, FALSE, NULL, 'Name of approver', 1, 1, 1),
    (v_approval_table_id, 'Approver Role', 'approver_role', 'VARCHAR', 50, NULL, FALSE, NULL, 'Role of approver', 2, 1, 1),
    (v_approval_table_id, 'Action', 'action', 'VARCHAR', 20, NULL, FALSE, NULL, 'Approval action taken', 3, 1, 1),
    (v_approval_table_id, 'Action Date', 'action_date', 'TIMESTAMP', NULL, NULL, FALSE, NULL, 'Date of approval action', 4, 1, 1),
    (v_approval_table_id, 'Comments', 'comments', 'TEXT', NULL, NULL, TRUE, NULL, 'Approver comments', 5, 1, 1),
    (v_approval_table_id, 'Decision', 'decision', 'VARCHAR', 20, NULL, FALSE, NULL, 'Approve or Reject', 6, 1, 1);

    -- =============================================================================
    -- 3. Create Forms
    -- =============================================================================
    
    -- 3.1 Application Form
    INSERT INTO public.dw_form_definitions (
        function_unit_id,
        form_name,
        form_code,
        form_type,
        description,
        created_by,
        updated_by
    ) VALUES (
        v_function_unit_id,
        'Leave Application Form',
        'leave_application_form',
        'MAIN',
        'Form for employees to submit leave requests',
        1,
        1
    ) RETURNING id INTO v_apply_form_id;

    -- Bind main table to application form
    INSERT INTO public.dw_form_table_bindings (
        form_id,
        table_id,
        binding_type,
        created_by,
        updated_by
    ) VALUES
    (v_apply_form_id, v_main_table_id, 'PRIMARY', 1, 1),
    (v_apply_form_id, v_detail_table_id, 'SUB', 1, 1);

    -- 3.2 Approval Form
    INSERT INTO public.dw_form_definitions (
        function_unit_id,
        form_name,
        form_code,
        form_type,
        description,
        created_by,
        updated_by
    ) VALUES (
        v_function_unit_id,
        'Leave Approval Form',
        'leave_approval_form',
        'MAIN',
        'Form for managers and HR to approve leave requests',
        1,
        1
    ) RETURNING id INTO v_approve_form_id;

    -- Bind tables to approval form
    INSERT INTO public.dw_form_table_bindings (
        form_id,
        table_id,
        binding_type,
        created_by,
        updated_by
    ) VALUES
    (v_approve_form_id, v_main_table_id, 'PRIMARY', 1, 1),
    (v_approve_form_id, v_approval_table_id, 'RELATED', 1, 1);

    -- =============================================================================
    -- 4. Create Process Definition
    -- =============================================================================
    
    INSERT INTO public.dw_process_definitions (
        function_unit_id,
        process_name,
        process_code,
        description,
        bpmn_xml,
        created_by,
        updated_by
    ) VALUES (
        v_function_unit_id,
        'Leave Approval Process',
        'leave_approval_process',
        'Multi-level approval workflow for leave requests',
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
    
    <!-- Start Event -->
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    
    <!-- User Task: Submit Application -->
    <bpmn:userTask id="Task_Submit" name="Submit Leave Application">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="' || v_apply_form_id || '" />
          <custom:property name="formName" value="Leave Application Form" />
          <custom:property name="formReadOnly" value="false" />
          <custom:property name="assignee" value="${applicant}" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- User Task: Manager Approval -->
    <bpmn:userTask id="Task_ManagerApproval" name="Manager Approval">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="' || v_approve_form_id || '" />
          <custom:property name="formName" value="Leave Approval Form" />
          <custom:property name="formReadOnly" value="false" />
          <custom:property name="assignee" value="${manager}" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_2</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- Exclusive Gateway: Manager Decision -->
    <bpmn:exclusiveGateway id="Gateway_ManagerDecision" name="Manager Approved?">
      <bpmn:incoming>Flow_3</bpmn:incoming>
      <bpmn:outgoing>Flow_Approved</bpmn:outgoing>
      <bpmn:outgoing>Flow_Rejected</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    
    <!-- User Task: HR Approval -->
    <bpmn:userTask id="Task_HRApproval" name="HR Approval">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="' || v_approve_form_id || '" />
          <custom:property name="formName" value="Leave Approval Form" />
          <custom:property name="formReadOnly" value="false" />
          <custom:property name="assignee" value="${hr}" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_Approved</bpmn:incoming>
      <bpmn:outgoing>Flow_4</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- Exclusive Gateway: HR Decision -->
    <bpmn:exclusiveGateway id="Gateway_HRDecision" name="HR Approved?">
      <bpmn:incoming>Flow_4</bpmn:incoming>
      <bpmn:outgoing>Flow_FinalApproved</bpmn:outgoing>
      <bpmn:outgoing>Flow_FinalRejected</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    
    <!-- End Event: Approved -->
    <bpmn:endEvent id="EndEvent_Approved" name="Approved">
      <bpmn:incoming>Flow_FinalApproved</bpmn:incoming>
    </bpmn:endEvent>
    
    <!-- End Event: Rejected -->
    <bpmn:endEvent id="EndEvent_Rejected" name="Rejected">
      <bpmn:incoming>Flow_Rejected</bpmn:incoming>
      <bpmn:incoming>Flow_FinalRejected</bpmn:incoming>
    </bpmn:endEvent>
    
    <!-- Sequence Flows -->
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_Submit" />
    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_Submit" targetRef="Task_ManagerApproval" />
    <bpmn:sequenceFlow id="Flow_3" sourceRef="Task_ManagerApproval" targetRef="Gateway_ManagerDecision" />
    <bpmn:sequenceFlow id="Flow_Approved" name="Yes" sourceRef="Gateway_ManagerDecision" targetRef="Task_HRApproval" />
    <bpmn:sequenceFlow id="Flow_Rejected" name="No" sourceRef="Gateway_ManagerDecision" targetRef="EndEvent_Rejected" />
    <bpmn:sequenceFlow id="Flow_4" sourceRef="Task_HRApproval" targetRef="Gateway_HRDecision" />
    <bpmn:sequenceFlow id="Flow_FinalApproved" name="Yes" sourceRef="Gateway_HRDecision" targetRef="EndEvent_Approved" />
    <bpmn:sequenceFlow id="Flow_FinalRejected" name="No" sourceRef="Gateway_HRDecision" targetRef="EndEvent_Rejected" />
    
  </bpmn:process>
  
  <!-- BPMN Diagram -->
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
</bpmn:definitions>',
        1,
        1
    ) RETURNING id INTO v_process_id;

    -- =============================================================================
    -- 5. Create Actions
    -- =============================================================================
    
    -- 5.1 Submit Action
    INSERT INTO public.dw_action_definitions (
        function_unit_id,
        action_name,
        action_code,
        action_type,
        description,
        http_method,
        endpoint_path,
        request_body_template,
        response_handler,
        created_by,
        updated_by
    ) VALUES (
        v_function_unit_id,
        'Submit Leave Request',
        'submit_leave_request',
        'SUBMIT',
        'Submit a new leave request for approval',
        'POST',
        '/api/leave-management/requests/submit',
        '{"requestNumber": "${requestNumber}", "status": "SUBMITTED"}',
        'return { success: true, message: "Leave request submitted successfully" }',
        1,
        1
    );

    -- 5.2 Approve Action
    INSERT INTO public.dw_action_definitions (
        function_unit_id,
        action_name,
        action_code,
        action_type,
        description,
        http_method,
        endpoint_path,
        request_body_template,
        response_handler,
        created_by,
        updated_by
    ) VALUES (
        v_function_unit_id,
        'Approve Leave Request',
        'approve_leave_request',
        'APPROVE',
        'Approve a pending leave request',
        'POST',
        '/api/leave-management/requests/${requestId}/approve',
        '{"decision": "APPROVED", "comments": "${comments}"}',
        'return { success: true, message: "Leave request approved" }',
        1,
        1
    );

    -- 5.3 Reject Action
    INSERT INTO public.dw_action_definitions (
        function_unit_id,
        action_name,
        action_code,
        action_type,
        description,
        http_method,
        endpoint_path,
        request_body_template,
        response_handler,
        created_by,
        updated_by
    ) VALUES (
        v_function_unit_id,
        'Reject Leave Request',
        'reject_leave_request',
        'REJECT',
        'Reject a pending leave request',
        'POST',
        '/api/leave-management/requests/${requestId}/reject',
        '{"decision": "REJECTED", "comments": "${comments}"}',
        'return { success: true, message: "Leave request rejected" }',
        1,
        1
    );

    -- 5.4 Withdraw Action
    INSERT INTO public.dw_action_definitions (
        function_unit_id,
        action_name,
        action_code,
        action_type,
        description,
        http_method,
        endpoint_path,
        request_body_template,
        response_handler,
        created_by,
        updated_by
    ) VALUES (
        v_function_unit_id,
        'Withdraw Leave Request',
        'withdraw_leave_request',
        'CANCEL',
        'Withdraw a submitted leave request',
        'POST',
        '/api/leave-management/requests/${requestId}/withdraw',
        '{"status": "WITHDRAWN"}',
        'return { success: true, message: "Leave request withdrawn" }',
        1,
        1
    );

    -- 5.5 Query Action
    INSERT INTO public.dw_action_definitions (
        function_unit_id,
        action_name,
        action_code,
        action_type,
        description,
        http_method,
        endpoint_path,
        request_body_template,
        response_handler,
        created_by,
        updated_by
    ) VALUES (
        v_function_unit_id,
        'Query Leave Requests',
        'query_leave_requests',
        'QUERY',
        'Query leave requests with filters',
        'GET',
        '/api/leave-management/requests?status=${status}&startDate=${startDate}&endDate=${endDate}',
        NULL,
        'return { success: true, data: response.data }',
        1,
        1
    );

    RAISE NOTICE 'Demo Leave Management System created successfully!';
    RAISE NOTICE 'Function Unit ID: %', v_function_unit_id;
    RAISE NOTICE 'Main Table ID: %', v_main_table_id;
    RAISE NOTICE 'Detail Table ID: %', v_detail_table_id;
    RAISE NOTICE 'Approval Table ID: %', v_approval_table_id;
    RAISE NOTICE 'Application Form ID: %', v_apply_form_id;
    RAISE NOTICE 'Approval Form ID: %', v_approve_form_id;
    RAISE NOTICE 'Process ID: %', v_process_id;
END $$;
