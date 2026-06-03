package com.admin.repository.gateway;

import com.admin.entity.gateway.MetricsSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MetricsSnapshotRepository extends JpaRepository<MetricsSnapshot, Long> {

    Page<MetricsSnapshot> findByTenantIdAndEnvironmentId(String tenantId, Long environmentId, Pageable pageable);

    List<MetricsSnapshot> findByTenantIdAndEnvironmentIdAndPeriodEndBetween(
            String tenantId, Long environmentId, Instant start, Instant end);

    Page<MetricsSnapshot> findByTenantIdAndApiDefinitionIdAndEnvironmentId(
            String tenantId, Long apiDefinitionId, Long environmentId, Pageable pageable);

    /** Get the most recent snapshot — used for delta QPS calculation */
    Page<MetricsSnapshot> findByTenantIdAndEnvironmentIdOrderByPeriodEndDesc(
            String tenantId, Long environmentId, Pageable pageable);
}