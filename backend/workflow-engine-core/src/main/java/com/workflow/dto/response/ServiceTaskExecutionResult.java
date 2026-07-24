package com.workflow.dto.response;

import lombok.Data;

import java.util.Map;

/**
 * Activepieces flow execution result DTO.
 * Returned for AP Action synchronous execution.
 */
@Data
public class ServiceTaskExecutionResult {

    /** Whether the execution succeeded */
    private boolean success;

    /** Execution record ID */
    private Long executionRecordId;

    /** Execution status: SUCCESS, FAILED, TIMEOUT */
    private String status;

    /** Output data returned by the AP flow */
    private Map<String, Object> outputData;

    /** Error message (on failure) */
    private String errorMessage;

    public static ServiceTaskExecutionResult success(Long executionRecordId, Map<String, Object> outputData) {
        ServiceTaskExecutionResult result = new ServiceTaskExecutionResult();
        result.setSuccess(true);
        result.setExecutionRecordId(executionRecordId);
        result.setStatus("SUCCESS");
        result.setOutputData(outputData);
        return result;
    }

    public static ServiceTaskExecutionResult failure(Long executionRecordId, String status, String errorMessage) {
        ServiceTaskExecutionResult result = new ServiceTaskExecutionResult();
        result.setSuccess(false);
        result.setExecutionRecordId(executionRecordId);
        result.setStatus(status);
        result.setErrorMessage(errorMessage);
        return result;
    }
}
