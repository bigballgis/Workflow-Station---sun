package com.admin.service.gateway;

import com.admin.entity.gateway.ApiVersion;
import com.admin.entity.gateway.AccessPolicy;
import com.admin.entity.gateway.TrafficPolicy;
import com.admin.entity.gateway.Environment;
import com.admin.entity.gateway.GatewayRelease;
import com.admin.entity.gateway.PublishHistory;
import com.admin.entity.gateway.ReleaseApproval;
import com.admin.repository.gateway.ApiVersionRepository;
import com.admin.repository.gateway.AccessPolicyRepository;
import com.admin.repository.gateway.TrafficPolicyRepository;
import com.admin.repository.gateway.EnvironmentRepository;
import com.admin.repository.gateway.GatewayReleaseRepository;
import com.admin.repository.gateway.PublishHistoryRepository;
import com.admin.repository.gateway.ReleaseApprovalRepository;
import com.admin.repository.gateway.ProviderRevisionRepository;
import com.admin.adapter.gateway.spi.GatewayProvider;
import com.admin.adapter.gateway.spi.GatewayProviderFactory;
import com.admin.adapter.gateway.dto.PublishResult;
import com.admin.adapter.gateway.dto.ReleaseSnapshot;
import com.admin.adapter.gateway.dto.RollbackResult;
import com.admin.entity.gateway.ProviderRevision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseService {

    private final GatewayReleaseRepository releaseRepo;
    private final PublishHistoryRepository historyRepo;
    private final ReleaseApprovalRepository approvalRepo;
    private final ApiVersionRepository apiVersionRepo;
    private final AccessPolicyRepository accessPolicyRepo;
    private final TrafficPolicyRepository trafficPolicyRepo;
    private final EnvironmentRepository envRepo;
    private final GatewayProviderFactory providerFactory;
    private final ProviderRevisionRepository providerRevisionRepo;

    @Transactional(readOnly = true)
    public Page<GatewayRelease> listReleases(String tenantId, Pageable pageable) {
        return releaseRepo.findByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<GatewayRelease> getRelease(String tenantId, Long releaseId) {
        return releaseRepo.findByIdAndTenantId(releaseId, tenantId);
    }

    @Transactional
    public GatewayRelease createRelease(String tenantId, Long environmentId, String releaseName,
                                         List<Long> apiVersionIds, String description, String operator) {
        Environment env = envRepo.findByIdAndTenantId(environmentId, tenantId)
                .orElseThrow(() -> new RuntimeException("Environment not found: " + environmentId));

        // Build snapshot from SoT metadata
        List<Map<String, Object>> versionSnapshots = new ArrayList<>();
        for (Long vid : apiVersionIds) {
            ApiVersion v = apiVersionRepo.findByIdAndTenantId(vid, tenantId)
                    .orElseThrow(() -> new RuntimeException("API version not found: " + vid));
            Map<String, Object> snap = new HashMap<>();
            snap.put("id", v.getId());
            snap.put("version", v.getVersion());
            snap.put("apiDefinitionId", v.getApiDefinitionId());
            snap.put("upstreamRef", v.getUpstreamRef());
            versionSnapshots.add(snap);
        }

        // Gather policies for these versions
        List<Map<String, Object>> accessPolicies = new ArrayList<>();
        List<Map<String, Object>> trafficPolicies = new ArrayList<>();
        for (Long vid : apiVersionIds) {
            accessPolicyRepo.findByTenantIdAndApiVersionId(tenantId, vid)
                    .forEach(p -> {
                        Map<String, Object> m = new HashMap<>(p.getPolicyJson());
                        m.put("type", p.getPolicyType());
                        m.put("apiVersionId", p.getApiVersionId());
                        accessPolicies.add(m);
                    });
            trafficPolicyRepo.findByTenantIdAndApiVersionId(tenantId, vid)
                    .forEach(p -> {
                        Map<String, Object> m = new HashMap<>(p.getPolicyJson());
                        m.put("type", p.getPolicyType());
                        m.put("apiVersionId", p.getApiVersionId());
                        trafficPolicies.add(m);
                    });
        }

        String releaseNo = env.getEnvCode() + "-" + Instant.now().toString().replaceAll("[-:T.Z]", "").substring(0, 12);

        Map<String, Object> snapshotJson = new HashMap<>();
        snapshotJson.put("apiVersions", versionSnapshots);
        snapshotJson.put("accessPolicies", accessPolicies);
        snapshotJson.put("trafficPolicies", trafficPolicies);

        String snapshotHash = computeHash(snapshotJson.toString());

        GatewayRelease release = GatewayRelease.builder()
                .tenantId(tenantId)
                .environmentId(environmentId)
                .releaseNo(releaseNo)
                .state("DRAFT")
                .snapshotJson(snapshotJson)
                .snapshotHash(snapshotHash)
                .description(description)
                .createdBy(operator)
                .build();

        return releaseRepo.save(release);
    }

    @Transactional
    public GatewayRelease submitTesting(String tenantId, Long releaseId, String operator) {
        GatewayRelease release = releaseRepo.findByIdAndTenantId(releaseId, tenantId)
                .orElseThrow(() -> new RuntimeException("Release not found: " + releaseId));
        if (!"DRAFT".equals(release.getState())) {
            throw new IllegalStateException("Invalid state transition: " + release.getState() + " -> TESTING");
        }
        release.setState("TESTING");
        release.setUpdatedBy(operator);
        return releaseRepo.save(release);
    }

    @Transactional
    public GatewayRelease publishRelease(String tenantId, Long releaseId, String operator) {
        GatewayRelease release = releaseRepo.findByIdAndTenantId(releaseId, tenantId)
                .orElseThrow(() -> new RuntimeException("Release not found: " + releaseId));
        if (!"TESTING".equals(release.getState())) {
            throw new IllegalStateException("Invalid state transition: " + release.getState() + " -> PUBLISHED");
        }

        Environment env = envRepo.findByIdAndTenantId(release.getEnvironmentId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Environment not found: " + release.getEnvironmentId()));

        // Phase 2: PROD approval gate
        if (!isApprovedForPublish(tenantId, releaseId, env)) {
            throw new IllegalStateException("GATEWAY_APPROVAL_REQUIRED: Release must be approved before publishing to PROD");
        }

        // Build snapshot DTO
        @SuppressWarnings("unchecked")
        ReleaseSnapshot snapshot = ReleaseSnapshot.builder()
                .releaseId(releaseId)
                .releaseNo(release.getReleaseNo())
                .environmentCode(env.getEnvCode())
                .apiVersions((List<Map<String, Object>>) release.getSnapshotJson().get("apiVersions"))
                .accessPolicies((List<Map<String, Object>>) release.getSnapshotJson().get("accessPolicies"))
                .trafficPolicies((List<Map<String, Object>>) release.getSnapshotJson().get("trafficPolicies"))
                .snapshotHash(release.getSnapshotHash())
                .build();

        // Phase 5: resolve adapter by environment's gateway_provider
        GatewayProvider provider = providerFactory.resolve(env);
        PublishResult result = provider.publishRelease(snapshot, env);

        // Phase 5: record provider runtime revision
        if (result.isSuccess() && result.getRuntimeRevision() != null) {
            providerRevisionRepo.save(ProviderRevision.builder()
                    .tenantId(tenantId)
                    .releaseId(releaseId)
                    .environmentId(env.getId())
                    .gatewayProvider(env.getGatewayProvider())
                    .runtimeRevision(result.getRuntimeRevision())
                    .detailJson(Map.of("operation", "PUBLISH", "releaseNo", release.getReleaseNo()))
                    .build());
        }

        // Record history
        PublishHistory history = PublishHistory.builder()
                .tenantId(tenantId)
                .releaseId(releaseId)
                .operation("PUBLISH")
                .result(result.isSuccess() ? "SUCCESS" : "FAILED")
                .runtimeRevision(result.getRuntimeRevision())
                .operator(operator)
                .build();
        historyRepo.save(history);

        if (result.isSuccess()) {
            release.setState("PUBLISHED");
        }
        release.setUpdatedBy(operator);
        return releaseRepo.save(release);
    }

    @Transactional
    public GatewayRelease rollbackRelease(String tenantId, Long releaseId, Long targetReleaseId,
                                           String reason, String operator) {
        GatewayRelease release = releaseRepo.findByIdAndTenantId(releaseId, tenantId)
                .orElseThrow(() -> new RuntimeException("Release not found: " + releaseId));
        if (!"PUBLISHED".equals(release.getState())) {
            throw new IllegalStateException("Invalid state transition: " + release.getState() + " -> ROLLED_BACK");
        }

        Environment env = envRepo.findByIdAndTenantId(release.getEnvironmentId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Environment not found"));

        // Phase 5: resolve adapter by environment's gateway_provider
        GatewayProvider provider = providerFactory.resolve(env);
        RollbackResult result = provider.rollbackRelease(String.valueOf(targetReleaseId), env);

        // Phase 5: record provider runtime revision
        if (result.isSuccess() && result.getRuntimeRevision() != null) {
            providerRevisionRepo.save(ProviderRevision.builder()
                    .tenantId(tenantId)
                    .releaseId(releaseId)
                    .environmentId(env.getId())
                    .gatewayProvider(env.getGatewayProvider())
                    .runtimeRevision(result.getRuntimeRevision())
                    .detailJson(Map.of("operation", "ROLLBACK", "targetReleaseId", targetReleaseId))
                    .build());
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("reason", reason);
        detail.put("targetReleaseId", targetReleaseId);

        PublishHistory history = PublishHistory.builder()
                .tenantId(tenantId)
                .releaseId(releaseId)
                .operation("ROLLBACK")
                .result(result.isSuccess() ? "SUCCESS" : "FAILED")
                .runtimeRevision(result.getRuntimeRevision())
                .detailJson(detail)
                .operator(operator)
                .build();
        historyRepo.save(history);

        if (result.isSuccess()) {
            release.setState("ROLLED_BACK");
        }
        release.setUpdatedBy(operator);
        return releaseRepo.save(release);
    }

    @Transactional(readOnly = true)
    public Page<PublishHistory> getReleaseHistory(String tenantId, Long releaseId, Pageable pageable) {
        return historyRepo.findByTenantIdAndReleaseIdOrderByCreatedAtDesc(tenantId, releaseId, pageable);
    }

    // ==================== Phase 2: Promotion ====================

    @Transactional
    public GatewayRelease promoteRelease(String tenantId, Long releaseId, String targetEnvCode,
                                          String description, String operator) {
        GatewayRelease source = releaseRepo.findByIdAndTenantId(releaseId, tenantId)
                .orElseThrow(() -> new RuntimeException("Release not found: " + releaseId));
        if (!"PUBLISHED".equals(source.getState())) {
            throw new IllegalStateException("Release must be PUBLISHED to promote, current: " + source.getState());
        }

        Environment targetEnv = envRepo.findByTenantIdAndEnvCode(tenantId, targetEnvCode)
                .orElseThrow(() -> new RuntimeException("Target environment not found: " + targetEnvCode));

        // Create a new release in the target environment with the same snapshot
        String releaseNo = targetEnv.getEnvCode() + "-PROMOTE-" + Instant.now().toString().replaceAll("[-:T.Z]", "").substring(0, 12);

        GatewayRelease promoted = GatewayRelease.builder()
                .tenantId(tenantId)
                .environmentId(targetEnv.getId())
                .releaseNo(releaseNo)
                .state("DRAFT")
                .snapshotJson(new HashMap<>(source.getSnapshotJson()))
                .snapshotHash(source.getSnapshotHash())
                .description(description != null ? description : "Promoted from release " + source.getReleaseNo())
                .sourceReleaseId(source.getId())
                .promotedFromEnvId(source.getEnvironmentId())
                .createdBy(operator)
                .build();

        promoted = releaseRepo.save(promoted);

        // Record promotion in publish history on source release
        Map<String, Object> detail = new HashMap<>();
        detail.put("targetEnvironmentCode", targetEnvCode);
        detail.put("promotedReleaseId", promoted.getId());
        PublishHistory history = PublishHistory.builder()
                .tenantId(tenantId)
                .releaseId(releaseId)
                .operation("PROMOTION")
                .result("SUCCESS")
                .detailJson(detail)
                .operator(operator)
                .build();
        historyRepo.save(history);

        source.setState("PROMOTED");
        source.setUpdatedBy(operator);
        releaseRepo.save(source);

        return promoted;
    }

    // ==================== Phase 2: Prod Approval ====================

    @Transactional
    public ReleaseApproval requestApproval(String tenantId, Long releaseId, String approverRole,
                                            String comment, String operator) {
        // Verify release exists
        releaseRepo.findByIdAndTenantId(releaseId, tenantId)
                .orElseThrow(() -> new RuntimeException("Release not found: " + releaseId));

        ReleaseApproval existing = approvalRepo.findByReleaseIdAndTenantIdAndStatus(releaseId, tenantId, "PENDING")
                .orElse(null);
        if (existing != null) {
            throw new IllegalStateException("Approval already requested for this release");
        }

        ReleaseApproval approval = ReleaseApproval.builder()
                .tenantId(tenantId)
                .releaseId(releaseId)
                .approverRole(approverRole)
                .status("PENDING")
                .comment(comment)
                .build();

        return approvalRepo.save(approval);
    }

    @Transactional
    public ReleaseApproval approve(String tenantId, Long releaseId, boolean approved,
                                    String comment, String approverId) {
        ReleaseApproval approval = approvalRepo.findByReleaseIdAndTenantIdAndStatus(releaseId, tenantId, "PENDING")
                .orElseThrow(() -> new RuntimeException("No pending approval for release: " + releaseId));

        approval.setStatus(approved ? "APPROVED" : "DENIED");
        approval.setApproverId(approverId);
        approval.setComment(comment);
        approval.setDecidedAt(Instant.now());

        return approvalRepo.save(approval);
    }

    /**
     * Check if a release is approved for PROD publish.
     * Returns true if no approval is needed (non-PROD) or if approved.
     */
    @Transactional(readOnly = true)
    public boolean isApprovedForPublish(String tenantId, Long releaseId, Environment env) {
        if (!"PROD".equalsIgnoreCase(env.getEnvCode())) {
            return true; // Only PROD requires approval
        }
        return approvalRepo.findByReleaseIdAndTenantIdAndStatus(releaseId, tenantId, "APPROVED").isPresent();
    }

    private String computeHash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute hash", e);
        }
    }
}