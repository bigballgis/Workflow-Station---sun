package com.workflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 审计日志实体类
 * 记录所有流程操作的审计轨迹，支持合规检查和业务分析
 */
@Entity
@Table(name = "wf_audit_logs", indexes = {
    @Index(name = "idx_audit_user_id", columnList = "userId"),
    @Index(name = "idx_audit_operation_type", columnList = "operationType"),
    @Index(name = "idx_audit_resource_type", columnList = "resourceType"),
    @Index(name = "idx_audit_resource_id", columnList = "resourceId"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_ip_address", columnList = "ipAddress"),
    @Index(name = "idx_audit_session_id", columnList = "sessionId"),
    @Index(name = "idx_audit_tenant_id", columnList = "tenantId"),
    @Index(name = "idx_audit_composite", columnList = "userId,operationType,timestamp")
})
public class AuditLog {
    
    @Id
    @Column(length = 64)
    private String id;
    
    /**
     * 操作用户ID
     */
    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;
    
    /**
     * 操作类型：CREATE, UPDATE, DELETE, EXECUTE, COMPLETE, DELEGATE, CLAIM, SUSPEND, RESUME, TERMINATE
     */
    @Column(name = "operation_type", length = 50, nullable = false)
    private String operationType;
    
    /**
     * 资源类型：PROCESS_DEFINITION, PROCESS_INSTANCE, TASK, VARIABLE, FORM, USER, ROLE
     */
    @Column(name = "resource_type", length = 50, nullable = false)
    private String resourceType;
    
    /**
     * 资源ID
     */
    @Column(name = "resource_id", length = 64, nullable = false)
    private String resourceId;
    
    /**
     * 资源名称
     */
    @Column(name = "resource_name", length = 255)
    private String resourceName;
    
    /**
     * 操作描述
     */
    @Column(name = "operation_description", columnDefinition = "TEXT")
    private String operationDescription;
    
    /**
     * 操作前数据（JSON格式，敏感数据已脱敏）
     */
    @Column(name = "before_data", columnDefinition = "JSONB")
    private String beforeData;
    
    /**
     * 操作后数据（JSON格式，敏感数据已脱敏）
     */
    @Column(name = "after_data", columnDefinition = "JSONB")
    private String afterData;
    
    /**
     * 操作结果：SUCCESS, FAILURE, PARTIAL
     */
    @Column(name = "operation_result", length = 20, nullable = false)
    private String operationResult;
    
    /**
     * 错误信息（操作失败时记录）
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    /**
     * 操作时间戳
     */
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    /**
     * 客户端IP地址
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    /**
     * 用户代理信息
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    /**
     * 会话ID
     */
    @Column(name = "session_id", length = 128)
    private String sessionId;
    
    /**
     * 请求ID（用于关联同一请求的多个操作）
     */
    @Column(name = "request_id", length = 64)
    private String requestId;
    
    /**
     * 操作持续时间（毫秒）
     */
    @Column(name = "duration_ms")
    private Long durationMs;
    
    /**
     * 租户ID（多租户支持）
     */
    @Column(name = "tenant_id", length = 64)
    private String tenantId;
    
    /**
     * 额外的上下文信息（JSON格式）
     */
    @Column(name = "context_data", columnDefinition = "JSONB")
    private String contextData;
    
    /**
     * 风险等级：LOW, MEDIUM, HIGH, CRITICAL
     */
    @Column(name = "risk_level", length = 20)
    private String riskLevel;
    
    /**
     * 是否为敏感操作
     */
    @Column(name = "is_sensitive", nullable = false)
    private Boolean isSensitive = false;
    
    // 构造函数
    public AuditLog() {}
    
    public AuditLog(String id, String userId, String operationType, String resourceType, 
                   String resourceId, String operationResult, LocalDateTime timestamp) {
        this.id = id;
        this.userId = userId;
        this.operationType = operationType;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.operationResult = operationResult;
        this.timestamp = timestamp;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    
    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }
    
    public String getOperationDescription() { return operationDescription; }
    public void setOperationDescription(String operationDescription) { this.operationDescription = operationDescription; }
    
    public String getBeforeData() { return beforeData; }
    public void setBeforeData(String beforeData) { this.beforeData = beforeData; }
    
    public String getAfterData() { return afterData; }
    public void setAfterData(String afterData) { this.afterData = afterData; }
    
    public String getOperationResult() { return operationResult; }
    public void setOperationResult(String operationResult) { this.operationResult = operationResult; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    
    public String getContextData() { return contextData; }
    public void setContextData(String contextData) { this.contextData = contextData; }
    
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    
    public Boolean getIsSensitive() { return isSensitive; }
    public void setIsSensitive(Boolean isSensitive) { this.isSensitive = isSensitive; }
}