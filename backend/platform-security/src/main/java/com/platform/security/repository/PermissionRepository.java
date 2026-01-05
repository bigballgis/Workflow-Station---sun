package com.platform.security.repository;

import com.platform.security.model.ApiPermission;
import com.platform.security.model.DataPermission;
import com.platform.security.model.Permission;
import com.platform.security.model.Role;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository interface for permission data access.
 * This interface should be implemented by the actual data layer.
 */
public interface PermissionRepository {
    
    /**
     * Find all permissions for a user by user ID.
     */
    Set<Permission> findPermissionsByUserId(String userId);
    
    /**
     * Find all roles for a user by user ID.
     */
    Set<Role> findRolesByUserId(String userId);
    
    /**
     * Find API permission by path and method.
     */
    Optional<ApiPermission> findApiPermission(String apiPath, String method);
    
    /**
     * Find all API permissions.
     */
    List<ApiPermission> findAllApiPermissions();
    
    /**
     * Find data permission for a user and resource type.
     */
    Optional<DataPermission> findDataPermission(String userId, String resourceType);
    
    /**
     * Find permission by code.
     */
    Optional<Permission> findPermissionByCode(String code);
    
    /**
     * Find role by code.
     */
    Optional<Role> findRoleByCode(String code);
}
