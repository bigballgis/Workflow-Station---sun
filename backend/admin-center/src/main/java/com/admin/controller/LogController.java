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

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/logs")
@RequiredArgsConstructor
@Tag(name = "日志管理", description = "日志查询、分析和导出接口")
public class LogController {
    
    private final LogManagerComponent logManager;
    
    // ==================== 日志查询 ====================
    
    @PostMapping("/query")
    @Operation(summary = "查询日志")
    public ResponseEntity<Page<SystemLog>> queryLogs(
            @RequestBody LogQueryRequest request, Pageable pageable) {
        return ResponseEntity.ok(logManager.queryLogs(request, pageable));
    }
    
    @GetMapping("/search")
    @Operation(summary = "搜索日志")
    public ResponseEntity<Page<SystemLog>> searchLogs(
            @RequestParam String keyword, Pageable pageable) {
        return ResponseEntity.ok(logManager.searchLogs(keyword, pageable));
    }
    
    @GetMapping("/type/{logType}")
    @Operation(summary = "按类型获取日志")
    public ResponseEntity<Page<SystemLog>> getLogsByType(
            @PathVariable LogType logType, Pageable pageable) {
        return ResponseEntity.ok(logManager.getLogsByType(logType, pageable));
    }
    
    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户日志")
    public ResponseEntity<Page<SystemLog>> getLogsByUser(
            @PathVariable String userId, Pageable pageable) {
        return ResponseEntity.ok(logManager.getLogsByUser(userId, pageable));
    }
    
    // ==================== 用户行为分析 ====================
    
    @GetMapping("/user/{userId}/behavior")
    @Operation(summary = "分析用户行为")
    public ResponseEntity<UserBehaviorAnalysis> analyzeUserBehavior(
            @PathVariable String userId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(logManager.analyzeUserBehavior(userId, days));
    }
    
    // ==================== 日志分析 ====================
    
    @GetMapping("/statistics")
    @Operation(summary = "获取日志统计")
    public ResponseEntity<LogStatistics> getLogStatistics(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(logManager.getLogStatistics(days));
    }
    
    @GetMapping("/error-trend")
    @Operation(summary = "获取错误趋势")
    public ResponseEntity<List<ErrorTrendPoint>> getErrorTrend(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(logManager.getErrorTrend(days));
    }
    
    @GetMapping("/performance-bottlenecks")
    @Operation(summary = "检测性能瓶颈")
    public ResponseEntity<List<PerformanceBottleneck>> detectPerformanceBottlenecks(
            @RequestParam(defaultValue = "1000") long thresholdMs,
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(logManager.detectPerformanceBottlenecks(thresholdMs, days));
    }
    
    // ==================== 日志导出 ====================
    
    @PostMapping("/export")
    @Operation(summary = "导出日志")
    public ResponseEntity<byte[]> exportLogs(
            @RequestBody LogQueryRequest request,
            @RequestParam(defaultValue = "csv") String format) {
        LogExportResult result = logManager.exportLogs(request, format);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(result.getContentType()));
        headers.setContentDispositionFormData("attachment", result.getFilename());
        
        return new ResponseEntity<>(result.getContent().getBytes(), headers, HttpStatus.OK);
    }
    
    // ==================== 保留策略管理 ====================
    
    @PostMapping("/retention-policies")
    @Operation(summary = "创建保留策略")
    public ResponseEntity<LogRetentionPolicy> createRetentionPolicy(
            @Valid @RequestBody RetentionPolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(logManager.createRetentionPolicy(request));
    }
    
    @GetMapping("/retention-policies")
    @Operation(summary = "获取保留策略列表")
    public ResponseEntity<List<LogRetentionPolicy>> getRetentionPolicies() {
        return ResponseEntity.ok(logManager.getRetentionPolicies());
    }
    
    @PutMapping("/retention-policies/{id}")
    @Operation(summary = "更新保留策略")
    public ResponseEntity<LogRetentionPolicy> updateRetentionPolicy(
            @PathVariable String id,
            @Valid @RequestBody RetentionPolicyRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(logManager.updateRetentionPolicy(id, request, userId));
    }
    
    @PostMapping("/retention-policies/apply")
    @Operation(summary = "应用保留策略")
    public ResponseEntity<Void> applyRetentionPolicies() {
        logManager.applyRetentionPolicies();
        return ResponseEntity.ok().build();
    }
}
