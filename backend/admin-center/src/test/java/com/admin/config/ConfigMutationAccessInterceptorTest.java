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

class ConfigMutationAccessInterceptorTest {

    private ConfigMutationAccessInterceptor interceptor;

    @BeforeEach
    void setUp() {
        I18nService i18nService = mock(I18nService.class);
        when(i18nService.getMessage("auth.no_permission")).thenReturn("No permission");
        interceptor = new ConfigMutationAccessInterceptor(
                i18nService, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditorCanRead() throws Exception {
        authenticate(List.of("AUDITOR"), List.of("audit:read", "log:read", "user:read"));
        assertTrue(preHandle("GET", "/configs/some.key", new MockHttpServletResponse()));
    }

    @Test
    void auditorWritesAreForbidden() throws Exception {
        authenticate(List.of("AUDITOR"), List.of("audit:read", "log:read", "user:read"));
        for (String[] call : new String[][] {
                {"POST", "/configs"},
                {"PUT", "/configs/some.key"},
                {"DELETE", "/configs/some.key"},
                {"POST", "/configs/some.key/rollback/2"},
                {"POST", "/configs/sync/dev/prod"}}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertFalse(preHandle(call[0], call[1], response), call[0] + " " + call[1]);
            assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
            assertTrue(response.getContentAsString().contains("PERM_ACCESS_DENIED"));
        }
    }

    @Test
    void unauthenticatedWriteIsForbidden() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(preHandle("PUT", "/configs/some.key", response));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    }

    @Test
    void sysAdminCanWrite() throws Exception {
        authenticate(List.of("SYS_ADMIN"), List.of());
        assertTrue(preHandle("PUT", "/configs/some.key", new MockHttpServletResponse()));
    }

    @Test
    void systemConfigPermissionCanWrite() throws Exception {
        authenticate(List.of("OPS"), List.of("system:config"));
        assertTrue(preHandle("DELETE", "/configs/some.key", new MockHttpServletResponse()));
    }

    @Test
    void systemAdminPermissionCanWrite() throws Exception {
        authenticate(List.of("OPS"), List.of("system:admin"));
        assertTrue(preHandle("POST", "/configs", new MockHttpServletResponse()));
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
