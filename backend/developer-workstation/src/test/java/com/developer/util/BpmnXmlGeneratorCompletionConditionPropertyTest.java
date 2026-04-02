package com.developer.util;

import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test for BPMN XML completion condition conditional generation.
 *
 * Feature: multi-instance-task-dispatch, Property 3: 完成条件条件性生成
 *
 * For any MultiInstanceConfig, the generated XML should contain a
 * <bpmn:completionCondition> element within <bpmn:multiInstanceLoopCharacteristics>
 * if and only if a completionCondition expression is configured, and the content
 * should match the configured expression.
 *
 * **Validates: Requirements 1.5**
 */
public class BpmnXmlGeneratorCompletionConditionPropertyTest {

    /**
     * Feature: multi-instance-task-dispatch, Property 3
     *
     * For any randomly generated MultiInstanceConfig with or without a completion condition,
     * the generated BPMN XML must conditionally include the completionCondition element
     * based on whether the configuration has a completionCondition set.
     *
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 100)
    @Label("Feature: multi-instance-task-dispatch, Property 3: 完成条件条件性生成")
    void completionConditionIsGeneratedConditionally(
            @ForAll("validMultiInstanceConfigs") BpmnXmlGenerator.MultiInstanceConfig config) {

        // When: Generate BPMN XML
        String xml = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

        // Then: Verify completionCondition element presence matches configuration
        if (config.getCompletionCondition() != null && !config.getCompletionCondition().trim().isEmpty()) {
            // Should contain completionCondition element
            assertThat(xml)
                    .as("XML should contain <bpmn:completionCondition> when completionCondition is configured")
                    .contains("<bpmn:completionCondition");
            
            assertThat(xml)
                    .as("XML should contain </bpmn:completionCondition> closing tag")
                    .contains("</bpmn:completionCondition>");
            
            // Verify the content matches the configured expression
            assertThat(xml)
                    .as("completionCondition content should match configured expression")
                    .contains(escapeXml(config.getCompletionCondition()));
            
            // Verify it's within multiInstanceLoopCharacteristics
            int multiInstanceStart = xml.indexOf("<bpmn:multiInstanceLoopCharacteristics");
            int multiInstanceEnd = xml.indexOf("</bpmn:multiInstanceLoopCharacteristics>");
            int completionConditionPos = xml.indexOf("<bpmn:completionCondition");
            
            assertThat(completionConditionPos)
                    .as("completionCondition should be within multiInstanceLoopCharacteristics")
                    .isGreaterThan(multiInstanceStart)
                    .isLessThan(multiInstanceEnd);
            
        } else {
            // Should NOT contain completionCondition element
            assertThat(xml)
                    .as("XML should NOT contain <bpmn:completionCondition> when completionCondition is null or empty")
                    .doesNotContain("<bpmn:completionCondition");
            
            assertThat(xml)
                    .as("XML should NOT contain </bpmn:completionCondition> when completionCondition is null or empty")
                    .doesNotContain("</bpmn:completionCondition>");
        }
    }

    // ==================== Providers ====================

    /**
     * Generate valid MultiInstanceConfig objects with random but valid values.
     * This provider generates configs with and without completion conditions.
     */
    @Provide
    Arbitrary<BpmnXmlGenerator.MultiInstanceConfig> validMultiInstanceConfigs() {
        // Combine required fields
        Arbitrary<RequiredFields> requiredFields = Combinators.combine(
                subTableIds(),
                subTableNames(),
                subTableDisplayNames(),
                assigneeFields(),
                taskNames(),
                executionModes()
        ).as(RequiredFields::new);
        
        // Combine optional fields (including completion condition)
        Arbitrary<OptionalFields> optionalFields = Combinators.combine(
                completionConditions(), // Mix of null, empty, and valid conditions
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

    /**
     * Generate completion conditions with a mix of:
     * - null values (no completion condition)
     * - empty strings (no completion condition)
     * - valid completion condition expressions
     * 
     * Distribution: ~40% null, ~10% empty, ~50% valid expressions
     */
    private Arbitrary<String> completionConditions() {
        Arbitrary<String> validConditions = Arbitraries.of(
                "${nrOfCompletedInstances == nrOfInstances}",
                "${nrOfCompletedInstances >= 3}",
                "${nrOfCompletedInstances > nrOfInstances / 2}",
                "${nrOfActiveInstances == 0}",
                "${nrOfCompletedInstances >= nrOfInstances * 0.8}",
                "${nrOfCompletedInstances == 1}",
                "${nrOfCompletedInstances > 0}",
                "${nrOfInstances - nrOfActiveInstances >= 5}",
                "${loopCounter >= 10}",
                "${nrOfCompletedInstances / nrOfInstances > 0.5}"
        );
        
        Arbitrary<String> emptyStrings = Arbitraries.of("", "  ", "   ");
        
        return Arbitraries.frequencyOf(
                Tuple.of(4, Arbitraries.just((String) null)),  // 40% null
                Tuple.of(1, emptyStrings),                      // 10% empty
                Tuple.of(5, validConditions)                    // 50% valid
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

    /**
     * XML special character escaping (same as in BpmnXmlGenerator)
     */
    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
