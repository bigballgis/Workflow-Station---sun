package com.portal.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The permission view says what the user holds, or that it cannot be read; never "nothing" on a failure. */
class UserPermissionControllerTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final UserPermissionController controller = new UserPermissionController(restTemplate);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "adminCenterUrl", "http://admin");
    }

    @Test
    void theUsersBusinessUnitRolesAreListed() {
        answer("/business-units?profileContext=PORTAL", List.of(Map.of("id", "bu-1", "name", "Finance")));
        answer("/business-unit-roles", List.of(Map.of("roleId", "r-1", "roleName", "Clerk", "roleCode", "CLERK",
                "businessUnitId", "bu-1", "businessUnitName", "Finance")));

        var response = controller.getMyPermissions("u-1");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> data = response.getBody().getData();
        assertThat(data.get("businessUnits")).isEqualTo(List.of(Map.of("id", "bu-1", "name", "Finance")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roles = (List<Map<String, Object>>) data.get("buBoundedRoles");
        assertThat(roles).singleElement().satisfies(item -> assertThat(item.get("role"))
                .isEqualTo(Map.of("id", "r-1", "name", "Clerk", "code", "CLERK", "type", "BU_BOUNDED")));
    }

    @Test
    void anAdminCenterFailureIsA503NotAnEmptySummary() {
        answer("/business-units?profileContext=PORTAL", List.of(Map.of("id", "bu-1", "name", "Finance")));
        when(restTemplate.exchange(endsWith("/business-unit-roles"), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class))).thenThrow(new ResourceAccessException("connection refused"));

        var response = controller.getMyPermissions("u-1");

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getData()).isNull();
    }

    @SuppressWarnings("unchecked")
    private void answer(String pathUnderUser, List<Map<String, Object>> body) {
        when(restTemplate.exchange(eq("http://admin/api/v1/admin/users/u-1" + pathUnderUser), eq(HttpMethod.GET),
                any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(body));
    }
}
