package com.admin.repository.gateway;

import com.admin.entity.gateway.SubscriptionRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface SubscriptionRequestRepository extends JpaRepository<SubscriptionRequest, Long> {
    Page<SubscriptionRequest> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, String status, Pageable pageable);
    Page<SubscriptionRequest> findByTenantIdAndRequesterIdOrderByCreatedAtDesc(String tenantId, String requesterId, Pageable pageable);
    List<SubscriptionRequest> findByTenantIdAndStatus(String tenantId, String status);
}
