package com.platform.security.service;

import com.platform.common.dto.DataFilter;

import java.util.List;
import java.util.Set;

/**
 * Permission Service interface for unified permission control.
 * Validates: Requirements 4.1, 4.2, 4.4, 4.5, 4.6
 */
public interface PermissionService {
    
    /**
     * Check if user has a specific permission.
     * 
     * @param userId User's unique identifier
     * @param permission Permission identifier
     * @return true if user has the permission
     */
    boolean hasPermission(String userId, String permission);
    
    /**
     * Check if user has any of the specified permissions.
     * 
     * @param userId User's unique identifier
     * @param permissions Set of permission identifiers
     * @return true if user has any of the permissions
     */
    boolean hasAnyPermission(String userId, Set<String> permissions);
    
    /**
     * Check if user has all of the specified permissions.
     * 
     * @param userId User's unique identifier
     * @param permissions Set of permission identifiers
     * @return true if user has all permissions
     */
    boolean hasAllPermissions(String userId, Set<String> permissions);
    
    /**
     * Check if user has API access permission.
     * 
     * @param userId User's unique identifier
     * @param apiPath API path (e.g., "/api/users")
     * @param method HTTP method (GET, POST, PUT, DELETE)
     * @return true if user can access the API
     */
    boolean hasApiPermission(String userId, String apiPath, String method);
    
    /**
     * Get data filter for row-level security.
     * 
     * @param userId User's unique identifier
     * @param resourceType Resource type (e.g., "process", "task", "form")
     * @return DataFilter with SQL conditions
     */
    DataFilter getDataFilter(String userId, String resourceType);
    
    /**
     * Get accessible columns for column-level security.
     * 
     * @param userId User's unique identifier
     * @param resourceType Resource type
     * @return List of accessible column names
     */
    List<String> getAccessibleColumns(String userId, String resourceType);
    
    /**
     * Get all permissions for a user.
     * 
     * @param userId User's unique identifier
     * @return Set of permission identifiers
     */
    Set<String> getUserPermissions(String userId);
    
    /**
     * Get all roles for a user.
     * 
     * @param userId User's unique identifier
     * @return Set of role identifiers
     */
    Set<String> getUserRoles(String userId);
    
    /**
     * Check if user has a specific role.
     * 
     * @param userId User's unique identifier
     * @param role Role identifier
     * @return true if user has the role
     */
    boolean hasRole(String userId, String role);
    
    /**
     * Invalidate permission cache for a user.
     * 
     * @param userId User's unique identifier
     */
    void invalidateUserPermissionCache(String userId);
}
