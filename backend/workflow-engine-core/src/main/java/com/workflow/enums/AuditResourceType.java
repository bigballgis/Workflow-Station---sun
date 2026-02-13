package com.workflow.enums;

/**
 * 审计资源类型枚举
 * 定义所有需要审计的资源类型
 */
public enum AuditResourceType {
    
    PROCESS_DEFINITION("Process Definition"),
    PROCESS_INSTANCE("Process Instance"),
    TASK("Task"),
    VARIABLE("Process Variable"),
    FORM("Form"),
    USER("User"),
    ROLE("Role"),
    BUSINESS_UNIT("Business Unit"),
    VIRTUAL_GROUP("Virtual Group"),
    SYSTEM("System"),
    DATA_TABLE("Data Table"),
    FILE("File"),
    NOTIFICATION("Notification"),
    AUDIT_LOG("Audit Log");
    
    private final String description;
    
    AuditResourceType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据字符串获取资源类型
     */
    public static AuditResourceType fromString(String resourceType) {
        for (AuditResourceType type : AuditResourceType.values()) {
            if (type.name().equals(resourceType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown resource type: " + resourceType);
    }
}