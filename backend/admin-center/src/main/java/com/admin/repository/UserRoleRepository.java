package com.admin.repository;

import com.admin.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户角色关联仓库接口
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, String> {
    
    /**
     * 根据用户ID查找
     */
    List<UserRole> findByUserId(String userId);
    
    /**
     * 根据角色ID查找
     */
    List<UserRole> findByRoleId(String roleId);
    
    /**
     * 分页根据角色ID查找
     */
    @Query("SELECT ur FROM UserRole ur WHERE ur.role.id = :roleId")
    Page<UserRole> findByRoleIdPaged(@Param("roleId") String roleId, Pageable pageable);
    
    /**
     * 根据用户ID和角色ID查找
     */
    Optional<UserRole> findByUserIdAndRoleId(String userId, String roleId);
    
    /**
     * 删除用户的所有角色
     */
    void deleteByUserId(String userId);
    
    /**
     * 删除角色的所有用户关联
     */
    void deleteByRoleId(String roleId);
    
    /**
     * 检查用户是否有指定角色
     */
    boolean existsByUserIdAndRoleId(String userId, String roleId);
    
    /**
     * 统计角色的用户数量
     */
    long countByRoleId(String roleId);
}
