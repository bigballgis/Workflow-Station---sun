package com.platform.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * User principal containing authenticated user information.
 * Shared across all platform modules for unified authentication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique user identifier
     */
    private String userId;
    
    /**
     * Username for display
     */
    private String username;
    
    /**
     * User's email address
     */
    private String email;
    
    /**
     * List of role identifiers assigned to the user
     */
    private List<String> roles;
    
    /**
     * List of permission identifiers granted to the user
     */
    private List<String> permissions;
    
    /**
     * User's preferred language (en, zh-CN, zh-TW)
     */
    private String language;
    
    /**
     * User's display name
     */
    private String displayName;
    
    /**
     * Whether the user is a super admin
     */
    private boolean superAdmin;
    
    /**
     * Check if user has a specific role
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
    
    /**
     * Check if user has a specific permission
     */
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
    
    /**
     * Check if user has any of the specified roles
     */
    public boolean hasAnyRole(String... roleList) {
        if (roles == null) return false;
        for (String role : roleList) {
            if (roles.contains(role)) return true;
        }
        return false;
    }
    
    /**
     * Check if user has any of the specified permissions
     */
    public boolean hasAnyPermission(String... permissionList) {
        if (permissions == null) return false;
        for (String permission : permissionList) {
            if (permissions.contains(permission)) return true;
        }
        return false;
    }
}
