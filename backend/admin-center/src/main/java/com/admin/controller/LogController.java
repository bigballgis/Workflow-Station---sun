package com.admin.controller;

import com.admin.component.LogManagerComponent;
import com.admin.component.LogManagerComponent.*;
import com.admin.entity.LogRetentionPolicy;
import com.admin.entity.SystemLog;
import com.admin.enums.LogType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.platform.security.util.SecurityContextUtils;
import com.platform.common.i18n.I18nService;

@Slf4j
@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
@Tag(name = "Log Management", description = "Log query, analysis and export APIs")
public class LogController {
    
    private final LogManagerComponent logManager;
    private final I18nService i18nService;
    
    // ==================== Log Query ====================
    
    @PostMapping("/query")
    @Operation(summary = "Query logs")
    public ResponseEntity<Page<SystemLog>> queryLogs(
            @RequestBody LogQueryRequest request, Pageable pageable) {
        return ResponseEntity.ok(logManager.queryLogs(request, pageable));
    }
    
    @GetMapping("/search")
    @Operation(summary = "Search logs")
    public ResponseEntity<Page<SystemLog>> searchLogs(
            @RequestParam String keyword, Pageable pageable) {
        return ResponseEntity.ok(logManager.searchLogs(keyword, pageable));
    }
    
    @GetMapping("/type/{logType}")
    @Operation(summary = "Get logs by type")
    public ResponseEntity<Page<SystemLog>> getLogsByType(
            @PathVariable LogType logType, Pageable pageable) {
        return ResponseEntity.ok(logManager.getLogsByType(logType, pageable));
    }
    
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user logs")
    public ResponseEntity<Page<SystemLog>> getLogsByUser(
            @PathVariable String userId, Pageable pageable) {
        return ResponseEntity.ok(logManager.getLogsByUser(userId, pageable));
    }
    
    // ==================== User Behavior Analysis ====================
    
    @GetMapping("/user/{userId}/behavior")
    @Operation(summary = "Analyze user behavior")
    public ResponseEntity<UserBehaviorAnalysis> analyzeUserBehavior(
            @PathVariable String userId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(logManager.analyzeUserBehavior(userId, days));
    }
    
    // ==================== Log Analysis ====================
    
    @GetMapping("/statistics")
    @Operation(summary = "Get log statistics")
    public ResponseEntity<LogStatistics> getLogStatistics(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(logManager.getLogStatistics(days));
    }
    
    @GetMapping("/error-trend")
    @Operation(summary = "Get error trend")
    public ResponseEntity<List<ErrorTrendPoint>> getErrorTrend(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(logManager.getErrorTrend(days));
    }
    
    @GetMapping("/performance-bottlenecks")
    @Operation(summary = "Detect performance bottlenecks")
    public ResponseEntity<List<PerformanceBottleneck>> detectPerformanceBottlenecks(
            @RequestParam(defaultValue = "1000") long thresholdMs,
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(logManager.detectPerformanceBottlenecks(thresholdMs, days));
    }
    
    // ==================== Log Export ====================
    
    @PostMapping("/export")
    @Operation(summary = "Export logs")
    public ResponseEntity<byte[]> exportLogs(
            @RequestBody LogQueryRequest request,
            @RequestParam(defaultValue = "csv") String format) {
        LogExportResult result = logManager.exportLogs(request, format);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(result.getContentType()));
        headers.setContentDispositionFormData("attachment", result.getFilename());
        
        return new ResponseEntity<>(result.getContent().getBytes(), headers, HttpStatus.OK);
    }
    
    // ==================== Retention Policy Management ====================
    
    @PostMapping("/retention-policies")
    @Operation(summary = "Create retention policy")
    public ResponseEntity<LogRetentionPolicy> createRetentionPolicy(
            @Valid @RequestBody RetentionPolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(logManager.createRetentionPolicy(request));
    }
    
    @GetMapping("/retention-policies")
    @Operation(summary = "Get retention policy list")
    public ResponseEntity<List<LogRetentionPolicy>> getRetentionPolicies() {
        return ResponseEntity.ok(logManager.getRetentionPolicies());
    }
    
    @PutMapping("/retention-policies/{id}")
    @Operation(summary = "Update retention policy")
    public ResponseEntity<LogRetentionPolicy> updateRetentionPolicy(
            @PathVariable String id,
            @Valid @RequestBody RetentionPolicyRequest request) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return ResponseEntity.ok(logManager.updateRetentionPolicy(id, request, userId));
    }
    
    @PostMapping("/retention-policies/apply")
    @Operation(summary = "Apply retention policies")
    public ResponseEntity<Void> applyRetentionPolicies() {
        logManager.applyRetentionPolicies();
        return ResponseEntity.ok().build();
    }
}
