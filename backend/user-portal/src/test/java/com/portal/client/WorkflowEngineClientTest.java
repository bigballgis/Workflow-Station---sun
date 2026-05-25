package com.portal.client;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowEngineClientTest {

    private final WorkflowEngineClient client = new WorkflowEngineClient(new RestTemplate());

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void authorizedGetEntityForwardsAuthorizationHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer header-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        HttpEntity<Void> entity = ReflectionTestUtils.invokeMethod(client, "authorizedGetEntity");

        assertEquals("Bearer header-token", entity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void authorizedGetEntityFallsBackToAccessTokenCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "cookie-token"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        HttpEntity<Void> entity = ReflectionTestUtils.invokeMethod(client, "authorizedGetEntity");

        assertEquals("Bearer cookie-token", entity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }
}
