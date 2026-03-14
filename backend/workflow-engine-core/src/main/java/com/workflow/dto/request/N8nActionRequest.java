package com.workflow.dto.request;

import lombok.Data;

import java.util.Map;

/**
 * N8N Action 同步执行请求 DTO
 * 用于 user-portal 通过 WorkflowEngineClient 转发 N8N Action 执行请求
 */
@Data
public class N8nActionRequest {

    /** N8N 连接配置 ID */
    private String n8nConfigId;

    /** N8N 工作流 ID */
    private String n8nWorkflowId;

    /** Webhook 触发地址 */
    private String webhookUrl;

    /** 执行超时秒数（默认 120） */
    private Integer timeoutSeconds = 120;

    /** 输入变量映射 JSON 字符串 [{"source":"xxx","target":"yyy"}] */
    private String inputMapping;

    /** 输出变量映射 JSON 字符串 [{"source":"xxx","target":"yyy"}] */
    private String outputMapping;

    /** 用户输入的参数数据 */
    private Map<String, Object> inputData;

    /** 流程实例 ID */
    private String processInstanceId;

    /** 当前任务 ID */
    private String taskId;
}
