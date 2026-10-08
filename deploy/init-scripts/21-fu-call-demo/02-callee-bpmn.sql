-- =============================================================================
-- 21-fu-call-demo / 02: Callee BPMN — "Vendor Qualification Check"
--
--   Start → Review Vendor → [qualified?] → Approved (end)
--                                       └→ Rejected (end)
--
-- The two outcomes matter to the demo: approving lets the calling purchase
-- request continue, while rejecting fails the caller along with it. The verdict
-- is carried by the platform's own approvalStatus variable, not by the end
-- event's name, so renaming these nodes changes nothing.
--
-- The process id MUST equal the Function Unit code: deployment keys on it, and
-- the caller's callActivity targets that key.
--
-- Dependencies: 00-callee-function-unit.sql, 01-callee-tables.sql
-- =============================================================================

DO $main$
DECLARE
    v_fu_id           BIGINT;
    v_review_form_id  BIGINT;
    v_action_approve  BIGINT;
    v_action_reject   BIGINT;
    v_bpmn_xml        TEXT;
BEGIN
    SELECT id INTO v_fu_id FROM dw_function_units WHERE code = 'fu-call-demo-vendor';
    IF v_fu_id IS NULL THEN
        RAISE EXCEPTION 'Function unit fu-call-demo-vendor not found.';
    END IF;

    SELECT id INTO v_review_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_fu_id AND form_name = 'Vendor Review Form';

    SELECT id INTO v_action_approve FROM dw_action_definitions
    WHERE function_unit_id = v_fu_id AND action_name = 'Approve Vendor';

    SELECT id INTO v_action_reject FROM dw_action_definitions
    WHERE function_unit_id = v_fu_id AND action_name = 'Reject Vendor';

    v_bpmn_xml := '<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
    xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
    xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
    xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
    xmlns:flowable="http://flowable.org/bpmn"
    xmlns:custom="http://workflow.platform/schema/custom"
    xmlns:custom_1="http://custom.bpmn.io/schema"
    id="Definitions_VendorCheck"
    targetNamespace="http://bpmn.io/schema/bpmn">

  <bpmn:process id="fu-call-demo-vendor" name="Vendor Qualification Check" isExecutable="true">

    <bpmn:startEvent id="VC_Start" name="Start">
      <bpmn:outgoing>VC_Flow_Start_Review</bpmn:outgoing>
    </bpmn:startEvent>

    <!-- The only human step: a reviewer records the verdict. -->
    <bpmn:userTask id="VC_Task_Review" name="Review Vendor">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[' || v_action_approve || ',' || v_action_reject || ']" />
          <custom_1:values name="actionNames" value="[&quot;Approve Vendor&quot;,&quot;Reject Vendor&quot;]" />
          <custom_1:values name="formId" value="' || v_review_form_id || '" />
          <custom_1:values name="formName" value="Vendor Review Form" />
        </custom_1:properties>
        <custom:properties>
          <custom:property name="assigneeType" value="INITIATOR" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>VC_Flow_Start_Review</bpmn:incoming>
      <bpmn:outgoing>VC_Flow_Review_Gateway</bpmn:outgoing>
    </bpmn:userTask>

    <bpmn:exclusiveGateway id="VC_Gateway" name="Qualified?">
      <bpmn:incoming>VC_Flow_Review_Gateway</bpmn:incoming>
      <bpmn:outgoing>VC_Flow_Approved</bpmn:outgoing>
      <bpmn:outgoing>VC_Flow_Rejected</bpmn:outgoing>
    </bpmn:exclusiveGateway>

    <bpmn:endEvent id="VC_End_Approved" name="Approved">
      <bpmn:incoming>VC_Flow_Approved</bpmn:incoming>
    </bpmn:endEvent>

    <bpmn:endEvent id="VC_End_Rejected" name="Rejected">
      <bpmn:incoming>VC_Flow_Rejected</bpmn:incoming>
    </bpmn:endEvent>

    <bpmn:sequenceFlow id="VC_Flow_Start_Review"   sourceRef="VC_Start"       targetRef="VC_Task_Review" />
    <bpmn:sequenceFlow id="VC_Flow_Review_Gateway" sourceRef="VC_Task_Review" targetRef="VC_Gateway" />
    <bpmn:sequenceFlow id="VC_Flow_Approved" name="Yes" sourceRef="VC_Gateway" targetRef="VC_End_Approved">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${decision == ''yes''}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="VC_Flow_Rejected" name="No" sourceRef="VC_Gateway" targetRef="VC_End_Rejected">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${decision == ''no''}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>

  </bpmn:process>

  <bpmndi:BPMNDiagram id="VC_Diagram">
    <bpmndi:BPMNPlane id="VC_Plane" bpmnElement="fu-call-demo-vendor">
      <bpmndi:BPMNShape id="VC_Start_di" bpmnElement="VC_Start">
        <dc:Bounds x="160" y="182" width="36" height="36" />
        <bpmndi:BPMNLabel><dc:Bounds x="166" y="225" width="25" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="VC_Task_Review_di" bpmnElement="VC_Task_Review">
        <dc:Bounds x="250" y="160" width="120" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="VC_Gateway_di" bpmnElement="VC_Gateway" isMarkerVisible="true">
        <dc:Bounds x="425" y="175" width="50" height="50" />
        <bpmndi:BPMNLabel><dc:Bounds x="420" y="145" width="60" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="VC_End_Approved_di" bpmnElement="VC_End_Approved">
        <dc:Bounds x="542" y="112" width="36" height="36" />
        <bpmndi:BPMNLabel><dc:Bounds x="536" y="155" width="50" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="VC_End_Rejected_di" bpmnElement="VC_End_Rejected">
        <dc:Bounds x="542" y="252" width="36" height="36" />
        <bpmndi:BPMNLabel><dc:Bounds x="538" y="295" width="46" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="VC_Flow_Start_Review_di" bpmnElement="VC_Flow_Start_Review">
        <di:waypoint x="196" y="200" /><di:waypoint x="250" y="200" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="VC_Flow_Review_Gateway_di" bpmnElement="VC_Flow_Review_Gateway">
        <di:waypoint x="370" y="200" /><di:waypoint x="425" y="200" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="VC_Flow_Approved_di" bpmnElement="VC_Flow_Approved">
        <di:waypoint x="450" y="175" /><di:waypoint x="450" y="130" /><di:waypoint x="542" y="130" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="VC_Flow_Rejected_di" bpmnElement="VC_Flow_Rejected">
        <di:waypoint x="450" y="225" /><di:waypoint x="450" y="270" /><di:waypoint x="542" y="270" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>';

    DELETE FROM dw_process_definitions WHERE function_unit_id = v_fu_id;

    INSERT INTO dw_process_definitions (
        function_unit_id, function_unit_version_id, bpmn_xml, created_at, updated_at
    ) VALUES (
        v_fu_id, v_fu_id, v_bpmn_xml, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

    RAISE NOTICE '[21-fu-call-demo] Callee BPMN written (process id = fu-call-demo-vendor)';
END $main$;
