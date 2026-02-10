package com.developer.repository;

import com.platform.security.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for role queries in the security permission system.
 * Provides database access for user role validation.
 * 
 * Requirements: 2.1, 4.4
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, String> {
    
    /**
     * Find all roles for a user by username.
     * Joins across user and role tables.
     * Includes both direct user-role assignments and virtual group-role assignments.
     * 
     * @param username the username to query roles for
     * @return list of roles the user has
     */
    @Query(value = "SELECT DISTINCT r.id, r.code, r.name, r.description " +
           "FROM sys_roles r " +
           "WHERE r.id IN ( " +
           "  SELECT ur.role_id " +
           "  FROM sys_user_roles ur " +
           "  JOIN sys_users u ON ur.user_id = u.id " +
           "  WHERE u.username = :username " +
           "  UNION " +
           "  SELECT vgr.role_id " +
           "  FROM sys_virtual_group_roles vgr " +
           "  JOIN sys_virtual_group_members vgm ON vgr.virtual_group_id = vgm.group_id " +
           "  JOIN sys_users u ON vgm.user_id = u.id " +
           "  WHERE u.username = :username " +
           ") AND r.status = 'ACTIVE'",
           nativeQuery = true)
    List<Object[]> findRolesByUsername(@Param("username") String username);
    
    /**
     * Check if a user has a specific role by username.
     * Returns true if the user has the role, false otherwise.
     * Checks both direct user-role assignments and virtual group-role assignments.
     * 
     * @param username the username to check
     * @param role the role code to check for
     * @return true if user has the role, false otherwise
     */
    @Query(value = "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END " +
           "FROM ( " +
           "  SELECT r.id " +
           "  FROM sys_roles r " +
           "  JOIN sys_user_roles ur ON r.id = ur.role_id " +
           "  JOIN sys_users u ON ur.user_id = u.id " +
           "  WHERE u.username = :username AND r.code = :role AND r.status = 'ACTIVE' " +
           "  UNION " +
           "  SELECT r.id " +
           "  FROM sys_roles r " +
           "  JOIN sys_virtual_group_roles vgr ON r.id = vgr.role_id " +
           "  JOIN sys_virtual_group_members vgm ON vgr.virtual_group_id = vgm.group_id " +
           "  JOIN sys_users u ON vgm.user_id = u.id " +
           "  WHERE u.username = :username AND r.code = :role AND r.status = 'ACTIVE' " +
           ") AS combined_roles",
           nativeQuery = true)
    boolean hasRole(@Param("username") String username, @Param("role") String role);
    
    /**
     * Find all roles for a user by user ID.
     * Alternative lookup method for user ID-based queries.
     * Includes both direct user-role assignments and virtual group-role assignments.
     * 
     * @param userId the user ID to query roles for
     * @return list of roles the user has
     */
    @Query(value = "SELECT DISTINCT r.id, r.code, r.name, r.description " +
           "FROM sys_roles r " +
           "WHERE r.id IN ( " +
           "  SELECT ur.role_id " +
           "  FROM sys_user_roles ur " +
           "  WHERE ur.user_id = :userId " +
           "  UNION " +
           "  SELECT vgr.role_id " +
           "  FROM sys_virtual_group_roles vgr " +
           "  JOIN sys_virtual_group_members vgm ON vgr.virtual_group_id = vgm.group_id " +
           "  WHERE vgm.user_id = :userId " +
           ") AND r.status = 'ACTIVE'",
           nativeQuery = true)
    List<Object[]> findRolesByUserId(@Param("userId") String userId);
    
    /**
     * Check if a user has a specific role by user ID.
     * Alternative lookup method for user ID-based queries.
     * Checks both direct user-role assignments and virtual group-role assignments.
     * 
     * @param userId the user ID to check
     * @param role the role code to check for
     * @return true if user has the role, false otherwise
     */
    @Query(value = "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END " +
           "FROM ( " +
           "  SELECT r.id " +
           "  FROM sys_roles r " +
           "  JOIN sys_user_roles ur ON r.id = ur.role_id " +
           "  WHERE ur.user_id = :userId AND r.code = :role AND r.status = 'ACTIVE' " +
           "  UNION " +
           "  SELECT r.id " +
           "  FROM sys_roles r " +
           "  JOIN sys_virtual_group_roles vgr ON r.id = vgr.role_id " +
           "  JOIN sys_virtual_group_members vgm ON vgr.virtual_group_id = vgm.group_id " +
           "  WHERE vgm.user_id = :userId AND r.code = :role AND r.status = 'ACTIVE' " +
           ") AS combined_roles",
           nativeQuery = true)
    boolean hasRoleByUserId(@Param("userId") String userId, @Param("role") String role);
}