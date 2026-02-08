package com.admin.repository;

import com.platform.security.entity.UserRole;
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
 * Note: UserRole entity uses userId and roleId as String fields, not relationships
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, String> {
    
    /**
     * 根据用户ID查找
     */
    @Query("SELECT ur FROM UserRole ur WHERE ur.userId = :userId")
    List<UserRole> findByUserId(@Param("userId") String userId);
    
    /**
     * 根据角色ID查找
     */
    @Query("SELECT ur FROM UserRole ur WHERE ur.roleId = :roleId")
    List<UserRole> findByRoleId(@Param("roleId") String roleId);
    
    /**
     * 分页根据角色ID查找
     */
    @Query("SELECT ur FROM UserRole ur WHERE ur.roleId = :roleId")
    Page<UserRole> findByRoleIdPaged(@Param("roleId") String roleId, Pageable pageable);
    
    /**
     * 根据用户ID和角色ID查找
     */
    @Query("SELECT ur FROM UserRole ur WHERE ur.userId = :userId AND ur.roleId = :roleId")
    Optional<UserRole> findByUserIdAndRoleId(@Param("userId") String userId, @Param("roleId") String roleId);
    
    /**
     * 删除用户的所有角色
     */
    @Query("DELETE FROM UserRole ur WHERE ur.userId = :userId")
    @org.springframework.data.jpa.repository.Modifying
    void deleteByUserId(@Param("userId") String userId);
    
    /**
     * 删除角色的所有用户关联
     */
    @Query("DELETE FROM UserRole ur WHERE ur.roleId = :roleId")
    @org.springframework.data.jpa.repository.Modifying
    void deleteByRoleId(@Param("roleId") String roleId);
    
    /**
     * 检查用户是否有指定角色
     */
    @Query("SELECT COUNT(ur) > 0 FROM UserRole ur WHERE ur.userId = :userId AND ur.roleId = :roleId")
    boolean existsByUserIdAndRoleId(@Param("userId") String userId, @Param("roleId") String roleId);
    
    /**
     * 统计角色的用户数量
     */
    @Query("SELECT COUNT(ur) FROM UserRole ur WHERE ur.roleId = :roleId")
    long countByRoleId(@Param("roleId") String roleId);
    
    /**
     * 获取用户的所有角色ID
     */
    @Query("SELECT ur.roleId FROM UserRole ur WHERE ur.userId = :userId")
    List<String> findRoleIdsByUserId(@Param("userId") String userId);
    
    /**
     * 获取用户的所有角色ID（包括通过虚拟组分配的）
     * 包括：
     * 1. 直接分配给用户的角色 (sys_user_roles)
     * 2. 通过虚拟组分配的角色 (sys_virtual_group_members + sys_virtual_group_roles)
     */
    @Query("""
        SELECT DISTINCT r.id FROM Role r
        WHERE r.id IN (
            SELECT ur.roleId FROM UserRole ur WHERE ur.userId = :userId
            UNION
            SELECT vgr.roleId FROM VirtualGroupRole vgr
            WHERE vgr.virtualGroupId IN (
                SELECT vgm.groupId FROM VirtualGroupMember vgm WHERE vgm.userId = :userId
            )
        )
        """)
    List<String> findAllRoleIdsByUserId(@Param("userId") String userId);
}
