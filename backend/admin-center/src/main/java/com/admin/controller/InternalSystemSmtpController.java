package com.admin.controller;

import com.admin.component.SystemSmtpConfigResolver;
import com.platform.common.constant.PlatformConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Internal API for service-to-service global SMTP settings lookup.
 */
@RestController
@RequestMapping("/internal/system-smtp")
@Tag(name = "内部-系统 SMTP", description = "服务间读取全局 SMTP 配置")
public class InternalSystemSmtpController {

    private final SystemSmtpConfigResolver systemSmtpConfigResolver;
    private final String serviceInternalToken;

    public InternalSystemSmtpController(
            SystemSmtpConfigResolver systemSmtpConfigResolver,
            @Value("${service.internal-token:}") String serviceInternalToken) {
        this.systemSmtpConfigResolver = systemSmtpConfigResolver;
        this.serviceInternalToken = serviceInternalToken;
    }

    @GetMapping
    @Operation(summary = "获取全局 SMTP 配置（内部）")
    public ResponseEntity<Map<String, Object>> getSystemSmtp(
            @RequestHeader(value = PlatformConstants.HEADER_SERVICE_TOKEN, required = false) String serviceToken) {
        if (!isValidServiceToken(serviceToken)) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "FORBIDDEN",
                    "message", "valid X-Service-Token required"
            ));
        }
        try {
            SystemSmtpConfigResolver.SystemSmtpEndpoint endpoint =
                    systemSmtpConfigResolver.requireSystemSmtpEndpoint();
            return ResponseEntity.ok(Map.of(
                    "host", endpoint.host(),
                    "port", endpoint.port(),
                    "useTls", endpoint.useTls()
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "SYSTEM_SMTP_NOT_CONFIGURED",
                    "message", ex.getMessage() != null ? ex.getMessage() : "System SMTP not configured"
            ));
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
