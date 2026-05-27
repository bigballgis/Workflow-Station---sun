package com.workflow.enums;

/**
 * Audit operation type enum
 * Defines all operation types that require audit logging
 */
public enum AuditOperationType {
    
    // Process definition operations
    DEPLOY_PROCESS("Deploy Process Definition"),
    UPDATE_PROCESS("Update Process Definition"),
    DELETE_PROCESS("Delete Process Definition"),
    SUSPEND_PROCESS_DEFINITION("Suspend Process Definition"),
    ACTIVATE_PROCESS_DEFINITION("Activate Process Definition"),
    
    // Process instance operations
    START_PROCESS("Start Process Instance"),
    SUSPEND_PROCESS_INSTANCE("Suspend Process Instance"),
    RESUME_PROCESS_INSTANCE("Resume Process Instance"),
    TERMINATE_PROCESS_INSTANCE("Terminate Process Instance"),
    DELETE_PROCESS_INSTANCE("Delete Process Instance"),
    
    // Task operations
    CREATE_TASK("Create Task"),
    ASSIGN_TASK("Assign Task"),
    CLAIM_TASK("Claim Task"),
    DELEGATE_TASK("Delegate Task"),
    COMPLETE_TASK("Complete Task"),
    RETURN_TASK("Return Task"),
    DELETE_TASK("Delete Task"),
    UPDATE_TASK("Update Task"),
    
    // Variable operations
    SET_VARIABLE("Set Variable"),
    UPDATE_VARIABLE("Update Variable"),
    DELETE_VARIABLE("Delete Variable"),
    
    // Form operations
    CREATE_FORM("Create Form"),
    UPDATE_FORM("Update Form"),
    DELETE_FORM("Delete Form"),
    SUBMIT_FORM("Submit Form"),
    
    // User and permission operations
    CREATE_USER("Create User"),
    UPDATE_USER("Update User"),
    DELETE_USER("Delete User"),
    ASSIGN_ROLE("Assign Role"),
    REVOKE_ROLE("Revoke Role"),
    
    // System operations
    LOGIN("User Login"),
    LOGOUT("User Logout"),
    ACCESS_DENIED("Access Denied"),
    SYSTEM_ERROR("System Error"),
    SECURITY_EVENT("Security Event"),
    
    // Data operations
    EXPORT_DATA("Export Data"),
    IMPORT_DATA("Import Data"),
    BACKUP_DATA("Backup Data"),
    RESTORE_DATA("Restore Data"),
    
    // Monitoring operations
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
     * Get operation type from string
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