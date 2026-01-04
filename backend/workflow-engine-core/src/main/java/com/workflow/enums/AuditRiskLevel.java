package com.workflow.enums;

/**
 * 审计风险等级枚举
 * 用于标识操作的风险等级，便于安全监控和告警
 */
public enum AuditRiskLevel {
    
    LOW("低风险", "常规操作，无特殊风险"),
    MEDIUM("中等风险", "需要关注的操作，可能影响业务流程"),
    HIGH("高风险", "重要操作，可能影响系统安全或数据完整性"),
    CRITICAL("严重风险", "关键操作，必须严格监控和审查");
    
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
     * 根据操作类型和资源类型评估风险等级
     */
    public static AuditRiskLevel evaluateRiskLevel(AuditOperationType operationType, AuditResourceType resourceType) {
        // 系统级操作通常是高风险
        if (resourceType == AuditResourceType.SYSTEM) {
            return CRITICAL;
        }
        
        // 删除操作通常是高风险
        if (operationType.name().contains("DELETE")) {
            return HIGH;
        }
        
        // 权限相关操作是高风险
        if (operationType == AuditOperationType.ASSIGN_ROLE || 
            operationType == AuditOperationType.REVOKE_ROLE ||
            operationType == AuditOperationType.ACCESS_DENIED) {
            return HIGH;
        }
        
        // 流程定义操作是中等风险
        if (resourceType == AuditResourceType.PROCESS_DEFINITION) {
            return MEDIUM;
        }
        
        // 数据导出/导入是中等风险
        if (operationType == AuditOperationType.EXPORT_DATA || 
            operationType == AuditOperationType.IMPORT_DATA) {
            return MEDIUM;
        }
        
        // 其他操作默认为低风险
        return LOW;
    }
    
    /**
     * 根据字符串获取风险等级
     */
    public static AuditRiskLevel fromString(String riskLevel) {
        for (AuditRiskLevel level : AuditRiskLevel.values()) {
            if (level.name().equals(riskLevel)) {
                return level;
            }
        }
        throw new IllegalArgumentException("未知的风险等级: " + riskLevel);
    }
}