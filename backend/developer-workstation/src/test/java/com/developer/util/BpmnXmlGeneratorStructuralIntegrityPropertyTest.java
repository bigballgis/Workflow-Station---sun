package com.developer.util;

import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test for BPMN XML generation structural integrity.
 *
 * Feature: multi-instance-task-dispatch, Property 1: BPMN XML 生成结构完整性
 *
 * For any valid MultiInstanceConfig, the generated BPMN XML must contain all required
 * BPMN elements and attributes: <bpmn:subProcess>, <bpmn:multiInstanceLoopCharacteristics>,
 * flowable:collection, flowable:elementVariable, and at least one <bpmn:userTask> with
 * required extension properties (assigneeType, subTableId, assigneeField, rowIdVariable).
 *
 * **Validates: Requirements 1.1, 1.2, 1.4**
 */
public class BpmnXmlGeneratorStructuralIntegrityPropertyTest {

    /**
     * Feature: multi-instance-task-dispatch, Property 1
     *
     * For any randomly generated valid MultiInstanceConfig, the generated BPMN XML
     * must contain all required structural elements and attributes.
     *
     * **Validates: Requirements 1.1, 1.2, 1.4**
     */
    @Property(tries = 100)
    @Label("Feature: multi-instance-task-dispatch, Property 1: BPMN XML 生成结构完整性")
    void generatedXmlContainsAllRequiredElements(
            @ForAll("validMultiInstanceConfigs") BpmnXmlGenerator.MultiInstanceConfig config) {

        // When: Generate BPMN XML
        String xml = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

        // Then: Verify all required elements exist

        // 1. <bpmn:subProcess> element with correct ID
        String expectedSubProcessId = "MultiInstance_SubTable_" + config.getSubTableId();
        assertThat(xml)
                .as("XML should contain subProcess element with correct ID")
                .contains("<bpmn:subProcess id=\"" + expectedSubProcessId + "\"");

        // 2. <bpmn:multiInstanceLoopCharacteristics> element
        assertThat(xml)
                .as("XML should contain multiInstanceLoopCharacteristics element")
                .contains("<bpmn:multiInstanceLoopCharacteristics");

        // 3. flowable:collection attribute
        String expectedCollectionVar = config.getCollectionVariableName() != null
                ? config.getCollectionVariableName()
                : "multiInstance_" + config.getSubTableName() + "_collection";
        assertThat(xml)
                .as("XML should contain flowable:collection with correct variable name")
                .contains("<flowable:collection>" + expectedCollectionVar + "</flowable:collection>");

        // 4. flowable:elementVariable attribute
        String expectedElementVar = config.getElementVariableName() != null
                ? config.getElementVariableName()
                : "currentItem";
        assertThat(xml)
                .as("XML should contain flowable:elementVariable with correct variable name")
                .contains("<flowable:elementVariable>" + expectedElementVar + "</flowable:elementVariable>");

        // 5. At least one <bpmn:userTask> element
        String expectedUserTaskId = "MI_UserTask_" + config.getSubTableId();
        assertThat(xml)
                .as("XML should contain at least one userTask element")
                .contains("<bpmn:userTask id=\"" + expectedUserTaskId + "\"");

        // 6. Required extension properties in userTask
        assertThat(xml)
                .as("UserTask should have assigneeType extension property")
                .contains("<custom:property name=\"assigneeType\" value=\"ELEMENT_VARIABLE\" />");

        assertThat(xml)
                .as("UserTask should have subTableId extension property")
                .contains("<custom:property name=\"subTableId\" value=\"" + config.getSubTableId() + "\" />");

        assertThat(xml)
                .as("UserTask should have subTableName extension property")
                .contains("<custom:property name=\"subTableName\" value=\"" + config.getSubTableName() + "\" />");

        assertThat(xml)
                .as("UserTask should have assigneeField extension property")
                .contains("<custom:property name=\"assigneeField\" value=\"" + config.getAssigneeField() + "\" />");

        assertThat(xml)
                .as("UserTask should have rowIdVariable extension property")
                .contains("<custom:property name=\"rowIdVariable\" value=\"" + expectedElementVar + ".rowId\" />");

        // 7. Verify internal structure: startEvent, endEvent, sequenceFlows
        assertThat(xml)
                .as("SubProcess should contain startEvent")
                .contains("<bpmn:startEvent id=\"MI_Start_" + config.getSubTableId() + "\" />");

        assertThat(xml)
                .as("SubProcess should contain endEvent")
                .contains("<bpmn:endEvent id=\"MI_End_" + config.getSubTableId() + "\" />");

        assertThat(xml)
                .as("SubProcess should contain sequence flows")
                .contains("<bpmn:sequenceFlow id=\"MI_Flow1_" + config.getSubTableId() + "\"")
                .contains("<bpmn:sequenceFlow id=\"MI_Flow2_" + config.getSubTableId() + "\"");

        // 8. Verify closing tag
        assertThat(xml)
                .as("XML should have closing subProcess tag")
                .contains("</bpmn:subProcess>");
    }

