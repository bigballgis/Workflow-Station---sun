package com.workflow.dto.response;

import lombok.Data;

import java.util.Map;

/**
 * N8N 工作流执行结果 DTO
 * 用于返回 N8N Action 同步执行的结果
 */
@Data
public class N8nExecutionResult {

    /** 是否执行成功 */
    private boolean success;

    /** 执行记录 ID */
    private Long executionRecordId;

    /** 执行状态: SUCCESS, FAILED, TIMEOUT */
    private String status;

    /** N8N 返回的输出数据 */
    private Map<String, Object> outputData;

    /** 错误信息（失败时） */
    private String errorMessage;

    public static N8nExecutionResult success(Long executionRecordId, Map<String, Object> outputData) {
        N8nExecutionResult result = new N8nExecutionResult();
        result.setSuccess(true);
        result.setExecutionRecordId(executionRecordId);
        result.setStatus("SUCCESS");
        result.setOutputData(outputData);
        return result;
    }

    public static N8nExecutionResult failure(Long executionRecordId, String status, String errorMessage) {
        N8nExecutionResult result = new N8nExecutionResult();
        result.setSuccess(false);
        result.setExecutionRecordId(executionRecordId);
        result.setStatus(status);
        result.setErrorMessage(errorMessage);
        return result;
    }
}
