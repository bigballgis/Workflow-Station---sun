package com.portal.dto.bi;

public record PortalGuestTokenResponse(
        String token,
        String dashboardEmbedId,
        String supersetDomain) {
}
