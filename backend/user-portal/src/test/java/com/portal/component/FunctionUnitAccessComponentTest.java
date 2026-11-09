package com.portal.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.common.dto.UserPrincipal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;

/**
 * Focused unit tests for the FU gate: code resolve, current workspace role, no SYS_ADMIN bypass.
 */
@ExtendWith(MockitoExtension.class)
class FunctionUnitAccessComponentTest {

    private static final String USER_ID = "user-view-allowed";
    private static final String FU_CODE = "fu-20260505-thwmut";
    private static final String CATALOG_ID = "ac21cde7-096a-4b17-969e-7c1a23319180";

    @Mock
    private RestTemplate restTemplate;

    private FunctionUnitAccessComponent component;

    @BeforeEach
    void setUp() {
        component = new FunctionUnitAccessComponent(restTemplate);
    }

    @AfterEach
    void clearSession() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void canAccessFunctionUnit_resolvesCodeBeforeAccessLookup() {
        mockResolveCodeToCatalogId(FU_CODE, CATALOG_ID);
        mockAccessRoles(CATALOG_ID, List.of("MANAGER"));
        mockPortalRoles(USER_ID, List.of(Map.of("id", "role-manager", "code", "MANAGER")));
        signIn(USER_ID, "role-manager", false);

        assertThat(component.canAccessFunctionUnit(USER_ID, FU_CODE)).isTrue();
    }

    @Test
    void canAccessFunctionUnit_deniesWhenAccessOnlyOnUnresolvedCode() {
        mockResolveCodeToCatalogId(FU_CODE, CATALOG_ID);
        mockAccessRoles(FU_CODE, List.of("MANAGER"));
        mockPortalRoles(USER_ID, List.of(Map.of("id", "role-manager", "code", "MANAGER")));

        assertThat(component.canAccessFunctionUnit(USER_ID, FU_CODE)).isFalse();
    }

    /**
     * D-05: no System Administrator shortcut — the access roles decide, and the admin-profile role
     * list is never even consulted.
     */
    @Test
    void systemAdministratorWithoutAnAccessRoleIsDenied() {
        signIn("user-view-admin", "role-clerk-id", true);
        mockResolveCodeToCatalogId(FU_CODE, CATALOG_ID);
        mockAccessRoles(CATALOG_ID, List.of("MANAGER"));
        mockPortalRoles("user-view-admin", List.of(Map.of("id", "role-clerk-id", "code", "CLERK")));

        assertThat(component.canAccessFunctionUnit("user-view-admin", FU_CODE)).isFalse();
        verify(restTemplate, never()).exchange(contains("profileContext=ADMIN"), eq(HttpMethod.GET), isNull(),
                any(ParameterizedTypeReference.class));
    }

    /** §7.2: launch / view access follows the current workspace role, not every role the user holds. */
    @Test
    void canAccessFunctionUnit_followsCurrentWorkspaceRoleOnly() {
        mockResolveCodeToCatalogId(FU_CODE, CATALOG_ID);
        mockAccessRoles(CATALOG_ID, List.of("ROLE_Y"));
        mockPortalRoles(USER_ID, List.of(
                Map.of("id", "role-x-id", "code", "ROLE_X"),
                Map.of("id", "role-y-id", "code", "ROLE_Y")));

        signIn(USER_ID, "role-x-id", false);
        assertThat(component.canAccessFunctionUnit(USER_ID, FU_CODE)).isFalse();

        signIn(USER_ID, "role-y-id", false);
        assertThat(component.canAccessFunctionUnit(USER_ID, FU_CODE)).isTrue();
    }

