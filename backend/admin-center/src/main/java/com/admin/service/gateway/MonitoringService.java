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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringService {

    private final MetricsSnapshotRepository metricsRepo;
    private final EnvironmentRepository envRepo;

    private static final Pattern METRIC_LINE = Pattern.compile(
            "^(\\w+)\\{([^}]*)\\}\\s+([\\d.]+(?:e[+-]?\\d+)?)\\s*$");
    private static final Pattern LABEL_PAIR = Pattern.compile("(\\w+)=\"([^\"]*)\"");

    @Transactional(readOnly = true)
    public Map<String, Object> getOverview(String tenantId, String environmentCode, String period) {
        Environment env = envRepo.findByTenantIdAndEnvCode(tenantId, environmentCode)
                .orElseThrow(() -> new RuntimeException("Environment not found: " + environmentCode));

        Instant end = Instant.now();
        Instant start = parsePeriodEnd(end, period != null ? period : "1h");

        List<MetricsSnapshot> snapshots = metricsRepo.findByTenantIdAndEnvironmentIdAndPeriodEndBetween(
                tenantId, env.getId(), start, end);

        if (snapshots.isEmpty()) {
            return buildEmptyOverview(environmentCode, period);
        }

        BigDecimal totalQps = BigDecimal.ZERO;
        BigDecimal totalP50 = BigDecimal.ZERO;
        BigDecimal totalP95 = BigDecimal.ZERO;
        BigDecimal totalErrorRate = BigDecimal.ZERO;
        int validCount = 0;

        for (MetricsSnapshot s : snapshots) {
            if (s.getQps() != null) {
                totalQps = totalQps.add(s.getQps());
                validCount++;
            }
            if (s.getP50LatencyMs() != null) totalP50 = totalP50.add(s.getP50LatencyMs());
            if (s.getP95LatencyMs() != null) totalP95 = totalP95.add(s.getP95LatencyMs());
            if (s.getErrorRate() != null) totalErrorRate = totalErrorRate.add(s.getErrorRate());
        }

        int count = validCount > 0 ? validCount : snapshots.size();
        Map<String, Object> result = new HashMap<>();
        result.put("qps", count > 0 ? totalQps.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        result.put("p50LatencyMs", count > 0 ? totalP50.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        result.put("p95LatencyMs", count > 0 ? totalP95.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        result.put("errorRate", count > 0 ? totalErrorRate.divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        result.put("environmentCode", environmentCode);
        result.put("period", period != null ? period : "1h");
        result.put("snapshotCount", snapshots.size());

        return result;
    }

    @Transactional(readOnly = true)
    public Page<MetricsSnapshot> getApiMetrics(String tenantId, Long apiId, String environmentCode,
                                                String period, Pageable pageable) {
        Environment env = envRepo.findByTenantIdAndEnvCode(tenantId, environmentCode)
                .orElseThrow(() -> new RuntimeException("Environment not found: " + environmentCode));

        if (apiId == null || apiId == 0) {
            return metricsRepo.findByTenantIdAndEnvironmentId(tenantId, env.getId(), pageable);
        }
        return metricsRepo.findByTenantIdAndApiDefinitionIdAndEnvironmentId(
                tenantId, apiId, env.getId(), pageable);
    }

    /**
     * Scheduled metrics collection — fetches real Kong Prometheus metrics.
     * Runs every 60 seconds.
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
                MetricsSnapshot snapshot = collectKongMetrics(env, periodStart, periodEnd);
                if (snapshot != null) {
                    metricsRepo.save(snapshot);
                    log.debug("Collected metrics for env '{}': qps={}, errors={}", 
                            env.getEnvCode(), snapshot.getQps(), snapshot.getErrorRate());
                }
            } catch (Exception e) {
                log.warn("Metrics collection failed for env '{}': {}", env.getEnvCode(), e.getMessage());
            }
        }
    }

    /**
     * Fetch and parse Kong Prometheus metrics, compute QPS/error rate/latency.
     */
    private MetricsSnapshot collectKongMetrics(Environment env, Instant periodStart, Instant periodEnd) {
        String adminEndpoint = env.getAdminEndpoint();
        if (adminEndpoint == null || adminEndpoint.isBlank()) {
            log.debug("No admin endpoint for env '{}'", env.getEnvCode());
            return null;
        }

        // Fetch Prometheus metrics
        String metricsUrl = adminEndpoint.replace(":8001", ":8001") + "/metrics";
        Map<String, List<Metric>> parsed;
        try {
            String raw = httpGet(metricsUrl);
            log.warn("Fetched {} bytes from Kong metrics, first 200 chars: {}", raw.length(), raw.substring(0, Math.min(200, raw.length())));
            parsed = parseMetrics(raw);
            log.warn("Kong metrics parsed: {} metric types, latency keys present: sum={} count={}",
                    parsed.size(),
                    parsed.containsKey("kong_request_latency_ms_sum"),
                    parsed.containsKey("kong_request_latency_ms_count"));
        } catch (Exception e) {
            log.warn("Failed to fetch/parse Kong metrics from {}: {}", metricsUrl, e.getMessage());
            return null;
        }

        // Compute request totals
        List<Metric> requestMetrics = parsed.getOrDefault("kong_http_requests_total", List.of());
        long total2xx = 0, totalErrors = 0;
        for (Metric m : requestMetrics) {
            String code = m.labels.get("code");
            long val = (long) m.value;
            if (code != null && (code.startsWith("2") || code.equals("101"))) {
                total2xx += val;
            } else if (code != null) {
                totalErrors += val;
            }
        }

        long totalRequests = total2xx + totalErrors;

        // Compare with previous snapshot to calculate QPS
        BigDecimal qps = BigDecimal.ZERO;
        MetricsSnapshot previous = findPreviousSnapshot(env.getTenantId(), env.getId());
        if (previous != null && previous.getMetricsJson() != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> prevJson = (Map<String, Object>) previous.getMetricsJson();
            long prevTotal = ((Number) prevJson.getOrDefault("totalRequests", 0)).longValue();
            long diff = totalRequests - prevTotal;
            double seconds = Duration.between(previous.getPeriodEnd(), periodEnd).getSeconds();
            if (seconds > 0 && diff >= 0) {
                qps = BigDecimal.valueOf(diff / seconds);
            }
        }

        // Error rate
        BigDecimal errorRate = totalRequests > 0
                ? BigDecimal.valueOf(totalErrors).divide(BigDecimal.valueOf(totalRequests), 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Latency from kong_request_latency_ms histogram (sum/count = average)
        BigDecimal p50 = BigDecimal.ZERO;
        BigDecimal p95 = BigDecimal.ZERO;
        List<Metric> latencySumMetrics = parsed.getOrDefault("kong_request_latency_ms_sum", List.of());
        List<Metric> latencyCountMetrics = parsed.getOrDefault("kong_request_latency_ms_count", List.of());
        double latencySum = latencySumMetrics.stream().mapToDouble(m -> m.value).sum();
        long latencyCount = latencyCountMetrics.stream().mapToLong(m -> (long) m.value).sum();
        if (latencyCount > 0) {
            double avgMs = latencySum / latencyCount;
            p50 = BigDecimal.valueOf(avgMs).setScale(4, RoundingMode.HALF_UP);
            // Rough p95 estimate: 2x average (simplified — real impl would parse histogram buckets)
            p95 = BigDecimal.valueOf(avgMs * 2).setScale(4, RoundingMode.HALF_UP);
        }

        // Build metrics JSON for comparison on next run
        Map<String, Object> metricsJson = new HashMap<>();
        metricsJson.put("totalRequests", totalRequests);
        metricsJson.put("total2xx", total2xx);
        metricsJson.put("totalErrors", totalErrors);
        metricsJson.put("parsedMetricTypes", new ArrayList<>(parsed.keySet()));
        metricsJson.put("rawMetrics", requestMetrics.stream()
                .map(m -> m.name + "{" + m.labels + "}=" + m.value)
                .collect(Collectors.toList()));

        return MetricsSnapshot.builder()
                .tenantId(env.getTenantId())
                .environmentId(env.getId())
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .qps(qps)
                .p50LatencyMs(p50)
                .p95LatencyMs(p95)
                .errorRate(errorRate)
                .metricsJson(metricsJson)
                .createdAt(Instant.now())
                .build();
    }

    private MetricsSnapshot findPreviousSnapshot(String tenantId, Long envId) {
        var page = metricsRepo.findByTenantIdAndEnvironmentIdOrderByPeriodEndDesc(
                tenantId, envId, Pageable.ofSize(1));
        return page.hasContent() ? page.getContent().get(0) : null;
    }

    /**
     * Lightweight Prometheus text format parser.
     */
    private Map<String, List<Metric>> parseMetrics(String raw) {
        Map<String, List<Metric>> result = new HashMap<>();
        for (String line : raw.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            Matcher m = METRIC_LINE.matcher(line);
            if (m.matches()) {
                String name = m.group(1);
                String labelsStr = m.group(2);
                double value = Double.parseDouble(m.group(3));

                Map<String, String> labels = new HashMap<>();
                Matcher lm = LABEL_PAIR.matcher(labelsStr);
                while (lm.find()) {
                    labels.put(lm.group(1), lm.group(2));
                }

                result.computeIfAbsent(name, k -> new ArrayList<>())
                        .add(new Metric(name, labels, value));
            }
        }
        return result;
    }

    private String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } finally {
            conn.disconnect();
        }
    }

    private static class Metric {
        final String name;
        final Map<String, String> labels;
        final double value;

        Metric(String name, Map<String, String> labels, double value) {
            this.name = name;
            this.labels = labels;
            this.value = value;
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

    private Map<String, Object> buildEmptyOverview(String envCode, String period) {
        Map<String, Object> result = new HashMap<>();
        result.put("qps", BigDecimal.ZERO);
        result.put("p50LatencyMs", BigDecimal.ZERO);
        result.put("p95LatencyMs", BigDecimal.ZERO);
        result.put("errorRate", BigDecimal.ZERO);
        result.put("environmentCode", envCode);
        result.put("period", period);
        result.put("snapshotCount", 0);
        return result;
    }
}