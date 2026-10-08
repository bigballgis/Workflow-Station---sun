-- =============================================================================
-- 21-fu-call-demo / 06: Caller BPMN — every form of Function Unit call
--
--   Start
--     → Raise Request              (userTask)
--     → Check Primary Vendor       (callActivity)                    ← CALL ONCE
--     → Check Additional Vendors   (callActivity + multiInstance)    ← CALL PER ROW
--     → Confirm Budget Lines       (subProcess + multiInstance)      ← MI SUB-TASK
--     → Approve Purchase           (userTask)
--     → [approved?] → Approved / Rejected (end)
--
-- The third block is the pre-existing multi-instance sub-task mechanism. It is
-- here on purpose: the two mechanisms look similar on a diagram but share no
-- code path, and this diagram is the live proof they coexist.
--
-- calledElement carries the callee's CODE, never its id — ids are remapped on
-- import, codes are not, and the deployed process key equals the code.
--
-- Dependencies: 00–05. The callee must be DEPLOYED before this unit can deploy;
-- the engine refuses a call to a process that is not there yet.
-- =============================================================================

DO $main$
DECLARE
    v_fu_id              BIGINT;
    v_request_form_id    BIGINT;
    v_approval_form_id   BIGINT;
    v_budget_form_id     BIGINT;
    v_action_submit      BIGINT;
    v_action_approve     BIGINT;
    v_action_reject      BIGINT;
    v_action_confirm     BIGINT;
    v_budget_table_id    BIGINT;
    v_bpmn_xml           TEXT;
