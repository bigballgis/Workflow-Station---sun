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
    @Query("SELECT ur FROM UserRole ur WHERE ur.user.id = :userId")
    List<UserRole> findByUserId(@Param("userId") String userId);
    
    /**
     * 根据角色ID查找
     */
    @Query("SELECT ur FROM UserRole ur WHERE ur.role.id = :roleId")
    List<UserRole> findByRoleId(@Param("roleId") String roleId);
    
    /**
     * 分页根据角色ID查找
     */
    @Query("SELECT ur FROM UserRole ur WHERE ur.role.id = :roleId")
    Page<UserRole> findByRoleIdPaged(@Param("roleId") String roleId, Pageable pageable);
    
    /**
     * 根据用户ID和角色ID查找
     */
    @Query("SELECT ur FROM UserRole ur WHERE ur.user.id = :userId AND ur.role.id = :roleId")
    Optional<UserRole> findByUserIdAndRoleId(@Param("userId") String userId, @Param("roleId") String roleId);
    
    /**
     * 删除用户的所有角色
     */
    @Query("DELETE FROM UserRole ur WHERE ur.user.id = :userId")
    @org.springframework.data.jpa.repository.Modifying
    void deleteByUserId(@Param("userId") String userId);
    
    /**
     * 删除角色的所有用户关联
     */
    @Query("DELETE FROM UserRole ur WHERE ur.role.id = :roleId")
    @org.springframework.data.jpa.repository.Modifying
    void deleteByRoleId(@Param("roleId") String roleId);
    
    /**
     * 检查用户是否有指定角色
     */
    @Query("SELECT COUNT(ur) > 0 FROM UserRole ur WHERE ur.user.id = :userId AND ur.role.id = :roleId")
    boolean existsByUserIdAndRoleId(@Param("userId") String userId, @Param("roleId") String roleId);
    
    /**
     * 统计角色的用户数量
     */
    @Query("SELECT COUNT(ur) FROM UserRole ur WHERE ur.role.id = :roleId")
    long countByRoleId(@Param("roleId") String roleId);
    
    /**
     * 获取用户的所有角色ID
     */
    @Query("SELECT ur.role.id FROM UserRole ur WHERE ur.user.id = :userId")
    List<String> findRoleIdsByUserId(@Param("userId") String userId);
}
