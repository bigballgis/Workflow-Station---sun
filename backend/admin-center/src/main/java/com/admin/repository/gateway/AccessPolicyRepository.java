package com.admin.repository.gateway;

import com.admin.entity.gateway.AccessPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccessPolicyRepository extends JpaRepository<AccessPolicy, Long> {

    List<AccessPolicy> findByTenantIdAndApiVersionId(String tenantId, Long apiVersionId);
    List<AccessPolicy> findByTenantIdAndApiVersionIdAndApplicationId(String tenantId, Long apiVersionId, Long applicationId);

    Optional<AccessPolicy> findByIdAndTenantId(Long id, String tenantId);
}
