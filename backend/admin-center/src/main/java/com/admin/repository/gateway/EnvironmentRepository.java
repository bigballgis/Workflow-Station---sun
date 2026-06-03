package com.admin.repository.gateway;

import com.admin.entity.gateway.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, Long> {

    Page<Environment> findByTenantIdAndEnabled(String tenantId, Boolean enabled, Pageable pageable);

    Optional<Environment> findByTenantIdAndEnvCode(String tenantId, String envCode);
    Optional<Environment> findByIdAndTenantId(Long id, String tenantId);
    java.util.List<Environment> findByEnabledTrue();
}
