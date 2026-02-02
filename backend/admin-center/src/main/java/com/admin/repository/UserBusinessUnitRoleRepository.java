package com.admin.repository;

import com.platform.security.entity.UserBusinessUnitRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户业务单元角色分配仓库接口
 */
@Repository
public interface UserBusinessUnitRoleRepository extends JpaRepository<UserBusinessUnitRole, String> {
    
    /**
     * 根据用户ID查找所有业务单元角色分配
     */
    List<UserBusinessUnitRole> findByUserId(String userId);
    
    /**
     * 根据业务单元ID查找所有用户角色分配
     */
    List<UserBusinessUnitRole> findByBusinessUnitId(String businessUnitId);
    
    /**
     * 根据用户ID和业务单元ID查找所有角色分配
     */
    List<UserBusinessUnitRole> findByUserIdAndBusinessUnitId(String userId, String businessUnitId);
    
    /**
     * 根据用户ID、业务单元ID和角色ID查找分配
     */
    Optional<UserBusinessUnitRole> findByUserIdAndBusinessUnitIdAndRoleId(String userId, String businessUnitId, String roleId);
    
    /**
     * 检查用户在业务单元的角色分配是否存在
     */
    boolean existsByUserIdAndBusinessUnitIdAndRoleId(String userId, String businessUnitId, String roleId);
    
    /**
     * 检查用户是否属于某个业务单元
     */
    boolean existsByUserIdAndBusinessUnitId(String userId, String businessUnitId);
    
    /**
     * 删除用户的所有业务单元角色分配
     */
    void deleteByUserId(String userId);
    
    /**
     * 删除业务单元的所有用户角色分配
     */
    void deleteByBusinessUnitId(String businessUnitId);
    
    /**
     * 删除用户在指定业务单元的所有角色分配
     */
    void deleteByUserIdAndBusinessUnitId(String userId, String businessUnitId);
    
    /**
     * 根据用户ID查找所有业务单元角色分配（包含业务单元和角色信息）
     */
    @Query("SELECT ubur FROM UserBusinessUnitRole ubur " +
           "LEFT JOIN FETCH ubur.businessUnit " +
           "LEFT JOIN FETCH ubur.role " +
           "WHERE ubur.userId = :userId")
    List<UserBusinessUnitRole> findByUserIdWithDetails(@Param("userId") String userId);
    
    /**
     * 根据业务单元ID查找所有用户角色分配（包含用户和角色信息）
     */
    @Query("SELECT ubur FROM UserBusinessUnitRole ubur " +
           "LEFT JOIN FETCH ubur.user " +
           "LEFT JOIN FETCH ubur.role " +
           "WHERE ubur.businessUnitId = :businessUnitId")
    List<UserBusinessUnitRole> findByBusinessUnitIdWithDetails(@Param("businessUnitId") String businessUnitId);
    
    /**
     * 统计用户所属的业务单元数量
     */
    @Query("SELECT COUNT(DISTINCT ubur.businessUnitId) FROM UserBusinessUnitRole ubur WHERE ubur.userId = :userId")
    long countDistinctBusinessUnitsByUserId(@Param("userId") String userId);
    
    /**
     * 统计业务单元的成员数量
     */
    @Query("SELECT COUNT(DISTINCT ubur.userId) FROM UserBusinessUnitRole ubur WHERE ubur.businessUnitId = :businessUnitId")
    long countDistinctUsersByBusinessUnitId(@Param("businessUnitId") String businessUnitId);
    
    /**
     * 根据业务单元ID和角色ID查找所有用户ID
     * 用于任务分配时获取候选人列表
     */
    @Query("SELECT DISTINCT ubur.userId FROM UserBusinessUnitRole ubur WHERE ubur.businessUnitId = :businessUnitId AND ubur.roleId = :roleId")
    List<String> findUserIdsByBusinessUnitIdAndRoleId(@Param("businessUnitId") String businessUnitId, @Param("roleId") String roleId);
    
    /**
     * 根据业务单元ID和角色ID查找所有用户角色分配（包含用户信息）
     */
    @Query("SELECT ubur FROM UserBusinessUnitRole ubur " +
           "LEFT JOIN FETCH ubur.user " +
           "WHERE ubur.businessUnitId = :businessUnitId AND ubur.roleId = :roleId")
    List<UserBusinessUnitRole> findByBusinessUnitIdAndRoleIdWithUser(@Param("businessUnitId") String businessUnitId, @Param("roleId") String roleId);
    
    /**
     * 根据角色ID查找所有用户角色分配
     */
    List<UserBusinessUnitRole> findByRoleId(String roleId);
}
