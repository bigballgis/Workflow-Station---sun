package com.admin.repository.gateway;

import com.admin.entity.gateway.TrafficPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrafficPolicyRepository extends JpaRepository<TrafficPolicy, Long> {

    List<TrafficPolicy> findByTenantIdAndApiVersionId(String tenantId, Long apiVersionId);

    Optional<TrafficPolicy> findByIdAndTenantId(Long id, String tenantId);
}
