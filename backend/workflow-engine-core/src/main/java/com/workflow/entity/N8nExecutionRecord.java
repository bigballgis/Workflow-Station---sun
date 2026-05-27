package com.workflow.entity;

import com.workflow.config.JsonbType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;

/**
 * N8N workflow execution record entity
 * Records detailed information of each N8N task execution, including execution status, input/output data, duration, etc.
 */
@Entity
@Table(name = "wf_n8n_execution_record", indexes = {
    @Index(name = "idx_n8n_exec_process_instance", columnList = "process_instance_id"),
    @Index(name = "idx_n8n_exec_task_id", columnList = "task_id"),
    @Index(name = "idx_n8n_exec_status", columnList = "status"),
    @Index(name = "idx_n8n_exec_created_at", columnList = "created_at")
})
public class N8nExecutionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Process instance ID */
    @Column(name = "process_instance_id", length = 64)
    private String processInstanceId;

    /** Flowable task ID */
    @Column(name = "task_id", length = 64)
    private String taskId;

    /** N8N config ID */
    @Column(name = "n8n_config_id", length = 36)
    private String n8nConfigId;

    /** N8N workflow ID */
    @Column(name = "n8n_workflow_id", length = 100)
    private String n8nWorkflowId;

    /** Webhook URL */
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    /** Callback token */
    @Column(name = "callback_token", length = 64)
    private String callbackToken;

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
            timeoutSeconds = 300;
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getN8nConfigId() { return n8nConfigId; }
    public void setN8nConfigId(String n8nConfigId) { this.n8nConfigId = n8nConfigId; }

    public String getN8nWorkflowId() { return n8nWorkflowId; }
    public void setN8nWorkflowId(String n8nWorkflowId) { this.n8nWorkflowId = n8nWorkflowId; }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }

    public String getCallbackToken() { return callbackToken; }
    public void setCallbackToken(String callbackToken) { this.callbackToken = callbackToken; }

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
