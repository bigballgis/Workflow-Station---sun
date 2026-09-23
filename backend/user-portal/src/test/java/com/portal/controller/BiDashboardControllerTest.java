package com.portal.controller;

import com.platform.common.dto.UserPrincipal;
import com.portal.client.PortalBiClient;
import com.portal.dto.bi.PortalGuestTokenRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BiDashboardControllerTest {

    private final PortalBiClient portalBiClient = mock(PortalBiClient.class);
    private final BiDashboardController controller = new BiDashboardController(portalBiClient);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsRequestsWithoutAnAuthenticatedPortalPrincipal() {
        assertThatThrownBy(() -> controller.getDashboards(null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verifyNoInteractions(portalBiClient);
    }

    @Test
    void rejectsBusinessUnitThatContradictsTheSignedSession() {
        authenticate("portal-user", "bu-signed");

        assertThatThrownBy(() -> controller.getDashboards("bu-forged"))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(portalBiClient);
    }

    @Test
    void delegatesTheAuthenticatedUserAndSignedBusinessUnit() {
        authenticate("portal-user", "bu-finance");
        when(portalBiClient.getDashboards("portal-user", "bu-finance")).thenReturn(List.of());

        controller.getDashboards(null);
        controller.getGuestToken(new PortalGuestTokenRequest("dashboard-1", null, "bu-finance"));

        verify(portalBiClient).getDashboards("portal-user", "bu-finance");
        verify(portalBiClient).getGuestToken(
                "portal-user",
                new PortalGuestTokenRequest("dashboard-1", null, "bu-finance"));
    }

    private static void authenticate(String userId, String activeBusinessUnitId) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(userId)
                .username(userId)
                .activeBusinessUnitId(activeBusinessUnitId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
