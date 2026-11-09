package com.portal.controller;

import com.platform.common.util.SafeUrlInput;
import com.platform.security.util.SecurityContextUtils;
import com.platform.common.dto.ApiResponse;
import com.portal.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * User Permission Controller
 * Provides the user's permission view (profile page and user menu)
 */
@Slf4j
@RestController
@RequestMapping("/my-permissions")
@RequiredArgsConstructor
@Tag(name = "User Permissions", description = "User permission view")
public class UserPermissionController {

    private final RestTemplate restTemplate;

    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    /**
     * C-3 (docs/ap-integration/DECISIONS.md#d6): shared secret marking this as a trusted
     * first-party service call, required for admin-center to honor the forwarded X-User-Id.
     */
    @Value("${service.internal-token:}")
    private String serviceInternalToken;

    /**
     * When Admin Center cannot be read the answer is 503, never an empty summary: "you have no business unit and no
     * role" would be a wrong statement about the user, not a smaller one.
     */
    @GetMapping
    @Operation(summary = "Get my permissions",
               description = "门户权限摘要：UBR 来自 admin 业务单元角色接口（逐条 BU—角色）；业务角色一律带 BU（D-14 / D-64）；"
                       + "不返回虚拟组（门户不提供虚拟组能力）。virtualGroups 恒为空数组以保持兼容。")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyPermissions(
            @CurrentUserId String userId) {
        log.info("Getting permissions for user: {}", userId);

        List<Map<String, Object>> businessUnits;
        List<Map<String, Object>> ubrRows;
        try {
            businessUnits = adminList("/business-units?profileContext=PORTAL", userId);
            ubrRows = adminList("/business-unit-roles", userId);
        } catch (RestClientException e) {
            log.warn("Admin center unavailable for the permissions of user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("503", "Admin center service unavailable"));
        }

        List<Map<String, Object>> buBoundedRoles = new ArrayList<>();
        for (Map<String, Object> ubr : ubrRows) {
            Object roleId = ubr.get("roleId");
            Object roleName = ubr.get("roleName");
            Object roleCode = ubr.get("roleCode");
            Object buId = ubr.get("businessUnitId");
            Object buName = ubr.get("businessUnitName");
            Map<String, Object> role = new HashMap<>();
            role.put("id", roleId != null ? String.valueOf(roleId) : "");
            role.put("name", roleName != null ? String.valueOf(roleName) : "");
            if (roleCode != null) {
                role.put("code", String.valueOf(roleCode));
            }
            role.put("type", "BU_BOUNDED");
            Map<String, Object> bu = new HashMap<>();
            bu.put("id", buId != null ? String.valueOf(buId) : "");
            bu.put("name", buName != null ? String.valueOf(buName) : "");
            Map<String, Object> item = new HashMap<>();
            item.put("role", role);
            item.put("activatedBusinessUnits", List.of(bu));
            buBoundedRoles.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("buBoundedRoles", buBoundedRoles);
        result.put("businessUnits", businessUnits);
        result.put("virtualGroups", List.of());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Create HttpHeaders with authentication information from SecurityContext
     * for service-to-service RestTemplate calls to admin-center.
     */
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        SecurityContextUtils.getCurrentUserId().ifPresent(id -> headers.set("X-User-Id", id));
        SecurityContextUtils.getCurrentUsername().ifPresent(name -> headers.set("X-Username", name));
        if (serviceInternalToken != null && !serviceInternalToken.isBlank()) {
            headers.set(com.platform.common.constant.PlatformConstants.HEADER_SERVICE_TOKEN, serviceInternalToken);
        }
        return headers;
    }

    /**
     * A list under {@code /users/{userId}} in Admin Center: the user's business units, or their business-unit roles
     * (UBR, as {@code UserBusinessUnitRoleController} returns them). Failures are thrown, not read as "none".
     */
    private List<Map<String, Object>> adminList(String pathUnderUser, String userId) {
        String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId) + pathUnderUser;
        HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
            url, HttpMethod.GET, entity,
            new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );
        if (response.getBody() == null) {
            throw new RestClientException("Admin center returned no body for " + pathUnderUser);
        }
        return response.getBody();
    }
}
