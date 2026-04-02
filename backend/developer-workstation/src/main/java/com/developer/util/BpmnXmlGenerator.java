package com.developer.util;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * BPMN XML 生成器
 * 负责生成符合 BPMN 2.0 标准的 XML 结构
 */
public class BpmnXmlGenerator {

    /**
     * 多实例子流程配置
     */
    @Data
    @Builder
    public static class MultiInstanceConfig {
        /**
         * 子表 ID
         */
        private String subTableId;
        
        /**
         * 子表物理表名
         */
        private String subTableName;
        
        /**
         * 子表显示名称
         */
        private String subTableDisplayName;
        
        /**
         * 处理人字段名
         */
        private String assigneeField;
        
        /**
         * 任务名称
         */
        private String taskName;
        
        /**
         * 任务表单 ID（可选）
         */
        private String formId;
        
        /**
         * 执行模式：PARALLEL（并行）或 SEQUENTIAL（顺序）
         */
        private ExecutionMode executionMode;
        
        /**
         * 完成条件表达式（可选）
         */
        private String completionCondition;
        
        /**
         * 集合变量名（可选，默认为 multiInstance_{subTableName}_collection）
         */
        private String collectionVariableName;
        
        /**
         * 元素变量名（可选，默认为 currentItem）
         */
        private String elementVariableName;
    }
    
    /**
     * 执行模式枚举
     */
    public enum ExecutionMode {
        PARALLEL,
        SEQUENTIAL
    }

    /**
     * 生成多实例子流程 XML
     * 
     * @param config 多实例配置
     * @return 生成的 BPMN XML 字符串
     */
    public static String generateMultiInstanceSubProcess(MultiInstanceConfig config) {
        validateConfig(config);
        
        // 生成唯一 ID
        String subProcessId = "MultiInstance_SubTable_" + config.getSubTableId();
        String startEventId = "MI_Start_" + config.getSubTableId();
        String userTaskId = "MI_UserTask_" + config.getSubTableId();
        String endEventId = "MI_End_" + config.getSubTableId();
        String flow1Id = "MI_Flow1_" + config.getSubTableId();
        String flow2Id = "MI_Flow2_" + config.getSubTableId();
        
        // 确定集合变量名和元素变量名
        String collectionVar = config.getCollectionVariableName() != null 
            ? config.getCollectionVariableName()
            : "multiInstance_" + config.getSubTableName() + "_collection";
        String elementVar = config.getElementVariableName() != null
            ? config.getElementVariableName()
            : "currentItem";
        
        // 确定 isSequential 属性值
        boolean isSequential = config.getExecutionMode() == ExecutionMode.SEQUENTIAL;
        
        StringBuilder xml = new StringBuilder();
        
        // 子流程开始标签
        xml.append("  <bpmn:subProcess id=\"").append(subProcessId).append("\" name=\"多实例-")
           .append(escapeXml(config.getSubTableDisplayName())).append("\">\n");
        
        // 多实例循环特性
        xml.append("    <bpmn:multiInstanceLoopCharacteristics isSequential=\"")
           .append(isSequential).append("\">\n");
        
        // 扩展元素：collection 和 elementVariable
        xml.append("      <bpmn:extensionElements>\n");
        xml.append("        <flowable:collection>").append(collectionVar).append("</flowable:collection>\n");
        xml.append("        <flowable:elementVariable>").append(elementVar).append("</flowable:elementVariable>\n");
        xml.append("      </bpmn:extensionElements>\n");
        
        // 完成条件（可选）
        if (config.getCompletionCondition() != null && !config.getCompletionCondition().trim().isEmpty()) {
            xml.append("      <bpmn:completionCondition xsi:type=\"bpmn:tFormalExpression\">");
            xml.append(escapeXml(config.getCompletionCondition()));
            xml.append("</bpmn:completionCondition>\n");
        }
        
        xml.append("    </bpmn:multiInstanceLoopCharacteristics>\n");
        
        // 子流程内部：开始事件
        xml.append("    <bpmn:startEvent id=\"").append(startEventId).append("\" />\n");
        
        // 用户任务
        xml.append("    <bpmn:userTask id=\"").append(userTaskId).append("\" name=\"")
           .append(escapeXml(config.getTaskName())).append("\">\n");
        
        // 用户任务扩展属性
        xml.append("      <bpmn:extensionElements>\n");
        xml.append("        <custom:properties>\n");
        xml.append("          <custom:property name=\"assigneeType\" value=\"ELEMENT_VARIABLE\" />\n");
        xml.append("          <custom:property name=\"subTableId\" value=\"").append(config.getSubTableId()).append("\" />\n");
        xml.append("          <custom:property name=\"subTableName\" value=\"").append(config.getSubTableName()).append("\" />\n");
        xml.append("          <custom:property name=\"assigneeField\" value=\"").append(config.getAssigneeField()).append("\" />\n");
        xml.append("          <custom:property name=\"rowIdVariable\" value=\"").append(elementVar).append(".rowId\" />\n");
        
        // 可选的表单 ID
        if (config.getFormId() != null && !config.getFormId().trim().isEmpty()) {
            xml.append("          <custom:property name=\"formId\" value=\"").append(config.getFormId()).append("\" />\n");
        }
        
        xml.append("        </custom:properties>\n");
        xml.append("      </bpmn:extensionElements>\n");
        xml.append("    </bpmn:userTask>\n");
        
        // 结束事件
        xml.append("    <bpmn:endEvent id=\"").append(endEventId).append("\" />\n");
        
        // 序列流：开始 → 用户任务
        xml.append("    <bpmn:sequenceFlow id=\"").append(flow1Id).append("\" sourceRef=\"")
           .append(startEventId).append("\" targetRef=\"").append(userTaskId).append("\" />\n");
        
        // 序列流：用户任务 → 结束
        xml.append("    <bpmn:sequenceFlow id=\"").append(flow2Id).append("\" sourceRef=\"")
           .append(userTaskId).append("\" targetRef=\"").append(endEventId).append("\" />\n");
        
        // 子流程结束标签
        xml.append("  </bpmn:subProcess>\n");
        
        return xml.toString();
    }

    /**
     * 验证配置的完整性
     */
    private static void validateConfig(MultiInstanceConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("MultiInstanceConfig cannot be null");
        }
        if (config.getSubTableId() == null || config.getSubTableId().trim().isEmpty()) {
            throw new IllegalArgumentException("subTableId cannot be null or empty");
        }
        if (config.getSubTableName() == null || config.getSubTableName().trim().isEmpty()) {
            throw new IllegalArgumentException("subTableName cannot be null or empty");
        }
        if (config.getSubTableDisplayName() == null || config.getSubTableDisplayName().trim().isEmpty()) {
            throw new IllegalArgumentException("subTableDisplayName cannot be null or empty");
        }
        if (config.getAssigneeField() == null || config.getAssigneeField().trim().isEmpty()) {
            throw new IllegalArgumentException("assigneeField cannot be null or empty");
        }
        if (config.getTaskName() == null || config.getTaskName().trim().isEmpty()) {
            throw new IllegalArgumentException("taskName cannot be null or empty");
        }
        if (config.getExecutionMode() == null) {
            throw new IllegalArgumentException("executionMode cannot be null");
        }
    }

    /**
     * XML 特殊字符转义
     */
    private static String escapeXml(String text) {
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
