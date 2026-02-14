package com.workflow.enums;

/**
 * 审计操作类型枚举
 * 定义所有需要记录审计日志的操作类型
 */
public enum AuditOperationType {
    
    // 流程定义操作
    DEPLOY_PROCESS("Deploy Process Definition"),
    UPDATE_PROCESS("Update Process Definition"),
    DELETE_PROCESS("Delete Process Definition"),
    SUSPEND_PROCESS_DEFINITION("Suspend Process Definition"),
    ACTIVATE_PROCESS_DEFINITION("Activate Process Definition"),
    
    // 流程实例操作
    START_PROCESS("Start Process Instance"),
    SUSPEND_PROCESS_INSTANCE("Suspend Process Instance"),
    RESUME_PROCESS_INSTANCE("Resume Process Instance"),
    TERMINATE_PROCESS_INSTANCE("Terminate Process Instance"),
    DELETE_PROCESS_INSTANCE("Delete Process Instance"),
    
    // 任务操作
    CREATE_TASK("Create Task"),
    ASSIGN_TASK("Assign Task"),
    CLAIM_TASK("Claim Task"),
    DELEGATE_TASK("Delegate Task"),
    COMPLETE_TASK("Complete Task"),
    RETURN_TASK("Return Task"),
    DELETE_TASK("Delete Task"),
    UPDATE_TASK("Update Task"),
    
    // 变量操作
    SET_VARIABLE("Set Variable"),
    UPDATE_VARIABLE("Update Variable"),
    DELETE_VARIABLE("Delete Variable"),
    
    // 表单操作
    CREATE_FORM("Create Form"),
    UPDATE_FORM("Update Form"),
    DELETE_FORM("Delete Form"),
    SUBMIT_FORM("Submit Form"),
    
    // 用户和权限操作
    CREATE_USER("Create User"),
    UPDATE_USER("Update User"),
    DELETE_USER("Delete User"),
    ASSIGN_ROLE("Assign Role"),
    REVOKE_ROLE("Revoke Role"),
    
    // 系统操作
    LOGIN("User Login"),
    LOGOUT("User Logout"),
    ACCESS_DENIED("Access Denied"),
    SYSTEM_ERROR("System Error"),
    SECURITY_EVENT("Security Event"),
    
    // 数据操作
    EXPORT_DATA("Export Data"),
    IMPORT_DATA("Import Data"),
    BACKUP_DATA("Backup Data"),
    RESTORE_DATA("Restore Data"),
    
    // 监控操作
    VIEW_PROCESS_DIAGRAM("View Process Diagram"),
    VIEW_STATISTICS("View Statistics"),
    VIEW_AUDIT_LOG("View Audit Log");
    
    private final String description;
    
    AuditOperationType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据字符串获取操作类型
     */
    public static AuditOperationType fromString(String operationType) {
        for (AuditOperationType type : AuditOperationType.values()) {
            if (type.name().equals(operationType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown operation type: " + operationType);
    }
}