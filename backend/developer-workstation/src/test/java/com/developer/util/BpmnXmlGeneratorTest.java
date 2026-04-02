package com.developer.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * BpmnXmlGenerator 单元测试
 */
class BpmnXmlGeneratorTest {

    @Test
    void generateMultiInstanceSubProcess_withMinimalConfig_shouldGenerateValidXml() {
        // Given: 最小配置
        BpmnXmlGenerator.MultiInstanceConfig config = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("45")
                .subTableName("fu_participants")
                .subTableDisplayName("参与人列表")
                .assigneeField("assignee_user_id")
                .taskName("填写参会信息")
                .executionMode(BpmnXmlGenerator.ExecutionMode.PARALLEL)
                .build();

        // When: 生成 XML
        String xml = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

        // Then: 验证必需元素存在
        assertThat(xml).contains("<bpmn:subProcess id=\"MultiInstance_SubTable_45\"");
        assertThat(xml).contains("name=\"多实例-参与人列表\"");
        assertThat(xml).contains("<bpmn:multiInstanceLoopCharacteristics isSequential=\"false\">");
        assertThat(xml).contains("<flowable:collection>multiInstance_fu_participants_collection</flowable:collection>");
        assertThat(xml).contains("<flowable:elementVariable>currentItem</flowable:elementVariable>");
        assertThat(xml).contains("<bpmn:userTask id=\"MI_UserTask_45\" name=\"填写参会信息\">");
        assertThat(xml).contains("<custom:property name=\"assigneeType\" value=\"ELEMENT_VARIABLE\" />");
        assertThat(xml).contains("<custom:property name=\"subTableId\" value=\"45\" />");
        assertThat(xml).contains("<custom:property name=\"subTableName\" value=\"fu_participants\" />");
        assertThat(xml).contains("<custom:property name=\"assigneeField\" value=\"assignee_user_id\" />");
        assertThat(xml).contains("<custom:property name=\"rowIdVariable\" value=\"currentItem.rowId\" />");
        assertThat(xml).contains("<bpmn:startEvent id=\"MI_Start_45\" />");
        assertThat(xml).contains("<bpmn:endEvent id=\"MI_End_45\" />");
        assertThat(xml).contains("<bpmn:sequenceFlow id=\"MI_Flow1_45\"");
        assertThat(xml).contains("<bpmn:sequenceFlow id=\"MI_Flow2_45\"");
    }

    @Test
    void generateMultiInstanceSubProcess_withSequentialMode_shouldGenerateIsSequentialTrue() {
        // Given: 顺序执行模式
        BpmnXmlGenerator.MultiInstanceConfig config = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("50")
                .subTableName("approval_steps")
                .subTableDisplayName("审批步骤")
                .assigneeField("approver_id")
                .taskName("审批")
                .executionMode(BpmnXmlGenerator.ExecutionMode.SEQUENTIAL)
                .build();

        // When: 生成 XML
        String xml = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

        // Then: 验证 isSequential 为 true
        assertThat(xml).contains("<bpmn:multiInstanceLoopCharacteristics isSequential=\"true\">");
    }

    @Test
    void generateMultiInstanceSubProcess_withCompletionCondition_shouldIncludeCompletionCondition() {
        // Given: 包含完成条件
        BpmnXmlGenerator.MultiInstanceConfig config = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("60")
                .subTableName("reviewers")
                .subTableDisplayName("评审人")
                .assigneeField("reviewer_id")
                .taskName("评审")
                .executionMode(BpmnXmlGenerator.ExecutionMode.PARALLEL)
                .completionCondition("${nrOfCompletedInstances >= 3}")
                .build();

        // When: 生成 XML
        String xml = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

        // Then: 验证完成条件存在
        assertThat(xml).contains("<bpmn:completionCondition xsi:type=\"bpmn:tFormalExpression\">");
        assertThat(xml).contains("${nrOfCompletedInstances &gt;= 3}");
        assertThat(xml).contains("</bpmn:completionCondition>");
    }

    @Test
    void generateMultiInstanceSubProcess_withFormId_shouldIncludeFormIdProperty() {
        // Given: 包含表单 ID
        BpmnXmlGenerator.MultiInstanceConfig config = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("70")
                .subTableName("tasks")
                .subTableDisplayName("任务列表")
                .assigneeField("assignee")
                .taskName("完成任务")
                .executionMode(BpmnXmlGenerator.ExecutionMode.PARALLEL)
                .formId("form_123")
                .build();

        // When: 生成 XML
        String xml = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

        // Then: 验证表单 ID 存在
        assertThat(xml).contains("<custom:property name=\"formId\" value=\"form_123\" />");
    }

    @Test
    void generateMultiInstanceSubProcess_withCustomVariableNames_shouldUseCustomNames() {
        // Given: 自定义变量名
        BpmnXmlGenerator.MultiInstanceConfig config = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("80")
                .subTableName("items")
                .subTableDisplayName("项目")
                .assigneeField("owner")
                .taskName("处理项目")
                .executionMode(BpmnXmlGenerator.ExecutionMode.PARALLEL)
                .collectionVariableName("customCollection")
                .elementVariableName("customElement")
                .build();

        // When: 生成 XML
        String xml = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

        // Then: 验证使用自定义变量名
        assertThat(xml).contains("<flowable:collection>customCollection</flowable:collection>");
        assertThat(xml).contains("<flowable:elementVariable>customElement</flowable:elementVariable>");
        assertThat(xml).contains("<custom:property name=\"rowIdVariable\" value=\"customElement.rowId\" />");
    }

