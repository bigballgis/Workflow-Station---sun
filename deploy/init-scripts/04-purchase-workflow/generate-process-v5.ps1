# Generate Purchase Request Process with New 9 Assignee Types
# This script generates the BPMN XML with the new assignee types

# BPMN XML with new assignee types
$bpmnXml = @'
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions 
    xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" 
    xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
    xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
    xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:custom="http://custom.bpmn.io/schema"
    id="Definitions_PurchaseRequest" 
    targetNamespace="http://workflow.example.com/purchase-request">
  <bpmn:process id="Process_PurchaseRequest" name="Purchase Request Process" isExecutable="true">
    <bpmn:extensionElements>
      <custom:properties>
        <custom:property name="globalActionIds" value="[26,29]"/>
      </custom:properties>
    </bpmn:extensionElements>
    
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    
    <!-- Task 1: Submit Request - INITIATOR (Direct Assignment) -->
    <bpmn:userTask id="Task_Submit" name="Submit Request">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="11"/>
          <custom:property name="formName" value="Purchase Request Main Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[21,29]"/>
          <custom:property name="assigneeType" value="INITIATOR"/>
          <custom:property name="assigneeLabel" value="Initiator"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- Task 2: Department Review - INITIATOR_BU_ROLE (Claim Mode) -->
    <!-- Assigned to users with PURCHASE_REVIEWER role in initiator's BU -->
    <bpmn:userTask id="Task_DeptReview" name="Department Review">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="13"/>
          <custom:property name="formName" value="Approval Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[22,23,24,25]"/>
          <custom:property name="assigneeType" value="INITIATOR_BU_ROLE"/>
          <custom:property name="roleId" value="PURCHASE_REVIEWER_ROLE"/>
          <custom:property name="assigneeLabel" value="Initiator BU Role: Purchase Reviewer"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_2</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- Task 3: Manager Approval - ENTITY_MANAGER (Direct Assignment) -->
    <bpmn:userTask id="Task_ManagerApproval" name="Manager Approval">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="13"/>
          <custom:property name="formName" value="Approval Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[22,23,24,25]"/>
          <custom:property name="assigneeType" value="ENTITY_MANAGER"/>
          <custom:property name="assigneeLabel" value="Entity Manager"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_3</bpmn:incoming>
      <bpmn:outgoing>Flow_4</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:exclusiveGateway id="Gateway_Amount" name="Amount Check">
      <bpmn:incoming>Flow_4</bpmn:incoming>
      <bpmn:outgoing>Flow_5</bpmn:outgoing>
      <bpmn:outgoing>Flow_6</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    
    <!-- Task 4: Function Manager Approval - FUNCTION_MANAGER (Direct Assignment) -->
    <!-- Only for high-value requests (>= 10000) -->
    <bpmn:userTask id="Task_FunctionManagerApproval" name="Function Manager Approval">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="13"/>
          <custom:property name="formName" value="Approval Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[22,23,24,25]"/>
          <custom:property name="assigneeType" value="FUNCTION_MANAGER"/>
          <custom:property name="assigneeLabel" value="Function Manager"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_5</bpmn:incoming>
      <bpmn:outgoing>Flow_7</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- Task 5: Parent BU Review - INITIATOR_PARENT_BU_ROLE (Claim Mode) -->
    <!-- Assigned to users with SENIOR_APPROVER role in initiator's parent BU -->
    <bpmn:userTask id="Task_ParentBuReview" name="Parent BU Review">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="13"/>
          <custom:property name="formName" value="Approval Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[22,23,24,25]"/>
          <custom:property name="assigneeType" value="INITIATOR_PARENT_BU_ROLE"/>
          <custom:property name="roleId" value="SENIOR_APPROVER_ROLE"/>
          <custom:property name="assigneeLabel" value="Initiator Parent BU Role: Senior Approver"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_7</bpmn:incoming>
      <bpmn:outgoing>Flow_8</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- Task 6: Finance Review - FIXED_BU_ROLE (Claim Mode) -->
    <!-- Assigned to users with FINANCE_REVIEWER role in DEPT-FINANCE -->
    <bpmn:userTask id="Task_FinanceReview" name="Finance Review">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="13"/>
          <custom:property name="formName" value="Approval Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[22,23,27]"/>
          <custom:property name="assigneeType" value="FIXED_BU_ROLE"/>
          <custom:property name="businessUnitId" value="DEPT-FINANCE"/>
          <custom:property name="roleId" value="FINANCE_REVIEWER_ROLE"/>
          <custom:property name="assigneeLabel" value="Fixed BU Role: Finance Reviewer"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_8</bpmn:incoming>
      <bpmn:incoming>Flow_6</bpmn:incoming>
      <bpmn:outgoing>Flow_9</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- Task 7: Multi-Department Countersign - BU_UNBOUNDED_ROLE (Claim Mode) -->
    <!-- Assigned to users with COUNTERSIGN_APPROVER role via virtual group -->
    <bpmn:userTask id="Task_Countersign" name="Multi-Department Countersign">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="16"/>
          <custom:property name="formName" value="Countersign Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[22,23,30]"/>
          <custom:property name="assigneeType" value="BU_UNBOUNDED_ROLE"/>
          <custom:property name="roleId" value="COUNTERSIGN_APPROVER_ROLE"/>
          <custom:property name="assigneeLabel" value="BU Unbounded Role: Countersign Approver"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_9</bpmn:incoming>
      <bpmn:outgoing>Flow_10</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:endEvent id="EndEvent_1" name="End">
      <bpmn:incoming>Flow_10</bpmn:incoming>
    </bpmn:endEvent>
    
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_Submit"/>
    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_Submit" targetRef="Task_DeptReview"/>
    <bpmn:sequenceFlow id="Flow_3" sourceRef="Task_DeptReview" targetRef="Task_ManagerApproval"/>
    <bpmn:sequenceFlow id="Flow_4" sourceRef="Task_ManagerApproval" targetRef="Gateway_Amount"/>
    <bpmn:sequenceFlow id="Flow_5" name="Amount >= 10000" sourceRef="Gateway_Amount" targetRef="Task_FunctionManagerApproval">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression"><![CDATA[${total_amount >= 10000}]]></bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_6" name="Amount &lt; 10000" sourceRef="Gateway_Amount" targetRef="Task_FinanceReview">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression"><![CDATA[${total_amount < 10000}]]></bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_7" sourceRef="Task_FunctionManagerApproval" targetRef="Task_ParentBuReview"/>
    <bpmn:sequenceFlow id="Flow_8" sourceRef="Task_ParentBuReview" targetRef="Task_FinanceReview"/>
    <bpmn:sequenceFlow id="Flow_9" sourceRef="Task_FinanceReview" targetRef="Task_Countersign"/>
    <bpmn:sequenceFlow id="Flow_10" sourceRef="Task_Countersign" targetRef="EndEvent_1"/>
  </bpmn:process>
  
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_PurchaseRequest">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="100" y="200" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Submit_di" bpmnElement="Task_Submit">
        <dc:Bounds x="200" y="178" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_DeptReview_di" bpmnElement="Task_DeptReview">
        <dc:Bounds x="360" y="178" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_ManagerApproval_di" bpmnElement="Task_ManagerApproval">
        <dc:Bounds x="520" y="178" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_Amount_di" bpmnElement="Gateway_Amount" isMarkerVisible="true">
        <dc:Bounds x="680" y="193" width="50" height="50"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_FunctionManagerApproval_di" bpmnElement="Task_FunctionManagerApproval">
        <dc:Bounds x="800" y="80" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_ParentBuReview_di" bpmnElement="Task_ParentBuReview">
        <dc:Bounds x="960" y="80" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_FinanceReview_di" bpmnElement="Task_FinanceReview">
        <dc:Bounds x="1120" y="178" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Countersign_di" bpmnElement="Task_Countersign">
        <dc:Bounds x="1280" y="178" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="1440" y="200" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <di:waypoint x="136" y="218"/>
        <di:waypoint x="200" y="218"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2">
        <di:waypoint x="300" y="218"/>
        <di:waypoint x="360" y="218"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_3_di" bpmnElement="Flow_3">
        <di:waypoint x="460" y="218"/>
        <di:waypoint x="520" y="218"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_4_di" bpmnElement="Flow_4">
        <di:waypoint x="620" y="218"/>
        <di:waypoint x="680" y="218"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_5_di" bpmnElement="Flow_5">
        <di:waypoint x="705" y="193"/>
        <di:waypoint x="705" y="120"/>
        <di:waypoint x="800" y="120"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_6_di" bpmnElement="Flow_6">
        <di:waypoint x="730" y="218"/>
        <di:waypoint x="1120" y="218"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_7_di" bpmnElement="Flow_7">
        <di:waypoint x="900" y="120"/>
        <di:waypoint x="960" y="120"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_8_di" bpmnElement="Flow_8">
        <di:waypoint x="1060" y="120"/>
        <di:waypoint x="1090" y="120"/>
        <di:waypoint x="1090" y="218"/>
        <di:waypoint x="1120" y="218"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_9_di" bpmnElement="Flow_9">
        <di:waypoint x="1220" y="218"/>
        <di:waypoint x="1280" y="218"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_10_di" bpmnElement="Flow_10">
        <di:waypoint x="1380" y="218"/>
        <di:waypoint x="1440" y="218"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
