package com.portal.controller;

import com.platform.security.util.SecurityContextUtils;
import com.portal.client.PortalBiClient;
import com.portal.dto.bi.PortalDataViewDashboardResponse;
import com.portal.dto.bi.PortalGuestTokenRequest;
import com.portal.dto.bi.PortalGuestTokenResponse;
import com.portal.dto.bi.PortalUserDashboardResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/bi")
@RequiredArgsConstructor
public class BiDashboardController {

    private final PortalBiClient portalBiClient;

    @GetMapping("/dashboards")
    public ResponseEntity<List<PortalUserDashboardResponse>> getDashboards(
            @RequestParam(required = false) String activeBusinessUnitId) {
        String userId = requireAuthenticatedUserId();
        String activeBuId = resolveActiveBusinessUnit(activeBusinessUnitId);
        return ResponseEntity.ok(portalBiClient.getDashboards(userId, activeBuId));
    }

    @PostMapping("/guest-token")
    public ResponseEntity<PortalGuestTokenResponse> getGuestToken(
            @RequestBody @Valid PortalGuestTokenRequest request) {
        String userId = requireAuthenticatedUserId();
        String activeBuId = resolveActiveBusinessUnit(request.activeBusinessUnitId());
        return ResponseEntity.ok(portalBiClient.getGuestToken(
                userId, request.withActiveBusinessUnitId(activeBuId)));
    }

    @GetMapping("/data-view-assignments/views/{viewId}/dashboards")
    public ResponseEntity<List<PortalDataViewDashboardResponse>> getDataViewDashboards(
            @PathVariable Long viewId) {
        return ResponseEntity.ok(portalBiClient.getDataViewDashboards(
                requireAuthenticatedUserId(), viewId));
    }

    private String requireAuthenticatedUserId() {
        return SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "Portal authentication required"));
    }

    /**
     * Prefer the signed workspace context carried by the Portal JWT. A request may repeat
     * it for UI consistency, but it may not contradict the signed claim. Older sessions
     * without that claim fall back to the requested BU; Admin Center still verifies membership.
     */
    private String resolveActiveBusinessUnit(String requestedBusinessUnitId) {
        String requested = requestedBusinessUnitId == null || requestedBusinessUnitId.isBlank()
                ? null : requestedBusinessUnitId.trim();
        return SecurityContextUtils.getCurrentActiveBusinessUnitId()
                .map(signed -> {
                    if (requested != null && !signed.equals(requested)) {
                        throw new AccessDeniedException("Active business unit does not match the authenticated session");
                    }
                    return signed;
                })
                .orElse(requested);
    }
}
