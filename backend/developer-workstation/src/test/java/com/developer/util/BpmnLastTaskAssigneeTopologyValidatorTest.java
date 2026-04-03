package com.developer.util;

import com.developer.dto.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BpmnLastTaskAssigneeTopologyValidatorTest {

    private static final String HEADER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
              xmlns:custom="http://custom.bpmn.io/schema">
            <bpmn:process id="Process_1" isExecutable="true">
            """;

    private static final String FOOTER = """
            </bpmn:process>
            </bpmn:definitions>
            """;

    @Test
    @DisplayName("单入线 + LAST_TASK_ASSIGNEE 应通过")
    void validSingleIncoming() {
        String xml = HEADER + """
                <bpmn:userTask id="Task_1" name="审批">
                  <bpmn:incoming>Flow_1</bpmn:incoming>
                  <bpmn:extensionElements>
                    <custom:properties>
                      <custom:property name="assigneeAnchor" value="LAST_TASK_ASSIGNEE" />
                    </custom:properties>
                  </bpmn:extensionElements>
                </bpmn:userTask>
                """ + FOOTER;
        ValidationResult r = BpmnLastTaskAssigneeTopologyValidator.validate(xml);
        assertTrue(r.isValid(), r.getErrors().toString());
    }

    @Test
    @DisplayName("双入线 + LAST_TASK_ASSIGNEE 应失败")
    void invalidTwoIncoming() {
        String xml = HEADER + """
                <bpmn:userTask id="Task_1" name="审批">
                  <bpmn:incoming>Flow_1</bpmn:incoming>
                  <bpmn:incoming>Flow_2</bpmn:incoming>
                  <bpmn:extensionElements>
                    <custom:properties>
                      <custom:property name="assigneeAnchor" value="LAST_TASK_ASSIGNEE" />
                    </custom:properties>
                  </bpmn:extensionElements>
                </bpmn:userTask>
                """ + FOOTER;
        ValidationResult r = BpmnLastTaskAssigneeTopologyValidator.validate(xml);
        assertFalse(r.isValid());
        assertEquals(1, r.getErrors().size());
        assertEquals("LAST_TASK_ANCHOR_NOT_SINGLE_INCOMING", r.getErrors().get(0).getCode());
    }

    @Test
    @DisplayName("无 incoming + LAST_TASK_ASSIGNEE 应失败")
    void invalidZeroIncoming() {
        String xml = HEADER + """
                <bpmn:userTask id="Task_1" name="审批">
                  <bpmn:extensionElements>
                    <custom:properties>
                      <custom:property name="assigneeAnchor" value="LAST_TASK_ASSIGNEE" />
                    </custom:properties>
                  </bpmn:extensionElements>
                </bpmn:userTask>
                """ + FOOTER;
        ValidationResult r = BpmnLastTaskAssigneeTopologyValidator.validate(xml);
        assertFalse(r.isValid());
    }

    @Test
    @DisplayName("双入线但锚点为 INITIATOR 应通过")
    void twoIncomingInitiatorAnchorOk() {
        String xml = HEADER + """
                <bpmn:userTask id="Task_1" name="审批">
                  <bpmn:incoming>Flow_1</bpmn:incoming>
                  <bpmn:incoming>Flow_2</bpmn:incoming>
                  <bpmn:extensionElements>
                    <custom:properties>
                      <custom:property name="assigneeAnchor" value="INITIATOR" />
                    </custom:properties>
                  </bpmn:extensionElements>
                </bpmn:userTask>
                """ + FOOTER;
        ValidationResult r = BpmnLastTaskAssigneeTopologyValidator.validate(xml);
        assertTrue(r.isValid(), r.getErrors().toString());
    }
}
