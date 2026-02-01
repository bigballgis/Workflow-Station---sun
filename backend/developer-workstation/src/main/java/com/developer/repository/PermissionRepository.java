package com.developer.repository;

import com.platform.security.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for permission queries in the security permission system.
 * Provides database access for user permission validation.
 * 
 * Requirements: 1.1, 4.4, 5.3
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, String> {
    
    /**
     * Find all permissions for a user by username.
     * Joins across user, role, and permission tables.
     * 
     * @param username the username to query permissions for
     * @return list of permissions the user has
     */
    @Query(value = "SELECT DISTINCT p.code, p.name, p.description, p.module, p.resource_type, p.action " +
           "FROM sys_permissions p " +
           "JOIN sys_role_permissions rp ON p.id = rp.permission_id " +
           "JOIN sys_roles r ON rp.role_id = r.id " +
           "JOIN sys_user_roles ur ON r.id = ur.role_id " +
           "JOIN sys_users u ON ur.user_id = u.id " +
           "WHERE u.username = :username AND r.status = 'ACTIVE' AND p.enabled = true",
           nativeQuery = true)
    List<Object[]> findPermissionsByUsername(@Param("username") String username);
    
    /**
     * Check if a user has a specific permission by username.
     * Returns true if the user has the permission, false otherwise.
     * 
     * @param username the username to check
     * @param permission the permission code to check for
     * @return true if user has the permission, false otherwise
     */
    @Query(value = "SELECT CASE WHEN COUNT(p.id) > 0 THEN true ELSE false END " +
           "FROM sys_permissions p " +
           "JOIN sys_role_permissions rp ON p.id = rp.permission_id " +
           "JOIN sys_roles r ON rp.role_id = r.id " +
           "JOIN sys_user_roles ur ON r.id = ur.role_id " +
           "JOIN sys_users u ON ur.user_id = u.id " +
           "WHERE u.username = :username AND p.code = :permission " +
           "AND r.status = 'ACTIVE' AND p.enabled = true",
           nativeQuery = true)
    boolean hasPermission(@Param("username") String username, @Param("permission") String permission);
    
    /**
     * Find all permissions for a user by user ID.
     * Alternative lookup method for user ID-based queries.
     * 
     * @param userId the user ID to query permissions for
     * @return list of permissions the user has
     */
    @Query(value = "SELECT DISTINCT p.code, p.name, p.description, p.module, p.resource_type, p.action " +
           "FROM sys_permissions p " +
           "JOIN sys_role_permissions rp ON p.id = rp.permission_id " +
           "JOIN sys_roles r ON rp.role_id = r.id " +
           "JOIN sys_user_roles ur ON r.id = ur.role_id " +
           "WHERE ur.user_id = :userId AND r.status = 'ACTIVE' AND p.enabled = true",
           nativeQuery = true)
    List<Object[]> findPermissionsByUserId(@Param("userId") String userId);
    
    /**
     * Check if a user has a specific permission by user ID.
     * Alternative lookup method for user ID-based queries.
     * 
     * @param userId the user ID to check
     * @param permission the permission code to check for
     * @return true if user has the permission, false otherwise
     */
    @Query(value = "SELECT CASE WHEN COUNT(p.id) > 0 THEN true ELSE false END " +
           "FROM sys_permissions p " +
           "JOIN sys_role_permissions rp ON p.id = rp.permission_id " +
           "JOIN sys_roles r ON rp.role_id = r.id " +
           "JOIN sys_user_roles ur ON r.id = ur.role_id " +
           "WHERE ur.user_id = :userId AND p.code = :permission " +
           "AND r.status = 'ACTIVE' AND p.enabled = true",
           nativeQuery = true)
    boolean hasPermissionByUserId(@Param("userId") String userId, @Param("permission") String permission);
}