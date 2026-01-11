package com.admin.repository;

import com.admin.entity.DeveloperRolePermission;
import com.admin.enums.DeveloperPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/**
 * 开发角色权限 Repository
 */
@Repository
public interface DeveloperRolePermissionRepository extends JpaRepository<DeveloperRolePermission, String> {
    
    /**
     * 根据角色ID查询权限列表
     */
    List<DeveloperRolePermission> findByRoleId(String roleId);
    
    /**
     * 根据角色ID查询权限枚举列表
     */
    @Query("SELECT drp.permission FROM DeveloperRolePermission drp WHERE drp.roleId = :roleId")
    Set<DeveloperPermission> findPermissionsByRoleId(@Param("roleId") String roleId);
    
    /**
     * 根据多个角色ID查询权限枚举列表
     */
    @Query("SELECT DISTINCT drp.permission FROM DeveloperRolePermission drp WHERE drp.roleId IN :roleIds")
    Set<DeveloperPermission> findPermissionsByRoleIds(@Param("roleIds") List<String> roleIds);
    
    /**
     * 检查角色是否有指定权限
     */
    boolean existsByRoleIdAndPermission(String roleId, DeveloperPermission permission);
    
    /**
     * 删除角色的所有权限
     */
    @Modifying
    void deleteByRoleId(String roleId);
    
    /**
     * 删除角色的指定权限
     */
    @Modifying
    void deleteByRoleIdAndPermission(String roleId, DeveloperPermission permission);
}
