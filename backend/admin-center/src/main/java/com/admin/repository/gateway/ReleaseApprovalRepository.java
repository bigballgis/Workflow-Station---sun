package com.admin.repository.gateway;

import com.admin.entity.gateway.ReleaseApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReleaseApprovalRepository extends JpaRepository<ReleaseApproval, Long> {

    Optional<ReleaseApproval> findByReleaseIdAndTenantId(Long releaseId, String tenantId);

    Optional<ReleaseApproval> findByReleaseIdAndTenantIdAndStatus(Long releaseId, String tenantId, String status);
}
