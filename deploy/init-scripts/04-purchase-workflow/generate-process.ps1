# 采购申请流程 - 生成流程定义SQL
# 使用UTF-8编码生成包含Base64 BPMN的SQL文件

$bpmnXml = @'
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions 
    xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" 
    xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
    xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
    xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:custom="http://custom.bpmn.io/schema"
    id="Definitions_Purchase" 
    targetNamespace="http://workflow.example.com/purchase">
  <bpmn:process id="Process_Purchase" name="采购申请流程" isExecutable="true">
    <bpmn:extensionElements>
      <custom:properties>
        <custom:property name="globalActionIds" value="[6,7,8]"/>
      </custom:properties>
    </bpmn:extensionElements>
    
    <bpmn:startEvent id="StartEvent_1" name="开始">
      <bpmn:outgoing>Flow_Start_Submit</bpmn:outgoing>
    </bpmn:startEvent>
    
    <!-- 任务1: 提交采购申请 - INITIATOR -->
    <bpmn:userTask id="Task_Submit" name="提交采购申请">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="1"/>
          <custom:property name="formName" value="采购申请主表单"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[1,12]"/>
          <custom:property name="assigneeType" value="INITIATOR"/>
          <custom:property name="assigneeLabel" value="流程发起人"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_Start_Submit</bpmn:incoming>
      <bpmn:outgoing>Flow_Submit_Gateway1</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- 网关1: 金额判断 -->
    <bpmn:exclusiveGateway id="Gateway_Amount" name="金额判断">
      <bpmn:incoming>Flow_Submit_Gateway1</bpmn:incoming>
      <bpmn:outgoing>Flow_Small_DeptOthers</bpmn:outgoing>
      <bpmn:outgoing>Flow_Medium_EntityMgr</bpmn:outgoing>
      <bpmn:outgoing>Flow_Large_FuncMgr</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    
    <!-- 任务2: 部门审核 - DEPT_OTHERS -->
    <bpmn:userTask id="Task_DeptReview" name="部门审核">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="3"/>
          <custom:property name="formName" value="审批表单"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,4,5]"/>
          <custom:property name="assigneeType" value="DEPT_OTHERS"/>
          <custom:property name="assigneeLabel" value="本部门其他人"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_Small_DeptOthers</bpmn:incoming>
      <bpmn:outgoing>Flow_DeptReview_End</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- 任务3: 实体经理审批 - ENTITY_MANAGER -->
    <bpmn:userTask id="Task_EntityMgr" name="实体经理审批">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="3"/>
          <custom:property name="formName" value="审批表单"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,4,5,13]"/>
          <custom:property name="assigneeType" value="ENTITY_MANAGER"/>
          <custom:property name="assigneeLabel" value="实体经理"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_Medium_EntityMgr</bpmn:incoming>
      <bpmn:outgoing>Flow_EntityMgr_ParentDept</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- 任务4: 上级部门审批 - PARENT_DEPT -->
    <bpmn:userTask id="Task_ParentDept" name="上级部门审批">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="3"/>
          <custom:property name="formName" value="审批表单"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,4,5]"/>
          <custom:property name="assigneeType" value="PARENT_DEPT"/>
          <custom:property name="assigneeLabel" value="上级部门"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_EntityMgr_ParentDept</bpmn:incoming>
      <bpmn:outgoing>Flow_ParentDept_End</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- 任务5: 职能经理审批 - FUNCTION_MANAGER -->
    <bpmn:userTask id="Task_FuncMgr" name="职能经理审批">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="3"/>
          <custom:property name="formName" value="审批表单"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,4,5,13]"/>
          <custom:property name="assigneeType" value="FUNCTION_MANAGER"/>
          <custom:property name="assigneeLabel" value="职能经理"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_Large_FuncMgr</bpmn:incoming>
      <bpmn:outgoing>Flow_FuncMgr_FixedDept</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- 任务6: 财务部审核 - FIXED_DEPT -->
    <bpmn:userTask id="Task_Finance" name="财务部审核">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="3"/>
          <custom:property name="formName" value="审批表单"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,4,5]"/>
          <custom:property name="assigneeType" value="FIXED_DEPT"/>
          <custom:property name="assigneeLabel" value="财务部"/>
          <custom:property name="assigneeValue" value="dept-finance"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_FuncMgr_FixedDept</bpmn:incoming>
      <bpmn:outgoing>Flow_Finance_VirtualGroup</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- 任务7: 采购决策委员会审核 - VIRTUAL_GROUP -->
    <bpmn:userTask id="Task_Committee" name="采购决策委员会审核">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="3"/>
          <custom:property name="formName" value="审批表单"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,4,5]"/>
          <custom:property name="assigneeType" value="VIRTUAL_GROUP"/>
          <custom:property name="assigneeLabel" value="采购决策委员会"/>
          <custom:property name="assigneeValue" value="vg-purchase-committee"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_Finance_VirtualGroup</bpmn:incoming>
      <bpmn:outgoing>Flow_Committee_End</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- 合并网关 -->
    <bpmn:exclusiveGateway id="Gateway_Merge" name="合并">
      <bpmn:incoming>Flow_DeptReview_End</bpmn:incoming>
      <bpmn:incoming>Flow_ParentDept_End</bpmn:incoming>
      <bpmn:incoming>Flow_Committee_End</bpmn:incoming>
      <bpmn:outgoing>Flow_Merge_End</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    
    <bpmn:endEvent id="EndEvent_1" name="结束">
      <bpmn:incoming>Flow_Merge_End</bpmn:incoming>
    </bpmn:endEvent>
    
    <!-- 顺序流 -->
    <bpmn:sequenceFlow id="Flow_Start_Submit" sourceRef="StartEvent_1" targetRef="Task_Submit"/>
    <bpmn:sequenceFlow id="Flow_Submit_Gateway1" sourceRef="Task_Submit" targetRef="Gateway_Amount"/>
    <bpmn:sequenceFlow id="Flow_Small_DeptOthers" name="金额小于1万" sourceRef="Gateway_Amount" targetRef="Task_DeptReview">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${total_amount &lt; 10000}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_Medium_EntityMgr" name="1万到10万" sourceRef="Gateway_Amount" targetRef="Task_EntityMgr">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${total_amount >= 10000 &amp;&amp; total_amount &lt; 100000}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_Large_FuncMgr" name="金额大于10万" sourceRef="Gateway_Amount" targetRef="Task_FuncMgr">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${total_amount >= 100000}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_DeptReview_End" sourceRef="Task_DeptReview" targetRef="Gateway_Merge"/>
    <bpmn:sequenceFlow id="Flow_EntityMgr_ParentDept" sourceRef="Task_EntityMgr" targetRef="Task_ParentDept"/>
    <bpmn:sequenceFlow id="Flow_ParentDept_End" sourceRef="Task_ParentDept" targetRef="Gateway_Merge"/>
    <bpmn:sequenceFlow id="Flow_FuncMgr_FixedDept" sourceRef="Task_FuncMgr" targetRef="Task_Finance"/>
    <bpmn:sequenceFlow id="Flow_Finance_VirtualGroup" sourceRef="Task_Finance" targetRef="Task_Committee"/>
    <bpmn:sequenceFlow id="Flow_Committee_End" sourceRef="Task_Committee" targetRef="Gateway_Merge"/>
    <bpmn:sequenceFlow id="Flow_Merge_End" sourceRef="Gateway_Merge" targetRef="EndEvent_1"/>
  </bpmn:process>
  
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_Purchase">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="100" y="250" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="103" y="293" width="30" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Submit_di" bpmnElement="Task_Submit">
        <dc:Bounds x="200" y="228" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_Amount_di" bpmnElement="Gateway_Amount" isMarkerVisible="true">
        <dc:Bounds x="365" y="243" width="50" height="50"/>
        <bpmndi:BPMNLabel><dc:Bounds x="361" y="300" width="58" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_DeptReview_di" bpmnElement="Task_DeptReview">
        <dc:Bounds x="480" y="80" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_EntityMgr_di" bpmnElement="Task_EntityMgr">
        <dc:Bounds x="480" y="228" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_ParentDept_di" bpmnElement="Task_ParentDept">
        <dc:Bounds x="640" y="228" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_FuncMgr_di" bpmnElement="Task_FuncMgr">
        <dc:Bounds x="480" y="380" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Finance_di" bpmnElement="Task_Finance">
        <dc:Bounds x="640" y="380" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Committee_di" bpmnElement="Task_Committee">
        <dc:Bounds x="800" y="380" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_Merge_di" bpmnElement="Gateway_Merge" isMarkerVisible="true">
        <dc:Bounds x="925" y="243" width="50" height="50"/>
        <bpmndi:BPMNLabel><dc:Bounds x="933" y="300" width="34" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="1032" y="250" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="1035" y="293" width="30" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_Start_Submit_di" bpmnElement="Flow_Start_Submit">
        <di:waypoint x="136" y="268"/>
        <di:waypoint x="200" y="268"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Submit_Gateway1_di" bpmnElement="Flow_Submit_Gateway1">
        <di:waypoint x="300" y="268"/>
        <di:waypoint x="365" y="268"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Small_DeptOthers_di" bpmnElement="Flow_Small_DeptOthers">
        <di:waypoint x="390" y="243"/>
        <di:waypoint x="390" y="120"/>
        <di:waypoint x="480" y="120"/>
        <bpmndi:BPMNLabel><dc:Bounds x="400" y="103" width="50" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Medium_EntityMgr_di" bpmnElement="Flow_Medium_EntityMgr">
        <di:waypoint x="415" y="268"/>
        <di:waypoint x="480" y="268"/>
        <bpmndi:BPMNLabel><dc:Bounds x="410" y="250" width="80" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Large_FuncMgr_di" bpmnElement="Flow_Large_FuncMgr">
        <di:waypoint x="390" y="293"/>
        <di:waypoint x="390" y="420"/>
        <di:waypoint x="480" y="420"/>
        <bpmndi:BPMNLabel><dc:Bounds x="400" y="403" width="55" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_DeptReview_End_di" bpmnElement="Flow_DeptReview_End">
        <di:waypoint x="580" y="120"/>
        <di:waypoint x="950" y="120"/>
        <di:waypoint x="950" y="243"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_EntityMgr_ParentDept_di" bpmnElement="Flow_EntityMgr_ParentDept">
        <di:waypoint x="580" y="268"/>
        <di:waypoint x="640" y="268"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_ParentDept_End_di" bpmnElement="Flow_ParentDept_End">
        <di:waypoint x="740" y="268"/>
        <di:waypoint x="925" y="268"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_FuncMgr_FixedDept_di" bpmnElement="Flow_FuncMgr_FixedDept">
        <di:waypoint x="580" y="420"/>
        <di:waypoint x="640" y="420"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Finance_VirtualGroup_di" bpmnElement="Flow_Finance_VirtualGroup">
        <di:waypoint x="740" y="420"/>
        <di:waypoint x="800" y="420"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Committee_End_di" bpmnElement="Flow_Committee_End">
        <di:waypoint x="900" y="420"/>
        <di:waypoint x="950" y="420"/>
        <di:waypoint x="950" y="293"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Merge_End_di" bpmnElement="Flow_Merge_End">
        <di:waypoint x="975" y="268"/>
        <di:waypoint x="1032" y="268"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
'@

# 转换为Base64
$base64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($bpmnXml))

# 生成SQL
$sql = @"
-- =====================================================
-- 采购申请 - 流程定义
-- =====================================================

-- 流程定义
INSERT INTO dw_process_definitions (function_unit_id, process_key, process_name, description, bpmn_xml, status, created_by)
SELECT f.id, 'Process_Purchase', '采购申请流程', '采购申请流程，支持金额分级审批，覆盖所有7种任务分配类型', 
'$base64',
'DRAFT', 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 流程版本
INSERT INTO dw_process_versions (process_definition_id, version_number, bpmn_xml, change_log, created_by)
SELECT pd.id, 1, pd.bpmn_xml, '初始版本：支持金额分级审批，覆盖7种任务分配类型', 'system'
FROM dw_process_definitions pd
JOIN dw_function_units f ON pd.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request';
"@

# 输出到文件（使用UTF-8编码）
$sql | Out-File -FilePath "04-08-process.sql" -Encoding UTF8

Write-Host "SQL file generated: 04-08-process.sql"
Write-Host "Base64 length: $($base64.Length)"
