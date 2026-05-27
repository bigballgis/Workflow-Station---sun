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
 * Permission request Repository
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
     * Pending approval: business unit requests where businessUnitId is in the current user's approvable BU list (paginated at database level).
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
     * Pending approval: virtual group join requests where virtualGroupId is in the current user's approvable VG list.
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
     * Pending approval: business unit (join / role removal / exit) or virtual group join, OR combined query (for approvers managing both BU and VG).
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
     * Approval history: only records processed by this user as approver (including all types such as BU and VG).
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
