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

@Slf4j
@RestController
@RequestMapping("/monitor")
@RequiredArgsConstructor
@Tag(name = "系统监控", description = "系统指标查询和告警管理接口")
public class SystemMonitorController {
    
    private final SystemMonitorComponent monitorComponent;
    
    // ==================== 指标查询 ====================
    
    @GetMapping("/metrics/system")
    @Operation(summary = "获取系统指标")
    public ResponseEntity<SystemMetrics> getSystemMetrics() {
        return ResponseEntity.ok(monitorComponent.collectSystemMetrics());
    }
    
    @GetMapping("/metrics/business")
    @Operation(summary = "获取业务指标")
    public ResponseEntity<BusinessMetrics> getBusinessMetrics() {
        return ResponseEntity.ok(monitorComponent.collectBusinessMetrics());
    }
    
    @GetMapping("/metrics/application")
    @Operation(summary = "获取应用指标")
    public ResponseEntity<ApplicationMetrics> getApplicationMetrics() {
        return ResponseEntity.ok(monitorComponent.collectApplicationMetrics());
    }
    
    @GetMapping("/metrics/all")
    @Operation(summary = "获取所有指标")
    public ResponseEntity<Map<String, Object>> getAllMetrics() {
        return ResponseEntity.ok(Map.of(
                "system", monitorComponent.collectSystemMetrics(),
                "business", monitorComponent.collectBusinessMetrics(),
                "application", monitorComponent.collectApplicationMetrics()
        ));
    }
    
    // ==================== 告警规则管理 ====================
    
    @PostMapping("/alert-rules")
    @Operation(summary = "创建告警规则")
    public ResponseEntity<AlertRule> createAlertRule(@Valid @RequestBody AlertRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(monitorComponent.createAlertRule(request));
    }
    
    @GetMapping("/alert-rules")
    @Operation(summary = "获取启用的告警规则")
    public ResponseEntity<List<AlertRule>> getEnabledRules() {
        return ResponseEntity.ok(monitorComponent.getEnabledRules());
    }
    
    // ==================== 告警管理 ====================
    
    @GetMapping("/alerts/active")
    @Operation(summary = "获取活跃告警")
    public ResponseEntity<List<Alert>> getActiveAlerts() {
        return ResponseEntity.ok(monitorComponent.getActiveAlerts());
    }
    
    @GetMapping("/alerts/active/count")
    @Operation(summary = "获取活跃告警数量")
    public ResponseEntity<Long> getActiveAlertCount() {
        return ResponseEntity.ok(monitorComponent.getActiveAlertCount());
    }
    
    @PostMapping("/alerts/{alertId}/acknowledge")
    @Operation(summary = "确认告警")
    public ResponseEntity<Alert> acknowledgeAlert(
            @PathVariable String alertId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(monitorComponent.acknowledgeAlert(alertId, userId));
    }
    
    @PostMapping("/alerts/{alertId}/resolve")
    @Operation(summary = "解决告警")
    public ResponseEntity<Alert> resolveAlert(
            @PathVariable String alertId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(monitorComponent.resolveAlert(alertId, userId));
    }
}