    /** §7.2: audit access follows the current workspace role too, System Administrator included. */
    @Test
    void canAuditFunctionUnit_followsCurrentWorkspaceRoleOnly() {
        mockResolveCodeToCatalogId(FU_CODE, CATALOG_ID);
        mockAuditRoles(CATALOG_ID, List.of("AUDITOR_ROLE"));
        mockPortalRoles(USER_ID, List.of(
                Map.of("id", "role-x-id", "code", "ROLE_X"),
                Map.of("id", "role-audit-id", "code", "AUDITOR_ROLE")));

        signIn(USER_ID, "role-x-id", true);
        assertThat(component.canAuditFunctionUnit(USER_ID, FU_CODE)).isFalse();

        signIn(USER_ID, "role-audit-id", true);
        assertThat(component.canAuditFunctionUnit(USER_ID, FU_CODE)).isTrue();
    }

    @Test
    void filterAccessibleFunctionUnits_withActiveRole_onlyReturnsThatRole() {
        mockPortalRoles(USER_ID, List.of(
                Map.of("id", "role-x-id", "code", "ROLE_X"),
                Map.of("id", "role-y-id", "code", "ROLE_Y")));
        mockAccessRoles("unit-x", List.of("ROLE_X"));
        mockAccessRoles("unit-y", List.of("ROLE_Y"));

        List<Map<String, Object>> filtered = component.filterAccessibleFunctionUnits(
                USER_ID,
                List.of(enabledUnit("unit-x"), enabledUnit("unit-y")),
                "role-x-id");

        assertThat(filtered).extracting(u -> u.get("id")).containsExactly("unit-x");
    }

    /** §7.2: outside permission self-service, no current role means no roles — never all of them combined. */
    @Test
    void filterAccessibleFunctionUnits_withoutActiveRoleOutsideSelfService_returnsEmpty() {
        signInSession(USER_ID, "FULL");

        assertThat(component.filterAccessibleFunctionUnits(USER_ID, List.of(enabledUnit("unit-x")), null)).isEmpty();
    }

    @Test
    void filterAccessibleFunctionUnits_selfServiceWithoutActiveRole_keepsAllHeldRoles() {
        signInSession(USER_ID, "PERMISSION_SELF_SERVICE_ONLY");
        mockPortalRoles(USER_ID, List.of(
                Map.of("id", "role-x-id", "code", "ROLE_X"),
                Map.of("id", "role-y-id", "code", "ROLE_Y")));
        mockAccessRoles("unit-x", List.of("ROLE_X"));
        mockAccessRoles("unit-y", List.of("ROLE_Y"));

        List<Map<String, Object>> filtered = component.filterAccessibleFunctionUnits(
                USER_ID,
                List.of(enabledUnit("unit-x"), enabledUnit("unit-y")),
                null);

        assertThat(filtered).extracting(u -> u.get("id")).containsExactly("unit-x", "unit-y");
    }

    @Test
    void filterAccessibleFunctionUnits_activeRoleNotHeld_returnsEmpty() {
        mockPortalRoles(USER_ID, List.of(Map.of("id", "role-x-id", "code", "ROLE_X")));

        List<Map<String, Object>> filtered = component.filterAccessibleFunctionUnits(
                USER_ID,
                List.of(enabledUnit("unit-x")),
                "role-other-id");

        assertThat(filtered).isEmpty();
    }

    @Test
    void resolveAlignedWithActiveCatalog_invalidatesStaleProcessKeyCache() {
        String encoded = java.net.URLEncoder.encode(FU_CODE, StandardCharsets.UTF_8);
        String oldId = "4687c5b3-6d06-4fae-be02-449925ae014c";
        String newId = "ce946f29-09b8-497e-ba85-839ab45fa663";
        when(restTemplate.exchange(
                contains("/function-units/by-process-key/" + encoded),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of("id", oldId)))
                .thenReturn(ResponseEntity.ok(Map.of("id", newId)));

        assertThat(component.resolveFunctionUnitId(FU_CODE)).isEqualTo(oldId);
        assertThat(component.resolveFunctionUnitIdAlignedWithActiveCatalog(FU_CODE, newId))
                .isEqualTo(newId);
    }

