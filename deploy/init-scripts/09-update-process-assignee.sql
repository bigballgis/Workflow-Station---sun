-- 09-update-process-assignee.sql
-- 更新流程定义，使用正确的任务分配配置
-- assigneeType: 
--   - initiator: 流程发起人
--   - user: 指定用户
--   - group: 指定组（部门或角色）
--   - manager: 发起人的直属上级
--   - expression: 表达式

UPDATE dw_process_definitions 
SET bpmn_xml = encode(convert_to('<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:camunda="http://camunda.org/schema/1.0/bpmn" xmlns:custom="http://custom.bpmn.io/schema" xmlns:flowable="http://flowable.org/bpmn" id="Definitions_LeaveRequest" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_LeaveRequest" name="请假申请流程" isExecutable="true">
    <bpmn:extensionElements>
      <custom:properties>
        <custom:property name="globalActionIds" value="[13,15]" />
        <custom:property name="globalActionNames" value="[&quot;撤回&quot;,&quot;取消&quot;]" />
        <custom:property name="description" value="员工请假申请流程，支持多级审批和HR复核" />
      </custom:properties>
    </bpmn:extensionElements>
    <bpmn:startEvent id="StartEvent_1" name="提交申请">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="Task_FillApplication" name="填写请假申请" flowable:assignee="${initiator}">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="3" />
          <custom:property name="formName" value="请假申请表单" />
          <custom:property name="actionIds" value="[12]" />
          <custom:property name="actionNames" value="[&quot;提交&quot;]" />
          <custom:property name="description" value="员工填写请假申请表单" />
          <custom:property name="assigneeType" value="initiator" />
          <custom:property name="assigneeLabel" value="流程发起人" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:exclusiveGateway id="Gateway_Duration" name="请假天数判断">
      <bpmn:incoming>Flow_2</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:userTask id="Task_ManagerApproval" name="直属领导审批" flowable:assignee="${initiatorManager}">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="3" />
          <custom:property name="formName" value="请假申请表单" />
          <custom:property name="formReadOnly" value="true" />
          <custom:property name="actionIds" value="[10,11,14]" />
          <custom:property name="actionNames" value="[&quot;批准&quot;,&quot;拒绝&quot;,&quot;驳回&quot;]" />
          <custom:property name="description" value="直属领导审批请假申请" />
          <custom:property name="assigneeType" value="manager" />
          <custom:property name="assigneeLabel" value="发起人的直属上级" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_3</bpmn:incoming>
      <bpmn:outgoing>Flow_5</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:exclusiveGateway id="Gateway_ManagerResult" name="领导审批结果">
      <bpmn:incoming>Flow_5</bpmn:incoming>
      <bpmn:outgoing>Flow_6</bpmn:outgoing>
      <bpmn:outgoing>Flow_8</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:userTask id="Task_HRApproval" name="HR审批" flowable:candidateGroups="dept_hr">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="3" />
          <custom:property name="formName" value="请假申请表单" />
          <custom:property name="formReadOnly" value="true" />
          <custom:property name="actionIds" value="[10,11,14]" />
          <custom:property name="actionNames" value="[&quot;批准&quot;,&quot;拒绝&quot;,&quot;驳回&quot;]" />
          <custom:property name="description" value="HR审批请假申请" />
          <custom:property name="assigneeType" value="group" />
          <custom:property name="assigneeValue" value="dept_hr" />
          <custom:property name="assigneeLabel" value="人力资源部" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_6</bpmn:incoming>
      <bpmn:outgoing>Flow_9</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:exclusiveGateway id="Gateway_HRResult" name="HR审批结果">
      <bpmn:incoming>Flow_9</bpmn:incoming>
      <bpmn:outgoing>Flow_10</bpmn:outgoing>
      <bpmn:outgoing>Flow_11</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:userTask id="Task_HRReview" name="HR复核" flowable:candidateGroups="dept_hr">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="3" />
          <custom:property name="formName" value="请假申请表单" />
          <custom:property name="formReadOnly" value="true" />
          <custom:property name="subFormId" value="4" />
          <custom:property name="subFormName" value="请假时间调整表单" />
          <custom:property name="actionIds" value="[16,17]" />
          <custom:property name="actionNames" value="[&quot;修改时间&quot;,&quot;确认复核&quot;]" />
          <custom:property name="description" value="HR复核请假信息，可调整请假时间" />
          <custom:property name="assigneeType" value="group" />
          <custom:property name="assigneeValue" value="dept_hr" />
          <custom:property name="assigneeLabel" value="人力资源部" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_10</bpmn:incoming>
      <bpmn:outgoing>Flow_14</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:serviceTask id="Task_SendNotification" name="发送审批结果通知">
      <bpmn:incoming>Flow_14</bpmn:incoming>
      <bpmn:outgoing>Flow_12</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:serviceTask id="Task_UpdateAttendance" name="更新考勤系统">
      <bpmn:incoming>Flow_12</bpmn:incoming>
      <bpmn:outgoing>Flow_13</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:endEvent id="EndEvent_Approved" name="审批通过">
      <bpmn:incoming>Flow_13</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:endEvent id="EndEvent_Rejected" name="审批拒绝">
      <bpmn:incoming>Flow_8</bpmn:incoming>
      <bpmn:incoming>Flow_11</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_FillApplication" />
    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_FillApplication" targetRef="Gateway_Duration" />
    <bpmn:sequenceFlow id="Flow_3" sourceRef="Gateway_Duration" targetRef="Task_ManagerApproval" />
    <bpmn:sequenceFlow id="Flow_5" sourceRef="Task_ManagerApproval" targetRef="Gateway_ManagerResult" />
    <bpmn:sequenceFlow id="Flow_6" name="需HR审批" sourceRef="Gateway_ManagerResult" targetRef="Task_HRApproval">
      <bpmn:conditionExpression>${approved == true}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_8" name="拒绝" sourceRef="Gateway_ManagerResult" targetRef="EndEvent_Rejected">
      <bpmn:conditionExpression>${approved == false}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_9" sourceRef="Task_HRApproval" targetRef="Gateway_HRResult" />
    <bpmn:sequenceFlow id="Flow_10" name="批准" sourceRef="Gateway_HRResult" targetRef="Task_HRReview">
      <bpmn:conditionExpression>${approved == true}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_11" name="拒绝" sourceRef="Gateway_HRResult" targetRef="EndEvent_Rejected">
      <bpmn:conditionExpression>${approved == false}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_14" sourceRef="Task_HRReview" targetRef="Task_SendNotification" />
    <bpmn:sequenceFlow id="Flow_12" sourceRef="Task_SendNotification" targetRef="Task_UpdateAttendance" />
    <bpmn:sequenceFlow id="Flow_13" sourceRef="Task_UpdateAttendance" targetRef="EndEvent_Approved" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_LeaveRequest">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="152" y="202" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="148" y="245" width="44" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_FillApplication_di" bpmnElement="Task_FillApplication">
        <dc:Bounds x="250" y="180" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_Duration_di" bpmnElement="Gateway_Duration" isMarkerVisible="true">
        <dc:Bounds x="415" y="195" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="403" y="252" width="77" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_ManagerApproval_di" bpmnElement="Task_ManagerApproval">
        <dc:Bounds x="530" y="180" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_ManagerResult_di" bpmnElement="Gateway_ManagerResult" isMarkerVisible="true">
        <dc:Bounds x="695" y="195" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="683" y="252" width="77" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_HRApproval_di" bpmnElement="Task_HRApproval">
        <dc:Bounds x="810" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_HRResult_di" bpmnElement="Gateway_HRResult" isMarkerVisible="true">
        <dc:Bounds x="975" y="95" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="966" y="65" width="66" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_HRReview_di" bpmnElement="Task_HRReview">
        <dc:Bounds x="1090" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_SendNotification_di" bpmnElement="Task_SendNotification">
        <dc:Bounds x="1260" y="180" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_UpdateAttendance_di" bpmnElement="Task_UpdateAttendance">
        <dc:Bounds x="1430" y="180" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_Approved_di" bpmnElement="EndEvent_Approved">
        <dc:Bounds x="1602" y="202" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="1598" y="245" width="44" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_Rejected_di" bpmnElement="EndEvent_Rejected">
        <dc:Bounds x="842" y="352" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="838" y="395" width="44" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <di:waypoint x="188" y="220" />
        <di:waypoint x="250" y="220" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2">
        <di:waypoint x="350" y="220" />
        <di:waypoint x="415" y="220" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_3_di" bpmnElement="Flow_3">
        <di:waypoint x="465" y="220" />
        <di:waypoint x="530" y="220" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_5_di" bpmnElement="Flow_5">
        <di:waypoint x="630" y="220" />
        <di:waypoint x="695" y="220" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_6_di" bpmnElement="Flow_6">
        <di:waypoint x="720" y="195" />
        <di:waypoint x="720" y="120" />
        <di:waypoint x="810" y="120" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="730" y="133" width="55" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_8_di" bpmnElement="Flow_8">
        <di:waypoint x="720" y="245" />
        <di:waypoint x="720" y="370" />
        <di:waypoint x="842" y="370" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="730" y="353" width="22" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_9_di" bpmnElement="Flow_9">
        <di:waypoint x="910" y="120" />
        <di:waypoint x="975" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_10_di" bpmnElement="Flow_10">
        <di:waypoint x="1025" y="120" />
        <di:waypoint x="1090" y="120" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="1048" y="102" width="22" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_11_di" bpmnElement="Flow_11">
        <di:waypoint x="1000" y="145" />
        <di:waypoint x="1000" y="370" />
        <di:waypoint x="878" y="370" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="910" y="353" width="22" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_14_di" bpmnElement="Flow_14">
        <di:waypoint x="1140" y="160" />
        <di:waypoint x="1140" y="220" />
        <di:waypoint x="1260" y="220" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_12_di" bpmnElement="Flow_12">
        <di:waypoint x="1360" y="220" />
        <di:waypoint x="1430" y="220" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_13_di" bpmnElement="Flow_13">
        <di:waypoint x="1530" y="220" />
        <di:waypoint x="1602" y="220" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>', 'UTF8'), 'base64')
WHERE function_unit_id = 1;
