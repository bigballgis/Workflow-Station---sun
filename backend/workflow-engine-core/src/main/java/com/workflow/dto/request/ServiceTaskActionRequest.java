package com.workflow.dto.request;

import lombok.Data;

import java.util.Map;

/**
 * Activepieces Action synchronous execution request DTO.
 * Used by user-portal (via WorkflowEngineClient) to forward an AP Action execution request.
 */
@Data
public class ServiceTaskActionRequest {

    /** AP flow ID (webhook flow identifier) */
    private String apFlowId;

    /**
     * Optional full sync webhook URL override. When blank, the engine builds the URL
     * from the configured AP webhook base URL + apFlowId.
     */
    private String webhookUrl;

    /** Execution timeout seconds (default 120) */
    private Integer timeoutSeconds = 120;

    /**
     * @deprecated Envelope contract v1 (FR-C07) removed per-task variable mapping; the field is
     * kept only so older callers' request bodies still deserialize. Its value is ignored.
     */
    @Deprecated
    private String inputMapping;

    /**
     * @deprecated Envelope contract v1 (FR-C07) removed per-task variable mapping; the field is
     * kept only so older callers' request bodies still deserialize. Its value is ignored.
     */
    @Deprecated
    private String outputMapping;

    /** User-supplied input parameter data */
    private Map<String, Object> inputData;

    /** Process instance ID */
    private String processInstanceId;

    /** Current task ID */
    private String taskId;
}
