package com.admin.repository;

import com.admin.entity.Approver;
import com.admin.enums.ApproverTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 审批人配置仓库接口
 */
@Repository
public interface ApproverRepository extends JpaRepository<Approver, String> {
    
    /**
     * 根据目标类型和目标ID查找所有审批人
     */
    List<Approver> findByTargetTypeAndTargetId(ApproverTargetType targetType, String targetId);
    
    /**
     * 根据用户ID查找所有审批人配置
     */
    List<Approver> findByUserId(String userId);
    
    /**
     * 根据目标类型、目标ID和用户ID查找审批人
     */
    Optional<Approver> findByTargetTypeAndTargetIdAndUserId(ApproverTargetType targetType, String targetId, String userId);
    
    /**
     * 检查用户是否是指定目标的审批人
     */
    boolean existsByTargetTypeAndTargetIdAndUserId(ApproverTargetType targetType, String targetId, String userId);
    
    /**
     * 检查用户是否是任何目标的审批人
     */
    boolean existsByUserId(String userId);
    
    /**
     * 检查目标是否有审批人
     */
    boolean existsByTargetTypeAndTargetId(ApproverTargetType targetType, String targetId);
    
    /**
     * 删除目标的所有审批人
     */
    void deleteByTargetTypeAndTargetId(ApproverTargetType targetType, String targetId);
    
    /**
     * 删除用户的所有审批人配置
     */
    void deleteByUserId(String userId);
    
    /**
     * 根据目标类型和目标ID查找所有审批人（包含用户信息）
     */
    @Query("SELECT a FROM Approver a LEFT JOIN FETCH a.user WHERE a.targetType = :targetType AND a.targetId = :targetId")
    List<Approver> findByTargetTypeAndTargetIdWithUser(@Param("targetType") ApproverTargetType targetType, 
                                                        @Param("targetId") String targetId);
    
    /**
     * 根据用户ID查找所有审批人配置（按目标类型分组）
     */
    @Query("SELECT a FROM Approver a WHERE a.userId = :userId ORDER BY a.targetType, a.targetId")
    List<Approver> findByUserIdOrderByTarget(@Param("userId") String userId);
    
    /**
     * 统计目标的审批人数量
     */
    long countByTargetTypeAndTargetId(ApproverTargetType targetType, String targetId);
    
    /**
     * 查找用户作为审批人的所有虚拟组ID
     */
    @Query("SELECT a.targetId FROM Approver a WHERE a.userId = :userId AND a.targetType = 'VIRTUAL_GROUP'")
    List<String> findVirtualGroupIdsByApproverUserId(@Param("userId") String userId);
    
    /**
     * 查找用户作为审批人的所有业务单元ID
     */
    @Query("SELECT a.targetId FROM Approver a WHERE a.userId = :userId AND a.targetType = 'BUSINESS_UNIT'")
    List<String> findBusinessUnitIdsByApproverUserId(@Param("userId") String userId);
    
    /**
     * 查找指定目标类型的所有有审批人的目标ID
     */
    @Query("SELECT DISTINCT a.targetId FROM Approver a WHERE a.targetType = :targetType")
    List<String> findDistinctTargetIdsByTargetType(@Param("targetType") ApproverTargetType targetType);
}
