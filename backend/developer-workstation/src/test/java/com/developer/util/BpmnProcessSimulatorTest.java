package com.developer.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BpmnProcessSimulator")
class BpmnProcessSimulatorTest {

    private static final String LINEAR_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <bpmn:process id="Process_1" isExecutable="true">
                <bpmn:startEvent id="StartEvent_1" name="Start">
                  <bpmn:outgoing>Flow_1</bpmn:outgoing>
                </bpmn:startEvent>
                <bpmn:userTask id="Task_1" name="Review">
                  <bpmn:incoming>Flow_1</bpmn:incoming>
                  <bpmn:outgoing>Flow_2</bpmn:outgoing>
                </bpmn:userTask>
                <bpmn:endEvent id="EndEvent_1" name="End">
                  <bpmn:incoming>Flow_2</bpmn:incoming>
                </bpmn:endEvent>
                <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_1" />
                <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_1" targetRef="EndEvent_1" />
              </bpmn:process>
            </bpmn:definitions>
            """;

    private static final String GATEWAY_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <bpmn:process id="Process_1" isExecutable="true">
                <bpmn:startEvent id="StartEvent_1" name="Start" />
                <bpmn:exclusiveGateway id="Gateway_1" name="Approved?" />
                <bpmn:endEvent id="EndEvent_Approved" name="Approved" />
                <bpmn:endEvent id="EndEvent_Rejected" name="Rejected" />
                <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Gateway_1" />
                <bpmn:sequenceFlow id="Flow_Yes" sourceRef="Gateway_1" targetRef="EndEvent_Approved">
                  <bpmn:conditionExpression xsi:type="tFormalExpression">${approved == true}</bpmn:conditionExpression>
                </bpmn:sequenceFlow>
                <bpmn:sequenceFlow id="Flow_No" sourceRef="Gateway_1" targetRef="EndEvent_Rejected">
                  <bpmn:conditionExpression xsi:type="tFormalExpression">${approved == false}</bpmn:conditionExpression>
                </bpmn:sequenceFlow>
              </bpmn:process>
            </bpmn:definitions>
            """;

    @Test
    @DisplayName("Should simulate linear process with all steps")
    void shouldSimulateLinearProcess() {
        Map<String, Object> result = BpmnProcessSimulator.simulate(LINEAR_BPMN, Map.of("initiator", "admin"));

        assertThat(result.get("error")).isNull();
        assertThat(result.get("completed")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) result.get("steps");
        assertThat(steps).hasSize(3);
        assertThat(steps.get(0).get("nodeId")).isEqualTo("StartEvent_1");
        assertThat(steps.get(1).get("nodeName")).isEqualTo("Review");
        assertThat(steps.get(2).get("nodeType")).isEqualTo("endEvent");
    }

    @Test
    @DisplayName("Should follow gateway branch based on variables")
    void shouldEvaluateGatewayConditions() {
        Map<String, Object> approved = BpmnProcessSimulator.simulate(GATEWAY_BPMN, Map.of("approved", true));
        Map<String, Object> rejected = BpmnProcessSimulator.simulate(GATEWAY_BPMN, Map.of("approved", false));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> approvedSteps = (List<Map<String, Object>>) approved.get("steps");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rejectedSteps = (List<Map<String, Object>>) rejected.get("steps");

        assertThat(approvedSteps.get(approvedSteps.size() - 1).get("nodeId")).isEqualTo("EndEvent_Approved");
        assertThat(rejectedSteps.get(rejectedSteps.size() - 1).get("nodeId")).isEqualTo("EndEvent_Rejected");
    }

    @Test
    @DisplayName("Should return error when start event is missing")
    void shouldFailWithoutStartEvent() {
        String bpmn = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="Process_1">
                    <bpmn:endEvent id="EndEvent_1" />
                  </bpmn:process>
                </bpmn:definitions>
                """;

        Map<String, Object> result = BpmnProcessSimulator.simulate(bpmn, Map.of());
        assertThat(result.get("error")).isEqualTo("Process is missing a start event");
        assertThat(result.get("completed")).isEqualTo(false);
    }

    @Test
    @DisplayName("Should evaluate simple boolean condition helper")
    void shouldEvaluateSimpleConditions() {
        assertThat(BpmnProcessSimulator.evaluateSimpleCondition("${approved == true}", Map.of("approved", true))).isTrue();
        assertThat(BpmnProcessSimulator.evaluateSimpleCondition("${approved == false}", Map.of("approved", true))).isFalse();
    }
}