    @Test
    void resolveAlignedWithActiveCatalog_usesEnabledPinWhenLatestIsNewerDisabled() {
        String encoded = java.net.URLEncoder.encode(FU_CODE, StandardCharsets.UTF_8);
        String enabledId = "d64105f6-3ff8-411e-b162-6e33056b02bd";
        String newerDisabledId = "d3c59f31-fae0-4b01-ad75-27b881548601";
        when(restTemplate.exchange(
                contains("/function-units/by-process-key/" + encoded),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("not found"));
        when(restTemplate.exchange(
                contains("/function-units/code/" + encoded + "/latest"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of("id", newerDisabledId)));

        assertThat(component.resolveFunctionUnitId(FU_CODE)).isEqualTo(newerDisabledId);
        assertThat(component.resolveFunctionUnitIdAlignedWithActiveCatalog(FU_CODE, enabledId))
                .isEqualTo(enabledId);
        assertThat(component.resolveFunctionUnitId(FU_CODE)).isEqualTo(enabledId);
    }

    @Test
    void resolveNewRequestRoleKeys_includesIdAndCodeForActiveRole() {
        mockPortalRoles(USER_ID, List.of(
                Map.of("id", "role-x-id", "code", "ROLE_X"),
                Map.of("id", "role-y-id", "code", "ROLE_Y")));

        assertThat(component.resolveNewRequestRoleKeys(USER_ID, "role-x-id"))
                .containsExactlyInAnyOrder("role-x-id", "ROLE_X");
    }

    private static Map<String, Object> enabledUnit(String id) {
        Map<String, Object> unit = new HashMap<>();
        unit.put("id", id);
        unit.put("name", id);
        unit.put("enabled", true);
        return unit;
    }

    @SuppressWarnings("unchecked")
    private void mockResolveCodeToCatalogId(String code, String catalogId) {
        String encoded = java.net.URLEncoder.encode(code, StandardCharsets.UTF_8);
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", catalogId);

        when(restTemplate.exchange(
                contains("/function-units/by-process-key/" + encoded),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("not found"));

        when(restTemplate.exchange(
                contains("/function-units/code/" + encoded + "/latest"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(payload));
    }

    @SuppressWarnings("unchecked")
    private void mockAccessRoles(String functionUnitId, List<String> roleCodes) {
        List<Map<String, Object>> accessList = roleCodes.stream()
                .map(code -> Map.<String, Object>of(
                        "targetType", "ROLE",
                        "targetCode", code,
                        "targetId", "role-" + code.toLowerCase()))
                .toList();

        when(restTemplate.exchange(
                contains("/function-units/" + functionUnitId + "/access"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(accessList));
    }

    @SuppressWarnings("unchecked")
    private void mockAuditRoles(String functionUnitId, List<String> roleCodes) {
        List<Map<String, Object>> accessList = roleCodes.stream()
                .map(code -> Map.<String, Object>of(
                        "targetType", "ROLE",
                        "targetCode", code,
                        "targetId", "role-" + code.toLowerCase()))
                .toList();

        when(restTemplate.exchange(
                contains("/function-units/" + functionUnitId + "/audit-access"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(accessList));
    }

    private static void signIn(String userId, String activeRoleId, boolean systemAdministrator) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UserPrincipal.builder().userId(userId).username(userId)
                        .roles(systemAdministrator ? List.of("SYS_ADMIN") : List.of())
                        .superAdmin(systemAdministrator)
                        .activeBusinessUnitId("bu-1").activeRoleId(activeRoleId).build(),
                null, List.of()));
    }

    private static void signInSession(String userId, String portalAccessMode) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UserPrincipal.builder().userId(userId).username(userId).portalAccessMode(portalAccessMode).build(),
                null, List.of()));
    }

    @SuppressWarnings("unchecked")
    private void mockPortalRoles(String userId, List<Map<String, Object>> roles) {
        mockProfileRoles(userId, "PORTAL", roles);
    }

    @SuppressWarnings("unchecked")
    private void mockProfileRoles(String userId, String profile, List<Map<String, Object>> roles) {
        when(restTemplate.exchange(
                contains("/users/" + userId + "/roles?profileContext=" + profile),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(roles));
    }
}
