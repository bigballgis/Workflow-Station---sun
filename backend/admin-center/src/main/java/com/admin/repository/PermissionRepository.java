package com.admin.repository;

import com.platform.security.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 权限仓库接口
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, String> {
    
    /**
     * 根据编码查找权限
     */
    Optional<Permission> findByCode(String code);
    
    /**
     * 检查编码是否存在
     */
    boolean existsByCode(String code);
    
    /**
     * 根据类型查找权限
     */
    List<Permission> findByType(String type);
    
    /**
     * 根据资源查找权限
     */
    List<Permission> findByResource(String resource);
    
    /**
     * 根据资源和操作查找权限
     */
    Optional<Permission> findByResourceAndAction(String resource, String action);
    
    /**
     * 查找角色的所有权限
     * Note: Using native query since platform-security entities don't have JPA relationships
     */
    @Query(value = "SELECT p.* FROM sys_permissions p " +
           "JOIN sys_role_permissions rp ON p.id = rp.permission_id " +
           "WHERE rp.role_id = :roleId",
           nativeQuery = true)
    Set<Permission> findByRoleId(@Param("roleId") String roleId);
    
    /**
     * 查找用户的所有权限（通过角色）
     * Note: Using native query since platform-security entities don't have JPA relationships
     */
    @Query(value = "SELECT DISTINCT p.* FROM sys_permissions p " +
           "JOIN sys_role_permissions rp ON p.id = rp.permission_id " +
           "JOIN sys_roles r ON rp.role_id = r.id " +
           "JOIN sys_user_roles ur ON r.id = ur.role_id " +
           "WHERE ur.user_id = :userId AND r.status = 'ACTIVE'",
           nativeQuery = true)
    Set<Permission> findByUserId(@Param("userId") String userId);
}
