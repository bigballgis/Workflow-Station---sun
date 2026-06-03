package com.admin.service.gateway;

import com.admin.adapter.gateway.spi.GatewayProvider;
import com.admin.adapter.gateway.spi.GatewayProviderFactory;
import com.admin.entity.gateway.DriftReport;
import com.admin.entity.gateway.Environment;
import com.admin.entity.gateway.GatewayRelease;
import com.admin.repository.gateway.DriftReportRepository;
import com.admin.repository.gateway.EnvironmentRepository;
import com.admin.repository.gateway.GatewayReleaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriftDetectorService {

    private final DriftReportRepository driftReportRepo;
    private final EnvironmentRepository envRepo;
    private final GatewayReleaseRepository releaseRepo;
    private final GatewayProviderFactory providerFactory;

    @Transactional(readOnly = true)
    public Page<DriftReport> listReports(String tenantId, Long environmentId, Pageable pageable) {
        if (environmentId != null) {
            return driftReportRepo.findByTenantIdAndEnvironmentId(tenantId, environmentId, pageable);
        }
        return driftReportRepo.findByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<DriftReport> getReport(String tenantId, Long reportId) {
        return driftReportRepo.findByIdAndTenantId(reportId, tenantId);
    }

    @Transactional
    public DriftReport syncEnvironment(String tenantId, String environmentCode) {
        Environment env = envRepo.findByTenantIdAndEnvCode(tenantId, environmentCode)
                .orElseThrow(() -> new RuntimeException("Environment not found: " + environmentCode));

        // Load desired state: latest published release for this environment
        List<GatewayRelease> latestReleases = releaseRepo.findByTenantIdAndEnvironmentIdAndState(
                tenantId, env.getId(), "PUBLISHED",
                org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent();

        Map<String, Object> desiredSnapshot = latestReleases.isEmpty()
                ? Map.of()
                : latestReleases.get(0).getSnapshotJson();

        // Fetch actual runtime state from gateway
        List<Map<String, Object>> runtimeState;
        try {
            runtimeState = providerFactory.resolve(env).fetchRuntimeState(env);
        } catch (Exception e) {
            log.error("Failed to fetch runtime state for env '{}'", environmentCode, e);
            DriftReport failed = DriftReport.builder()
                    .tenantId(tenantId)
                    .environmentId(env.getId())
                    .syncMode("REPORT_ONLY")
                    .status("FAILED")
                    .reportJson(Map.of("error", e.getMessage()))
                    .build();
            return driftReportRepo.save(failed);
        }

        // Compare desired vs actual
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> desiredApis = (List<Map<String, Object>>)
                desiredSnapshot.getOrDefault("apiVersions", List.of());

        DriftCompareResult compareResult = compare(desiredApis, runtimeState);

        Map<String, Object> reportJson = new HashMap<>();
        reportJson.put("missing", compareResult.missing());
        reportJson.put("extra", compareResult.extra());
        reportJson.put("mismatch", compareResult.mismatch());
        reportJson.put("desiredCount", desiredApis.size());
        reportJson.put("actualCount", runtimeState.size());

        DriftReport report = DriftReport.builder()
                .tenantId(tenantId)
                .environmentId(env.getId())
                .syncMode("REPORT_ONLY")
                .status("COMPLETED")
                .missingCount(compareResult.missing().size())
                .extraCount(compareResult.extra().size())
                .mismatchCount(compareResult.mismatch().size())
                .reportJson(reportJson)
                .createdAt(Instant.now())
                .build();

        return driftReportRepo.save(report);
    }

    /**
     * Scheduled drift sync for all enabled environments.
     * Runs every 30 minutes by default.
     */
    @Scheduled(fixedDelayString = "${gateway.drift.sync-interval-ms:1800000}")
    @Transactional
    public void scheduledDriftSync() {
        log.info("Starting scheduled drift sync...");
        List<Environment> enabledEnvs = envRepo.findByEnabledTrue();
        for (Environment env : enabledEnvs) {
            try {
                syncEnvironment(env.getTenantId(), env.getEnvCode());
                log.info("Drift sync completed for env '{}'", env.getEnvCode());
            } catch (Exception e) {
                log.error("Drift sync failed for env '{}': {}", env.getEnvCode(), e.getMessage());
            }
        }
    }

    /**
     * Compare desired APIs (from latest published release) vs runtime APIs (from gateway).
     * Matches by version ID (upstreamRef).
     */
    private DriftCompareResult compare(List<Map<String, Object>> desired, List<Map<String, Object>> actual) {
        List<Map<String, Object>> missing = new ArrayList<>();
        List<Map<String, Object>> extra = new ArrayList<>(actual);
        List<Map<String, Object>> mismatch = new ArrayList<>();

        for (Map<String, Object> d : desired) {
            String desiredUpstream = (String) d.get("upstreamRef");
            Optional<Map<String, Object>> match = extra.stream()
                    .filter(a -> desiredUpstream != null && desiredUpstream.equals(a.get("upstreamRef")))
                    .findFirst();
            if (match.isPresent()) {
                extra.remove(match.get());
                // Check for mismatches in version or other fields
                Object desiredVersion = d.get("version");
                Object actualVersion = match.get().get("version");
                if (desiredVersion != null && !desiredVersion.equals(actualVersion)) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("desired", d);
                    m.put("actual", match.get());
                    mismatch.add(m);
                }
            } else {
                missing.add(d);
            }
        }

        return new DriftCompareResult(missing, extra, mismatch);
    }

    private record DriftCompareResult(
            List<Map<String, Object>> missing,
            List<Map<String, Object>> extra,
            List<Map<String, Object>> mismatch) {}
}
