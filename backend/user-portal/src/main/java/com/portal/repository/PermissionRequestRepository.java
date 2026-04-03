package com.portal.repository;

import com.portal.entity.PermissionRequest;
import com.portal.enums.PermissionRequestStatus;
import com.portal.enums.PermissionRequestType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 权限申请Repository
 */
@Repository
public interface PermissionRequestRepository extends JpaRepository<PermissionRequest, Long> {

    List<PermissionRequest> findByApplicantId(String applicantId);

    Page<PermissionRequest> findByApplicantId(String applicantId, Pageable pageable);

    List<PermissionRequest> findByApplicantIdAndStatus(String applicantId, PermissionRequestStatus status);

    List<PermissionRequest> findByStatus(PermissionRequestStatus status);

    Page<PermissionRequest> findByStatus(PermissionRequestStatus status, Pageable pageable);

    Page<PermissionRequest> findByStatusIn(List<PermissionRequestStatus> statuses, Pageable pageable);

    @Query("""
            SELECT p FROM PermissionRequest p
            WHERE (p.applicantId = :userId OR p.submittedByUserId = :userId)
              AND p.requestType <> :excludeType
            ORDER BY p.createdAt DESC
            """)
    Page<PermissionRequest> findPortalVisibleForUser(
            @Param("userId") String userId,
            @Param("excludeType") PermissionRequestType excludeType,
            Pageable pageable);

    @Query("""
            SELECT p FROM PermissionRequest p
            WHERE (p.applicantId = :userId OR p.submittedByUserId = :userId)
              AND p.requestType <> :excludeType
              AND p.status = :status
            ORDER BY p.createdAt DESC
            """)
    Page<PermissionRequest> findPortalVisibleForUserWithStatus(
            @Param("userId") String userId,
            @Param("excludeType") PermissionRequestType excludeType,
            @Param("status") PermissionRequestStatus status,
            Pageable pageable);

    /**
     * 待审批：业务单元类申请，且 businessUnitId 在当前用户可审批的 BU 列表内（分页在数据库层完成）。
     */
    @Query("""
            SELECT p FROM PermissionRequest p
            WHERE p.status = :status
              AND p.requestType IN :buTypes
              AND p.businessUnitId IN :buIds
            ORDER BY p.createdAt DESC
            """)
    Page<PermissionRequest> findPendingForBusinessUnitApprovers(
            @Param("status") PermissionRequestStatus status,
            @Param("buTypes") List<PermissionRequestType> buTypes,
            @Param("buIds") List<String> buIds,
            Pageable pageable);

    /**
     * 待审批：虚拟组加入申请，且 virtualGroupId 在当前用户可审批的虚拟组列表内。
     */
    @Query("""
            SELECT p FROM PermissionRequest p
            WHERE p.status = :status
              AND p.requestType = :vgType
              AND p.virtualGroupId IN :vgIds
            ORDER BY p.createdAt DESC
            """)
    Page<PermissionRequest> findPendingForVirtualGroupJoinApprovers(
            @Param("status") PermissionRequestStatus status,
            @Param("vgType") PermissionRequestType vgType,
            @Param("vgIds") List<String> vgIds,
            Pageable pageable);

    /**
     * 待审批：业务单元类（加入 / 移除角色 / 退出成员）或虚拟组加入，OR 合并查询（审批人同时管 BU 与 VG 时使用）。
     */
    @Query("""
            SELECT p FROM PermissionRequest p
            WHERE p.status = :status
              AND (
                (p.requestType IN :buTypes AND p.businessUnitId IN :buIds)
                OR (p.requestType = :vgType AND p.virtualGroupId IN :vgIds)
              )
            ORDER BY p.createdAt DESC
            """)
    Page<PermissionRequest> findPendingForBuOrVirtualGroupApprovers(
            @Param("status") PermissionRequestStatus status,
            @Param("buTypes") List<PermissionRequestType> buTypes,
            @Param("buIds") List<String> buIds,
            @Param("vgType") PermissionRequestType vgType,
            @Param("vgIds") List<String> vgIds,
            Pageable pageable);

    /**
     * 审批历史：仅本人作为 approver 处理过的记录（含业务单元类与虚拟组类等所有类型）。
     */
    @Query("""
            SELECT p FROM PermissionRequest p
            WHERE p.status IN :statuses
              AND p.approverId = :userId
            ORDER BY p.id DESC
            """)
    Page<PermissionRequest> findProcessedHistoryByApproverId(
            @Param("userId") String userId,
            @Param("statuses") List<PermissionRequestStatus> statuses,
            Pageable pageable);
}
