-- =============================================================================
-- 16-meeting-participant-collection: Update BPMN XML with BPMNDiagram layout
-- 补充 bpmndi:BPMNDiagram 可视化布局信息，修复 "no diagram to display" 错误
-- =============================================================================

DO $diagram$
DECLARE
    v_function_unit_id     BIGINT;
    v_participant_table_id BIGINT;
    v_bpmn_xml             TEXT;
BEGIN
    SELECT id INTO v_function_unit_id FROM dw_function_units
    WHERE code = 'fu-20260403-a1b2c5';

    SELECT id INTO v_participant_table_id FROM dw_table_definitions
    WHERE function_unit_id = v_function_unit_id AND table_name = 'participants';

    v_bpmn_xml := '<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
    xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
    xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
    xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
    xmlns:flowable="http://flowable.org/bpmn"
    xmlns:custom="http://workflow.platform/schema/custom"
    xmlns:custom_1="http://custom.bpmn.io/schema"
    id="Definitions_MeetingParticipant"
    targetNamespace="http://bpmn.io/schema/bpmn">

  <bpmn:process id="MeetingParticipantCollectionProcess"
      name="Meeting Participant Info Collection Process" isExecutable="true">

    <bpmn:startEvent id="StartEvent_1" name="开始">
      <bpmn:outgoing>Flow_Start_Create</bpmn:outgoing>
    </bpmn:startEvent>

    <bpmn:userTask id="Task_CreateMeeting" name="创建会议">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="formId" value="21" />
          <custom_1:values name="formName" value="Create Meeting Form" />
        </custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_Start_Create</bpmn:incoming>
      <bpmn:outgoing>Flow_Create_Assign</bpmn:outgoing>
    </bpmn:userTask>

    <bpmn:userTask id="Task_AssignParticipants" name="分配参与人">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="formId" value="22" />
          <custom_1:values name="formName" value="Assign Participants Form" />
        </custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_Create_Assign</bpmn:incoming>
      <bpmn:outgoing>Flow_Assign_MI</bpmn:outgoing>
    </bpmn:userTask>

    <bpmn:subProcess id="MultiInstance_SubTable_31" name="多实例-参与人列表">
      <bpmn:multiInstanceLoopCharacteristics isSequential="false">
        <bpmn:extensionElements>
          <flowable:collection>multiInstance_participants_collection</flowable:collection>
          <flowable:elementVariable>currentItem</flowable:elementVariable>
        </bpmn:extensionElements>
      </bpmn:multiInstanceLoopCharacteristics>
      <bpmn:incoming>Flow_Assign_MI</bpmn:incoming>
      <bpmn:outgoing>Flow_MI_End</bpmn:outgoing>

      <bpmn:startEvent id="MI_Start_31" />
      <bpmn:userTask id="MI_UserTask_31" name="填写参会信息">
        <bpmn:extensionElements>
          <custom:properties>
            <custom:property name="assigneeType" value="ELEMENT_VARIABLE" />
            <custom:property name="subTableId" value="31" />
            <custom:property name="subTableName" value="participants" />
            <custom:property name="assigneeField" value="assignee_user_id" />
            <custom:property name="rowIdVariable" value="currentItem.rowId" />
            <custom:property name="formId" value="23" />
          </custom:properties>
          <custom_1:properties>
            <custom_1:values name="formId" value="23" />
            <custom_1:values name="formName" value="Participant Info Form" />
          </custom_1:properties>
        </bpmn:extensionElements>
      </bpmn:userTask>
      <bpmn:endEvent id="MI_End_31" />
      <bpmn:sequenceFlow id="MI_Flow1_31" sourceRef="MI_Start_31" targetRef="MI_UserTask_31" />
      <bpmn:sequenceFlow id="MI_Flow2_31" sourceRef="MI_UserTask_31" targetRef="MI_End_31" />
    </bpmn:subProcess>

    <bpmn:endEvent id="EndEvent_Complete" name="收集完成" />

    <bpmn:sequenceFlow id="Flow_Start_Create" sourceRef="StartEvent_1" targetRef="Task_CreateMeeting" />
    <bpmn:sequenceFlow id="Flow_Create_Assign" sourceRef="Task_CreateMeeting" targetRef="Task_AssignParticipants" />
    <bpmn:sequenceFlow id="Flow_Assign_MI" sourceRef="Task_AssignParticipants" targetRef="MultiInstance_SubTable_31" />
    <bpmn:sequenceFlow id="Flow_MI_End" sourceRef="MultiInstance_SubTable_31" targetRef="EndEvent_Complete" />

  </bpmn:process>

  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="MeetingParticipantCollectionProcess">

      <!-- StartEvent -->
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="152" y="102" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="158" y="145" width="24" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- Task: 创建会议 -->
      <bpmndi:BPMNShape id="Task_CreateMeeting_di" bpmnElement="Task_CreateMeeting">
        <dc:Bounds x="240" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>

      <!-- Task: 分配参与人 -->
      <bpmndi:BPMNShape id="Task_AssignParticipants_di" bpmnElement="Task_AssignParticipants">
        <dc:Bounds x="400" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>

      <!-- SubProcess: 多实例-参与人列表 -->
      <bpmndi:BPMNShape id="MultiInstance_SubTable_31_di" bpmnElement="MultiInstance_SubTable_31" isExpanded="true">
        <dc:Bounds x="560" y="40" width="300" height="160" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="580" y="46" width="120" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- SubProcess内部: StartEvent -->
      <bpmndi:BPMNShape id="MI_Start_31_di" bpmnElement="MI_Start_31">
        <dc:Bounds x="592" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>

      <!-- SubProcess内部: UserTask -->
      <bpmndi:BPMNShape id="MI_UserTask_31_di" bpmnElement="MI_UserTask_31">
        <dc:Bounds x="660" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>

      <!-- SubProcess内部: EndEvent -->
      <bpmndi:BPMNShape id="MI_End_31_di" bpmnElement="MI_End_31">
        <dc:Bounds x="792" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>

      <!-- EndEvent: 收集完成 -->
      <bpmndi:BPMNShape id="EndEvent_Complete_di" bpmnElement="EndEvent_Complete">
        <dc:Bounds x="922" y="102" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="916" y="145" width="48" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- Edges -->
      <bpmndi:BPMNEdge id="Flow_Start_Create_di" bpmnElement="Flow_Start_Create">
        <di:waypoint x="188" y="120" />
        <di:waypoint x="240" y="120" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="Flow_Create_Assign_di" bpmnElement="Flow_Create_Assign">
        <di:waypoint x="340" y="120" />
        <di:waypoint x="400" y="120" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="Flow_Assign_MI_di" bpmnElement="Flow_Assign_MI">
        <di:waypoint x="500" y="120" />
        <di:waypoint x="560" y="120" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="Flow_MI_End_di" bpmnElement="Flow_MI_End">
        <di:waypoint x="860" y="120" />
        <di:waypoint x="922" y="120" />
      </bpmndi:BPMNEdge>

      <!-- SubProcess内部 Edges -->
      <bpmndi:BPMNEdge id="MI_Flow1_31_di" bpmnElement="MI_Flow1_31">
        <di:waypoint x="628" y="120" />
        <di:waypoint x="660" y="120" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="MI_Flow2_31_di" bpmnElement="MI_Flow2_31">
        <di:waypoint x="760" y="120" />
        <di:waypoint x="792" y="120" />
      </bpmndi:BPMNEdge>

    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>

</bpmn:definitions>';

    DELETE FROM dw_process_definitions WHERE function_unit_id = v_function_unit_id;
    INSERT INTO dw_process_definitions (
        function_unit_id, function_unit_version_id, bpmn_xml, created_at, updated_at
    ) VALUES (
        v_function_unit_id, v_function_unit_id, v_bpmn_xml,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

    RAISE NOTICE 'BPMN XML updated with BPMNDiagram layout for fu-20260403-a1b2c5';
END $diagram$;
