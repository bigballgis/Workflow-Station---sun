# Purchase Request Process - Generate Process Definition SQL
# Uses dynamic form IDs from database

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
        <custom:property name="globalActionIds" value="[6,9]"/>
      </custom:properties>
    </bpmn:extensionElements>
    
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    
    <bpmn:userTask id="Task_Submit" name="Submit Request">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="FORM_ID_MAIN"/>
          <custom:property name="formName" value="Purchase Request Main Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[1,9]"/>
          <custom:property name="assigneeType" value="INITIATOR"/>
          <custom:property name="assigneeLabel" value="Initiator"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:userTask id="Task_DeptReview" name="Department Review">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="FORM_ID_APPROVAL"/>
          <custom:property name="formName" value="Approval Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,4,5]"/>
          <custom:property name="assigneeType" value="DEPT_OTHERS"/>
          <custom:property name="assigneeLabel" value="Department Others"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_2</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:userTask id="Task_ManagerApproval" name="Manager Approval">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="FORM_ID_APPROVAL"/>
          <custom:property name="formName" value="Approval Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,4,5]"/>
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
    
    <bpmn:userTask id="Task_FunctionManagerApproval" name="Function Manager Approval">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="FORM_ID_APPROVAL"/>
          <custom:property name="formName" value="Approval Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,4,5]"/>
          <custom:property name="assigneeType" value="FUNCTION_MANAGER"/>
          <custom:property name="assigneeLabel" value="Function Manager"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_5</bpmn:incoming>
      <bpmn:outgoing>Flow_7</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:userTask id="Task_ParentDeptReview" name="Parent Department Review">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="FORM_ID_APPROVAL"/>
          <custom:property name="formName" value="Approval Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,4,5]"/>
          <custom:property name="assigneeType" value="PARENT_DEPT"/>
          <custom:property name="assigneeLabel" value="Parent Department"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_7</bpmn:incoming>
      <bpmn:outgoing>Flow_8</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:userTask id="Task_FinanceReview" name="Finance Review">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="FORM_ID_APPROVAL"/>
          <custom:property name="formName" value="Approval Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,7]"/>
          <custom:property name="assigneeType" value="FIXED_DEPT"/>
          <custom:property name="assigneeLabel" value="Finance Department"/>
          <custom:property name="assigneeValue" value="dept-finance-001"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_8</bpmn:incoming>
      <bpmn:incoming>Flow_6</bpmn:incoming>
      <bpmn:outgoing>Flow_9</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:userTask id="Task_Countersign" name="Multi-Department Countersign">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="FORM_ID_COUNTERSIGN"/>
          <custom:property name="formName" value="Countersign Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,10]"/>
          <custom:property name="assigneeType" value="VIRTUAL_GROUP"/>
          <custom:property name="assigneeLabel" value="Countersign Group"/>
          <custom:property name="assigneeValue" value="vg-countersign-001"/>
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
    <bpmn:sequenceFlow id="Flow_7" sourceRef="Task_FunctionManagerApproval" targetRef="Task_ParentDeptReview"/>
    <bpmn:sequenceFlow id="Flow_8" sourceRef="Task_ParentDeptReview" targetRef="Task_FinanceReview"/>
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
      <bpmndi:BPMNShape id="Task_ParentDeptReview_di" bpmnElement="Task_ParentDeptReview">
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

$base64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($bpmnXml))

# Generate SQL that uses subqueries to get actual form IDs
$sql = @"
-- Purchase Request - Process Definition with Dynamic Form IDs
-- This SQL replaces placeholder form IDs with actual database IDs

-- First, delete existing process definition
DELETE FROM dw_process_definitions WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-purchase-request');

-- Insert new process definition with placeholder form IDs
INSERT INTO dw_process_definitions (function_unit_id, bpmn_xml)
SELECT f.id, '$base64'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- Update BPMN XML to replace placeholder form IDs with actual IDs
UPDATE dw_process_definitions 
SET bpmn_xml = REPLACE(
    REPLACE(
        REPLACE(bpmn_xml, 
            'FORM_ID_MAIN', 
            (SELECT id::text FROM dw_form_definitions WHERE form_name = 'Purchase Request Main Form' AND function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-purchase-request'))
        ),
        'FORM_ID_APPROVAL',
        (SELECT id::text FROM dw_form_definitions WHERE form_name = 'Approval Form' AND function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-purchase-request'))
    ),
    'FORM_ID_COUNTERSIGN',
    (SELECT id::text FROM dw_form_definitions WHERE form_name = 'Countersign Form' AND function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-purchase-request'))
)
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-purchase-request');
"@

[System.IO.File]::WriteAllText("04-08-process-v4.sql", $sql, [System.Text.Encoding]::UTF8)

Write-Host "SQL file generated: 04-08-process-v4.sql"
Write-Host "Base64 length: $($base64.Length)"
