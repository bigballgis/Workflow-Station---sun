package com.portal.client;

import com.platform.security.filter.JwtAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The engine receives exactly the token the portal's JwtAuthenticationFilter accepted for this request. */
class WorkflowEngineClientTest {

    private final WorkflowEngineClient client = new WorkflowEngineClient(
            new RestTemplate(), org.mockito.Mockito.mock(com.platform.common.i18n.I18nService.class));

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void authorizedGetEntityForwardsTheAuthenticatedToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthenticationFilter.AUTHENTICATED_TOKEN_ATTRIBUTE, "accepted-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        HttpEntity<Void> entity = ReflectionTestUtils.invokeMethod(client, "authorizedGetEntity");

        assertEquals("Bearer accepted-token", entity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void presentButUnacceptedCookieIsNotForwarded() {
        // e.g. a service call admitted by X-Service-Token while carrying an expired cookie.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("up_access_token", "expired-token"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        HttpEntity<Void> entity = ReflectionTestUtils.invokeMethod(client, "authorizedGetEntity");

        assertNull(entity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }
}
