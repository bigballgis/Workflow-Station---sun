package com.admin.repository.gateway;

import com.admin.entity.gateway.ApiDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApiDefinitionRepository extends JpaRepository<ApiDefinition, Long> {

    Page<ApiDefinition> findByTenantId(String tenantId, Pageable pageable);
    Page<ApiDefinition> findByTenantIdAndStatus(String tenantId, String status, Pageable pageable);

    Optional<ApiDefinition> findByTenantIdAndApiCode(String tenantId, String apiCode);
    Optional<ApiDefinition> findByIdAndTenantId(Long id, String tenantId);
    Page<ApiDefinition> findByTenantIdAndStatusAndDomain(String tenantId, String status, String domain, Pageable pageable);
}
