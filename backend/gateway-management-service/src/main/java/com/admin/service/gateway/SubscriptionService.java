package com.admin.service.gateway;

import com.admin.entity.gateway.*;
import com.admin.repository.gateway.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SubscriptionService {

    private final SubscriptionRequestRepository subscriptionRequestRepository;
    private final ApiSubscriptionRepository apiSubscriptionRepository;
    private final ApplicationRepository applicationRepository;
    private final EnvironmentRepository environmentRepository;
    private final CredentialRepository credentialRepository;
    private final AccessPolicyRepository accessPolicyRepository;

    /**
     * Create a subscription request.
     */
    public SubscriptionRequest createRequest(String tenantId, Long applicationId, Long environmentId,
                                              List<Long> apiVersionIds, String justification, String requesterId) {
        applicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        environmentRepository.findByIdAndTenantId(environmentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + environmentId));

        SubscriptionRequest request = SubscriptionRequest.builder()
                .tenantId(tenantId)
                .applicationId(applicationId)
                .environmentId(environmentId)
                .apiVersionIds(apiVersionIds)
                .justification(justification)
                .requesterId(requesterId)
                .status("PENDING")
                .build();

        return subscriptionRequestRepository.save(request);
    }

    /**
     * List subscription requests (approval queue or all).
     */
    public Page<SubscriptionRequest> listRequests(String tenantId, String status, Pageable pageable) {
        if (status != null && !status.isEmpty()) {
            return subscriptionRequestRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status, pageable);
        }
        return subscriptionRequestRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "PENDING", pageable);
    }

    /**
     * List my subscription requests.
     */
    public Page<SubscriptionRequest> listMyRequests(String tenantId, String requesterId, Pageable pageable) {
        return subscriptionRequestRepository.findByTenantIdAndRequesterIdOrderByCreatedAtDesc(tenantId, requesterId, pageable);
    }

    /**
     * Get subscription request detail.
     */
    public SubscriptionRequest getRequest(Long requestId) {
        return subscriptionRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription request not found: " + requestId));
    }

    /**
     * Approve or reject a subscription request.
     * On approve: auto-provision access policy + credential for each API version.
     */
    public SubscriptionRequest decide(Long requestId, boolean approved, String comment, String decidedBy) {
        SubscriptionRequest request = getRequest(requestId);

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Request is not in PENDING state: " + request.getStatus());
        }

        request.setStatus(approved ? "APPROVED" : "REJECTED");
        request.setDecidedBy(decidedBy);
        request.setDecidedAt(Instant.now());
        request.setDecisionComment(comment);
        request.setUpdatedAt(Instant.now());

        SubscriptionRequest saved = subscriptionRequestRepository.save(request);

        if (approved) {
            autoProvision(request);
        }

        return saved;
    }

    /**
     * Auto-provision: create access policy + credential for each API version.
     */
    private void autoProvision(SubscriptionRequest request) {
        for (Long apiVersionId : request.getApiVersionIds()) {
            Optional<ApiSubscription> existing = apiSubscriptionRepository
                    .findByTenantIdAndApplicationIdAndApiVersionIdAndEnvironmentId(
                            request.getTenantId(), request.getApplicationId(), apiVersionId, request.getEnvironmentId());

            if (existing.isPresent() && "ACTIVE".equals(existing.get().getStatus())) {
                log.info("Subscription already active for app={}, apiVersion={}, env={}",
                        request.getApplicationId(), apiVersionId, request.getEnvironmentId());
                continue;
            }

            // Create credential
            String apiKey = UUID.randomUUID().toString().replace("-", "");
            Credential credential = Credential.builder()
                    .tenantId(request.getTenantId())
                    .applicationId(request.getApplicationId())
                    .credentialType("API_KEY")
                    .displayName("Auto-provisioned: app-" + request.getApplicationId() + "-v" + apiVersionId)
                    .secretRef("auto/" + apiKey)
                    .status("ACTIVE")
                    .build();
            Credential savedCredential = credentialRepository.save(credential);

            // Create access policy
            Map<String, Object> policyConfig = new LinkedHashMap<>();
            policyConfig.put("credentialId", savedCredential.getId());
            policyConfig.put("source", "SUBSCRIPTION");

            AccessPolicy policy = AccessPolicy.builder()
                    .tenantId(request.getTenantId())
                    .apiVersionId(apiVersionId)
                    .applicationId(request.getApplicationId())
                    .policyType("SUBSCRIBED")
                    .enabled(true)
                    .policyJson(policyConfig)
                    .status("ACTIVE")
                    .build();
            accessPolicyRepository.save(policy);

            // Create subscription record
            ApiSubscription subscription = ApiSubscription.builder()
                    .tenantId(request.getTenantId())
                    .applicationId(request.getApplicationId())
                    .apiVersionId(apiVersionId)
                    .environmentId(request.getEnvironmentId())
                    .status("ACTIVE")
                    .credentialId(savedCredential.getId())
                    .grantedBy(request.getDecidedBy())
                    .grantedAt(Instant.now())
                    .build();
            apiSubscriptionRepository.save(subscription);

            log.info("Auto-provisioned subscription for app={}, apiVersion={}, env={}",
                    request.getApplicationId(), apiVersionId, request.getEnvironmentId());
        }
    }

    /**
     * List active subscriptions for an application.
     */
    public List<ApiSubscription> listApplicationSubscriptions(String tenantId, Long applicationId) {
        return apiSubscriptionRepository.findByTenantIdAndApplicationIdAndStatus(tenantId, applicationId, "ACTIVE");
    }

    /**
     * Revoke a subscription.
     */
    public ApiSubscription revoke(Long subscriptionId) {
        ApiSubscription subscription = apiSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + subscriptionId));

        subscription.setStatus("REVOKED");
        subscription.setRevokedAt(Instant.now());
        return apiSubscriptionRepository.save(subscription);
    }
}
