package com.workflow.entity;

import com.workflow.config.JsonbType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;

/**
 * Activepieces (AP) workflow execution record entity.
 * Records each AP flow invocation triggered from a BPMN Service Task or a user Action,
 * including execution status, input/output data and duration.
 *
 * <p>AP integration is synchronous: the engine POSTs to the AP sync webhook and the
 * flow returns its result in the HTTP response (no callback token / async wait needed).
 */
@Entity
@Table(name = "wf_ap_execution_record", indexes = {
    @Index(name = "idx_ap_exec_process_instance", columnList = "process_instance_id"),
    @Index(name = "idx_ap_exec_task_id", columnList = "task_id"),
    @Index(name = "idx_ap_exec_status", columnList = "status"),
    @Index(name = "idx_ap_exec_created_at", columnList = "created_at")
})
public class ApExecutionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Process instance ID */
    @Column(name = "process_instance_id", length = 64)
    private String processInstanceId;

    /** Flowable task / execution ID */
    @Column(name = "task_id", length = 64)
    private String taskId;

    /** AP flow ID (the webhook flow identifier) */
    @Column(name = "ap_flow_id", length = 100)
    private String apFlowId;

    /** Resolved AP sync webhook URL */
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    /** Execution status: PENDING, RUNNING, SUCCESS, FAILED, TIMEOUT */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** Execution source: SERVICE_TASK, ACTION */
    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    /** Input data JSON */
    @Type(JsonbType.class)
    @Column(name = "input_data", columnDefinition = "JSONB")
    private String inputData;

    /** Output data JSON */
    @Type(JsonbType.class)
    @Column(name = "output_data", columnDefinition = "JSONB")
    private String outputData;

    /** Error message */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Retry count */
    @Column(name = "retry_count")
    private Integer retryCount;

    /** Start time */
    @Column(name = "started_at")
    private Instant startedAt;

    /** Completion time */
    @Column(name = "completed_at")
    private Instant completedAt;

    /** Timeout seconds */
    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    /** Created time */
    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (timeoutSeconds == null) {
            timeoutSeconds = 120;
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getApFlowId() { return apFlowId; }
    public void setApFlowId(String apFlowId) { this.apFlowId = apFlowId; }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getInputData() { return inputData; }
    public void setInputData(String inputData) { this.inputData = inputData; }

    public String getOutputData() { return outputData; }
    public void setOutputData(String outputData) { this.outputData = outputData; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
