package com.platform.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constant.PlatformConstants;
import com.platform.common.dto.UserPrincipal;
import com.platform.security.config.JwtProperties;
import com.platform.security.service.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A token that fails validation must surface as 401 so the frontends renew the session. */
class JwtAuthenticationFilterTest {

    private static final String SERVICE_TOKEN = "svc-secret";

    private final JwtTokenService tokens = mock(JwtTokenService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            tokens, new JwtProperties(), new ObjectMapper().findAndRegisterModules(), SERVICE_TOKEN);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void expiredTokenIsAnswered401WithoutReachingTheApplication() throws Exception {
        when(tokens.validateToken("expired")).thenReturn(false);
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer expired");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_TOKEN_INVALID");
        verify(chain, never()).doFilter(any(), any());
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_TOKEN_ATTRIBUTE)).isNull();
    }

    @Test
    void tokenThatFailsToParseIsAnswered401() throws Exception {
        when(tokens.validateToken("garbage")).thenThrow(new IllegalArgumentException("malformed"));
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer garbage");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void serviceCallWithAnExpiredForwardedTokenContinuesUnauthenticated() throws Exception {
        when(tokens.validateToken("expired")).thenReturn(false);
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer expired");
        request.addHeader(PlatformConstants.HEADER_SERVICE_TOKEN, SERVICE_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_TOKEN_ATTRIBUTE)).isNull();
    }

    @Test
    void wrongServiceTokenDoesNotExemptAnExpiredToken() throws Exception {
        when(tokens.validateToken("expired")).thenReturn(false);
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer expired");
        request.addHeader(PlatformConstants.HEADER_SERVICE_TOKEN, "guess");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void unconfiguredServiceTokenExemptsNobody() throws Exception {
        JwtAuthenticationFilter unconfigured = new JwtAuthenticationFilter(
                tokens, new JwtProperties(), new ObjectMapper().findAndRegisterModules(), "");
        when(tokens.validateToken("expired")).thenReturn(false);
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer expired");
        request.addHeader(PlatformConstants.HEADER_SERVICE_TOKEN, "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        unconfigured.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void expiredCookieOfAnotherAppIsSkippedForTheCallersValidCookie() throws Exception {
        // A DW page calling Admin: an old ac_access_token lingers next to the valid dw_access_token.
        JwtProperties props = new JwtProperties();
        props.setCookieNames(List.of("ac_access_token", "dw_access_token"));
        JwtAuthenticationFilter adminFilter = new JwtAuthenticationFilter(
                tokens, props, new ObjectMapper().findAndRegisterModules(), SERVICE_TOKEN);
        when(tokens.validateToken("expired-admin")).thenReturn(false);
        when(tokens.validateToken("valid-dw")).thenReturn(true);
        when(tokens.extractUserPrincipal("valid-dw")).thenReturn(UserPrincipal.builder().userId("u-1").build());
        MockHttpServletRequest request = request();
        request.setCookies(new Cookie("ac_access_token", "expired-admin"), new Cookie("dw_access_token", "valid-dw"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        adminFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(((UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUserId())
                .isEqualTo("u-1");
        // Services that forward the caller downstream must send the accepted token, not the first present one.
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_TOKEN_ATTRIBUTE)).isEqualTo("valid-dw");
    }

    @Test
    void allPresentedCookiesExpiredIsAnswered401() throws Exception {
        JwtProperties props = new JwtProperties();
        props.setCookieNames(List.of("ac_access_token", "dw_access_token"));
        JwtAuthenticationFilter adminFilter = new JwtAuthenticationFilter(
                tokens, props, new ObjectMapper().findAndRegisterModules(), SERVICE_TOKEN);
        when(tokens.validateToken("expired-admin")).thenReturn(false);
        when(tokens.validateToken("expired-dw")).thenReturn(false);
        MockHttpServletRequest request = request();
        request.setCookies(new Cookie("ac_access_token", "expired-admin"), new Cookie("dw_access_token", "expired-dw"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        adminFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void requestWithoutTokenIsLeftToTheService() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/business-units");
        request.setServletPath("/business-units");
        return request;
    }
}
