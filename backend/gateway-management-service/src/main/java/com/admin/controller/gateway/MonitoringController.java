package com.admin.controller.gateway;

import com.admin.entity.gateway.MetricsSnapshot;
import com.admin.service.gateway.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/gateway/monitoring")
@RequiredArgsConstructor
@Tag(name = "Gateway Monitoring", description = "View gateway API metrics: QPS, latency, error rates")
public class MonitoringController {

    private final MonitoringService monitoringService;

    @GetMapping("/overview")
    @Operation(summary = "Get monitoring overview for an environment")
    public ResponseEntity<Map<String, Object>> getOverview(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestParam String environmentCode,
            @RequestParam(defaultValue = "1h") String period) {
        return ResponseEntity.ok(monitoringService.getOverview(tenantId, environmentCode, period));
    }

    @GetMapping("/apis/{apiId}")
    @Operation(summary = "Get metrics for a specific API")
    public ResponseEntity<Page<MetricsSnapshot>> getApiMetrics(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long apiId,
            @RequestParam String environmentCode,
            @RequestParam(defaultValue = "24h") String period,
            Pageable pageable) {
        return ResponseEntity.ok(monitoringService.getApiMetrics(
                tenantId, apiId, environmentCode, period, pageable));
    }
}
