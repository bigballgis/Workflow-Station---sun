package com.admin.controller;

import com.admin.component.EnvironmentVariableComponent;
import com.admin.dto.EnvironmentVariableResponse;
import com.admin.enums.EnvironmentValueKind;
import com.platform.common.constant.PlatformConstants;
import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import com.platform.common.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * Service-to-service catalog + Vault password resolve for DW test connection.
 */
@RestController
@RequestMapping("/internal/environment-variables")
@Tag(name = "内部-环境变量", description = "服务间读取环境变量与 Vault 密码")
public class InternalEnvironmentVariableController {

    private final EnvironmentVariableComponent environmentVariableComponent;
    private final String serviceInternalToken;

    public InternalEnvironmentVariableController(
            EnvironmentVariableComponent environmentVariableComponent,
            @Value("${service.internal-token:}") String serviceInternalToken) {
        this.environmentVariableComponent = environmentVariableComponent;
        this.serviceInternalToken = serviceInternalToken;
    }

    @GetMapping
    @Operation(summary = "列出当前 deploy_env 的环境变量")
    public ResponseEntity<List<EnvironmentVariableResponse>> list(
            @RequestHeader(value = PlatformConstants.HEADER_SERVICE_TOKEN, required = false) String serviceToken,
            @RequestParam(required = false) EnvironmentValueKind kind) {
        if (!isValidServiceToken(serviceToken)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(environmentVariableComponent.list(kind));
    }

    @GetMapping("/vault-password")
    @Operation(summary = "解析 VAULT 环境变量的 data.password")
    public ResponseEntity<Map<String, String>> vaultPassword(
            @RequestHeader(value = PlatformConstants.HEADER_SERVICE_TOKEN, required = false) String serviceToken,
            @RequestParam String varKey) {
        if (!isValidServiceToken(serviceToken)) {
            return ResponseEntity.status(403).body(Map.of("error", "FORBIDDEN"));
        }
        try {
            String password = environmentVariableComponent.resolveVaultPassword(varKey);
            return ResponseEntity.ok(Map.of("password", password));
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "VAULT_SECRET_NOT_FOUND",
                    "message", ex.getMessage() != null ? ex.getMessage() : "Environment variable not found"));
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.VALIDATION_FIELD_INVALID) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "VAULT_KIND_REQUIRED",
                        "message", ex.getMessage() != null ? ex.getMessage()
                                : "Email passwords must reference a VAULT environment variable"));
            }
            return ResponseEntity.status(404).body(Map.of(
                    "error", "VAULT_SECRET_NOT_FOUND",
                    "message", ex.getMessage() != null ? ex.getMessage() : "Vault secret not found"));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "VAULT_UNAVAILABLE",
                    "message", ex.getMessage() != null ? ex.getMessage() : "Vault is unavailable"));
        }
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
