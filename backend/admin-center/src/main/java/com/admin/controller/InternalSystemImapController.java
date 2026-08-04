package com.admin.controller;

import com.admin.component.SystemImapConfigResolver;
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
 * Internal API for service-to-service global IMAP settings lookup (email monitor).
 */
@RestController
@RequestMapping("/internal/system-imap")
@Tag(name = "内部-系统 IMAP", description = "服务间读取全局 IMAP 配置")
public class InternalSystemImapController {

    private final SystemImapConfigResolver systemImapConfigResolver;
    private final String serviceInternalToken;

    public InternalSystemImapController(
            SystemImapConfigResolver systemImapConfigResolver,
            @Value("${service.internal-token:}") String serviceInternalToken) {
        this.systemImapConfigResolver = systemImapConfigResolver;
        this.serviceInternalToken = serviceInternalToken;
    }

    @GetMapping
    @Operation(summary = "获取全局 IMAP 配置（内部）")
    public ResponseEntity<Map<String, Object>> getSystemImap(
            @RequestHeader(value = PlatformConstants.HEADER_SERVICE_TOKEN, required = false) String serviceToken) {
        if (!isValidServiceToken(serviceToken)) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "FORBIDDEN",
                    "message", "valid X-Service-Token required"
            ));
        }
        try {
            SystemImapConfigResolver.SystemImapEndpoint endpoint =
                    systemImapConfigResolver.requireSystemImapEndpoint();
            return ResponseEntity.ok(Map.of(
                    "host", endpoint.host(),
                    "port", endpoint.port(),
                    "useSsl", endpoint.useSsl()
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "SYSTEM_IMAP_NOT_CONFIGURED",
                    "message", ex.getMessage() != null ? ex.getMessage() : "System IMAP not configured"
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
