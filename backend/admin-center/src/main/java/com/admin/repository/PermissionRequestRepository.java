package com.admin.repository;

import com.admin.entity.PermissionRequest;
import com.admin.enums.PermissionRequestStatus;
import com.admin.enums.PermissionRequestType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 权限申请仓库接口
 */
@Repository
public interface PermissionRequestRepository extends JpaRepository<PermissionRequest, String> {
    
    /**
     * 根据申请人ID查找所有申请
     */
    List<PermissionRequest> findByApplicantId(String applicantId);
    
    /**
     * 根据申请人ID分页查找申请
     */
    Page<PermissionRequest> findByApplicantId(String applicantId, Pageable pageable);
    
    /**
     * 根据状态查找申请
     */
    List<PermissionRequest> findByStatus(PermissionRequestStatus status);
    
    /**
     * 根据申请类型查找申请
     */
    List<PermissionRequest> findByRequestType(PermissionRequestType requestType);
    
    /**
     * 根据目标ID查找申请
     */
    List<PermissionRequest> findByTargetId(String targetId);
    
    /**
     * 检查是否存在待审批的申请（虚拟组）
     */
    boolean existsByApplicantIdAndTargetIdAndRequestTypeAndStatus(
            String applicantId, String targetId, PermissionRequestType requestType, PermissionRequestStatus status);
    
    /**
     * 查找待审批的申请（根据目标ID列表）
     */
    @Query("SELECT pr FROM PermissionRequest pr WHERE pr.status = 'PENDING' AND pr.targetId IN :targetIds")
    List<PermissionRequest> findPendingByTargetIds(@Param("targetIds") List<String> targetIds);
    
    /**
     * 查找待审批的申请（根据目标类型和目标ID列表）
     */
    @Query("SELECT pr FROM PermissionRequest pr WHERE pr.status = 'PENDING' " +
           "AND pr.requestType = :requestType AND pr.targetId IN :targetIds")
    List<PermissionRequest> findPendingByRequestTypeAndTargetIds(
            @Param("requestType") PermissionRequestType requestType, 
            @Param("targetIds") List<String> targetIds);
    
    /**
     * 查找待审批的申请（排除申请人自己的申请）
     */
    @Query("SELECT pr FROM PermissionRequest pr WHERE pr.status = 'PENDING' " +
           "AND pr.targetId IN :targetIds AND pr.applicantId != :excludeApplicantId")
    List<PermissionRequest> findPendingByTargetIdsExcludingApplicant(
            @Param("targetIds") List<String> targetIds, 
            @Param("excludeApplicantId") String excludeApplicantId);
    
    /**
     * 根据申请人ID查找申请（包含申请人信息）
     */
    @Query("SELECT pr FROM PermissionRequest pr LEFT JOIN FETCH pr.applicant WHERE pr.applicantId = :applicantId ORDER BY pr.createdAt DESC")
    List<PermissionRequest> findByApplicantIdWithApplicant(@Param("applicantId") String applicantId);
    
    /**
     * 根据ID查找申请（包含申请人和审批人信息）
     */
    @Query("SELECT pr FROM PermissionRequest pr " +
           "LEFT JOIN FETCH pr.applicant " +
           "LEFT JOIN FETCH pr.approver " +
           "WHERE pr.id = :id")
    Optional<PermissionRequest> findByIdWithDetails(@Param("id") String id);
    
    /**
     * 分页查询所有申请（带筛选条件）
     */
    @Query("SELECT pr FROM PermissionRequest pr " +
           "LEFT JOIN FETCH pr.applicant " +
           "WHERE (:status IS NULL OR pr.status = :status) " +
           "AND (:requestType IS NULL OR pr.requestType = :requestType) " +
           "AND (:applicantId IS NULL OR pr.applicantId = :applicantId) " +
           "AND (:startDate IS NULL OR pr.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR pr.createdAt <= :endDate)")
    Page<PermissionRequest> findByConditions(
            @Param("status") PermissionRequestStatus status,
            @Param("requestType") PermissionRequestType requestType,
            @Param("applicantId") String applicantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);
    
    /**
     * 统计待审批申请数量
     */
    long countByStatus(PermissionRequestStatus status);
    
    /**
     * 统计用户的待审批申请数量
     */
    long countByApplicantIdAndStatus(String applicantId, PermissionRequestStatus status);
}
