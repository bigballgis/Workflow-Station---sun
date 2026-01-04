package com.workflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 异常记录实体类
 * 用于记录流程执行过程中的异常信息
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
     * 流程实例ID
     */
    @Column(length = 64)
    private String processInstanceId;
    
    /**
     * 流程定义ID
     */
    @Column(length = 64)
    private String processDefinitionId;
    
    /**
     * 流程定义Key
     */
    @Column(length = 255)
    private String processDefinitionKey;
    
    /**
     * 任务ID
     */
    @Column(length = 64)
    private String taskId;
    
    /**
     * 任务名称
     */
    @Column(length = 255)
    private String taskName;
    
    /**
     * 活动ID（BPMN节点ID）
     */
    @Column(length = 255)
    private String activityId;
    
    /**
     * 活动名称
     */
    @Column(length = 255)
    private String activityName;

    /**
     * 异常类型
     */
    @Column(length = 100, nullable = false)
    private String exceptionType;
    
    /**
     * 异常类名
     */
    @Column(length = 500)
    private String exceptionClass;
    
    /**
     * 异常消息
     */
    @Column(columnDefinition = "TEXT")
    private String exceptionMessage;
    
    /**
     * 完整堆栈跟踪
     */
    @Column(columnDefinition = "TEXT")
    private String stackTrace;
    
    /**
     * 根本原因
     */
    @Column(columnDefinition = "TEXT")
    private String rootCause;
    
    /**
     * 严重级别: CRITICAL, HIGH, MEDIUM, LOW
     */
    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private ExceptionSeverity severity;
    
    /**
     * 异常状态: PENDING, PROCESSING, RESOLVED, IGNORED
     */
    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private ExceptionStatus status;
    
    /**
     * 上下文数据（JSON格式）
     */
    @Column(columnDefinition = "TEXT")
    private String contextData;
    
    /**
     * 流程变量快照（JSON格式）
     */
    @Column(columnDefinition = "TEXT")
    private String variablesSnapshot;
    
    /**
     * 异常发生时间
     */
    @Column(nullable = false)
    private LocalDateTime occurredTime;
    
    /**
     * 重试次数
     */
    @Column(nullable = false)
    private Integer retryCount = 0;
    
    /**
     * 最大重试次数
     */
    @Column(nullable = false)
    private Integer maxRetryCount = 3;
    
    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryTime;
    
    /**
     * 最后重试时间
     */
    private LocalDateTime lastRetryTime;

    /**
     * 是否已解决
     */
    @Column(nullable = false)
    private Boolean resolved = false;
    
    /**
     * 解决时间
     */
    private LocalDateTime resolvedTime;
    
    /**
     * 解决人
     */
    @Column(length = 64)
    private String resolvedBy;
    
    /**
     * 解决方式: AUTO_RETRY, MANUAL_FIX, IGNORED, COMPENSATED
     */
    @Column(length = 50)
    private String resolutionMethod;
    
    /**
     * 解决备注
     */
    @Column(columnDefinition = "TEXT")
    private String resolutionNote;
    
    /**
     * 是否已发送告警
     */
    @Column(nullable = false)
    private Boolean alertSent = false;
    
    /**
     * 告警发送时间
     */
    private LocalDateTime alertSentTime;
    
    /**
     * 关联的异常记录ID（用于追踪重试链）
     */
    @Column(length = 64)
    private String parentExceptionId;
    
    /**
     * 租户ID
     */
    @Column(length = 64)
    private String tenantId;
    
    /**
     * 创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createdTime;
    
    /**
     * 更新时间
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
    
    // 枚举定义
    public enum ExceptionSeverity {
        CRITICAL,  // 严重：系统级错误，需要立即处理
        HIGH,      // 高：业务流程中断，需要尽快处理
        MEDIUM,    // 中：部分功能受影响，可以稍后处理
        LOW        // 低：轻微问题，可以忽略或延后处理
    }
    
    public enum ExceptionStatus {
        PENDING,    // 待处理
        PROCESSING, // 处理中
        RESOLVED,   // 已解决
        IGNORED     // 已忽略
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
