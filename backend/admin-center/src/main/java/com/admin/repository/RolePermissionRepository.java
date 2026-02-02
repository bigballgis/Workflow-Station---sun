package com.admin.repository;

import com.platform.security.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 角色权限关联仓库接口
 */
@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, String> {
    
    /**
     * 根据角色ID查找
     */
    List<RolePermission> findByRoleId(String roleId);
    
    /**
     * 根据权限ID查找
     */
    List<RolePermission> findByPermissionId(String permissionId);
    
    /**
     * 根据角色ID和权限ID查找
     */
    Optional<RolePermission> findByRoleIdAndPermissionId(String roleId, String permissionId);
    
    /**
     * 删除角色的所有权限
     */
    void deleteByRoleId(String roleId);
    
    /**
     * 检查角色是否有指定权限
     */
    boolean existsByRoleIdAndPermissionId(String roleId, String permissionId);
}
