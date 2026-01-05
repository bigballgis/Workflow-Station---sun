package com.admin.repository;

import com.admin.entity.Permission;
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
     * 根据资源查找权限
     */
    List<Permission> findByResource(String resource);
    
    /**
     * 根据资源和操作查找权限
     */
    Optional<Permission> findByResourceAndAction(String resource, String action);
    
    /**
     * 根据父权限ID查找子权限
     */
    List<Permission> findByParentIdOrderBySortOrder(String parentId);
    
    /**
     * 查找根权限
     */
    @Query("SELECT p FROM Permission p WHERE p.parentId IS NULL ORDER BY p.sortOrder")
    List<Permission> findRootPermissions();
    
    /**
     * 查找角色的所有权限
     */
    @Query("SELECT p FROM Permission p JOIN RolePermission rp ON p.id = rp.permission.id " +
           "WHERE rp.role.id = :roleId")
    Set<Permission> findByRoleId(@Param("roleId") String roleId);
    
    /**
     * 查找用户的所有权限（通过角色）
     */
    @Query("SELECT DISTINCT p FROM Permission p " +
           "JOIN RolePermission rp ON p.id = rp.permission.id " +
           "JOIN Role r ON rp.role.id = r.id " +
           "JOIN UserRole ur ON r.id = ur.role.id " +
           "WHERE ur.user.id = :userId AND r.status = 'ACTIVE'")
    Set<Permission> findByUserId(@Param("userId") String userId);
}
