package com.developer.util;

/**
 * 手动验证 BpmnXmlGenerator 生成的 XML 结构
 * 由于编译错误阻止测试运行，此类用于手动验证生成逻辑
 */
public class BpmnXmlGeneratorManualVerification {
    
    public static void main(String[] args) {
        // 测试用例 1: 最小配置 - 并行模式
        System.out.println("=== 测试用例 1: 最小配置 - 并行模式 ===");
        BpmnXmlGenerator.MultiInstanceConfig config1 = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("45")
                .subTableName("fu_participants")
                .subTableDisplayName("参与人列表")
                .assigneeField("assignee_user_id")
                .taskName("填写参会信息")
                .executionMode(BpmnXmlGenerator.ExecutionMode.PARALLEL)
                .build();
        String xml1 = BpmnXmlGenerator.generateMultiInstanceSubProcess(config1);
        System.out.println(xml1);
        System.out.println();
        
        // 测试用例 2: 顺序模式 + 完成条件
        System.out.println("=== 测试用例 2: 顺序模式 + 完成条件 ===");
        BpmnXmlGenerator.MultiInstanceConfig config2 = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("60")
                .subTableName("reviewers")
                .subTableDisplayName("评审人")
                .assigneeField("reviewer_id")
                .taskName("评审")
                .executionMode(BpmnXmlGenerator.ExecutionMode.SEQUENTIAL)
                .completionCondition("${nrOfCompletedInstances >= 3}")
                .formId("form_123")
                .build();
        String xml2 = BpmnXmlGenerator.generateMultiInstanceSubProcess(config2);
        System.out.println(xml2);
        System.out.println();
        
        // 测试用例 3: 自定义变量名
        System.out.println("=== 测试用例 3: 自定义变量名 ===");
        BpmnXmlGenerator.MultiInstanceConfig config3 = BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId("80")
                .subTableName("items")
                .subTableDisplayName("项目")
                .assigneeField("owner")
                .taskName("处理项目")
                .executionMode(BpmnXmlGenerator.ExecutionMode.PARALLEL)
                .collectionVariableName("customCollection")
                .elementVariableName("customElement")
                .build();
        String xml3 = BpmnXmlGenerator.generateMultiInstanceSubProcess(config3);
        System.out.println(xml3);
    }
}
