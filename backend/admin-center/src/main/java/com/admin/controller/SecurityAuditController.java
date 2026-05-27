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
import java.util.List;

import com.platform.security.util.SecurityContextUtils;
import com.platform.common.i18n.I18nService;

@Slf4j
@RestController
@RequestMapping("/security")
@RequiredArgsConstructor
@Tag(name = "Security Audit", description = "Security policy configuration and audit log management APIs")
public class SecurityAuditController {
    
    private final SecurityAuditComponent securityAuditComponent;
    private final I18nService i18nService;
    
    // ==================== Security Policy Management ====================
    
    @GetMapping("/policies")
    @Operation(summary = "Get all security policies")
    public ResponseEntity<List<SecurityPolicy>> getAllPolicies() {
        return ResponseEntity.ok(securityAuditComponent.getAllPolicies());
    }
    
    @GetMapping("/policies/{policyType}")
    @Operation(summary = "Get security policy by type")
    public ResponseEntity<SecurityPolicy> getPolicy(@PathVariable String policyType) {
        return ResponseEntity.ok(securityAuditComponent.getPolicy(policyType));
    }
    
    @PutMapping("/policies/password")
    @Operation(summary = "Update password policy")
    public ResponseEntity<SecurityPolicy> updatePasswordPolicy(
            @Valid @RequestBody PasswordPolicyConfig config) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return ResponseEntity.ok(securityAuditComponent.createOrUpdatePolicy("PASSWORD", config, userId));
    }
    
    @PutMapping("/policies/login")
    @Operation(summary = "Update login policy")
    public ResponseEntity<SecurityPolicy> updateLoginPolicy(
            @Valid @RequestBody LoginPolicyConfig config) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return ResponseEntity.ok(securityAuditComponent.createOrUpdatePolicy("LOGIN", config, userId));
    }
    
    @PutMapping("/policies/session")
    @Operation(summary = "Update session policy")
    public ResponseEntity<SecurityPolicy> updateSessionPolicy(
            @Valid @RequestBody SessionPolicyConfig config) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return ResponseEntity.ok(securityAuditComponent.createOrUpdatePolicy("SESSION", config, userId));
    }
    
    // ==================== Password Validation ====================
    
    @PostMapping("/validate-password")
    @Operation(summary = "Validate password against policy")
    public ResponseEntity<PasswordValidationResult> validatePassword(@RequestBody String password) {
        return ResponseEntity.ok(securityAuditComponent.validatePassword(password));
    }
    
    // ==================== Audit Log Query ====================
    
    /** All known resource types produced by AdminAuditAspect, returned to the UI for the filter dropdown */
    static final List<String> ALL_RESOURCE_TYPES = List.of(
            "AUTH",
            "BI_ASSIGNMENT", "BI_DASHBOARD", "BI_RBAC",
            "BUSINESS_UNIT",
            "RELATION_TABLE", "RELATION_TABLE_ROW",
            "ROLE",
            "TASK",
            "USER", "VIRTUAL_GROUP"
    );
    
    @PostMapping("/audit-logs/query")
    @Operation(summary = "Query audit logs")
    public ResponseEntity<Page<AuditLog>> queryAuditLogs(
            @RequestBody AuditQueryRequestDto requestDto, Pageable pageable) {
        AuditQueryRequest request = toInternalRequest(requestDto);
        // Default sort: newest first
        Pageable effective = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "timestamp"));
        return ResponseEntity.ok(securityAuditComponent.queryAuditLogs(request, effective));
    }
    
    @GetMapping("/audit-logs/resource-types")
    @Operation(summary = "Get all resource type enum values (for frontend dropdown filter)")
    public ResponseEntity<List<String>> getResourceTypes() {
        return ResponseEntity.ok(ALL_RESOURCE_TYPES);
    }

    private AuditQueryRequest toInternalRequest(AuditQueryRequestDto dto) {
        AuditQueryRequest req = new AuditQueryRequest();
        if (dto.getAction() != null && !dto.getAction().isBlank()) {
            try {
                req.setAction(AuditAction.valueOf(dto.getAction().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.debug("Unknown audit action: {}", dto.getAction());
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
    @Operation(summary = "Get user audit logs")
    public ResponseEntity<Page<AuditLog>> getAuditLogsByUser(
            @PathVariable String userId, Pageable pageable) {
        return ResponseEntity.ok(securityAuditComponent.getAuditLogsByUser(userId, pageable));
    }
    
    @GetMapping("/audit-logs/resource/{resourceType}/{resourceId}")
    @Operation(summary = "Get resource audit logs")
    public ResponseEntity<Page<AuditLog>> getAuditLogsByResource(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            Pageable pageable) {
        return ResponseEntity.ok(securityAuditComponent.getAuditLogsByResource(resourceType, resourceId, pageable));
    }
    
    @PostMapping("/audit-logs/export")
    @Operation(summary = "Export audit logs")
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
            Sheet sheet = wb.createSheet("Audit Log");
            Row header = sheet.createRow(0);
            String[] headers = {"Action", "Operator", "Resource Type", "Resource ID", "IP Address", "Result", "Time"};
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
                row.createCell(5).setCellValue(Boolean.TRUE.equals(log.getSuccess()) ? "Success" : "Failure");
                row.createCell(6).setCellValue(log.getTimestamp() != null ? log.getTimestamp().toString() : "");
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to export audit log", e);
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
    
    // ==================== Anomaly Detection ====================
    
    @GetMapping("/anomalies")
    @Operation(summary = "Detect anomalous behavior")
    public ResponseEntity<List<AnomalyDetectionResult>> detectAnomalies(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(securityAuditComponent.detectAnomalies(days));
    }
    
    // ==================== Compliance Report ====================
    
    @GetMapping("/compliance-report")
    @Operation(summary = "Generate compliance report")
    public ResponseEntity<ComplianceReport> generateComplianceReport(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(securityAuditComponent.generateComplianceReport(days));
    }
}
