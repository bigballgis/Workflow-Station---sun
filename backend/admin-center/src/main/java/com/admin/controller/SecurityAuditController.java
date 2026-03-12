package com.admin.controller;

import com.admin.component.SecurityAuditComponent;
import com.admin.component.SecurityAuditComponent.*;
import com.admin.entity.AuditLog;
import com.admin.entity.SecurityPolicy;
import com.admin.enums.AuditAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    
    /** 前端 action 简写到后端 AuditAction 的映射 */
    private static final Map<String, AuditAction> ACTION_MAP = new HashMap<>();
    static {
        ACTION_MAP.put("LOGIN", AuditAction.USER_LOGIN);
        ACTION_MAP.put("LOGOUT", AuditAction.USER_LOGOUT);
        ACTION_MAP.put("CREATE", AuditAction.DATA_CREATED);
        ACTION_MAP.put("UPDATE", AuditAction.DATA_UPDATED);
        ACTION_MAP.put("DELETE", AuditAction.DATA_DELETED);
        ACTION_MAP.put("PERMISSION_CHANGE", AuditAction.PERMISSION_GRANTED);
    }
    
    @PostMapping("/audit-logs/query")
    @Operation(summary = "查询审计日志")
    public ResponseEntity<Page<AuditLog>> queryAuditLogs(
            @RequestBody AuditQueryRequestDto requestDto, Pageable pageable) {
        AuditQueryRequest request = toInternalRequest(requestDto);
        // Default sort: newest first
        Pageable effective = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "timestamp"));
        return ResponseEntity.ok(securityAuditComponent.queryAuditLogs(request, effective));
    }
    
    private AuditQueryRequest toInternalRequest(AuditQueryRequestDto dto) {
        AuditQueryRequest req = new AuditQueryRequest();
        if (dto.getAction() != null && !dto.getAction().isBlank()) {
            AuditAction mapped = ACTION_MAP.get(dto.getAction().toUpperCase());
            if (mapped != null) {
                req.setAction(mapped);
            } else {
                try {
                    req.setAction(AuditAction.valueOf(dto.getAction().toUpperCase().replace(" ", "_")));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        req.setUserId(dto.getUserId());
        req.setUserName(dto.getUsername());
        req.setResourceType(dto.getResourceType());
        req.setResourceId(dto.getResourceId());
        if (dto.getResult() != null) {
            req.setSuccess("SUCCESS".equalsIgnoreCase(dto.getResult()));
        }
        if (dto.getStartTime() != null && !dto.getStartTime().isBlank()) {
            req.setStartTime(Instant.parse(dto.getStartTime()));
        }
        if (dto.getEndTime() != null && !dto.getEndTime().isBlank()) {
            req.setEndTime(Instant.parse(dto.getEndTime()));
        }
        if (dto.getSuccess() != null) {
            req.setSuccess(dto.getSuccess());
        }
        return req;
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
    
    @PostMapping("/audit-logs/export")
    @Operation(summary = "导出审计日志")
    public ResponseEntity<byte[]> exportAuditLogs(@RequestBody AuditQueryRequestDto requestDto) {
        AuditQueryRequest request = toInternalRequest(requestDto);
        Page<AuditLog> page = securityAuditComponent.queryAuditLogs(request, Pageable.unpaged());
        byte[] excel = buildAuditLogExcel(page.getContent());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-logs.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excel);
    }
    
    private byte[] buildAuditLogExcel(List<AuditLog> logs) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("审计日志");
            Row header = sheet.createRow(0);
            String[] headers = {"操作类型", "操作人", "资源类型", "资源ID", "IP地址", "结果", "时间"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int i = 0; i < logs.size(); i++) {
                AuditLog log = logs.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(log.getAction() != null ? log.getAction().name() : "");
                row.createCell(1).setCellValue(log.getUserName() != null ? log.getUserName() : "");
                row.createCell(2).setCellValue(log.getResourceType() != null ? log.getResourceType() : "");
                row.createCell(3).setCellValue(log.getResourceId() != null ? log.getResourceId() : "");
                row.createCell(4).setCellValue(log.getIpAddress() != null ? log.getIpAddress() : "");
                row.createCell(5).setCellValue(Boolean.TRUE.equals(log.getSuccess()) ? "成功" : "失败");
                row.createCell(6).setCellValue(log.getTimestamp() != null ? log.getTimestamp().toString() : "");
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("导出审计日志失败", e);
        }
    }
    
    @Data
    public static class AuditQueryRequestDto {
        private String action;
        private String userId;
        private String username;
        private String resourceType;
        private String resourceId;
        private String result;
        private String startTime;
        private String endTime;
        private Boolean success;
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
