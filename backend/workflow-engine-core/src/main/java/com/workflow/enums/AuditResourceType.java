package com.workflow.enums;

/**
 * 审计资源类型枚举
 * 定义所有需要审计的资源类型
 */
public enum AuditResourceType {
    
    PROCESS_DEFINITION("流程定义"),
    PROCESS_INSTANCE("流程实例"),
    TASK("任务"),
    VARIABLE("流程变量"),
    FORM("表单"),
    USER("用户"),
    ROLE("角色"),
    DEPARTMENT("部门"),
    VIRTUAL_GROUP("虚拟组"),
    SYSTEM("系统"),
    DATA_TABLE("数据表"),
    FILE("文件"),
    NOTIFICATION("通知"),
    AUDIT_LOG("审计日志");
    
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
        throw new IllegalArgumentException("未知的资源类型: " + resourceType);
    }
}