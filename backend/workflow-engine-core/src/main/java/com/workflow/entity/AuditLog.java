package com.workflow.entity;

import com.workflow.config.JsonbType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import java.time.LocalDateTime;

/**
 * Audit log entity
 * Records audit trail of all process operations, supports compliance checking and business analysis
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
     * Operation user ID
     */
    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;
    
    /**
     * Operation type: CREATE, UPDATE, DELETE, EXECUTE, COMPLETE, DELEGATE, CLAIM, SUSPEND, RESUME, TERMINATE
     */
    @Column(name = "operation_type", length = 50, nullable = false)
    private String operationType;
    
    /**
     * Resource type: PROCESS_DEFINITION, PROCESS_INSTANCE, TASK, VARIABLE, FORM, USER, ROLE
     */
    @Column(name = "resource_type", length = 50, nullable = false)
    private String resourceType;
    
    /**
     * Resource ID
     */
    @Column(name = "resource_id", length = 64, nullable = false)
    private String resourceId;
    
    /**
     * Resource name
     */
    @Column(name = "resource_name", length = 255)
    private String resourceName;
    
    /**
     * Operation description
     */
    @Column(name = "operation_description", columnDefinition = "TEXT")
    private String operationDescription;
    
    /**
     * Pre-operation data (JSON format, sensitive data masked)
     */
    @Type(JsonbType.class)
    @Column(name = "before_data", columnDefinition = "JSONB")
    private String beforeData;
    
    /**
     * Post-operation data (JSON format, sensitive data masked)
     */
    @Type(JsonbType.class)
    @Column(name = "after_data", columnDefinition = "JSONB")
    private String afterData;
    
    /**
     * Operation result: SUCCESS, FAILURE, PARTIAL
     */
    @Column(name = "operation_result", length = 20, nullable = false)
    private String operationResult;
    
    /**
     * Error message (recorded when operation fails)
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    /**
     * Operation timestamp
     */
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    /**
     * Client IP address
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    /**
     * User agent info
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    /**
     * Session ID
     */
    @Column(name = "session_id", length = 128)
    private String sessionId;
    
    /**
     * Request ID (used to correlate multiple operations of the same request)
     */
    @Column(name = "request_id", length = 64)
    private String requestId;
    
    /**
     * Operation duration (milliseconds)
     */
    @Column(name = "duration_ms")
    private Long durationMs;
    
    /**
     * Tenant ID (multi-tenant support)
     */
    @Column(name = "tenant_id", length = 64)
    private String tenantId;
    
    /**
     * Additional context information (JSON format)
     */
    @Type(JsonbType.class)
    @Column(name = "context_data", columnDefinition = "JSONB")
    private String contextData;
    
    /**
     * Risk level: LOW, MEDIUM, HIGH, CRITICAL
     */
    @Column(name = "risk_level", length = 20)
    private String riskLevel;
    
    /**
     * Whether sensitive operation
     */
    @Column(name = "is_sensitive", nullable = false)
    private Boolean isSensitive = false;
    
    // Constructor
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
