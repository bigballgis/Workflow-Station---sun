package com.admin.controller;

import com.admin.component.SystemMonitorComponent;
import com.admin.component.SystemMonitorComponent.*;
import com.admin.entity.Alert;
import com.admin.entity.AlertRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.platform.security.util.SecurityContextUtils;
import com.platform.common.i18n.I18nService;

@Slf4j
@RestController
@RequestMapping("/monitor")
@RequiredArgsConstructor
@Tag(name = "System Monitor", description = "System metrics query and alert management APIs")
public class SystemMonitorController {
    
    private final SystemMonitorComponent monitorComponent;
    private final I18nService i18nService;
    
    // ==================== Metrics Query ====================
    
    @GetMapping("/metrics/system")
    @Operation(summary = "Get system metrics")
    public ResponseEntity<SystemMetrics> getSystemMetrics() {
        return ResponseEntity.ok(monitorComponent.collectSystemMetrics());
    }
    
    @GetMapping("/metrics/business")
    @Operation(summary = "Get business metrics")
    public ResponseEntity<BusinessMetrics> getBusinessMetrics() {
        return ResponseEntity.ok(monitorComponent.collectBusinessMetrics());
    }
    
    @GetMapping("/metrics/application")
    @Operation(summary = "Get application metrics")
    public ResponseEntity<ApplicationMetrics> getApplicationMetrics() {
        return ResponseEntity.ok(monitorComponent.collectApplicationMetrics());
    }
    
    @GetMapping("/metrics/all")
    @Operation(summary = "Get all metrics")
    public ResponseEntity<Map<String, Object>> getAllMetrics() {
        return ResponseEntity.ok(Map.of(
                "system", monitorComponent.collectSystemMetrics(),
                "business", monitorComponent.collectBusinessMetrics(),
                "application", monitorComponent.collectApplicationMetrics()
        ));
    }
    
    // ==================== Alert Rule Management ====================
    
    @PostMapping("/alert-rules")
    @Operation(summary = "Create alert rule")
    public ResponseEntity<AlertRule> createAlertRule(@Valid @RequestBody AlertRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(monitorComponent.createAlertRule(request));
    }
    
    @GetMapping("/alert-rules")
    @Operation(summary = "Get enabled alert rules")
    public ResponseEntity<List<AlertRule>> getEnabledRules() {
        return ResponseEntity.ok(monitorComponent.getEnabledRules());
    }
    
    // ==================== Alert Management ====================
    
    @GetMapping("/alerts/active")
    @Operation(summary = "Get active alerts")
    public ResponseEntity<List<Alert>> getActiveAlerts() {
        return ResponseEntity.ok(monitorComponent.getActiveAlerts());
    }
    
    @GetMapping("/alerts/active/count")
    @Operation(summary = "Get active alert count")
    public ResponseEntity<Long> getActiveAlertCount() {
        return ResponseEntity.ok(monitorComponent.getActiveAlertCount());
    }
    
    @PostMapping("/alerts/{alertId}/acknowledge")
    @Operation(summary = "Acknowledge alert")
    public ResponseEntity<Alert> acknowledgeAlert(
            @PathVariable String alertId) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return ResponseEntity.ok(monitorComponent.acknowledgeAlert(alertId, userId));
    }
    
    @PostMapping("/alerts/{alertId}/resolve")
    @Operation(summary = "Resolve alert")
    public ResponseEntity<Alert> resolveAlert(
            @PathVariable String alertId) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return ResponseEntity.ok(monitorComponent.resolveAlert(alertId, userId));
    }
}
