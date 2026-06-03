package com.admin.repository.gateway;

import com.admin.entity.gateway.Credential;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CredentialRepository extends JpaRepository<Credential, Long> {

    Page<Credential> findByTenantIdAndApplicationId(String tenantId, Long applicationId, Pageable pageable);
    List<Credential> findByTenantIdAndApplicationIdAndStatus(String tenantId, Long applicationId, String status);

    Optional<Credential> findByIdAndTenantId(Long id, String tenantId);
}
