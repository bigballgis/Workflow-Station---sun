package com.platform.common.enums;

import lombok.Getter;

/**
 * Platform modules enumeration.
 */
@Getter
public enum Module {
    
    DEVELOPER_WORKSTATION("developer-workstation", "Developer Workstation"),
    ADMIN_CENTER("admin-center", "Admin Center"),
    USER_PORTAL("user-portal", "User Portal"),
    WORKFLOW_ENGINE("workflow-engine", "Workflow Engine Core"),
    API_GATEWAY("api-gateway", "API Gateway"),
    AUTH_SERVICE("auth-service", "Authentication Service");
    
    private final String code;
    private final String displayName;
    
    Module(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }
    
    /**
     * Get Module enum from code string
     */
    public static Module fromCode(String code) {
        if (code == null) return null;
        for (Module module : values()) {
            if (module.code.equalsIgnoreCase(code)) {
                return module;
            }
        }
        return null;
    }
}
