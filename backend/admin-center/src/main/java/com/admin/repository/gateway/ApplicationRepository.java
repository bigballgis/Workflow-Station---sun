package com.admin.repository.gateway;

import com.admin.entity.gateway.Application;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    Page<Application> findByTenantId(String tenantId, Pageable pageable);
    Page<Application> findByTenantIdAndStatus(String tenantId, String status, Pageable pageable);

    Optional<Application> findByTenantIdAndAppCode(String tenantId, String appCode);
    Optional<Application> findByIdAndTenantId(Long id, String tenantId);
}
