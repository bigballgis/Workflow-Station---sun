package com.developer.util;

import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test for BPMN XML execution mode mapping correctness.
 *
 * Feature: multi-instance-task-dispatch, Property 2: 执行模式映射正确性
 *
 * For any MultiInstanceConfig, when executionMode is PARALLEL, the generated XML
 * must contain isSequential="false"; when executionMode is SEQUENTIAL, the generated
 * XML must contain isSequential="true".
 *
 * **Validates: Requirements 1.3**
 */
public class BpmnXmlGeneratorExecutionModeMappingPropertyTest {

    /**
     * Feature: multi-instance-task-dispatch, Property 2
     *
     * For any randomly generated MultiInstanceConfig with PARALLEL mode,
     * the generated BPMN XML must contain isSequential="false".
     *
     * **Validates: Requirements 1.3**
     */
    @Property(tries = 100)
    @Label("Feature: multi-instance-task-dispatch, Property 2: PARALLEL mode maps to isSequential=false")
    void parallelModeGeneratesIsSequentialFalse(
            @ForAll("parallelConfigs") BpmnXmlGenerator.MultiInstanceConfig config) {

        // When: Generate BPMN XML with PARALLEL mode
        String xml = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

        // Then: Verify isSequential="false" is present
        assertThat(xml)
                .as("PARALLEL mode should generate isSequential=\"false\"")
                .contains("isSequential=\"false\"");

        // And: Verify isSequential="true" is NOT present
        assertThat(xml)
                .as("PARALLEL mode should NOT generate isSequential=\"true\"")
                .doesNotContain("isSequential=\"true\"");
    }

    /**
     * Feature: multi-instance-task-dispatch, Property 2
     *
     * For any randomly generated MultiInstanceConfig with SEQUENTIAL mode,
     * the generated BPMN XML must contain isSequential="true".
     *
     * **Validates: Requirements 1.3**
     */
    @Property(tries = 100)
    @Label("Feature: multi-instance-task-dispatch, Property 2: SEQUENTIAL mode maps to isSequential=true")
    void sequentialModeGeneratesIsSequentialTrue(
            @ForAll("sequentialConfigs") BpmnXmlGenerator.MultiInstanceConfig config) {

        // When: Generate BPMN XML with SEQUENTIAL mode
        String xml = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

        // Then: Verify isSequential="true" is present
        assertThat(xml)
                .as("SEQUENTIAL mode should generate isSequential=\"true\"")
                .contains("isSequential=\"true\"");

        // And: Verify isSequential="false" is NOT present
        assertThat(xml)
                .as("SEQUENTIAL mode should NOT generate isSequential=\"false\"")
                .doesNotContain("isSequential=\"false\"");
    }

    /**
     * Feature: multi-instance-task-dispatch, Property 2
     *
     * For any randomly generated MultiInstanceConfig (either PARALLEL or SEQUENTIAL),
     * the generated BPMN XML must contain exactly one isSequential attribute with
     * the correct value matching the execution mode.
     *
     * **Validates: Requirements 1.3**
     */
    @Property(tries = 100)
    @Label("Feature: multi-instance-task-dispatch, Property 2: Execution mode mapping is bijective")
    void executionModeMappingIsBijective(
            @ForAll("validMultiInstanceConfigs") BpmnXmlGenerator.MultiInstanceConfig config) {

        // When: Generate BPMN XML
        String xml = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

        // Then: Verify the mapping is correct based on execution mode
        boolean isSequential = config.getExecutionMode() == BpmnXmlGenerator.ExecutionMode.SEQUENTIAL;
        String expectedAttribute = "isSequential=\"" + isSequential + "\"";
        String unexpectedAttribute = "isSequential=\"" + !isSequential + "\"";

        assertThat(xml)
                .as("XML should contain isSequential=\"%s\" for %s mode",
                        isSequential, config.getExecutionMode())
                .contains(expectedAttribute);

        assertThat(xml)
                .as("XML should NOT contain isSequential=\"%s\" for %s mode",
                        !isSequential, config.getExecutionMode())
                .doesNotContain(unexpectedAttribute);

        // And: Verify exactly one isSequential attribute exists
        int count = countOccurrences(xml, "isSequential=");
        assertThat(count)
                .as("XML should contain exactly one isSequential attribute")
                .isEqualTo(1);
    }

    // ==================== Providers ====================

    /**
     * Generate MultiInstanceConfig objects with PARALLEL execution mode
     */
    @Provide
    Arbitrary<BpmnXmlGenerator.MultiInstanceConfig> parallelConfigs() {
        return validMultiInstanceConfigsWithMode(BpmnXmlGenerator.ExecutionMode.PARALLEL);
    }

    /**
     * Generate MultiInstanceConfig objects with SEQUENTIAL execution mode
     */
    @Provide
    Arbitrary<BpmnXmlGenerator.MultiInstanceConfig> sequentialConfigs() {
        return validMultiInstanceConfigsWithMode(BpmnXmlGenerator.ExecutionMode.SEQUENTIAL);
    }

    /**
     * Generate valid MultiInstanceConfig objects with random execution modes
     */
    @Provide
    Arbitrary<BpmnXmlGenerator.MultiInstanceConfig> validMultiInstanceConfigs() {
        return Combinators.combine(
                subTableIds(),
                subTableNames(),
                subTableDisplayNames(),
                assigneeFields(),
                taskNames(),
                executionModes()
        ).as((subTableId, subTableName, displayName, assigneeField, taskName, executionMode) ->
                BpmnXmlGenerator.MultiInstanceConfig.builder()
                        .subTableId(subTableId)
                        .subTableName(subTableName)
                        .subTableDisplayName(displayName)
                        .assigneeField(assigneeField)
                        .taskName(taskName)
                        .executionMode(executionMode)
                        .build()
        );
    }

    /**
     * Generate valid MultiInstanceConfig objects with a specific execution mode
     */
    private Arbitrary<BpmnXmlGenerator.MultiInstanceConfig> validMultiInstanceConfigsWithMode(
            BpmnXmlGenerator.ExecutionMode mode) {
        return Combinators.combine(
                subTableIds(),
                subTableNames(),
                subTableDisplayNames(),
                assigneeFields(),
                taskNames()
        ).as((subTableId, subTableName, displayName, assigneeField, taskName) ->
                BpmnXmlGenerator.MultiInstanceConfig.builder()
                        .subTableId(subTableId)
                        .subTableName(subTableName)
                        .subTableDisplayName(displayName)
                        .assigneeField(assigneeField)
                        .taskName(taskName)
                        .executionMode(mode)
                        .build()
        );
    }

    // ==================== Arbitrary Generators ====================

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

    // ==================== Helper Methods ====================

    /**
     * Count occurrences of a substring in a string
     */
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}
