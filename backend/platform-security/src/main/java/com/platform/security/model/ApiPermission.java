package com.platform.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * API Permission model for API-level access control.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiPermission implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String apiPath;
    private String method;
    private Set<String> requiredPermissions;
    private Set<String> requiredRoles;
    private boolean requireAll;
    private boolean enabled;
    
    /**
     * Check if the API path matches the given path.
     * Supports wildcard patterns like /api/users/*
     */
    public boolean matchesPath(String path) {
        if (apiPath.equals(path)) {
            return true;
        }
        if (apiPath.endsWith("/**")) {
            String prefix = apiPath.substring(0, apiPath.length() - 3);
            return path.startsWith(prefix);
        }
        if (apiPath.endsWith("/*")) {
            String prefix = apiPath.substring(0, apiPath.length() - 2);
            return path.startsWith(prefix) && !path.substring(prefix.length()).contains("/");
        }
        return false;
    }
}
