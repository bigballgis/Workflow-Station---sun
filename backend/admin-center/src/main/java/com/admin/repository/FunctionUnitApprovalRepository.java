package com.admin.repository;

import com.admin.entity.FunctionUnitApproval;
import com.admin.enums.ApprovalStatus;
import com.admin.enums.ApprovalType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 功能单元审批记录仓库接口
 */
@Repository
public interface FunctionUnitApprovalRepository extends JpaRepository<FunctionUnitApproval, String> {
    
    /**
     * 根据部署ID查找审批记录
     */
    List<FunctionUnitApproval> findByDeploymentId(String deploymentId);
    
    /**
     * 根据部署ID和审批类型查找审批记录
     */
    Optional<FunctionUnitApproval> findByDeploymentIdAndApprovalType(
            String deploymentId, ApprovalType approvalType);
    
    /**
     * 根据审批人ID查找待审批记录
     */
    @Query("SELECT a FROM FunctionUnitApproval a WHERE a.approverId = :approverId AND a.status = 'PENDING'")
    List<FunctionUnitApproval> findPendingByApproverId(@Param("approverId") String approverId);
    
    /**
     * 根据部署ID和状态查找审批记录
     */
    List<FunctionUnitApproval> findByDeploymentIdAndStatus(String deploymentId, ApprovalStatus status);
    
    /**
     * 检查部署是否有待审批记录
     */
    boolean existsByDeploymentIdAndStatus(String deploymentId, ApprovalStatus status);
    
    /**
     * 统计部署的审批通过数量
     */
    @Query("SELECT COUNT(a) FROM FunctionUnitApproval a WHERE a.deployment.id = :deploymentId AND a.status = 'APPROVED'")
    long countApprovedByDeploymentId(@Param("deploymentId") String deploymentId);
    
    /**
     * 统计部署的审批总数
     */
    long countByDeploymentId(String deploymentId);
    
    /**
     * 根据部署ID查找审批记录（按审批顺序排序）
     */
    List<FunctionUnitApproval> findByDeploymentIdOrderByApprovalOrder(String deploymentId);
    
    /**
     * 查找指定顺序之前的待审批记录
     */
    @Query("SELECT a FROM FunctionUnitApproval a WHERE " +
           "a.deployment.id = :deploymentId AND " +
           "a.approvalOrder < :order AND " +
           "a.status = 'PENDING'")
    List<FunctionUnitApproval> findPendingApprovalsBefore(
            @Param("deploymentId") String deploymentId,
            @Param("order") int order);
    
    /**
     * 查找待审批记录（按审批人）
     */
    @Query("SELECT a FROM FunctionUnitApproval a WHERE " +
           "a.status = 'PENDING' " +
           "ORDER BY a.createdAt DESC")
    List<FunctionUnitApproval> findPendingApprovalsByApprover(@Param("approverId") String approverId);
}
