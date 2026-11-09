package com.admin.security;

import com.admin.config.InternalServiceAuthenticator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constant.PlatformConstants;
import com.platform.common.dto.UserPrincipal;
import com.platform.common.i18n.I18nService;
import com.platform.security.service.UserRoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Decision matrix of the default-deny Admin authorization (platform access model §8.1, WP9). */
class AdminAccessInterceptorTest {

    private static final String SERVICE_TOKEN = "svc-secret";

    /** Endpoints with every kind of declaration; the package makes it an Admin handler. */
    @AdminAccess(AdminAudience.ADMIN_READ)
    static class Fixture {
        public void classRead() { }
        public void undeclaredWrite() { }
        @AdminAccess(AdminAudience.PUBLIC) public void publicEndpoint() { }
        @AdminAccess(AdminAudience.SERVICE) public void serviceOnly() { }
        @AdminAccess(AdminAudience.SIGNED_IN) public void signedIn() { }
        @AdminAccess(AdminAudience.WORKSTATION) public void workstation() { }
        @AdminAccess(AdminAudience.DEPLOYER) public void deploy() { }
        @AdminAccess(value = AdminAudience.SYS_ADMIN, services = true) public void approvalWrite() { }
        @AdminAccess(AdminAudience.SHARED_READ) public void sharedRead() { }
        @AdminAccess(AdminAudience.TEAM_READ) public void teamList() { }
        @AdminAccess(AdminAudience.LEAD_WRITE) public void leadWrite() { }
        @AdminAccess(value = AdminAudience.TEAM_READ, owner = AdminOwner.FUNCTION_UNIT_ID, ownerVariable = "id")
        public void teamReadOne() { }
        @AdminAccess(value = AdminAudience.TEAM_WRITE, owner = AdminOwner.FUNCTION_UNIT_ID, ownerVariable = "id")
        public void teamWrite() { }
        @AdminAccess(value = AdminAudience.TEAM_READ, services = true, owner = AdminOwner.FUNCTION_UNIT_ID,
                ownerVariable = "id")
        public void teamReadAlsoForServices() { }
    }

    private final UserRoleService userRoleService = mock(UserRoleService.class);
    private final AdminTeamScope teamScope = mock(AdminTeamScope.class);
    private AdminAccessInterceptor interceptor;

