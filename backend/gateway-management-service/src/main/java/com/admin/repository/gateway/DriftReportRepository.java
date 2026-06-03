package com.admin.repository.gateway;

import com.admin.entity.gateway.DriftReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriftReportRepository extends JpaRepository<DriftReport, Long> {

    Page<DriftReport> findByTenantIdAndEnvironmentId(String tenantId, Long environmentId, Pageable pageable);

    Page<DriftReport> findByTenantId(String tenantId, Pageable pageable);

    Optional<DriftReport> findByIdAndTenantId(Long id, String tenantId);
}
