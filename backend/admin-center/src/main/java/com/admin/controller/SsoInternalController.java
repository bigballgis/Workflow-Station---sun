package com.admin.controller;

import com.admin.config.PlatformSsoProperties;
import com.admin.dto.sso.SsoRedeemRequest;
import com.admin.dto.sso.SsoRedeemResponse;
import com.admin.service.PlatformSsoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 集群内服务调用：用内部密钥换取 SSO code 对应的用户标识。
 * 仅应通过集群网络访问；生产建议配合 NetworkPolicy 禁止公网直达 admin-center。
 */
@Slf4j
@RestController
@RequestMapping("/internal/sso")
@RequiredArgsConstructor
public class SsoInternalController {

    public static final String HEADER_INTERNAL = "X-Platform-Sso-Internal";

    private final PlatformSsoProperties ssoProperties;
    private final PlatformSsoService platformSsoService;

    @PostMapping("/redeem")
    public ResponseEntity<SsoRedeemResponse> redeem(
            @RequestHeader(value = HEADER_INTERNAL, required = false) String internalToken,
            @Valid @RequestBody SsoRedeemRequest request) {
        String expected = ssoProperties.getInternalToken();
        if (expected == null || expected.isBlank()) {
            log.error("SSO internal token is not configured");
            return ResponseEntity.status(503).build();
        }
        if (internalToken == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                internalToken.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(401).build();
        }
        try {
            return ResponseEntity.ok(platformSsoService.redeem(request));
        } catch (IllegalArgumentException e) {
            log.debug("SSO redeem failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

}