    @BeforeEach
    void setUp() {
        I18nService i18n = mock(I18nService.class);
        when(i18n.getMessage("auth.unauthorized")).thenReturn("Not logged in");
        when(i18n.getMessage("auth.no_permission")).thenReturn("No permission");
        when(teamScope.refusal(any(), any(), any(), anyBoolean())).thenReturn(Optional.empty());
        interceptor = new AdminAccessInterceptor(new InternalServiceAuthenticator(SERVICE_TOKEN), userRoleService,
                teamScope, i18n, new ObjectMapper().findAndRegisterModules());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicEndpointNeedsNoIdentity() throws Exception {
        assertThat(call("POST", "publicEndpoint").getStatus()).isEqualTo(200);
    }

    @Test
    void anonymousCallerGets401SoTheFrontendRenews() throws Exception {
        MockHttpServletResponse response = call("GET", "classRead");
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_FAILED");
    }

    @Test
    void auditorReadsButCannotWrite() throws Exception {
        signIn("AUDITOR");
        assertThat(call("GET", "classRead").getStatus()).isEqualTo(200);
        MockHttpServletResponse write = call("POST", "undeclaredWrite");
        assertThat(write.getStatus()).isEqualTo(403);
        assertThat(write.getContentAsString()).contains("PERM_ACCESS_DENIED");
    }

    @Test
    void classPolicyNeverWidensAWriteSysAdminStillWrites() throws Exception {
        signIn("SYS_ADMIN");
        assertThat(call("POST", "undeclaredWrite").getStatus()).isEqualTo(200);
        signIn("AUDITOR", "DEVELOPER", "TECH_LEAD");
        assertThat(call("POST", "undeclaredWrite").getStatus()).isEqualTo(403);
    }

    @Test
    void businessRoleIsNotAnAdminReader() throws Exception {
        signIn("MANAGER");
        assertThat(call("GET", "classRead").getStatus()).isEqualTo(403);
        assertThat(call("GET", "workstation").getStatus()).isEqualTo(403);
        assertThat(call("GET", "signedIn").getStatus()).isEqualTo(200);
    }

    @Test
    void developerUsesWorkstationLookupsButNotAdminReads() throws Exception {
        signIn("DEVELOPER");
        assertThat(call("GET", "workstation").getStatus()).isEqualTo(200);
        assertThat(call("GET", "classRead").getStatus()).isEqualTo(403);
    }

    /** Role part of the WP9 audiences: who gets past the role check (team scope is decided after it). */
    @Test
    void wp9AudienceMatrix() throws Exception {
        Map<String, List<String>> identities = new LinkedHashMap<>();
        identities.put("SYS_ADMIN", List.of("SYS_ADMIN"));
        identities.put("AUDITOR", List.of("AUDITOR"));
        identities.put("TECH_LEAD", List.of("TECH_LEAD"));
        identities.put("AUDITOR+TECH_LEAD", List.of("AUDITOR", "TECH_LEAD"));
        identities.put("DEVELOPER", List.of("DEVELOPER"));
        //                                     sharedRead teamList leadWrite teamWrite adminRead
        Map<String, int[]> expected = Map.of(
                "SYS_ADMIN", new int[]{200, 200, 200, 200, 200},
                "AUDITOR", new int[]{200, 200, 403, 403, 200},
                "TECH_LEAD", new int[]{200, 200, 200, 200, 403},
                "AUDITOR+TECH_LEAD", new int[]{200, 200, 200, 200, 200},
                "DEVELOPER", new int[]{403, 403, 403, 403, 403});
        identities.forEach((name, roles) -> {
            signIn(roles.toArray(String[]::new));
            int[] statuses = new int[]{
                    status("GET", "sharedRead"), status("POST", "teamList"), status("POST", "leadWrite"),
                    status("PUT", "teamWrite", pathId("fu-a")), status("GET", "classRead")};
            assertThat(statuses).as(name).containsExactly(expected.get(name));
        });
        SecurityContextHolder.clearContext();
        assertThat(status("GET", "sharedRead")).isEqualTo(401);
        assertThat(status("PUT", "teamWrite", serviceToken(SERVICE_TOKEN))).as("no service write by token")
                .isEqualTo(403);
    }

    @Test
    void theAdmittedCallerIsStoredForTheHandler() throws Exception {
        signIn("AUDITOR", "TECH_LEAD");
        MockHttpServletRequest request = request("GET", "sharedRead");
        interceptor.preHandle(request, new MockHttpServletResponse(), handler("sharedRead"));

        AdminCaller caller = AdminCaller.of(request);
        assertThat(caller.service()).isFalse();
        assertThat(caller.userId()).isEqualTo("u-1");
        assertThat(caller.readsAllTeams()).isTrue();
        assertThat(caller.writesAllTeams()).isFalse();

        MockHttpServletRequest serviceRequest = request("GET", "teamReadAlsoForServices", serviceToken(SERVICE_TOKEN));
        interceptor.preHandle(serviceRequest, new MockHttpServletResponse(), handler("teamReadAlsoForServices"));
        assertThat(AdminCaller.of(serviceRequest).service()).isTrue();
    }

    @Test
    void aTeamResourceOfAnotherTeamIsRefusedWithTheTeamNamed() throws Exception {
        signIn("TECH_LEAD");
        when(teamScope.refusal(any(), eq(AdminOwner.FUNCTION_UNIT_ID), eq("fu-b"), eq(true)))
                .thenReturn(Optional.of("This belongs to team Team B (TEAM_B)"));

        MockHttpServletResponse refused = call("PUT", "teamWrite", pathId("fu-b"));
        assertThat(refused.getStatus()).isEqualTo(403);
        assertThat(refused.getContentAsString()).contains("PERM_ACCESS_DENIED").contains("Team B (TEAM_B)");
        assertThat(call("PUT", "teamWrite", pathId("fu-a")).getStatus()).isEqualTo(200);
    }

    @Test
    void readsAndWritesAskTheScopeForTheirOwnKind() throws Exception {
        signIn("TECH_LEAD");
        call("GET", "teamReadOne", pathId("fu-a"));
        verify(teamScope).refusal(any(), eq(AdminOwner.FUNCTION_UNIT_ID), eq("fu-a"), eq(false));
        call("PUT", "teamWrite", pathId("fu-a"));
        verify(teamScope).refusal(any(), eq(AdminOwner.FUNCTION_UNIT_ID), eq("fu-a"), eq(true));
    }

    @Test
    void aServiceCallIsNeverTeamFiltered() throws Exception {
        assertThat(call("GET", "teamReadAlsoForServices", serviceToken(SERVICE_TOKEN), pathId("fu-b")).getStatus())
                .isEqualTo(200);
        verify(teamScope, never()).refusal(any(), any(), any(), anyBoolean());
    }

    @Test
    void endpointsWithoutAnOwnerAreNotCheckedHere() throws Exception {
        signIn("TECH_LEAD");
        call("POST", "teamList");
        call("POST", "leadWrite");
        verify(teamScope, never()).refusal(any(), any(), any(), anyBoolean());
    }

    @Test
    void serviceOnlyEndpointRefusesEveryUserRole() throws Exception {
        signIn("SYS_ADMIN");
        assertThat(call("GET", "serviceOnly").getStatus()).isEqualTo(403);
        SecurityContextHolder.clearContext();
        assertThat(call("GET", "serviceOnly").getStatus()).isEqualTo(401);
        assertThat(call("GET", "serviceOnly", serviceToken(SERVICE_TOKEN)).getStatus()).isEqualTo(200);
        assertThat(call("GET", "serviceOnly", serviceToken("guess")).getStatus()).isEqualTo(401);
    }

    @Test
    void listedServiceCallPassesWithoutUserRoles() throws Exception {
        assertThat(call("DELETE", "approvalWrite", serviceToken(SERVICE_TOKEN)).getStatus()).isEqualTo(200);
        verify(userRoleService, never()).getEffectiveRoleCodesForUser(anyString());
        assertThat(call("DELETE", "approvalWrite", serviceToken("guess")).getStatus()).isEqualTo(401);
    }

    @Test
    void serviceCallActingForAUserIsJudgedByTheUsersDatabaseRoles() throws Exception {
        // The forwarded access token says SYS_ADMIN, but the user is only a developer now.
        signIn("SYS_ADMIN");
        when(userRoleService.getEffectiveRoleCodesForUser("u-dev")).thenReturn(List.of("DEVELOPER"));
        when(userRoleService.getEffectiveRoleCodesForUser("u-biz")).thenReturn(List.of("MANAGER"));

        assertThat(call("POST", "deploy", serviceToken(SERVICE_TOKEN), userId("u-dev")).getStatus()).isEqualTo(200);
        assertThat(call("POST", "deploy", serviceToken(SERVICE_TOKEN), userId("u-biz")).getStatus()).isEqualTo(403);
        assertThat(call("POST", "undeclaredWrite", serviceToken(SERVICE_TOKEN), userId("u-dev")).getStatus())
                .isEqualTo(403);
    }

    @Test
    void serviceCallWithoutDelegatedUserCannotUseUserEndpoints() throws Exception {
        assertThat(call("POST", "deploy", serviceToken(SERVICE_TOKEN)).getStatus()).isEqualTo(403);
        assertThat(call("POST", "deploy", serviceToken(SERVICE_TOKEN), userId("system")).getStatus())
                .isEqualTo(403);
    }

    @Test
    void handlersOutsideAdminCenterAreLeftAlone() throws Exception {
        HandlerMethod springHandler = new HandlerMethod(new Object(), Object.class.getMethod("toString"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(new MockHttpServletRequest("GET", "/error"), response, springHandler))
                .isTrue();
    }

    private interface Header {
        void apply(MockHttpServletRequest request);
    }

    private static Header serviceToken(String value) {
        return request -> request.addHeader(PlatformConstants.HEADER_SERVICE_TOKEN, value);
    }

    private static Header userId(String value) {
        return request -> request.addHeader(PlatformConstants.HEADER_USER_ID, value);
    }

    private static Header pathId(String value) {
        return request -> request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", value));
    }

    private int status(String httpMethod, String handler, Header... headers) {
        try {
            return call(httpMethod, handler, headers).getStatus();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MockHttpServletResponse call(String httpMethod, String handler, Header... headers) throws Exception {
        MockHttpServletRequest request = request(httpMethod, handler, headers);
        MockHttpServletResponse response = new MockHttpServletResponse();
        if (!interceptor.preHandle(request, response, handler(handler))) {
            assertThat(response.getStatus()).isIn(401, 403);
        }
        return response;
    }

    private static MockHttpServletRequest request(String httpMethod, String handler, Header... headers) {
        MockHttpServletRequest request = new MockHttpServletRequest(httpMethod, "/x/" + handler);
        request.setServletPath("/x/" + handler);
        for (Header header : headers) {
            header.apply(request);
        }
        return request;
    }

    private static HandlerMethod handler(String name) throws NoSuchMethodException {
        return new HandlerMethod(new Fixture(), Fixture.class.getMethod(name));
    }

    private static void signIn(String... roles) {
        UserPrincipal principal = UserPrincipal.builder().userId("u-1").username("u1").roles(List.of(roles)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
