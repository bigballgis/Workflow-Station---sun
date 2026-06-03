package com.admin.controller.gateway;

import com.admin.entity.gateway.GatewayRelease;
import com.admin.entity.gateway.PublishHistory;
import com.admin.entity.gateway.ReleaseApproval;
import com.admin.service.gateway.ReleaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/gateway/releases")
@RequiredArgsConstructor
@Tag(name = "Gateway Release Management", description = "Manage gateway releases: publish, rollback, promote, approve, and history")
public class ReleaseController {

    private final ReleaseService releaseService;

    @PostMapping
    @Operation(summary = "Create a new release draft")
    public ResponseEntity<GatewayRelease> createRelease(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader(value = "X-Operator", required = false) String operator,
            @RequestBody Map<String, Object> body) {
        Long environmentId = Long.valueOf(body.get("environmentId").toString());
        String releaseName = (String) body.getOrDefault("releaseName", "");
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) body.get("apiVersionIds");
        List<Long> apiVersionIds = rawIds.stream().map(Long::valueOf).toList();
        String description = (String) body.getOrDefault("description", "");
        String op = operator != null ? operator : "system";
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(releaseService.createRelease(tenantId, environmentId, releaseName, apiVersionIds, description, op));
    }

    @GetMapping
    @Operation(summary = "List releases")
    public ResponseEntity<Page<GatewayRelease>> listReleases(
            @RequestHeader("X-Tenant-Id") String tenantId,
            Pageable pageable) {
        return ResponseEntity.ok(releaseService.listReleases(tenantId, pageable));
    }

    @GetMapping("/{releaseId}")
    @Operation(summary = "Get release detail")
    public ResponseEntity<GatewayRelease> getRelease(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long releaseId) {
        return releaseService.getRelease(tenantId, releaseId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{releaseId}/submit-testing")
    @Operation(summary = "Submit release for testing (DRAFT → TESTING)")
    public ResponseEntity<GatewayRelease> submitTesting(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader(value = "X-Operator", required = false) String operator,
            @PathVariable Long releaseId) {
        String op = operator != null ? operator : "system";
        return ResponseEntity.ok(releaseService.submitTesting(tenantId, releaseId, op));
    }

    @PostMapping("/{releaseId}/publish")
    @Operation(summary = "Publish release to gateway (TESTING → PUBLISHED)")
    public ResponseEntity<GatewayRelease> publishRelease(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader(value = "X-Operator", required = false) String operator,
            @PathVariable Long releaseId) {
        String op = operator != null ? operator : "system";
        return ResponseEntity.ok(releaseService.publishRelease(tenantId, releaseId, op));
    }

    @PostMapping("/{releaseId}/rollback")
    @Operation(summary = "Rollback release (PUBLISHED → ROLLED_BACK)")
    public ResponseEntity<GatewayRelease> rollbackRelease(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader(value = "X-Operator", required = false) String operator,
            @PathVariable Long releaseId,
            @RequestBody Map<String, Object> body) {
        Long targetReleaseId = Long.valueOf(body.get("targetReleaseId").toString());
        String reason = (String) body.getOrDefault("reason", "");
        String op = operator != null ? operator : "system";
        return ResponseEntity.ok(releaseService.rollbackRelease(tenantId, releaseId, targetReleaseId, reason, op));
    }

    @GetMapping("/{releaseId}/history")
    @Operation(summary = "Get publish/rollback history for a release")
    public ResponseEntity<Page<PublishHistory>> getReleaseHistory(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long releaseId,
            Pageable pageable) {
        return ResponseEntity.ok(releaseService.getReleaseHistory(tenantId, releaseId, pageable));
    }

    // ==================== Phase 2: Promotion ====================

    @PostMapping("/{releaseId}/promote")
    @Operation(summary = "Promote release to next environment")
    public ResponseEntity<GatewayRelease> promoteRelease(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader(value = "X-Operator", required = false) String operator,
            @PathVariable Long releaseId,
            @RequestBody Map<String, Object> body) {
        String targetEnvironmentCode = (String) body.get("targetEnvironmentCode");
        String description = (String) body.getOrDefault("description", "");
        String op = operator != null ? operator : "system";
        return ResponseEntity.ok(releaseService.promoteRelease(tenantId, releaseId,
                targetEnvironmentCode, description, op));
    }

    // ==================== Phase 2: Prod Approval ====================

    @PostMapping("/{releaseId}/request-approval")
    @Operation(summary = "Request PROD approval for a release")
    public ResponseEntity<ReleaseApproval> requestApproval(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader(value = "X-Operator", required = false) String operator,
            @PathVariable Long releaseId,
            @RequestBody Map<String, Object> body) {
        String approverRole = (String) body.getOrDefault("approverRole", "GATEWAY_ADMIN");
        String comment = (String) body.getOrDefault("comment", "");
        String op = operator != null ? operator : "system";
        return ResponseEntity.ok(releaseService.requestApproval(tenantId, releaseId,
                approverRole, comment, op));
    }

    @PostMapping("/{releaseId}/approve")
    @Operation(summary = "Approve or deny a release")
    public ResponseEntity<ReleaseApproval> approve(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader(value = "X-Operator", required = false) String approverId,
            @PathVariable Long releaseId,
            @RequestBody Map<String, Object> body) {
        boolean approved = Boolean.TRUE.equals(body.get("approved"));
        String comment = (String) body.getOrDefault("comment", "");
        String appId = approverId != null ? approverId : "system";
        return ResponseEntity.ok(releaseService.approve(tenantId, releaseId, approved, comment, appId));
    }
}
