package com.admin.controller;

import com.admin.dto.sso.SsoLoginRequest;
import com.admin.dto.sso.SsoLoginResponse;
import com.admin.service.PlatformSsoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一登录页调用的 SSO 登录（签发 authorization code）。
 */
@Slf4j
@RestController
@RequestMapping("/sso")
@RequiredArgsConstructor
public class SsoAuthController {

    private final PlatformSsoService platformSsoService;

    @PostMapping("/login")
    public ResponseEntity<SsoLoginResponse> login(@Valid @RequestBody SsoLoginRequest request) {
        try {
            return ResponseEntity.ok(platformSsoService.loginAndIssueCode(request));
        } catch (IllegalArgumentException e) {
            log.warn("SSO login failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
