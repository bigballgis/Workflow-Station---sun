package com.portal.repository;

import com.portal.entity.PermissionRequest;
import com.portal.enums.PermissionRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
