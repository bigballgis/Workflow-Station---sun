package com.portal.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Focused unit tests for View-access-related FU gate behavior (code resolve + SYS_ADMIN).
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

    @Test
    void canAccessFunctionUnit_resolvesCodeBeforeAccessLookup() {
        mockResolveCodeToCatalogId(FU_CODE, CATALOG_ID);
        mockAccessRoles(CATALOG_ID, List.of("MANAGER"));
        mockPortalRoles(USER_ID, List.of(Map.of("id", "role-manager", "code", "MANAGER")));

        assertThat(component.canAccessFunctionUnit(USER_ID, FU_CODE)).isTrue();
    }

    @Test
    void canAccessFunctionUnit_deniesWhenAccessOnlyOnUnresolvedCode() {
        mockResolveCodeToCatalogId(FU_CODE, CATALOG_ID);
        mockAccessRoles(FU_CODE, List.of("MANAGER"));
        mockPortalRoles(USER_ID, List.of(Map.of("id", "role-manager", "code", "MANAGER")));

        assertThat(component.canAccessFunctionUnit(USER_ID, FU_CODE)).isFalse();
    }

    @Test
    void isSystemAdministrator_usesAdminProfileNotPortal() {
        mockProfileRoles(USER_ID, "ADMIN", List.of(Map.of("code", "SYS_ADMIN")));

        assertThat(component.isSystemAdministrator(USER_ID)).isTrue();
    }

    @Test
    void isSystemAdministrator_falseWhenOnlyPortalBusinessRoles() {
        mockProfileRoles(USER_ID, "PORTAL", List.of(Map.of("code", "MANAGER")));

        assertThat(component.isSystemAdministrator(USER_ID)).isFalse();
    }

    @Test
    void sysAdminBypassesFunctionUnitAccessWithoutRoleIntersection() {
        mockProfileRoles("user-view-admin", "ADMIN", List.of(Map.of("code", "SYS_ADMIN")));

        assertThat(component.canAccessFunctionUnit("user-view-admin", FU_CODE)).isTrue();
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
