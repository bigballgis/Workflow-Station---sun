package com.workflow.dto.request;

import lombok.Data;

import java.util.Map;

/**
 * N8N 回调请求 DTO
 * N8N 工作流执行完成后，通过 POST /api/workflow/n8n/callback 回传执行结果
 */
@Data
public class N8nCallbackRequest {

    /** 回调令牌，用于验证回调请求的合法性 */
    private String callbackToken;

    /** 执行状态: success / failed */
    private String status;

    /** N8N 工作流返回的输出数据 */
    private Map<String, Object> outputData;

    /** 错误信息（失败时） */
    private String errorMessage;
}
