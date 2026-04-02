package com.developer.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Manual verification test for the property-based test logic.
 * This test manually verifies a few sample configurations to ensure
 * the property test assertions are correct.
 */
class BpmnXmlGeneratorPropertyTestManualVerification {

    @Test
    void verifyPropertyTestLogic_withMinimalConfig() {
        // Given: Minimal configuration (similar to what property test generates)
        BpmnXmlGenerator.MultiInstanceConfig config = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("45")
                .subTableName("fu_participants")
                .subTableDisplayName("参与人列表")
                .assigneeField("assignee_user_id")
                .taskName("填写参会信息")
                .executionMode(BpmnXmlGenerator.ExecutionMode.PARALLEL)
                .build();

        // When: Generate XML
        String xml = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

        // Then: Verify all assertions from property test
        String expectedSubProcessId = "MultiInstance_SubTable_" + config.getSubTableId();
        assertThat(xml).contains("<bpmn:subProcess id=\"" + expectedSubProcessId + "\"");
        assertThat(xml).contains("<bpmn:multiInstanceLoopCharacteristics");

        String expectedCollectionVar = "multiInstance_" + config.getSubTableName() + "_collection";
        assertThat(xml).contains("<flowable:collection>" + expectedCollectionVar + "</flowable:collection>");

        String expectedElementVar = "currentItem";
        assertThat(xml).contains("<flowable:elementVariable>" + expectedElementVar + "</flowable:elementVariable>");

        String expectedUserTaskId = "MI_UserTask_" + config.getSubTableId();
        assertThat(xml).contains("<bpmn:userTask id=\"" + expectedUserTaskId + "\"");

        assertThat(xml).contains("<custom:property name=\"assigneeType\" value=\"ELEMENT_VARIABLE\" />");
        assertThat(xml).contains("<custom:property name=\"subTableId\" value=\"" + config.getSubTableId() + "\" />");
        assertThat(xml).contains("<custom:property name=\"subTableName\" value=\"" + config.getSubTableName() + "\" />");
        assertThat(xml).contains("<custom:property name=\"assigneeField\" value=\"" + config.getAssigneeField() + "\" />");
        assertThat(xml).contains("<custom:property name=\"rowIdVariable\" value=\"" + expectedElementVar + ".rowId\" />");

        assertThat(xml).contains("<bpmn:startEvent id=\"MI_Start_" + config.getSubTableId() + "\" />");
        assertThat(xml).contains("<bpmn:endEvent id=\"MI_End_" + config.getSubTableId() + "\" />");
        assertThat(xml).contains("<bpmn:sequenceFlow id=\"MI_Flow1_" + config.getSubTableId() + "\"");
        assertThat(xml).contains("<bpmn:sequenceFlow id=\"MI_Flow2_" + config.getSubTableId() + "\"");
        assertThat(xml).contains("</bpmn:subProcess>");
    }

    @Test
    void verifyPropertyTestLogic_withCustomVariableNames() {
        // Given: Configuration with custom variable names
        BpmnXmlGenerator.MultiInstanceConfig config = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("100")
                .subTableName("custom_table")
                .subTableDisplayName("自定义表")
                .assigneeField("handler_id")
                .taskName("处理任务")
                .executionMode(BpmnXmlGenerator.ExecutionMode.SEQUENTIAL)
                .collectionVariableName("myCustomCollection")
                .elementVariableName("myElement")
                .build();

        // When: Generate XML
        String xml = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

        // Then: Verify custom variable names are used
        assertThat(xml).contains("<flowable:collection>myCustomCollection</flowable:collection>");
        assertThat(xml).contains("<flowable:elementVariable>myElement</flowable:elementVariable>");
        assertThat(xml).contains("<custom:property name=\"rowIdVariable\" value=\"myElement.rowId\" />");
        assertThat(xml).contains("<bpmn:multiInstanceLoopCharacteristics isSequential=\"true\">");
    }

    @Test
    void verifyPropertyTestLogic_withOptionalFields() {
        // Given: Configuration with optional completion condition and form ID
        BpmnXmlGenerator.MultiInstanceConfig config = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("200")
                .subTableName("approval_steps")
                .subTableDisplayName("审批步骤")
                .assigneeField("approver_id")
                .taskName("审批")
                .executionMode(BpmnXmlGenerator.ExecutionMode.PARALLEL)
                .completionCondition("${nrOfCompletedInstances >= 3}")
                .formId("form_12345")
                .build();

        // When: Generate XML
        String xml = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

        // Then: Verify optional fields are present
        assertThat(xml).contains("<bpmn:completionCondition xsi:type=\"bpmn:tFormalExpression\">");
        assertThat(xml).contains("${nrOfCompletedInstances &gt;= 3}");
        assertThat(xml).contains("<custom:property name=\"formId\" value=\"form_12345\" />");
    }

    @Test
    void verifyPropertyTestLogic_withoutOptionalFields() {
        // Given: Configuration without optional fields
        BpmnXmlGenerator.MultiInstanceConfig config = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("300")
                .subTableName("simple_table")
                .subTableDisplayName("简单表")
                .assigneeField("user_id")
                .taskName("简单任务")
                .executionMode(BpmnXmlGenerator.ExecutionMode.PARALLEL)
                .completionCondition(null)
                .formId(null)
                .build();

        // When: Generate XML
        String xml = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

        // Then: Verify optional fields are not present
        assertThat(xml).doesNotContain("<bpmn:completionCondition");
        assertThat(xml).doesNotContain("<custom:property name=\"formId\"");
    }

    @Test
    void verifyPropertyTestLogic_multipleConfigurations() {
        // Test with 10 different random-like configurations to simulate property test
        for (int i = 1; i <= 10; i++) {
            BpmnXmlGenerator.MultiInstanceConfig config = BpmnXmlGenerator.MultiInstanceConfig.builder()
                    .subTableId(String.valueOf(i * 100))
                    .subTableName("table_" + i)
                    .subTableDisplayName("表" + i)
                    .assigneeField("assignee_" + i)
                    .taskName("任务" + i)
                    .executionMode(i % 2 == 0 
                            ? BpmnXmlGenerator.ExecutionMode.PARALLEL 
                            : BpmnXmlGenerator.ExecutionMode.SEQUENTIAL)
                    .build();

            String xml = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

            // Verify core structure for each
            assertThat(xml).contains("<bpmn:subProcess id=\"MultiInstance_SubTable_" + (i * 100) + "\"");
            assertThat(xml).contains("<bpmn:multiInstanceLoopCharacteristics");
            assertThat(xml).contains("<flowable:collection>");
            assertThat(xml).contains("<flowable:elementVariable>");
            assertThat(xml).contains("<bpmn:userTask");
            assertThat(xml).contains("<custom:property name=\"assigneeType\" value=\"ELEMENT_VARIABLE\" />");
            assertThat(xml).contains("</bpmn:subProcess>");
        }
    }
}
