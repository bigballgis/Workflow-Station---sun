-- =============================================================================
-- 16-meeting-participant-collection: Insert BPMN Process Definition
-- 会议参与人信息收集：包含多实例子流程的 BPMN 流程定义
--
-- 流程：StartEvent → 创建会议 → 分配参与人 → 多实例子流程(填写参会信息) → 
--       收集完成 → EndEvent
--
-- Dependencies: 00-create-function-unit.sql, 01-create-tables.sql
-- Execution order: 00 → 01 → 02 → 03 → 04
-- =============================================================================

DO $main$
DECLARE
    v_function_unit_id      BIGINT;
    v_create_form_id        BIGINT;
    v_assign_form_id        BIGINT;
    v_participant_form_id   BIGINT;
    v_action_submit         BIGINT;
    v_action_complete       BIGINT;
    v_action_submit_info    BIGINT;
    v_participant_table_id  BIGINT;
    v_bpmn_xml              TEXT;
BEGIN
    SELECT id INTO v_function_unit_id FROM dw_function_units WHERE code = 'fu-20260403-a1b2c5';
    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit fu-20260403-a1b2c5 not found.';
    END IF;

    SELECT id INTO v_create_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_function_unit_id AND form_name = 'Create Meeting Form';

    SELECT id INTO v_assign_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_function_unit_id AND form_name = 'Assign Participants Form';

    SELECT id INTO v_participant_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_function_unit_id AND form_name = 'Participant Info Form';

    SELECT id INTO v_action_submit FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = '提交会议';

    SELECT id INTO v_action_complete FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = '完成分配';

    SELECT id INTO v_action_submit_info FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = '提交参会信息';

    SELECT id INTO v_participant_table_id FROM dw_table_definitions
    WHERE function_unit_id = v_function_unit_id AND table_name = 'participants';

    -- Build BPMN XML with actual IDs
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

    <!-- ===== Start Event ===== -->
    <bpmn:startEvent id="StartEvent_1" name="开始">
      <bpmn:outgoing>Flow_Start_Create</bpmn:outgoing>
    </bpmn:startEvent>

    <!-- ===== Task 1: 创建会议 ===== -->
    <bpmn:userTask id="Task_CreateMeeting" name="创建会议">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[' || v_action_submit || ']" />
          <custom_1:values name="actionNames" value="[&quot;提交会议&quot;]" />
          <custom_1:values name="formId" value="' || v_create_form_id || '" />
          <custom_1:values name="formName" value="Create Meeting Form" />
        </custom_1:properties>
        <custom:properties>
          <custom:property name="assigneeType" value="INITIATOR" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_Start_Create</bpmn:incoming>
      <bpmn:outgoing>Flow_Create_Assign</bpmn:outgoing>
    </bpmn:userTask>

    <!-- ===== Task 2: 分配参与人 ===== -->
    <bpmn:userTask id="Task_AssignParticipants" name="分配参与人">
      <bpmn:extensionElements>
        <custom_1:properties>
          <custom_1:values name="actionIds" value="[' || v_action_complete || ']" />
          <custom_1:values name="actionNames" value="[&quot;完成分配&quot;]" />
          <custom_1:values name="formId" value="' || v_assign_form_id || '" />
          <custom_1:values name="formName" value="Assign Participants Form" />
        </custom_1:properties>
        <custom:properties>
          <custom:property name="assigneeType" value="INITIATOR" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_Create_Assign</bpmn:incoming>
      <bpmn:outgoing>Flow_Assign_MI</bpmn:outgoing>
    </bpmn:userTask>';

    -- Multi-instance subprocess (appended separately for readability)
    v_bpmn_xml := v_bpmn_xml || '

    <!-- ===== 多实例子流程：填写参会信息 ===== -->
    <bpmn:subProcess id="MultiInstance_SubTable_' || v_participant_table_id || '"
        name="多实例-参与人列表">
      <bpmn:multiInstanceLoopCharacteristics isSequential="false"
          flowable:collection="multiInstance_participants_collection"
          flowable:elementVariable="currentItem" />

      <bpmn:startEvent id="MI_Start_' || v_participant_table_id || '" />

      <bpmn:userTask id="MI_UserTask_' || v_participant_table_id || '" name="填写参会信息">
        <bpmn:extensionElements>
          <custom:properties>
            <custom:property name="assigneeType" value="ELEMENT_VARIABLE" />
            <custom:property name="subTableId" value="' || v_participant_table_id || '" />
            <custom:property name="subTableName" value="participants" />
            <custom:property name="assigneeField" value="assignee_user_id" />
            <custom:property name="rowIdVariable" value="currentItem.rowId" />
            <custom:property name="formId" value="' || v_participant_form_id || '" />
          </custom:properties>
          <custom_1:properties>
            <custom_1:values name="actionIds" value="[' || v_action_submit_info || ']" />
            <custom_1:values name="actionNames" value="[&quot;提交参会信息&quot;]" />
            <custom_1:values name="formId" value="' || v_participant_form_id || '" />
            <custom_1:values name="formName" value="Participant Info Form" />
          </custom_1:properties>
        </bpmn:extensionElements>
      </bpmn:userTask>

      <bpmn:endEvent id="MI_End_' || v_participant_table_id || '" />

      <bpmn:sequenceFlow id="MI_Flow1_' || v_participant_table_id || '"
          sourceRef="MI_Start_' || v_participant_table_id || '"
          targetRef="MI_UserTask_' || v_participant_table_id || '" />
      <bpmn:sequenceFlow id="MI_Flow2_' || v_participant_table_id || '"
          sourceRef="MI_UserTask_' || v_participant_table_id || '"
          targetRef="MI_End_' || v_participant_table_id || '" />
    </bpmn:subProcess>';

    -- End event and sequence flows
    v_bpmn_xml := v_bpmn_xml || '

    <!-- ===== End Event ===== -->
    <bpmn:endEvent id="EndEvent_Complete" name="收集完成" />

    <!-- ===== Sequence Flows ===== -->
    <bpmn:sequenceFlow id="Flow_Start_Create"
        sourceRef="StartEvent_1" targetRef="Task_CreateMeeting" />
    <bpmn:sequenceFlow id="Flow_Create_Assign"
        sourceRef="Task_CreateMeeting" targetRef="Task_AssignParticipants" />
    <bpmn:sequenceFlow id="Flow_Assign_MI"
        sourceRef="Task_AssignParticipants"
        targetRef="MultiInstance_SubTable_' || v_participant_table_id || '" />
    <bpmn:sequenceFlow id="Flow_MI_End"
        sourceRef="MultiInstance_SubTable_' || v_participant_table_id || '"
        targetRef="EndEvent_Complete" />

  </bpmn:process>
</bpmn:definitions>';

    -- Insert or update process definition
    DELETE FROM dw_process_definitions WHERE function_unit_id = v_function_unit_id;

    INSERT INTO dw_process_definitions (
        function_unit_id, function_unit_version_id, bpmn_xml, created_at, updated_at
    ) VALUES (
        v_function_unit_id, v_function_unit_id, v_bpmn_xml,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

    RAISE NOTICE '========================================';
    RAISE NOTICE 'BPMN Process Definition Complete!';
    RAISE NOTICE 'Process: MeetingParticipantCollectionProcess';
    RAISE NOTICE 'Multi-instance subTable ID: %', v_participant_table_id;
    RAISE NOTICE 'Next: run 03-form-table-bindings.sql';
    RAISE NOTICE '========================================';

END $main$;
