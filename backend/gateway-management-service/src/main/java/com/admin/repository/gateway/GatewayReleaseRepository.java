package com.admin.repository.gateway;

import com.admin.entity.gateway.GatewayRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GatewayReleaseRepository extends JpaRepository<GatewayRelease, Long> {

    org.springframework.data.domain.Page<GatewayRelease> findByTenantIdAndState(String tenantId, String state, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<GatewayRelease> findByTenantIdAndEnvironmentIdAndState(String tenantId, Long environmentId, String state, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<GatewayRelease> findByTenantIdAndEnvironmentId(String tenantId, Long environmentId, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<GatewayRelease> findByTenantId(String tenantId, org.springframework.data.domain.Pageable pageable);

    Optional<GatewayRelease> findByIdAndTenantId(Long id, String tenantId);
}
