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

class OrganizationMutationAccessInterceptorTest {

    private OrganizationMutationAccessInterceptor interceptor;
    private I18nService i18nService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        i18nService = mock(I18nService.class);
        when(i18nService.getMessage("auth.no_permission")).thenReturn("No permission");
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        interceptor = new OrganizationMutationAccessInterceptor(i18nService, objectMapper, "");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getIsAlwaysAllowed() throws Exception {
        authenticate(List.of("AUDITOR"), List.of());
        assertTrue(preHandle("GET", "/business-units"));
    }

    @Test
    void auditorWriteToOrganizationIsForbidden() throws Exception {
        authenticate(List.of("AUDITOR"), List.of("user:read"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(preHandle("POST", "/business-units", response));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"success\":false"));
        assertTrue(body.contains("PERM_ACCESS_DENIED"));
        assertTrue(body.contains("No permission"));
    }

    @Test
    void auditorExitIsForbidden() throws Exception {
        authenticate(List.of("AUDITOR"), List.of());
        assertFalse(preHandle("POST", "/exit/user-1"));
    }

    @Test
    void sysAdminBypassesAuditorDeny() throws Exception {
        authenticate(List.of("AUDITOR", "SYS_ADMIN"), List.of());
        assertTrue(preHandle("POST", "/virtual-groups"));
    }

    @Test
    void writerWithUserWriteIsAllowed() throws Exception {
        authenticate(List.of("SOME_ROLE"), List.of("user:write"));
        assertTrue(preHandle("PUT", "/approvers/1"));
    }

    @Test
    void claimPathIsExcluded() throws Exception {
        authenticate(List.of("AUDITOR"), List.of());
        assertTrue(preHandle("POST", "/virtual-groups/vg-1/tasks/t-1/claim"));
    }

    @Test
    void trustedServiceTokenAllowsOrgMutationWithoutAdminRoles() throws Exception {
        interceptor = new OrganizationMutationAccessInterceptor(i18nService, objectMapper, "svc-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/business-units/bu-1/members");
        request.setServletPath("/business-units/bu-1/members");
        request.addHeader(com.platform.common.constant.PlatformConstants.HEADER_SERVICE_TOKEN, "svc-secret");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void wrongServiceTokenDoesNotBypassGuard() throws Exception {
        interceptor = new OrganizationMutationAccessInterceptor(i18nService, objectMapper, "svc-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/business-units/bu-1/members");
        request.setServletPath("/business-units/bu-1/members");
        request.addHeader(com.platform.common.constant.PlatformConstants.HEADER_SERVICE_TOKEN, "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    }

    private boolean preHandle(String method, String path) throws Exception {
        return preHandle(method, path, new MockHttpServletResponse());
    }

    private boolean preHandle(String method, String path, MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return interceptor.preHandle(request, response, new Object());
    }

    private void authenticate(List<String> roles, List<String> permissions) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId("u-1")
                .username("auditor")
                .roles(roles)
                .permissions(permissions)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", List.of()));
    }
}
