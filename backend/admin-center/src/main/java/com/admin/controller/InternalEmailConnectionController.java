package com.admin.controller;

import com.admin.component.EmailConnectionSyncComponent;
import com.platform.common.constant.PlatformConstants;
import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import com.platform.common.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Internal API for service-to-service email connection credential lookup.
 */
@Slf4j
@RestController
@RequestMapping("/internal/function-units")
@Tag(name = "内部-邮件连接", description = "工作流引擎内部调用")
public class InternalEmailConnectionController {

    private final EmailConnectionSyncComponent emailConnectionSyncComponent;
    private final String serviceInternalToken;

    public InternalEmailConnectionController(
            EmailConnectionSyncComponent emailConnectionSyncComponent,
            @Value("${service.internal-token:}") String serviceInternalToken) {
        this.emailConnectionSyncComponent = emailConnectionSyncComponent;
        this.serviceInternalToken = serviceInternalToken;
    }

    @GetMapping("/{functionUnitId}/connections/{connectionId}/credentials")
    @Operation(summary = "获取邮件连接凭据（内部）")
    public ResponseEntity<Map<String, Object>> getCredentials(
            @RequestHeader(value = PlatformConstants.HEADER_SERVICE_TOKEN, required = false) String serviceToken,
            @PathVariable String functionUnitId,
            @PathVariable String connectionId) {
        if (!isValidServiceToken(serviceToken)) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "FORBIDDEN",
                    "message", "valid X-Service-Token required"));
        }
        try {
            return emailConnectionSyncComponent.getCredentials(functionUnitId, connectionId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (ResourceNotFoundException ex) {
            log.warn("Vault catalog miss functionUnitId={} connectionId={}: {}",
                    functionUnitId, connectionId, ex.getMessage());
            return ResponseEntity.status(404).body(Map.of(
                    "error", "VAULT_SECRET_NOT_FOUND",
                    "message", ex.getMessage() != null ? ex.getMessage() : "Environment variable not found"));
        } catch (BusinessException ex) {
            return mapCredentialBusinessException(functionUnitId, connectionId, ex);
        } catch (IllegalStateException ex) {
            return mapCredentialIllegalState(functionUnitId, connectionId, ex);
        }
    }

    private ResponseEntity<Map<String, Object>> mapCredentialBusinessException(
            String functionUnitId, String connectionId, BusinessException ex) {
        if (ex.getErrorCode() == ErrorCode.VALIDATION_FIELD_INVALID) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "VAULT_KIND_REQUIRED",
                    "message", ex.getMessage() != null ? ex.getMessage()
                            : "Email passwords must reference a VAULT environment variable"));
        }
        if (ex.getErrorCode() == ErrorCode.EXTERNAL_SERVICE_ERROR) {
            log.warn("Vault unavailable functionUnitId={} connectionId={} errorCode={}",
                    functionUnitId, connectionId, ex.getErrorCode());
            return ResponseEntity.status(503).body(Map.of(
                    "error", "VAULT_UNAVAILABLE",
                    "message", ex.getMessage() != null ? ex.getMessage() : "Vault is unavailable"));
        }
        log.warn("Vault secret not found functionUnitId={} connectionId={} errorCode={}",
                functionUnitId, connectionId, ex.getErrorCode());
        return ResponseEntity.status(404).body(Map.of(
                "error", "VAULT_SECRET_NOT_FOUND",
                "message", ex.getMessage() != null ? ex.getMessage() : "Vault secret not found"));
    }

    private ResponseEntity<Map<String, Object>> mapCredentialIllegalState(
            String functionUnitId, String connectionId, IllegalStateException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Credential lookup failed";
        log.warn("Credential lookup failed functionUnitId={} connectionId={}: {}",
                functionUnitId, connectionId, message);
        if (message.toLowerCase().contains("vault")) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "VAULT_UNAVAILABLE",
                    "message", message));
        }
        return ResponseEntity.status(503).body(Map.of(
                "error", "SYSTEM_SMTP_NOT_CONFIGURED",
                "message", message));
    }

    @GetMapping("/by-code/{functionUnitCode}/id")
    @Operation(summary = "按 code 解析功能单元 ID（内部）")
    public ResponseEntity<Map<String, String>> resolveByCode(
            @RequestHeader(value = PlatformConstants.HEADER_SERVICE_TOKEN, required = false) String serviceToken,
            @PathVariable String functionUnitCode) {
        if (!isValidServiceToken(serviceToken)) {
            return ResponseEntity.status(403).build();
        }
        return emailConnectionSyncComponent.resolveFunctionUnitIdByCode(functionUnitCode)
                .map(id -> ResponseEntity.ok(Map.of("functionUnitId", id)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{functionUnitId}/code")
    @Operation(summary = "按功能单元 ID 解析 code（内部）")
    public ResponseEntity<Map<String, String>> resolveCodeById(
            @RequestHeader(value = PlatformConstants.HEADER_SERVICE_TOKEN, required = false) String serviceToken,
            @PathVariable String functionUnitId) {
        if (!isValidServiceToken(serviceToken)) {
            return ResponseEntity.status(403).build();
        }
        return emailConnectionSyncComponent.resolveFunctionUnitCodeById(functionUnitId)
                .map(code -> ResponseEntity.ok(Map.of("functionUnitCode", code)))
                .orElse(ResponseEntity.notFound().build());
    }

    private boolean isValidServiceToken(String provided) {
        if (serviceInternalToken == null || serviceInternalToken.isBlank()
                || provided == null || provided.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                serviceInternalToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
