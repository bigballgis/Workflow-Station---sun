package com.admin.controller;

import com.admin.component.SecurityAuditComponent;
import com.admin.component.SecurityAuditComponent.*;
import com.admin.entity.AuditLog;
import com.admin.entity.SecurityPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/security")
@RequiredArgsConstructor
@Tag(name = "安全审计", description = "安全策略配置和审计日志管理接口")
public class SecurityAuditController {
    
    private final SecurityAuditComponent securityAuditComponent;
    
    // ==================== 安全策略管理 ====================
    
    @GetMapping("/policies")
    @Operation(summary = "获取所有安全策略")
    public ResponseEntity<List<SecurityPolicy>> getAllPolicies() {
        return ResponseEntity.ok(securityAuditComponent.getAllPolicies());
    }
    
    @GetMapping("/policies/{policyType}")
    @Operation(summary = "获取指定类型的安全策略")
    public ResponseEntity<SecurityPolicy> getPolicy(@PathVariable String policyType) {
        return ResponseEntity.ok(securityAuditComponent.getPolicy(policyType));
    }
    
    @PutMapping("/policies/password")
    @Operation(summary = "更新密码策略")
    public ResponseEntity<SecurityPolicy> updatePasswordPolicy(
            @Valid @RequestBody PasswordPolicyConfig config,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(securityAuditComponent.createOrUpdatePolicy("PASSWORD", config, userId));
    }
    
    @PutMapping("/policies/login")
    @Operation(summary = "更新登录策略")
    public ResponseEntity<SecurityPolicy> updateLoginPolicy(
            @Valid @RequestBody LoginPolicyConfig config,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(securityAuditComponent.createOrUpdatePolicy("LOGIN", config, userId));
    }
    
    @PutMapping("/policies/session")
    @Operation(summary = "更新会话策略")
    public ResponseEntity<SecurityPolicy> updateSessionPolicy(
            @Valid @RequestBody SessionPolicyConfig config,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(securityAuditComponent.createOrUpdatePolicy("SESSION", config, userId));
    }
    
    // ==================== 密码验证 ====================
    
    @PostMapping("/validate-password")
    @Operation(summary = "验证密码是否符合策略")
    public ResponseEntity<PasswordValidationResult> validatePassword(@RequestBody String password) {
        return ResponseEntity.ok(securityAuditComponent.validatePassword(password));
    }
    
    // ==================== 审计日志查询 ====================
    
    @PostMapping("/audit-logs/query")
    @Operation(summary = "查询审计日志")
    public ResponseEntity<Page<AuditLog>> queryAuditLogs(
            @RequestBody AuditQueryRequest request, Pageable pageable) {
        return ResponseEntity.ok(securityAuditComponent.queryAuditLogs(request, pageable));
    }
    
    @GetMapping("/audit-logs/user/{userId}")
    @Operation(summary = "获取用户审计日志")
    public ResponseEntity<Page<AuditLog>> getAuditLogsByUser(
            @PathVariable String userId, Pageable pageable) {
        return ResponseEntity.ok(securityAuditComponent.getAuditLogsByUser(userId, pageable));
    }
    
    @GetMapping("/audit-logs/resource/{resourceType}/{resourceId}")
    @Operation(summary = "获取资源审计日志")
    public ResponseEntity<Page<AuditLog>> getAuditLogsByResource(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            Pageable pageable) {
        return ResponseEntity.ok(securityAuditComponent.getAuditLogsByResource(resourceType, resourceId, pageable));
    }
    
    // ==================== 异常检测 ====================
    
    @GetMapping("/anomalies")
    @Operation(summary = "检测异常行为")
    public ResponseEntity<List<AnomalyDetectionResult>> detectAnomalies(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(securityAuditComponent.detectAnomalies(days));
    }
    
    // ==================== 合规报告 ====================
    
    @GetMapping("/compliance-report")
    @Operation(summary = "生成合规报告")
    public ResponseEntity<ComplianceReport> generateComplianceReport(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(securityAuditComponent.generateComplianceReport(days));
    }
}
