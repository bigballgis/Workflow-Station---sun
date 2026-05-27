package com.developer.util;

import com.developer.entity.FieldDefinition;
import com.developer.enums.DataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BpmnProcessSimulator - Multi-Instance")
class BpmnProcessSimulatorMultiInstanceTest {

    private static final String MI_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:flowable="http://flowable.org/bpmn"
                              xmlns:custom="http://custom.namespace">
              <bpmn:process id="Process_1" isExecutable="true">
                <bpmn:startEvent id="StartEvent_1" name="Start" />
                <bpmn:subProcess id="MultiInstance_SubTable_100" name="Participants MI">
                  <bpmn:multiInstanceLoopCharacteristics isSequential="true">
                    <bpmn:extensionElements>
                      <flowable:collection>multiInstance_participants_collection</flowable:collection>
                      <flowable:elementVariable>currentItem</flowable:elementVariable>
                    </bpmn:extensionElements>
                  </bpmn:multiInstanceLoopCharacteristics>
                  <bpmn:startEvent id="MI_Start_100" name="MI Start" />
                  <bpmn:userTask id="MI_UserTask_100" name="Fill Information">
                    <bpmn:extensionElements>
                      <custom:properties>
                        <custom:property name="assigneeType" value="ELEMENT_VARIABLE" />
                        <custom:property name="subTableId" value="100" />
                        <custom:property name="assigneeField" value="assignee_id" />
                        <custom:property name="formId" value="200" />
                      </custom:properties>
                    </bpmn:extensionElements>
                  </bpmn:userTask>
                  <bpmn:endEvent id="MI_End_100" name="MI End" />
                  <bpmn:sequenceFlow id="MI_Flow_1" sourceRef="MI_Start_100" targetRef="MI_UserTask_100" />
                  <bpmn:sequenceFlow id="MI_Flow_2" sourceRef="MI_UserTask_100" targetRef="MI_End_100" />
                </bpmn:subProcess>
                <bpmn:endEvent id="EndEvent_1" name="End" />
                <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="MultiInstance_SubTable_100" />
                <bpmn:sequenceFlow id="Flow_2" sourceRef="MultiInstance_SubTable_100" targetRef="EndEvent_1" />
              </bpmn:process>
            </bpmn:definitions>
            """;

    @Test
    @DisplayName("Should sequentially expand all MI instances with auto-generated collection")
    void shouldExpandMultiInstanceSequentially() {
        Map<Long, List<FieldDefinition>> fieldsByTableId = Map.of(
                100L,
                List.of(
                        FieldDefinition.builder().fieldName("name").dataType(DataType.VARCHAR).build(),
                        FieldDefinition.builder().fieldName("assignee_id").dataType(DataType.VARCHAR).build()
                )
        );

        Map<String, Object> result = BpmnProcessSimulator.simulate(MI_BPMN, Map.of("initiator", "admin"), fieldsByTableId);

        assertThat(result.get("error")).isNull();
        assertThat(result.get("completed")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) result.get("steps");
        assertThat(steps).isNotEmpty();

        long userTaskSteps = steps.stream()
                .filter(s -> "MI_UserTask_100".equals(s.get("nodeId")))
                .count();
        assertThat(userTaskSteps).isEqualTo(3);

        @SuppressWarnings("unchecked")
        Map<String, Object> firstUserTask = steps.stream()
                .filter(s -> "MI_UserTask_100".equals(s.get("nodeId")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> miContext = (Map<String, Object>) firstUserTask.get("miContext");
        assertThat(miContext.get("instanceIndex")).isEqualTo(1);
        assertThat(miContext.get("totalInstances")).isEqualTo(3);
        @SuppressWarnings("unchecked")
        Map<String, Object> currentItem = (Map<String, Object>) miContext.get("currentItem");
        assertThat(currentItem.get("assignee_id")).isEqualTo("debug-user-1");

        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) result.get("variables");
        assertThat(variables.get("multiInstance_participants_collection")).isInstanceOf(List.class);
        assertThat(result.get("generatedCollections")).isNotNull();
    }

    @Test
    @DisplayName("Parallel MI should expand only one inner path to avoid step explosion")
    void shouldExpandParallelMultiInstanceWithSingleInnerPath() {
        String parallelBpmn = MI_BPMN.replace("isSequential=\"true\"", "isSequential=\"false\"");
        Map<Long, List<FieldDefinition>> fieldsByTableId = Map.of(
                100L,
                List.of(
                        FieldDefinition.builder().fieldName("name").dataType(DataType.VARCHAR).build(),
                        FieldDefinition.builder().fieldName("assignee_id").dataType(DataType.VARCHAR).build()
                )
        );

        Map<String, Object> result = BpmnProcessSimulator.simulate(parallelBpmn, Map.of("initiator", "admin"), fieldsByTableId);

        assertThat(result.get("error")).isNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) result.get("steps");
        long userTaskSteps = steps.stream()
                .filter(s -> "MI_UserTask_100".equals(s.get("nodeId")))
                .count();
        assertThat(userTaskSteps).isEqualTo(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> miContext = (Map<String, Object>) steps.stream()
                .filter(s -> "MI_UserTask_100".equals(s.get("nodeId")))
                .findFirst()
                .orElseThrow()
                .get("miContext");
        assertThat(miContext.get("parallelMode")).isEqualTo(true);
        assertThat(miContext.get("totalInstances")).isEqualTo(3);
    }

    @Test
    @DisplayName("Completion condition should stop sequential MI after enough instances")
    void shouldStopSequentialMultiInstanceWhenCompletionConditionMet() {
        String completionBpmn = MI_BPMN.replace(
                "</bpmn:extensionElements>",
                "</bpmn:extensionElements>\n"
                        + "      <bpmn:completionCondition xsi:type=\"bpmn:tFormalExpression\">"
                        + "${nrOfCompletedInstances >= 1}</bpmn:completionCondition>");
        Map<Long, List<FieldDefinition>> fieldsByTableId = Map.of(
                100L,
                List.of(
                        FieldDefinition.builder().fieldName("name").dataType(DataType.VARCHAR).build(),
                        FieldDefinition.builder().fieldName("assignee_id").dataType(DataType.VARCHAR).build()
                )
        );

        Map<String, Object> result = BpmnProcessSimulator.simulate(
                completionBpmn, Map.of("initiator", "admin"), fieldsByTableId);

        assertThat(result.get("error")).isNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) result.get("steps");
        long userTaskSteps = steps.stream()
                .filter(s -> "MI_UserTask_100".equals(s.get("nodeId")))
                .count();
        assertThat(userTaskSteps).isEqualTo(1);
    }

    @Test
    @DisplayName("Should evaluate ratio completion condition")
    void shouldEvaluateRatioCompletionCondition() {
        assertThat(BpmnProcessSimulator.shouldCompleteMultiInstance(
                "${nrOfCompletedInstances/nrOfInstances == 1}", 3, 3)).isTrue();
        assertThat(BpmnProcessSimulator.shouldCompleteMultiInstance(
                "${nrOfCompletedInstances/nrOfInstances == 1}", 1, 3)).isFalse();
        assertThat(BpmnProcessSimulator.shouldCompleteMultiInstance(
                "${nrOfCompletedInstances >= 1}", 1, 3)).isTrue();
    }
}
