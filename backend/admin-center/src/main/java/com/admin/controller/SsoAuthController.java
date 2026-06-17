package com.admin.controller;

import com.admin.config.PlatformSsoProperties;
import com.admin.dto.sso.SsoLoginRequest;
import com.admin.dto.sso.SsoLoginResponse;
import com.admin.service.PlatformSsoService;
import com.admin.sso.dsp.DspPasswordlessRequest;
import com.admin.sso.dsp.DspSsoService;
import com.platform.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
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
 *
 * <p>{@code /login} 为既有账号口令登录（保持原裸响应契约，前端已适配，避免破坏兼容）；
 * {@code /passwordless} 为本次新增的 DSP 免密入口，按规范使用统一 {@link ApiResponse} 包装。</p>
 */
@Slf4j
@RestController
@RequestMapping("/sso")
@RequiredArgsConstructor
public class SsoAuthController {

    private static final String ERR_BAD_REQUEST = "SSO_BAD_REQUEST";
    private static final String ERR_DISABLED = "SSO_DSP_DISABLED";

    private final PlatformSsoService platformSsoService;
    private final DspSsoService dspSsoService;
    private final PlatformSsoProperties ssoProperties;

    @PostMapping("/login")
    public ResponseEntity<SsoLoginResponse> login(@Valid @RequestBody SsoLoginRequest request) {
        try {
            return ResponseEntity.ok(platformSsoService.loginAndIssueCode(request));
        } catch (IllegalArgumentException e) {
            log.warn("SSO login failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DSP 免密登录：用浏览器侧 AMToken（或网关注入的 E2E header）换 SSO code。
     */
    @PostMapping("/passwordless")
    public ResponseEntity<ApiResponse<SsoLoginResponse>> passwordless(
            @Valid @RequestBody DspPasswordlessRequest request,
            HttpServletRequest httpRequest) {
        String e2eHeader = httpRequest.getHeader(ssoProperties.getDsp().getE2eHeaderName());
        if (request.getAmToken() == null || request.getAmToken().isBlank()) {
            // 兼容：AMToken 也可放在 header 中
            request.setAmToken(httpRequest.getHeader(ssoProperties.getDsp().getAmTokenName()));
        }
        try {
            SsoLoginResponse resp = dspSsoService.passwordless(request, e2eHeader);
            return ResponseEntity.ok(ApiResponse.success(resp));
        } catch (IllegalStateException e) {
            log.warn("DSP passwordless unavailable: {}", e.getMessage());
            return ResponseEntity.status(503).body(ApiResponse.error(ERR_DISABLED, e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("DSP passwordless failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(ERR_BAD_REQUEST, e.getMessage()));
        }
    }
}
