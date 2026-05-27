package com.workflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Exception record entity
 * Used to record exception information during process execution
 */
@Entity
@Table(name = "wf_exception_records", indexes = {
    @Index(name = "idx_exception_process_instance", columnList = "processInstanceId"),
    @Index(name = "idx_exception_task_id", columnList = "taskId"),
    @Index(name = "idx_exception_type", columnList = "exceptionType"),
    @Index(name = "idx_exception_severity", columnList = "severity"),
    @Index(name = "idx_exception_status", columnList = "status"),
    @Index(name = "idx_exception_occurred_time", columnList = "occurredTime"),
    @Index(name = "idx_exception_resolved", columnList = "resolved")
})
public class ExceptionRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    /**
     * Process instance ID
     */
    @Column(length = 64)
    private String processInstanceId;
    
    /**
     * Process definition ID
     */
    @Column(length = 64)
    private String processDefinitionId;
    
    /**
     * Process definition key
     */
    @Column(length = 255)
    private String processDefinitionKey;
    
    /**
     * Task ID
     */
    @Column(length = 64)
    private String taskId;
    
    /**
     * Task name
     */
    @Column(length = 255)
    private String taskName;
    
    /**
     * Activity ID (BPMN node ID)
     */
    @Column(length = 255)
    private String activityId;
    
    /**
     * Activity name
     */
    @Column(length = 255)
    private String activityName;

    /**
     * Exception type
     */
    @Column(length = 100, nullable = false)
    private String exceptionType;
    
    /**
     * Exception class name
     */
    @Column(length = 500)
    private String exceptionClass;
    
    /**
     * Exception message
     */
    @Column(columnDefinition = "TEXT")
    private String exceptionMessage;
    
    /**
     * Full stack trace
     */
    @Column(columnDefinition = "TEXT")
    private String stackTrace;
    
    /**
     * Root cause
     */
    @Column(columnDefinition = "TEXT")
    private String rootCause;
    
    /**
     * Severity level: CRITICAL, HIGH, MEDIUM, LOW
     */
    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private ExceptionSeverity severity;
    
    /**
     * Exception status: PENDING, PROCESSING, RESOLVED, IGNORED
     */
    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private ExceptionStatus status;
    
    /**
     * Context data (JSON format)
     */
    @Column(columnDefinition = "TEXT")
    private String contextData;
    
    /**
     * Process variable snapshot (JSON format)
     */
    @Column(columnDefinition = "TEXT")
    private String variablesSnapshot;
    
    /**
     * Exception occurrence time
     */
    @Column(nullable = false)
    private LocalDateTime occurredTime;
    
    /**
     * Retry count
     */
    @Column(nullable = false)
    private Integer retryCount = 0;
    
    /**
     * Max retry count
     */
    @Column(nullable = false)
    private Integer maxRetryCount = 3;
    
    /**
     * Next retry time
     */
    private LocalDateTime nextRetryTime;
    
    /**
     * Last retry time
     */
    private LocalDateTime lastRetryTime;

    /**
     * Whether resolved
     */
    @Column(nullable = false)
    private Boolean resolved = false;
    
    /**
     * Resolution time
     */
    private LocalDateTime resolvedTime;
    
    /**
     * Resolver
     */
    @Column(length = 64)
    private String resolvedBy;
    
    /**
     * Resolution method: AUTO_RETRY, MANUAL_FIX, IGNORED, COMPENSATED
     */
    @Column(length = 50)
    private String resolutionMethod;
    
    /**
     * Resolution notes
     */
    @Column(columnDefinition = "TEXT")
    private String resolutionNote;
    
    /**
     * Whether alert sent
     */
    @Column(nullable = false)
    private Boolean alertSent = false;
    
    /**
     * Alert sent time
     */
    private LocalDateTime alertSentTime;
    
    /**
     * Related exception record ID (used to track the retry chain)
     */
    @Column(length = 64)
    private String parentExceptionId;
    
    /**
     * Tenant ID
     */
    @Column(length = 64)
    private String tenantId;
    
    /**
     * Created time
     */
    @Column(nullable = false)
    private LocalDateTime createdTime;
    
    /**
     * Updated time
     */
    private LocalDateTime updatedTime;
    
    @PrePersist
    protected void onCreate() {
        createdTime = LocalDateTime.now();
        if (occurredTime == null) {
            occurredTime = createdTime;
        }
        if (status == null) {
            status = ExceptionStatus.PENDING;
        }
        if (severity == null) {
            severity = ExceptionSeverity.MEDIUM;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedTime = LocalDateTime.now();
    }
    
    // Enum definitions
    public enum ExceptionSeverity {
        CRITICAL,  // Critical: system-level error, requires immediate attention
        HIGH,      // High: business process interrupted, requires prompt attention
        MEDIUM,    // Medium: partial functionality affected, can be handled later
        LOW        // Low: minor issue, can be ignored or deferred
    }
    
    public enum ExceptionStatus {
        PENDING,    // Pending
        PROCESSING, // Processing
        RESOLVED,   // Resolved
        IGNORED     // Ignored
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    
    public String getProcessDefinitionId() { return processDefinitionId; }
    public void setProcessDefinitionId(String processDefinitionId) { this.processDefinitionId = processDefinitionId; }
    
    public String getProcessDefinitionKey() { return processDefinitionKey; }
    public void setProcessDefinitionKey(String processDefinitionKey) { this.processDefinitionKey = processDefinitionKey; }
    
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    
    public String getActivityId() { return activityId; }
    public void setActivityId(String activityId) { this.activityId = activityId; }
    
    public String getActivityName() { return activityName; }
    public void setActivityName(String activityName) { this.activityName = activityName; }
    
    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }
    
    public String getExceptionClass() { return exceptionClass; }
    public void setExceptionClass(String exceptionClass) { this.exceptionClass = exceptionClass; }
    
    public String getExceptionMessage() { return exceptionMessage; }
    public void setExceptionMessage(String exceptionMessage) { this.exceptionMessage = exceptionMessage; }
    
    public String getStackTrace() { return stackTrace; }
    public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }
    
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    
    public ExceptionSeverity getSeverity() { return severity; }
    public void setSeverity(ExceptionSeverity severity) { this.severity = severity; }
    
    public ExceptionStatus getStatus() { return status; }
    public void setStatus(ExceptionStatus status) { this.status = status; }
    
    public String getContextData() { return contextData; }
    public void setContextData(String contextData) { this.contextData = contextData; }
    
    public String getVariablesSnapshot() { return variablesSnapshot; }
    public void setVariablesSnapshot(String variablesSnapshot) { this.variablesSnapshot = variablesSnapshot; }
    
    public LocalDateTime getOccurredTime() { return occurredTime; }
    public void setOccurredTime(LocalDateTime occurredTime) { this.occurredTime = occurredTime; }
    
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    
    public Integer getMaxRetryCount() { return maxRetryCount; }
    public void setMaxRetryCount(Integer maxRetryCount) { this.maxRetryCount = maxRetryCount; }
    
    public LocalDateTime getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(LocalDateTime nextRetryTime) { this.nextRetryTime = nextRetryTime; }
    
    public LocalDateTime getLastRetryTime() { return lastRetryTime; }
    public void setLastRetryTime(LocalDateTime lastRetryTime) { this.lastRetryTime = lastRetryTime; }
    
    public Boolean getResolved() { return resolved; }
    public void setResolved(Boolean resolved) { this.resolved = resolved; }
    
    public LocalDateTime getResolvedTime() { return resolvedTime; }
    public void setResolvedTime(LocalDateTime resolvedTime) { this.resolvedTime = resolvedTime; }
    
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    
    public String getResolutionMethod() { return resolutionMethod; }
    public void setResolutionMethod(String resolutionMethod) { this.resolutionMethod = resolutionMethod; }
    
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
    
    public Boolean getAlertSent() { return alertSent; }
    public void setAlertSent(Boolean alertSent) { this.alertSent = alertSent; }
    
    public LocalDateTime getAlertSentTime() { return alertSentTime; }
    public void setAlertSentTime(LocalDateTime alertSentTime) { this.alertSentTime = alertSentTime; }
    
    public String getParentExceptionId() { return parentExceptionId; }
    public void setParentExceptionId(String parentExceptionId) { this.parentExceptionId = parentExceptionId; }
    
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    
    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
}