BEGIN
    SELECT id INTO v_fu_id FROM dw_function_units WHERE code = 'fu-call-demo-purchase';
    IF v_fu_id IS NULL THEN
        RAISE EXCEPTION 'Function unit fu-call-demo-purchase not found.';
    END IF;

    SELECT id INTO v_request_form_id  FROM dw_form_definitions
        WHERE function_unit_id = v_fu_id AND form_name = 'Purchase Request Form';
    SELECT id INTO v_approval_form_id FROM dw_form_definitions
        WHERE function_unit_id = v_fu_id AND form_name = 'Purchase Approval Form';
    SELECT id INTO v_budget_form_id   FROM dw_form_definitions
        WHERE function_unit_id = v_fu_id AND form_name = 'Budget Line Form';

    SELECT id INTO v_action_submit  FROM dw_action_definitions
        WHERE function_unit_id = v_fu_id AND action_name = 'Submit Request';
    SELECT id INTO v_action_approve FROM dw_action_definitions
        WHERE function_unit_id = v_fu_id AND action_name = 'Approve Purchase';
    SELECT id INTO v_action_reject  FROM dw_action_definitions
        WHERE function_unit_id = v_fu_id AND action_name = 'Reject Purchase';
    SELECT id INTO v_action_confirm FROM dw_action_definitions
        WHERE function_unit_id = v_fu_id AND action_name = 'Confirm Budget Line';

    SELECT id INTO v_budget_table_id FROM dw_table_definitions
        WHERE function_unit_id = v_fu_id AND table_name = 'budget_lines';

    -- ---- header + request step ---------------------------------------------
    v_bpmn_xml := '<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
    xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
    xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
    xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
    xmlns:flowable="http://flowable.org/bpmn"
    xmlns:custom="http://workflow.platform/schema/custom"
    xmlns:custom_1="http://custom.bpmn.io/schema"
    id="Definitions_PurchaseRequest"
    targetNamespace="http://bpmn.io/schema/bpmn">

  <bpmn:process id="fu-call-demo-purchase" name="Purchase Request (Function Unit Calls)" isExecutable="true">

    <bpmn:startEvent id="PR_Start" name="Start">
      <bpmn:outgoing>PR_Flow_Start_Raise</bpmn:outgoing>
    </bpmn:startEvent>

    <bpmn:userTask id="PR_Task_Raise" name="Raise Request">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[' || v_action_submit || ']" />
          <custom_1:values name="actionNames" value="[&quot;Submit Request&quot;]" />
          <custom_1:values name="formId" value="' || v_request_form_id || '" />
          <custom_1:values name="formName" value="Purchase Request Form" />
        </custom_1:properties>
        <custom:properties>
          <custom:property name="assigneeType" value="INITIATOR" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>PR_Flow_Start_Raise</bpmn:incoming>
      <bpmn:outgoing>PR_Flow_Raise_Primary</bpmn:outgoing>
    </bpmn:userTask>';

    -- ---- 1. call once -------------------------------------------------------
    v_bpmn_xml := v_bpmn_xml || '

    <!-- CALL ONCE: one child instance of the vendor check for the primary vendor.
         childFormName names a form of the CALLED unit; the request page renders
         that child form read-only so the requester sees the verdict. It is stored
         by name because form ids are remapped on import.
         Data passing (compiled by the engine on deploy into flowable:in / flowable:out):
         the vendor and department go in; the verdict comes back into primary_vendor_result. -->
    <bpmn:callActivity id="PR_Call_PrimaryVendor" name="Check Primary Vendor"
        calledElement="fu-call-demo-vendor">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="childFormName" value="Vendor Review Form" />
          <custom:property name="callInputMapping" value="[{&quot;from&quot;:&quot;primary_vendor&quot;,&quot;to&quot;:&quot;vendor_name&quot;},{&quot;from&quot;:&quot;department&quot;,&quot;to&quot;:&quot;category&quot;}]" />
          <custom:property name="callOutputMapping" value="[{&quot;from&quot;:&quot;check_result&quot;,&quot;to&quot;:&quot;primary_vendor_result&quot;}]" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>PR_Flow_Raise_Primary</bpmn:incoming>
      <bpmn:outgoing>PR_Flow_Primary_Extra</bpmn:outgoing>
    </bpmn:callActivity>';

    -- ---- 2. call once per row ----------------------------------------------
    v_bpmn_xml := v_bpmn_xml || '

    <!-- CALL PER ROW: one child instance per row of extra_vendors. The loop
         characteristics are the same BPMN construct the MI sub-process below
         uses, but here they sit on a callActivity, so each iteration starts a
         whole separate Function Unit process instead of expanding inline.
         callRowsTable names the rows; the engine compiles it on deploy into the
         collection of the loop (read from the rows of the request when the step is reached).
         Each call gets the vendor and category of ITS row, and when it finishes its
         check_result is copied back into that same row (check_status). -->
    <bpmn:callActivity id="PR_Call_ExtraVendors" name="Check Additional Vendors"
        calledElement="fu-call-demo-vendor">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="childFormName" value="Vendor Review Form" />
          <custom:property name="callRowsTable" value="extra_vendors" />
          <custom:property name="callInputMapping" value="[{&quot;from&quot;:&quot;row.vendor_name&quot;,&quot;to&quot;:&quot;vendor_name&quot;},{&quot;from&quot;:&quot;row.category&quot;,&quot;to&quot;:&quot;category&quot;}]" />
          <custom:property name="callOutputMapping" value="[{&quot;from&quot;:&quot;check_result&quot;,&quot;to&quot;:&quot;row.check_status&quot;}]" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>PR_Flow_Primary_Extra</bpmn:incoming>
      <bpmn:outgoing>PR_Flow_Extra_Budget</bpmn:outgoing>
      <bpmn:multiInstanceLoopCharacteristics isSequential="false" />
    </bpmn:callActivity>';

    -- ---- 3. multi-instance sub-task (NOT a call) ----------------------------
    v_bpmn_xml := v_bpmn_xml || '

    <!-- MI SUB-TASK: the pre-existing mechanism, included to show the two do not
         interfere. This expands INSIDE this process instance, one user task per
         budget_lines row, assigned from the row''s owner_user_id. No child
         process instance is created. -->
    <bpmn:subProcess id="PR_MI_BudgetLines" name="Confirm Budget Lines">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="multiInstance" value="true" />
          <custom:property name="miTaskStatusField" value="task_status" />
          <custom:property name="miTaskCurrentNodeField" value="task_current_node" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>PR_Flow_Extra_Budget</bpmn:incoming>
      <bpmn:outgoing>PR_Flow_Budget_Approve</bpmn:outgoing>
      <bpmn:multiInstanceLoopCharacteristics isSequential="false"
          flowable:collection="multiInstance_budget_lines_collection"
          flowable:elementVariable="currentItem" />

      <bpmn:startEvent id="PR_MI_Start">
        <bpmn:outgoing>PR_MI_Flow_1</bpmn:outgoing>
      </bpmn:startEvent>

      <bpmn:userTask id="PR_MI_Task_Confirm" name="Confirm Budget Line">
        <bpmn:extensionElements>
          <custom_1:properties>
            <custom_1:values name="actionIds" value="[' || v_action_confirm || ']" />
            <custom_1:values name="actionNames" value="[&quot;Confirm Budget Line&quot;]" />
            <custom_1:values name="formId" value="' || v_budget_form_id || '" />
            <custom_1:values name="formName" value="Budget Line Form" />
          </custom_1:properties>
          <custom:properties>
            <custom:property name="assigneeType" value="ELEMENT_VARIABLE" />
            <custom:property name="assigneeMode" value="user" />
            <custom:property name="subTableName" value="budget_lines" />
            <custom:property name="subTableId" value="' || v_budget_table_id || '" />
            <custom:property name="assigneeField" value="owner_user_id" />
            <custom:property name="rowIdVariable" value="currentItem.rowId" />
          </custom:properties>
        </bpmn:extensionElements>
        <bpmn:incoming>PR_MI_Flow_1</bpmn:incoming>
        <bpmn:outgoing>PR_MI_Flow_2</bpmn:outgoing>
      </bpmn:userTask>

      <bpmn:endEvent id="PR_MI_End">
        <bpmn:incoming>PR_MI_Flow_2</bpmn:incoming>
      </bpmn:endEvent>

      <bpmn:sequenceFlow id="PR_MI_Flow_1" sourceRef="PR_MI_Start" targetRef="PR_MI_Task_Confirm" />
      <bpmn:sequenceFlow id="PR_MI_Flow_2" sourceRef="PR_MI_Task_Confirm" targetRef="PR_MI_End" />
    </bpmn:subProcess>';

    -- ---- approval + outcome -------------------------------------------------
    v_bpmn_xml := v_bpmn_xml || '

    <bpmn:userTask id="PR_Task_Approve" name="Approve Purchase">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[' || v_action_approve || ',' || v_action_reject || ']" />
          <custom_1:values name="actionNames" value="[&quot;Approve Purchase&quot;,&quot;Reject Purchase&quot;]" />
          <custom_1:values name="formId" value="' || v_approval_form_id || '" />
          <custom_1:values name="formName" value="Purchase Approval Form" />
        </custom_1:properties>
        <custom:properties>
          <custom:property name="assigneeType" value="INITIATOR" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>PR_Flow_Budget_Approve</bpmn:incoming>
      <bpmn:outgoing>PR_Flow_Approve_Gateway</bpmn:outgoing>
    </bpmn:userTask>

    <bpmn:exclusiveGateway id="PR_Gateway" name="Approved?">
      <bpmn:incoming>PR_Flow_Approve_Gateway</bpmn:incoming>
      <bpmn:outgoing>PR_Flow_Approved</bpmn:outgoing>
      <bpmn:outgoing>PR_Flow_Rejected</bpmn:outgoing>
    </bpmn:exclusiveGateway>

    <bpmn:endEvent id="PR_End_Approved" name="Approved">
      <bpmn:incoming>PR_Flow_Approved</bpmn:incoming>
    </bpmn:endEvent>

    <bpmn:endEvent id="PR_End_Rejected" name="Rejected">
      <bpmn:incoming>PR_Flow_Rejected</bpmn:incoming>
    </bpmn:endEvent>

    <bpmn:sequenceFlow id="PR_Flow_Start_Raise"     sourceRef="PR_Start"              targetRef="PR_Task_Raise" />
    <bpmn:sequenceFlow id="PR_Flow_Raise_Primary"   sourceRef="PR_Task_Raise"         targetRef="PR_Call_PrimaryVendor" />
    <bpmn:sequenceFlow id="PR_Flow_Primary_Extra"   sourceRef="PR_Call_PrimaryVendor" targetRef="PR_Call_ExtraVendors" />
    <bpmn:sequenceFlow id="PR_Flow_Extra_Budget"    sourceRef="PR_Call_ExtraVendors"  targetRef="PR_MI_BudgetLines" />
    <bpmn:sequenceFlow id="PR_Flow_Budget_Approve"  sourceRef="PR_MI_BudgetLines"     targetRef="PR_Task_Approve" />
    <bpmn:sequenceFlow id="PR_Flow_Approve_Gateway" sourceRef="PR_Task_Approve"       targetRef="PR_Gateway" />
    <bpmn:sequenceFlow id="PR_Flow_Approved" name="Yes" sourceRef="PR_Gateway" targetRef="PR_End_Approved">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${decision == ''yes''}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="PR_Flow_Rejected" name="No" sourceRef="PR_Gateway" targetRef="PR_End_Rejected">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${decision == ''no''}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>

  </bpmn:process>';

    -- ---- diagram -------------------------------------------------------------
    v_bpmn_xml := v_bpmn_xml || '

  <bpmndi:BPMNDiagram id="PR_Diagram">
    <bpmndi:BPMNPlane id="PR_Plane" bpmnElement="fu-call-demo-purchase">
      <bpmndi:BPMNShape id="PR_Start_di" bpmnElement="PR_Start">
        <dc:Bounds x="140" y="252" width="36" height="36" />
        <bpmndi:BPMNLabel><dc:Bounds x="146" y="295" width="25" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="PR_Task_Raise_di" bpmnElement="PR_Task_Raise">
        <dc:Bounds x="230" y="230" width="120" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="PR_Call_PrimaryVendor_di" bpmnElement="PR_Call_PrimaryVendor">
        <dc:Bounds x="400" y="230" width="140" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="PR_Call_ExtraVendors_di" bpmnElement="PR_Call_ExtraVendors">
        <dc:Bounds x="590" y="230" width="150" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="PR_MI_BudgetLines_di" bpmnElement="PR_MI_BudgetLines" isExpanded="true">
        <dc:Bounds x="790" y="170" width="400" height="200" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="PR_MI_Start_di" bpmnElement="PR_MI_Start">
        <dc:Bounds x="830" y="252" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="PR_MI_Task_Confirm_di" bpmnElement="PR_MI_Task_Confirm">
        <dc:Bounds x="920" y="230" width="140" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="PR_MI_End_di" bpmnElement="PR_MI_End">
        <dc:Bounds x="1110" y="252" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="PR_Task_Approve_di" bpmnElement="PR_Task_Approve">
        <dc:Bounds x="1240" y="230" width="130" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="PR_Gateway_di" bpmnElement="PR_Gateway" isMarkerVisible="true">
        <dc:Bounds x="1420" y="245" width="50" height="50" />
        <bpmndi:BPMNLabel><dc:Bounds x="1412" y="215" width="66" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="PR_End_Approved_di" bpmnElement="PR_End_Approved">
        <dc:Bounds x="1540" y="182" width="36" height="36" />
        <bpmndi:BPMNLabel><dc:Bounds x="1534" y="225" width="50" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="PR_End_Rejected_di" bpmnElement="PR_End_Rejected">
        <dc:Bounds x="1540" y="322" width="36" height="36" />
        <bpmndi:BPMNLabel><dc:Bounds x="1536" y="365" width="46" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="PR_Flow_Start_Raise_di" bpmnElement="PR_Flow_Start_Raise">
        <di:waypoint x="176" y="270" /><di:waypoint x="230" y="270" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="PR_Flow_Raise_Primary_di" bpmnElement="PR_Flow_Raise_Primary">
        <di:waypoint x="350" y="270" /><di:waypoint x="400" y="270" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="PR_Flow_Primary_Extra_di" bpmnElement="PR_Flow_Primary_Extra">
        <di:waypoint x="540" y="270" /><di:waypoint x="590" y="270" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="PR_Flow_Extra_Budget_di" bpmnElement="PR_Flow_Extra_Budget">
        <di:waypoint x="740" y="270" /><di:waypoint x="790" y="270" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="PR_MI_Flow_1_di" bpmnElement="PR_MI_Flow_1">
        <di:waypoint x="866" y="270" /><di:waypoint x="920" y="270" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="PR_MI_Flow_2_di" bpmnElement="PR_MI_Flow_2">
        <di:waypoint x="1060" y="270" /><di:waypoint x="1110" y="270" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="PR_Flow_Budget_Approve_di" bpmnElement="PR_Flow_Budget_Approve">
        <di:waypoint x="1190" y="270" /><di:waypoint x="1240" y="270" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="PR_Flow_Approve_Gateway_di" bpmnElement="PR_Flow_Approve_Gateway">
        <di:waypoint x="1370" y="270" /><di:waypoint x="1420" y="270" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="PR_Flow_Approved_di" bpmnElement="PR_Flow_Approved">
        <di:waypoint x="1445" y="245" /><di:waypoint x="1445" y="200" /><di:waypoint x="1540" y="200" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="PR_Flow_Rejected_di" bpmnElement="PR_Flow_Rejected">
        <di:waypoint x="1445" y="295" /><di:waypoint x="1445" y="340" /><di:waypoint x="1540" y="340" />
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

    RAISE NOTICE '[21-fu-call-demo] Caller BPMN written: 1 single call, 1 per-row call, 1 MI sub-process';
END $main$;