    // ==================== Providers ====================

    /**
     * Generate valid MultiInstanceConfig objects with random but valid values
     */
    @Provide
    Arbitrary<BpmnXmlGenerator.MultiInstanceConfig> validMultiInstanceConfigs() {
        // jqwik Combinators.combine() supports max 8 parameters, so we split into two combines
        Arbitrary<RequiredFields> requiredFields = Combinators.combine(
                subTableIds(),
                subTableNames(),
                subTableDisplayNames(),
                assigneeFields(),
                taskNames(),
                executionModes()
        ).as(RequiredFields::new);
        
        Arbitrary<OptionalFields> optionalFields = Combinators.combine(
                optionalCompletionConditions(),
                optionalFormIds(),
                optionalCollectionVariableNames(),
                optionalElementVariableNames()
        ).as(OptionalFields::new);
        
        return Combinators.combine(requiredFields, optionalFields)
                .as((required, optional) ->
                        BpmnXmlGenerator.MultiInstanceConfig.builder()
                                .subTableId(required.subTableId)
                                .subTableName(required.subTableName)
                                .subTableDisplayName(required.displayName)
                                .assigneeField(required.assigneeField)
                                .taskName(required.taskName)
                                .executionMode(required.executionMode)
                                .completionCondition(optional.completionCondition)
                                .formId(optional.formId)
                                .collectionVariableName(optional.collectionVar)
                                .elementVariableName(optional.elementVar)
                                .build()
                );
    }
    
    // Helper records for grouping parameters
    private record RequiredFields(
            String subTableId,
            String subTableName,
            String displayName,
            String assigneeField,
            String taskName,
            BpmnXmlGenerator.ExecutionMode executionMode
    ) {}
    
    private record OptionalFields(
            String completionCondition,
            String formId,
            String collectionVar,
            String elementVar
    ) {}

    private Arbitrary<String> subTableIds() {
        return Arbitraries.integers().between(1, 9999)
                .map(String::valueOf);
    }

    private Arbitrary<String> subTableNames() {
        return Combinators.combine(
                Arbitraries.of("fu", "tbl", "data", "sub"),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15).map(String::toLowerCase)
        ).as((prefix, suffix) -> prefix + "_" + suffix);
    }

    private Arbitrary<String> subTableDisplayNames() {
        return Arbitraries.of(
                "参与人列表",
                "审批步骤",
                "评审人",
                "任务列表",
                "项目明细",
                "费用明细",
                "物料清单",
                "人员信息",
                "设备列表",
                "文档清单"
        );
    }

    private Arbitrary<String> assigneeFields() {
        return Arbitraries.of(
                "assignee_user_id",
                "approver_id",
                "reviewer_id",
                "handler_id",
                "owner_id",
                "processor_id",
                "responsible_user_id"
        );
    }

    private Arbitrary<String> taskNames() {
        return Arbitraries.of(
                "填写参会信息",
                "审批",
                "评审",
                "完成任务",
                "处理项目",
                "填写费用",
                "确认物料",
                "补充信息",
                "设备检查",
                "文档审核"
        );
    }

    private Arbitrary<BpmnXmlGenerator.ExecutionMode> executionModes() {
        return Arbitraries.of(
                BpmnXmlGenerator.ExecutionMode.PARALLEL,
                BpmnXmlGenerator.ExecutionMode.SEQUENTIAL
        );
    }

    private Arbitrary<String> optionalCompletionConditions() {
        Arbitrary<String> conditions = Arbitraries.of(
                "${nrOfCompletedInstances == nrOfInstances}",
                "${nrOfCompletedInstances >= 3}",
                "${nrOfCompletedInstances > nrOfInstances / 2}",
                "${nrOfActiveInstances == 0}"
        );
        return Arbitraries.frequencyOf(
                Tuple.of(3, conditions),
                Tuple.of(7, Arbitraries.just((String) null))
        );
    }

    private Arbitrary<String> optionalFormIds() {
        Arbitrary<String> formIds = Arbitraries.strings()
                .withCharRange('a', 'z')
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "form_" + s);
        return Arbitraries.frequencyOf(
                Tuple.of(3, formIds),
                Tuple.of(7, Arbitraries.just((String) null))
        );
    }

    private Arbitrary<String> optionalCollectionVariableNames() {
        Arbitrary<String> varNames = Arbitraries.strings()
                .withCharRange('a', 'z')
                .numeric()
                .withChars('_')
                .ofMinLength(5)
                .ofMaxLength(30)
                .map(s -> "collection_" + s);
        return Arbitraries.frequencyOf(
                Tuple.of(2, varNames),
                Tuple.of(8, Arbitraries.just((String) null))
        );
    }

    private Arbitrary<String> optionalElementVariableNames() {
        Arbitrary<String> varNames = Arbitraries.of(
                "currentItem",
                "item",
                "element",
                "row",
                "record"
        );
        return Arbitraries.frequencyOf(
                Tuple.of(2, varNames),
                Tuple.of(8, Arbitraries.just((String) null))
        );
    }
}
