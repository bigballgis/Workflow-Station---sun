package com.workflow.enums;

/**
 * Audit risk level enum
 * Used to identify the risk level of operations for security monitoring and alerting
 */
public enum AuditRiskLevel {
    
    LOW("Low Risk", "Routine operation, no special risk"),
    MEDIUM("Medium Risk", "Operation that needs attention, may affect business processes"),
    HIGH("High Risk", "Important operation, may affect system security or data integrity"),
    CRITICAL("Critical Risk", "Critical operation, must be strictly monitored and reviewed");
    
    private final String description;
    private final String detail;
    
    AuditRiskLevel(String description, String detail) {
        this.description = description;
        this.detail = detail;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getDetail() {
        return detail;
    }
    
    /**
     * Evaluate risk level based on operation type and resource type
     */
    public static AuditRiskLevel evaluateRiskLevel(AuditOperationType operationType, AuditResourceType resourceType) {
        // System-level operations are typically high risk
        if (resourceType == AuditResourceType.SYSTEM) {
            return CRITICAL;
        }
        
        // Delete operations are typically high risk
        if (operationType.name().contains("DELETE")) {
            return HIGH;
        }
        
        // Permission-related operations are high risk
        if (operationType == AuditOperationType.ASSIGN_ROLE || 
            operationType == AuditOperationType.REVOKE_ROLE ||
            operationType == AuditOperationType.ACCESS_DENIED) {
            return HIGH;
        }
        
        // Process definition operations are medium risk
        if (resourceType == AuditResourceType.PROCESS_DEFINITION) {
            return MEDIUM;
        }
        
        // Data export/import is medium risk
        if (operationType == AuditOperationType.EXPORT_DATA || 
            operationType == AuditOperationType.IMPORT_DATA) {
            return MEDIUM;
        }
        
        // Other operations default to low risk
        return LOW;
    }
    
    /**
     * Get risk level from string
     */
    public static AuditRiskLevel fromString(String riskLevel) {
        for (AuditRiskLevel level : AuditRiskLevel.values()) {
            if (level.name().equals(riskLevel)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown risk level: " + riskLevel);
    }
}