    @Test
    void generateMultiInstanceSubProcess_withXmlSpecialCharacters_shouldEscapeCorrectly() {
        // Given: 包含 XML 特殊字符的配置
        BpmnXmlGenerator.MultiInstanceConfig config = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("90")
                .subTableName("special_table")
                .subTableDisplayName("特殊<字符>&测试")
                .assigneeField("assignee")
                .taskName("任务 \"名称\" & <标签>")
                .executionMode(BpmnXmlGenerator.ExecutionMode.PARALLEL)
                .completionCondition("${count > 5 && count < 10}")
                .build();

        // When: 生成 XML
        String xml = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

        // Then: 验证特殊字符被正确转义
        assertThat(xml).contains("name=\"多实例-特殊&lt;字符&gt;&amp;测试\"");
        assertThat(xml).contains("name=\"任务 &quot;名称&quot; &amp; &lt;标签&gt;\"");
        assertThat(xml).contains("${count &gt; 5 &amp;&amp; count &lt; 10}");
    }

    @Test
    void generateMultiInstanceSubProcess_withNullConfig_shouldThrowException() {
        // When & Then: 空配置应抛出异常
        assertThatThrownBy(() -> BpmnXmlGenerator.generateMultiInstanceSubProcess(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MultiInstanceConfig cannot be null");
    }

    @Test
    void generateMultiInstanceSubProcess_withNullSubTableId_shouldThrowException() {
        // Given: subTableId 为 null
        BpmnXmlGenerator.MultiInstanceConfig config = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId(null)
                .subTableName("table")
                .subTableDisplayName("表")
                .assigneeField("assignee")
                .taskName("任务")
                .executionMode(BpmnXmlGenerator.ExecutionMode.PARALLEL)
                .build();

        // When & Then: 应抛出异常
        assertThatThrownBy(() -> BpmnXmlGenerator.generateMultiInstanceSubProcess(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subTableId cannot be null or empty");
    }

    @Test
    void generateMultiInstanceSubProcess_withEmptySubTableName_shouldThrowException() {
        // Given: subTableName 为空字符串
        BpmnXmlGenerator.MultiInstanceConfig config = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("100")
                .subTableName("")
                .subTableDisplayName("表")
                .assigneeField("assignee")
                .taskName("任务")
                .executionMode(BpmnXmlGenerator.ExecutionMode.PARALLEL)
                .build();

        // When & Then: 应抛出异常
        assertThatThrownBy(() -> BpmnXmlGenerator.generateMultiInstanceSubProcess(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subTableName cannot be null or empty");
    }

    @Test
    void generateMultiInstanceSubProcess_withNullExecutionMode_shouldThrowException() {
        // Given: executionMode 为 null
        BpmnXmlGenerator.MultiInstanceConfig config = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("110")
                .subTableName("table")
                .subTableDisplayName("表")
                .assigneeField("assignee")
                .taskName("任务")
                .executionMode(null)
                .build();

        // When & Then: 应抛出异常
        assertThatThrownBy(() -> BpmnXmlGenerator.generateMultiInstanceSubProcess(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("executionMode cannot be null");
    }

    @Test
    void generateMultiInstanceSubProcess_withEmptyCompletionCondition_shouldNotIncludeCompletionCondition() {
        // Given: completionCondition 为空字符串
        BpmnXmlGenerator.MultiInstanceConfig config = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("120")
                .subTableName("table")
                .subTableDisplayName("表")
                .assigneeField("assignee")
                .taskName("任务")
                .executionMode(BpmnXmlGenerator.ExecutionMode.PARALLEL)
                .completionCondition("")
                .build();

        // When: 生成 XML
        String xml = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

        // Then: 不应包含完成条件
        assertThat(xml).doesNotContain("<bpmn:completionCondition");
    }

    @Test
    void generateMultiInstanceSubProcess_withEmptyFormId_shouldNotIncludeFormIdProperty() {
        // Given: formId 为空字符串
        BpmnXmlGenerator.MultiInstanceConfig config = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("130")
                .subTableName("table")
                .subTableDisplayName("表")
                .assigneeField("assignee")
                .taskName("任务")
                .executionMode(BpmnXmlGenerator.ExecutionMode.PARALLEL)
                .formId("")
                .build();

        // When: 生成 XML
        String xml = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

        // Then: 不应包含 formId 属性
        assertThat(xml).doesNotContain("<custom:property name=\"formId\"");
    }

    @Test
    void generateMultiInstanceSubProcess_shouldGenerateUniqueIds() {
        // Given: 两个不同的配置
        BpmnXmlGenerator.MultiInstanceConfig config1 = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("200")
                .subTableName("table1")
                .subTableDisplayName("表1")
                .assigneeField("assignee")
                .taskName("任务1")
                .executionMode(BpmnXmlGenerator.ExecutionMode.PARALLEL)
                .build();

        BpmnXmlGenerator.MultiInstanceConfig config2 = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("201")
                .subTableName("table2")
                .subTableDisplayName("表2")
                .assigneeField("assignee")
                .taskName("任务2")
                .executionMode(BpmnXmlGenerator.ExecutionMode.PARALLEL)
                .build();

        // When: 生成两个 XML
        String xml1 = BpmnXmlGenerator.generateMultiInstanceSubProcess(config1);
        String xml2 = BpmnXmlGenerator.generateMultiInstanceSubProcess(config2);

        // Then: 验证 ID 不同
        assertThat(xml1).contains("MultiInstance_SubTable_200");
        assertThat(xml2).contains("MultiInstance_SubTable_201");
        assertThat(xml1).contains("MI_Start_200");
        assertThat(xml2).contains("MI_Start_201");
    }
}
