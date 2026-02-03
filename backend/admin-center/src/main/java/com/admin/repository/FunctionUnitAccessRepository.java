package com.admin.repository;

import com.admin.entity.FunctionUnitAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 功能单元访问权限 Repository
 */
@Repository
public interface FunctionUnitAccessRepository extends JpaRepository<FunctionUnitAccess, String> {
    
    /**
     * 根据功能单元ID查询所有访问配置
     */
    List<FunctionUnitAccess> findByFunctionUnitId(String functionUnitId);
    
    /**
     * 检查是否存在特定的访问配置（角色）
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM FunctionUnitAccess a " +
           "WHERE a.functionUnit.id = :functionUnitId AND a.targetType = 'ROLE' AND a.targetId = :roleId")
    boolean existsByFunctionUnitIdAndRoleId(@Param("functionUnitId") String functionUnitId, @Param("roleId") String roleId);
    
    /**
     * 查找特定的访问配置（角色）
     */
    @Query("SELECT a FROM FunctionUnitAccess a " +
           "WHERE a.functionUnit.id = :functionUnitId AND a.targetType = 'ROLE' AND a.targetId = :roleId")
    Optional<FunctionUnitAccess> findByFunctionUnitIdAndRoleId(@Param("functionUnitId") String functionUnitId, @Param("roleId") String roleId);
    
    /**
     * 删除功能单元的所有访问配置
     */
    void deleteByFunctionUnitId(String functionUnitId);
    
    /**
     * 删除指定角色的所有访问配置
     */
    @Query("DELETE FROM FunctionUnitAccess a WHERE a.targetType = 'ROLE' AND a.targetId = :roleId")
    void deleteByRoleId(@Param("roleId") String roleId);
    
    /**
     * 查询用户可访问的功能单元ID列表（通过角色）
     */
    @Query("SELECT DISTINCT a.functionUnit.id FROM FunctionUnitAccess a " +
           "WHERE a.targetType = 'ROLE' AND a.targetId IN :roleIds")
    List<String> findAccessibleFunctionUnitIdsByRoles(@Param("roleIds") List<String> roleIds);
    
    /**
     * 查询分配了指定角色的功能单元ID列表
     */
    @Query("SELECT a.functionUnit.id FROM FunctionUnitAccess a " +
           "WHERE a.targetType = 'ROLE' AND a.targetId = :roleId")
    List<String> findFunctionUnitIdsByRoleId(@Param("roleId") String roleId);
    
    /**
     * 查询没有配置访问权限的功能单元ID列表
     */
    @Query("SELECT f.id FROM FunctionUnit f WHERE f.id NOT IN (SELECT DISTINCT a.functionUnit.id FROM FunctionUnitAccess a)")
    List<String> findFunctionUnitIdsWithoutAccess();
}
