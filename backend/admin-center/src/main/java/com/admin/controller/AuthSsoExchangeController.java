package com.admin.controller;

import com.admin.dto.response.LoginResponse;
import com.admin.service.AuthService;
import com.admin.service.PlatformSsoService;
import com.admin.dto.sso.SsoRedeemRequest;
import com.admin.dto.sso.SsoRedeemResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin frontend exchanges the code for a local JWT after the unified /login callback
 * (symmetric with portal / dw exchanges).
 */
@Slf4j
@RestController
@RequestMapping("/auth/sso")
@RequiredArgsConstructor
public class AuthSsoExchangeController {

    private static final String ADMIN_CLIENT_ID = "admin";

    private final PlatformSsoService platformSsoService;
    private final AuthService authService;

    @PostMapping("/exchange")
    public ResponseEntity<LoginResponse> exchange(
            @RequestBody SsoExchangeBody body,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        if (body == null || body.getCode() == null || body.getCode().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            SsoRedeemResponse redeemed = platformSsoService.redeem(
                    redeemRequest(body.getCode()));
            String ip = getClientIpAddress(httpRequest);
            String ua = httpRequest.getHeader("User-Agent");
            LoginResponse session = authService.issueSsoSession(redeemed.getUserId(), ip, ua, httpResponse);
            return ResponseEntity.ok(session);
        } catch (IllegalArgumentException e) {
            log.debug("Admin SSO exchange failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.warn("Admin SSO exchange error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    LoginResponse.builder().error(e.getMessage()).build());
        }
    }

    private static SsoRedeemRequest redeemRequest(String code) {
        SsoRedeemRequest r = new SsoRedeemRequest();
        r.setCode(code);
        r.setClientId(ADMIN_CLIENT_ID);
        return r;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    @Data
    public static class SsoExchangeBody {
        private String code;
        private String state;
    }
}
