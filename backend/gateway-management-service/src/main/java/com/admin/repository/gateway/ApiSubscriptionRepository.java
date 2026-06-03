package com.admin.repository.gateway;

import com.admin.entity.gateway.ApiSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface ApiSubscriptionRepository extends JpaRepository<ApiSubscription, Long> {
    List<ApiSubscription> findByTenantIdAndApplicationId(String tenantId, Long applicationId);
    List<ApiSubscription> findByTenantIdAndApplicationIdAndStatus(String tenantId, Long applicationId, String status);
    Optional<ApiSubscription> findByTenantIdAndApplicationIdAndApiVersionIdAndEnvironmentId(String tenantId, Long applicationId, Long apiVersionId, Long environmentId);
}
