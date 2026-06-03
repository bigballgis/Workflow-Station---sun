package com.admin.controller.gateway;

import com.admin.entity.gateway.ApiSubscription;
import com.admin.entity.gateway.SubscriptionRequest;
import com.admin.service.gateway.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/gateway/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * Developer submits a subscription request.
     */
    @PostMapping("/request")
    public ResponseEntity<SubscriptionRequest> createRequest(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> body) {
        Long applicationId = Long.valueOf(body.get("applicationId").toString());
        Long environmentId = Long.valueOf(body.get("environmentId").toString());
        @SuppressWarnings("unchecked")
        List<Integer> apiVersionIdsRaw = (List<Integer>) body.get("apiVersionIds");
        List<Long> apiVersionIds = apiVersionIdsRaw.stream().map(Long::valueOf).toList();
        String justification = (String) body.getOrDefault("justification", "");

        SubscriptionRequest request = subscriptionService.createRequest(
                tenantId, applicationId, environmentId, apiVersionIds, justification, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    /**
     * List my subscription requests.
     */
    @GetMapping("/requests")
    public ResponseEntity<Page<SubscriptionRequest>> listMyRequests(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                subscriptionService.listMyRequests(tenantId, userId, PageRequest.of(page, size)));
    }

    /**
     * Get subscription request detail.
     */
    @GetMapping("/requests/{requestId}")
    public ResponseEntity<SubscriptionRequest> getRequest(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long requestId) {
        return ResponseEntity.ok(subscriptionService.getRequest(requestId));
    }

    /**
     * Approver lists pending approvals.
     */
    @GetMapping("/approvals")
    public ResponseEntity<Page<SubscriptionRequest>> listApprovals(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                subscriptionService.listRequests(tenantId, status, PageRequest.of(page, size)));
    }

    /**
     * Approve or reject a subscription request.
     */
    @PostMapping("/requests/{requestId}/decide")
    public ResponseEntity<SubscriptionRequest> decide(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long requestId,
            @RequestBody Map<String, Object> body) {
        boolean approved = (Boolean) body.get("approved");
        String comment = (String) body.getOrDefault("comment", "");

        return ResponseEntity.ok(subscriptionService.decide(requestId, approved, comment, userId));
    }

    /**
     * List active subscriptions for an application.
     */
    @GetMapping("/applications/{appId}")
    public ResponseEntity<List<ApiSubscription>> listAppSubscriptions(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long appId) {
        return ResponseEntity.ok(
                subscriptionService.listApplicationSubscriptions(tenantId, appId));
    }

    /**
     * Revoke a subscription.
     */
    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<ApiSubscription> revoke(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long subscriptionId) {
        return ResponseEntity.ok(subscriptionService.revoke(subscriptionId));
    }
}
