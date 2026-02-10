-- =====================================================
-- 采购工作流 - 流程定义
-- =====================================================
-- 此脚本为采购工作流创建 BPMN 流程定义
-- 前置条件：需要先创建功能单元

-- =====================================================
-- 采购审批流程 (Purchase Approval Process)
-- =====================================================
INSERT INTO dw_process_definitions (
    function_unit_id,
    bpmn_xml,
    created_at,
    updated_at
)
SELECT 
    fu.id,
    '<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC"
             xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI"
             typeLanguage="http://www.w3.org/2001/XMLSchema"
             expressionLanguage="http://www.w3.org/1999/XPath"
             targetNamespace="http://workflow.platform.com/purchase">

  <process id="purchase_approval_process" name="采购审批流程" isExecutable="true">
    
    <!-- 开始事件 -->
    <startEvent id="startEvent" name="开始">
      <extensionElements>
        <flowable:formProperty id="request_no" name="申请编号" type="string" required="true"/>
        <flowable:formProperty id="title" name="申请标题" type="string" required="true"/>
        <flowable:formProperty id="department" name="申请部门" type="string" required="true"/>
        <flowable:formProperty id="applicant" name="申请人" type="string" required="true"/>
        <flowable:formProperty id="total_amount" name="总金额" type="double" required="true"/>
      </extensionElements>
    </startEvent>

    <!-- 部门经理审批 -->
    <userTask id="deptManagerApproval" name="部门经理审批" flowable:candidateGroups="MANAGERS">
      <extensionElements>
        <flowable:formProperty id="approval_result" name="审批结果" type="enum" required="true">
          <flowable:value id="approved" name="批准"/>
          <flowable:value id="rejected" name="拒绝"/>
        </flowable:formProperty>
        <flowable:formProperty id="approval_comments" name="审批意见" type="string"/>
      </extensionElements>
    </userTask>

    <!-- 排他网关：判断部门经理审批结果 -->
    <exclusiveGateway id="deptApprovalGateway" name="部门经理审批结果"/>

    <!-- 财务审批 -->
    <userTask id="financeApproval" name="财务审批" flowable:candidateGroups="MANAGERS">
      <extensionElements>
        <flowable:formProperty id="approval_result" name="审批结果" type="enum" required="true">
          <flowable:value id="approved" name="批准"/>
          <flowable:value id="rejected" name="拒绝"/>
        </flowable:formProperty>
        <flowable:formProperty id="approval_comments" name="审批意见" type="string"/>
      </extensionElements>
    </userTask>

    <!-- 排他网关：判断财务审批结果 -->
    <exclusiveGateway id="financeApprovalGateway" name="财务审批结果"/>

    <!-- 结束事件：审批通过 -->
    <endEvent id="approvedEndEvent" name="审批通过"/>

    <!-- 结束事件：审批拒绝 -->
    <endEvent id="rejectedEndEvent" name="审批拒绝"/>

    <!-- 流程连线 -->
    <sequenceFlow id="flow1" sourceRef="startEvent" targetRef="deptManagerApproval"/>
    <sequenceFlow id="flow2" sourceRef="deptManagerApproval" targetRef="deptApprovalGateway"/>
    
    <!-- 部门经理批准 -->
    <sequenceFlow id="flow3" name="批准" sourceRef="deptApprovalGateway" targetRef="financeApproval">
      <conditionExpression xsi:type="tFormalExpression">
        <![CDATA[${approval_result == ''approved''}]]>
      </conditionExpression>
    </sequenceFlow>
    
    <!-- 部门经理拒绝 -->
    <sequenceFlow id="flow4" name="拒绝" sourceRef="deptApprovalGateway" targetRef="rejectedEndEvent">
      <conditionExpression xsi:type="tFormalExpression">
        <![CDATA[${approval_result == ''rejected''}]]>
      </conditionExpression>
    </sequenceFlow>
    
    <sequenceFlow id="flow5" sourceRef="financeApproval" targetRef="financeApprovalGateway"/>
    
    <!-- 财务批准 -->
    <sequenceFlow id="flow6" name="批准" sourceRef="financeApprovalGateway" targetRef="approvedEndEvent">
      <conditionExpression xsi:type="tFormalExpression">
        <![CDATA[${approval_result == ''approved''}]]>
      </conditionExpression>
    </sequenceFlow>
    
    <!-- 财务拒绝 -->
    <sequenceFlow id="flow7" name="拒绝" sourceRef="financeApprovalGateway" targetRef="rejectedEndEvent">
      <conditionExpression xsi:type="tFormalExpression">
        <![CDATA[${approval_result == ''rejected''}]]>
      </conditionExpression>
    </sequenceFlow>

  </process>

  <!-- BPMN 图形信息 -->
  <bpmndi:BPMNDiagram id="BPMNDiagram_purchase_approval_process">
    <bpmndi:BPMNPlane bpmnElement="purchase_approval_process" id="BPMNPlane_purchase_approval_process">
      
      <!-- 开始事件 -->
      <bpmndi:BPMNShape bpmnElement="startEvent" id="BPMNShape_startEvent">
        <omgdc:Bounds height="30.0" width="30.0" x="100.0" y="163.0"/>
      </bpmndi:BPMNShape>
      
      <!-- 部门经理审批 -->
      <bpmndi:BPMNShape bpmnElement="deptManagerApproval" id="BPMNShape_deptManagerApproval">
        <omgdc:Bounds height="80.0" width="100.0" x="200.0" y="138.0"/>
      </bpmndi:BPMNShape>
      
      <!-- 部门审批网关 -->
      <bpmndi:BPMNShape bpmnElement="deptApprovalGateway" id="BPMNShape_deptApprovalGateway">
        <omgdc:Bounds height="40.0" width="40.0" x="370.0" y="158.0"/>
      </bpmndi:BPMNShape>
      
      <!-- 财务审批 -->
      <bpmndi:BPMNShape bpmnElement="financeApproval" id="BPMNShape_financeApproval">
        <omgdc:Bounds height="80.0" width="100.0" x="480.0" y="138.0"/>
      </bpmndi:BPMNShape>
      
      <!-- 财务审批网关 -->
      <bpmndi:BPMNShape bpmnElement="financeApprovalGateway" id="BPMNShape_financeApprovalGateway">
        <omgdc:Bounds height="40.0" width="40.0" x="650.0" y="158.0"/>
      </bpmndi:BPMNShape>
      
      <!-- 审批通过结束事件 -->
      <bpmndi:BPMNShape bpmnElement="approvedEndEvent" id="BPMNShape_approvedEndEvent">
        <omgdc:Bounds height="28.0" width="28.0" x="760.0" y="164.0"/>
      </bpmndi:BPMNShape>
      
      <!-- 审批拒绝结束事件 -->
      <bpmndi:BPMNShape bpmnElement="rejectedEndEvent" id="BPMNShape_rejectedEndEvent">
        <omgdc:Bounds height="28.0" width="28.0" x="656.0" y="280.0"/>
      </bpmndi:BPMNShape>
      
      <!-- 连线：开始 -> 部门经理审批 -->
      <bpmndi:BPMNEdge bpmnElement="flow1" id="BPMNEdge_flow1">
        <omgdi:waypoint x="130.0" y="178.0"/>
        <omgdi:waypoint x="200.0" y="178.0"/>
      </bpmndi:BPMNEdge>
      
      <!-- 连线：部门经理审批 -> 部门审批网关 -->
      <bpmndi:BPMNEdge bpmnElement="flow2" id="BPMNEdge_flow2">
        <omgdi:waypoint x="300.0" y="178.0"/>
        <omgdi:waypoint x="370.0" y="178.0"/>
      </bpmndi:BPMNEdge>
      
      <!-- 连线：部门审批网关 -> 财务审批（批准） -->
      <bpmndi:BPMNEdge bpmnElement="flow3" id="BPMNEdge_flow3">
        <omgdi:waypoint x="410.0" y="178.0"/>
        <omgdi:waypoint x="480.0" y="178.0"/>
      </bpmndi:BPMNEdge>
      
      <!-- 连线：部门审批网关 -> 拒绝结束（拒绝） -->
      <bpmndi:BPMNEdge bpmnElement="flow4" id="BPMNEdge_flow4">
        <omgdi:waypoint x="390.0" y="198.0"/>
        <omgdi:waypoint x="390.0" y="294.0"/>
        <omgdi:waypoint x="656.0" y="294.0"/>
      </bpmndi:BPMNEdge>
      
      <!-- 连线：财务审批 -> 财务审批网关 -->
      <bpmndi:BPMNEdge bpmnElement="flow5" id="BPMNEdge_flow5">
        <omgdi:waypoint x="580.0" y="178.0"/>
        <omgdi:waypoint x="650.0" y="178.0"/>
      </bpmndi:BPMNEdge>
      
      <!-- 连线：财务审批网关 -> 通过结束（批准） -->
      <bpmndi:BPMNEdge bpmnElement="flow6" id="BPMNEdge_flow6">
        <omgdi:waypoint x="690.0" y="178.0"/>
        <omgdi:waypoint x="760.0" y="178.0"/>
      </bpmndi:BPMNEdge>
      
      <!-- 连线：财务审批网关 -> 拒绝结束（拒绝） -->
      <bpmndi:BPMNEdge bpmnElement="flow7" id="BPMNEdge_flow7">
        <omgdi:waypoint x="670.0" y="198.0"/>
        <omgdi:waypoint x="670.0" y="280.0"/>
      </bpmndi:BPMNEdge>
      
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>

</definitions>',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM dw_function_units fu
WHERE fu.code = 'PURCHASE';

-- =====================================================
-- 验证插入结果
-- =====================================================
-- 查询流程定义
-- SELECT 
--     pd.id,
--     fu.name as function_unit_name,
--     LENGTH(pd.bpmn_xml) as bpmn_xml_length,
--     pd.created_at
-- FROM dw_process_definitions pd
-- JOIN dw_function_units fu ON pd.function_unit_id = fu.id
-- WHERE fu.code = 'PURCHASE';
