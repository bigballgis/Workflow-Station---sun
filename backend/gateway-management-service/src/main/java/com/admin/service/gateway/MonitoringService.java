package com.admin.service.gateway;

import com.admin.entity.gateway.Environment;
import com.admin.entity.gateway.MetricsSnapshot;
import com.admin.repository.gateway.EnvironmentRepository;
import com.admin.repository.gateway.MetricsSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringService {

    private final MetricsSnapshotRepository metricsRepo;
    private final EnvironmentRepository envRepo;

    @Transactional(readOnly = true)
    public Map<String, Object> getOverview(String tenantId, String environmentCode, String period) {
        Environment env = envRepo.findByTenantIdAndEnvCode(tenantId, environmentCode)
                .orElseThrow(() -> new RuntimeException("Environment not found: " + environmentCode));

        Instant end = Instant.now();
        Instant start = parsePeriodEnd(end, period != null ? period : "1h");

        List<MetricsSnapshot> snapshots = metricsRepo.findByTenantIdAndEnvironmentIdAndPeriodEndBetween(
                tenantId, env.getId(), start, end);

        if (snapshots.isEmpty()) {
            return buildEmptyOverview();
        }

        // Aggregate: average QPS, min/max latency, avg error rate
        BigDecimal totalQps = BigDecimal.ZERO;
        BigDecimal totalP50 = BigDecimal.ZERO;
        BigDecimal totalP95 = BigDecimal.ZERO;
        BigDecimal totalErrorRate = BigDecimal.ZERO;

        for (MetricsSnapshot s : snapshots) {
            if (s.getQps() != null) totalQps = totalQps.add(s.getQps());
            if (s.getP50LatencyMs() != null) totalP50 = totalP50.add(s.getP50LatencyMs());
            if (s.getP95LatencyMs() != null) totalP95 = totalP95.add(s.getP95LatencyMs());
            if (s.getErrorRate() != null) totalErrorRate = totalErrorRate.add(s.getErrorRate());
        }

        int count = snapshots.size();
        Map<String, Object> result = new HashMap<>();
        result.put("qps", totalQps.divide(BigDecimal.valueOf(count), 4, java.math.RoundingMode.HALF_UP));
        result.put("p50LatencyMs", totalP50.divide(BigDecimal.valueOf(count), 4, java.math.RoundingMode.HALF_UP));
        result.put("p95LatencyMs", totalP95.divide(BigDecimal.valueOf(count), 4, java.math.RoundingMode.HALF_UP));
        result.put("errorRate", totalErrorRate.divide(BigDecimal.valueOf(count), 6, java.math.RoundingMode.HALF_UP));
        result.put("environmentCode", environmentCode);
        result.put("period", period != null ? period : "1h");
        result.put("snapshotCount", count);

        return result;
    }

    @Transactional(readOnly = true)
    public Page<MetricsSnapshot> getApiMetrics(String tenantId, Long apiId, String environmentCode,
                                                String period, Pageable pageable) {
        Environment env = envRepo.findByTenantIdAndEnvCode(tenantId, environmentCode)
                .orElseThrow(() -> new RuntimeException("Environment not found: " + environmentCode));

        return metricsRepo.findByTenantIdAndApiDefinitionIdAndEnvironmentId(
                tenantId, apiId, env.getId(), pageable);
    }

    /**
     * Scheduled metrics collection.
     * Runs every 60 seconds by default. In production, this would call Kong Prometheus
     * or parse access logs. Currently stubbed.
     */
    @Scheduled(fixedDelayString = "${gateway.metrics.collect-interval-ms:60000}")
    @Transactional
    public void scheduledMetricsCollection() {
        log.debug("Starting scheduled metrics collection...");
        List<Environment> enabledEnvs = envRepo.findByEnabledTrue();
        Instant now = Instant.now();
        Instant periodEnd = now.truncatedTo(ChronoUnit.MINUTES);
        Instant periodStart = periodEnd.minus(1, ChronoUnit.MINUTES);

        for (Environment env : enabledEnvs) {
            try {
                // TODO: Phase 2 — call Kong Prometheus or parse access logs
                // For now, create stub snapshot
                MetricsSnapshot snapshot = MetricsSnapshot.builder()
                        .tenantId(env.getTenantId())
                        .environmentId(env.getId())
                        .periodStart(periodStart)
                        .periodEnd(periodEnd)
                        .build();
                metricsRepo.save(snapshot);
            } catch (Exception e) {
                log.warn("Metrics collection failed for env '{}': {}", env.getEnvCode(), e.getMessage());
            }
        }
    }

    private Instant parsePeriodEnd(Instant end, String period) {
        return switch (period) {
            case "1h" -> end.minus(1, ChronoUnit.HOURS);
            case "6h" -> end.minus(6, ChronoUnit.HOURS);
            case "24h" -> end.minus(24, ChronoUnit.HOURS);
            case "7d" -> end.minus(7, ChronoUnit.DAYS);
            case "30d" -> end.minus(30, ChronoUnit.DAYS);
            default -> end.minus(1, ChronoUnit.HOURS);
        };
    }

    private Map<String, Object> buildEmptyOverview() {
        Map<String, Object> result = new HashMap<>();
        result.put("qps", BigDecimal.ZERO);
        result.put("p50LatencyMs", BigDecimal.ZERO);
        result.put("p95LatencyMs", BigDecimal.ZERO);
        result.put("errorRate", BigDecimal.ZERO);
        result.put("snapshotCount", 0);
        return result;
    }
}