'@

# Convert to Base64
$base64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($bpmnXml))

# Generate SQL
$sql = @"
-- Purchase Request - Process Definition with New 9 Assignee Types
-- =====================================================
-- Assignee Types Used:
-- 1. INITIATOR - Task_Submit (Direct)
-- 2. INITIATOR_BU_ROLE - Task_DeptReview (Claim, roleId=PURCHASE_REVIEWER_ROLE)
-- 3. ENTITY_MANAGER - Task_ManagerApproval (Direct)
-- 4. FUNCTION_MANAGER - Task_FunctionManagerApproval (Direct)
-- 5. INITIATOR_PARENT_BU_ROLE - Task_ParentBuReview (Claim, roleId=SENIOR_APPROVER_ROLE)
-- 6. FIXED_BU_ROLE - Task_FinanceReview (Claim, businessUnitId=DEPT-FINANCE, roleId=FINANCE_REVIEWER_ROLE)
-- 7. BU_UNBOUNDED_ROLE - Task_Countersign (Claim, roleId=COUNTERSIGN_APPROVER_ROLE)
-- =====================================================

DELETE FROM dw_process_definitions WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-purchase-request');

INSERT INTO dw_process_definitions (function_unit_id, bpmn_xml)
SELECT f.id, '$base64'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';
"@

# Write to file
[System.IO.File]::WriteAllText("04-08-process-v5.sql", $sql, [System.Text.Encoding]::UTF8)

Write-Host "SQL file generated: 04-08-process-v5.sql"
Write-Host "Base64 length: $($base64.Length)"
Write-Host ""
Write-Host "Assignee Types Used:"
Write-Host "1. INITIATOR - Task_Submit"
Write-Host "2. INITIATOR_BU_ROLE - Task_DeptReview (roleId=PURCHASE_REVIEWER_ROLE)"
Write-Host "3. ENTITY_MANAGER - Task_ManagerApproval"
Write-Host "4. FUNCTION_MANAGER - Task_FunctionManagerApproval"
Write-Host "5. INITIATOR_PARENT_BU_ROLE - Task_ParentBuReview (roleId=SENIOR_APPROVER_ROLE)"
Write-Host "6. FIXED_BU_ROLE - Task_FinanceReview (businessUnitId=DEPT-FINANCE, roleId=FINANCE_REVIEWER_ROLE)"
Write-Host "7. BU_UNBOUNDED_ROLE - Task_Countersign (roleId=COUNTERSIGN_APPROVER_ROLE)"
