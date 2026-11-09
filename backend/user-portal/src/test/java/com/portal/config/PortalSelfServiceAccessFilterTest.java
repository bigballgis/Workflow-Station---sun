package com.portal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.UserPrincipal;
import com.platform.common.i18n.I18nService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Only sessions Portal issued reach Portal (platform access model §7.1): a Developer Workstation or Admin Center token
 * carries no {@code portalAccessMode}, so Portal entry and the current BU + Role were never checked for it.
 */
class PortalSelfServiceAccessFilterTest {

    private final I18nService i18n = mock(I18nService.class);
    private final PortalSelfServiceAccessFilter filter = new PortalSelfServiceAccessFilter(new ObjectMapper(), i18n);
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void signedIn(String accessMode) {
        UserPrincipal principal = UserPrincipal.builder().userId("u1").username("u1").portalAccessMode(accessMode).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private MockHttpServletResponse call(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void aTokenFromAnotherAppIsRefused() throws Exception {
        when(i18n.getMessage(anyString())).thenReturn("Not logged in");
        signedIn(null);

        MockHttpServletResponse response = call("/api/portal/processes/definitions");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_PORTAL_SESSION_REQUIRED");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void aFullPortalSessionGoesThrough() throws Exception {
        signedIn("FULL");

        MockHttpServletResponse response = call("/api/portal/processes/definitions");

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void aSelfServiceSessionStaysOnItsWhitelist() throws Exception {
        when(i18n.getMessage(anyString())).thenReturn("Permission self-service only");
        signedIn("PERMISSION_SELF_SERVICE_ONLY");

        assertThat(call("/api/portal/tasks/todo/query").getStatus()).isEqualTo(403);
        assertThat(call("/api/portal/permissions/my").getStatus()).isEqualTo(200);
    }

    @Test
    void anonymousRequestsAreLeftToTheHandlers() throws Exception {
        MockHttpServletResponse response = call("/api/portal/health/live");

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }
}
