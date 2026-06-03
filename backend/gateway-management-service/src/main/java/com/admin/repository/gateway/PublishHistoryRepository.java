package com.admin.repository.gateway;

import com.admin.entity.gateway.PublishHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PublishHistoryRepository extends JpaRepository<PublishHistory, Long> {

    org.springframework.data.domain.Page<PublishHistory> findByTenantIdAndReleaseIdOrderByCreatedAtDesc(String tenantId, Long releaseId, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<PublishHistory> findByTenantId(String tenantId, org.springframework.data.domain.Pageable pageable);

    Optional<PublishHistory> findByIdAndTenantId(Long id, String tenantId);
}
