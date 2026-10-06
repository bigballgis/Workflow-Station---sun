package com.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.common.dto.UserPrincipal;
import com.platform.common.i18n.I18nService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlaPolicyAccessInterceptorTest {

    private SlaPolicyAccessInterceptor interceptor;

    @BeforeEach
    void setUp() {
        I18nService i18nService = mock(I18nService.class);
        when(i18nService.getMessage("auth.no_permission")).thenReturn("No permission");
        interceptor = new SlaPolicyAccessInterceptor(i18nService, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditorWithViewCanReadButNotWrite() throws Exception {
        authenticate(List.of("AUDITOR"), List.of("sla:policy:view"));
        assertTrue(preHandle("POST", "/sla-policies/query", new MockHttpServletResponse()));
        assertTrue(preHandle("GET", "/sla-policies/FU1/history", new MockHttpServletResponse()));
        assertForbidden("PUT", "/sla-policies/FU1");
        assertForbidden("POST", "/sla-policies/FU1/recalculate");
    }

    @Test
    void auditorIsDeniedWritesEvenWithEditPermission() throws Exception {
        authenticate(List.of("AUDITOR"), List.of("sla:policy:edit"));
        assertForbidden("PUT", "/sla-policies/FU1");
    }

    @Test
    void userWithoutSlaPermissionCannotRead() throws Exception {
        authenticate(List.of("OPS"), List.of("system:config"));
        assertForbidden("POST", "/sla-policies/query");
        assertForbidden("GET", "/sla-policies/FU1/jobs");
    }

    @Test
    void editPermissionCanReadAndWrite() throws Exception {
        authenticate(List.of("OPS"), List.of("sla:policy:edit"));
        assertTrue(preHandle("GET", "/sla-policies/FU1/jobs", new MockHttpServletResponse()));
        assertTrue(preHandle("PUT", "/sla-policies/FU1", new MockHttpServletResponse()));
        assertTrue(preHandle("POST", "/sla-policies/FU1/recalculate", new MockHttpServletResponse()));
    }

    @Test
    void sysAdminBypasses() throws Exception {
        authenticate(List.of("SYS_ADMIN"), List.of());
        assertTrue(preHandle("PUT", "/sla-policies/FU1", new MockHttpServletResponse()));
    }

    @Test
    void unauthenticatedIsForbidden() throws Exception {
        assertForbidden("POST", "/sla-policies/query");
    }

    private void assertForbidden(String method, String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(preHandle(method, path, response), method + " " + path);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertTrue(response.getContentAsString().contains("PERM_ACCESS_DENIED"));
    }

    private boolean preHandle(String method, String path, MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return interceptor.preHandle(request, response, new Object());
    }

    private void authenticate(List<String> roles, List<String> permissions) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId("u-1")
                .username("tester")
                .roles(roles)
                .permissions(permissions)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", List.of()));
    }
}
