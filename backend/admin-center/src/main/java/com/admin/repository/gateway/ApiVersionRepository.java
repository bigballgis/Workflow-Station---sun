package com.admin.repository.gateway;

import com.admin.entity.gateway.ApiVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApiVersionRepository extends JpaRepository<ApiVersion, Long> {

    Page<ApiVersion> findByTenantIdAndApiDefinitionId(String tenantId, Long apiDefinitionId, Pageable pageable);

    Optional<ApiVersion> findByTenantIdAndApiDefinitionIdAndVersion(String tenantId, Long apiDefinitionId, String version);
    Optional<ApiVersion> findByIdAndTenantId(Long id, String tenantId);
}
