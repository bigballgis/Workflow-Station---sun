package com.admin.bi.controller;

import com.admin.bi.dto.request.GuestTokenRequest;
import com.admin.bi.dto.response.GuestTokenResponse;
import com.admin.bi.service.BiGuestTokenService;
import com.platform.common.i18n.I18nService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.platform.security.util.SecurityContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Guest Token controller.
 * Obtains Superset Guest Token for frontend embedded Dashboard rendering.
 */
@RestController
@RequestMapping("/bi/guest-token")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Guest Token Management", description = "Get Superset Guest Token API")
public class BiGuestTokenController {

    private final BiGuestTokenService guestTokenService;
    private final I18nService i18nService;

    @PostMapping
    @Operation(summary = "Get Guest Token", description = "Verify user Dashboard assignment permission and obtain Superset Guest Token")
    public ResponseEntity<GuestTokenResponse> getGuestToken(
            @RequestBody @Valid GuestTokenRequest request) {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated")));
        log.info("User {} requesting guest token for dashboard {}", userId, request.getDashboardId());
        GuestTokenResponse response = guestTokenService.getGuestToken(userId, request);
        return ResponseEntity.ok(response);
    }
}
