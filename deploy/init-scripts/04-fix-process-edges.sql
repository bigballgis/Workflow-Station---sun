-- 修复流程定义中缺失的BPMNEdge（连线图形信息）
UPDATE dw_process_definitions 
SET bpmn_xml = '<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:camunda="http://camunda.org/schema/1.0/bpmn" id="Definitions_LeaveRequest" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_LeaveRequest" name="请假申请流程" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="提交申请">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="Task_FillApplication" name="填写请假申请">
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:exclusiveGateway id="Gateway_Duration" name="请假天数判断">
      <bpmn:incoming>Flow_2</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:userTask id="Task_ManagerApproval" name="直属领导审批">
      <bpmn:incoming>Flow_3</bpmn:incoming>
      <bpmn:outgoing>Flow_5</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:exclusiveGateway id="Gateway_ManagerResult" name="领导审批结果">
      <bpmn:incoming>Flow_5</bpmn:incoming>
      <bpmn:outgoing>Flow_6</bpmn:outgoing>
      <bpmn:outgoing>Flow_8</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:userTask id="Task_HRApproval" name="HR审批">
      <bpmn:incoming>Flow_6</bpmn:incoming>
      <bpmn:outgoing>Flow_9</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:exclusiveGateway id="Gateway_HRResult" name="HR审批结果">
      <bpmn:incoming>Flow_9</bpmn:incoming>
      <bpmn:outgoing>Flow_10</bpmn:outgoing>
      <bpmn:outgoing>Flow_11</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:serviceTask id="Task_SendNotification" name="发送审批结果通知">
      <bpmn:incoming>Flow_10</bpmn:incoming>
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
    <bpmn:sequenceFlow id="Flow_6" name="需HR审批" sourceRef="Gateway_ManagerResult" targetRef="Task_HRApproval" />
    <bpmn:sequenceFlow id="Flow_8" name="拒绝" sourceRef="Gateway_ManagerResult" targetRef="EndEvent_Rejected" />
    <bpmn:sequenceFlow id="Flow_9" sourceRef="Task_HRApproval" targetRef="Gateway_HRResult" />
    <bpmn:sequenceFlow id="Flow_10" name="批准" sourceRef="Gateway_HRResult" targetRef="Task_SendNotification" />
    <bpmn:sequenceFlow id="Flow_11" name="拒绝" sourceRef="Gateway_HRResult" targetRef="EndEvent_Rejected" />
    <bpmn:sequenceFlow id="Flow_12" sourceRef="Task_SendNotification" targetRef="Task_UpdateAttendance" />
    <bpmn:sequenceFlow id="Flow_13" sourceRef="Task_UpdateAttendance" targetRef="EndEvent_Approved" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_LeaveRequest">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="152" y="202" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_FillApplication_di" bpmnElement="Task_FillApplication">
        <dc:Bounds x="240" y="180" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_Duration_di" bpmnElement="Gateway_Duration" isMarkerVisible="true">
        <dc:Bounds x="395" y="195" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_ManagerApproval_di" bpmnElement="Task_ManagerApproval">
        <dc:Bounds x="500" y="180" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_ManagerResult_di" bpmnElement="Gateway_ManagerResult" isMarkerVisible="true">
        <dc:Bounds x="655" y="195" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_HRApproval_di" bpmnElement="Task_HRApproval">
        <dc:Bounds x="760" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_HRResult_di" bpmnElement="Gateway_HRResult" isMarkerVisible="true">
        <dc:Bounds x="915" y="95" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_SendNotification_di" bpmnElement="Task_SendNotification">
        <dc:Bounds x="890" y="180" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_UpdateAttendance_di" bpmnElement="Task_UpdateAttendance">
        <dc:Bounds x="1040" y="180" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_Approved_di" bpmnElement="EndEvent_Approved">
        <dc:Bounds x="1192" y="202" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_Rejected_di" bpmnElement="EndEvent_Rejected">
        <dc:Bounds x="792" y="302" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <di:waypoint x="188" y="220" />
        <di:waypoint x="240" y="220" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2">
        <di:waypoint x="340" y="220" />
        <di:waypoint x="395" y="220" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_3_di" bpmnElement="Flow_3">
        <di:waypoint x="445" y="220" />
        <di:waypoint x="500" y="220" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_5_di" bpmnElement="Flow_5">
        <di:waypoint x="600" y="220" />
        <di:waypoint x="655" y="220" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_6_di" bpmnElement="Flow_6">
        <di:waypoint x="680" y="195" />
        <di:waypoint x="680" y="120" />
        <di:waypoint x="760" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_8_di" bpmnElement="Flow_8">
        <di:waypoint x="680" y="245" />
        <di:waypoint x="680" y="320" />
        <di:waypoint x="792" y="320" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_9_di" bpmnElement="Flow_9">
        <di:waypoint x="860" y="120" />
        <di:waypoint x="915" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_10_di" bpmnElement="Flow_10">
        <di:waypoint x="940" y="145" />
        <di:waypoint x="940" y="180" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_11_di" bpmnElement="Flow_11">
        <di:waypoint x="940" y="145" />
        <di:waypoint x="940" y="320" />
        <di:waypoint x="828" y="320" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_12_di" bpmnElement="Flow_12">
        <di:waypoint x="990" y="220" />
        <di:waypoint x="1040" y="220" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_13_di" bpmnElement="Flow_13">
        <di:waypoint x="1140" y="220" />
        <di:waypoint x="1192" y="220" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>'
WHERE function_unit_id = 1;
