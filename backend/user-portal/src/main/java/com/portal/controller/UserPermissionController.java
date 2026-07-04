package com.portal.controller;

import com.platform.common.util.ApiResponseBodyUnwrap;
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
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * User Permission Controller
 * Provides user's permission view and role status information
 */
@Slf4j
@RestController
@RequestMapping("/my-permissions")
@RequiredArgsConstructor
@Tag(name = "User Permissions", description = "User permission view and role status")
public class UserPermissionController {

    private final RestTemplate restTemplate;

    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    @GetMapping
    @Operation(summary = "Get my permissions",
               description = "门户权限摘要：UBR 来自 admin 业务单元角色接口（逐条 BU—角色）；无界角色来自业务角色列表；"
                       + "不返回虚拟组（门户不提供虚拟组能力）。virtualGroups 恒为空数组以保持兼容。")
    public ApiResponse<Map<String, Object>> getMyPermissions(
            @CurrentUserId String userId) {
        String effectiveUserId = SecurityContextUtils.getCurrentUserId().orElse(userId);
        if (effectiveUserId == null || effectiveUserId.isEmpty()) {
            return ApiResponse.error("UNAUTHORIZED", "User identity not available");
        }
        log.info("Getting permissions for user: {}", effectiveUserId);

        try {
            List<Map<String, Object>> roles = getUserRoles(effectiveUserId);
            List<Map<String, Object>> businessUnits = getUserBusinessUnits(effectiveUserId);
            List<Map<String, Object>> ubrRows = fetchUserBusinessUnitRoles(effectiveUserId);

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

            List<Map<String, Object>> buUnboundedRoles = new ArrayList<>();
            for (Map<String, Object> role : roles) {
                if ("BU_UNBOUNDED".equals(role.get("type"))) {
                    buUnboundedRoles.add(role);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("buBoundedRoles", buBoundedRoles);
            result.put("buUnboundedRoles", buUnboundedRoles);
            result.put("businessUnits", businessUnits);
            result.put("virtualGroups", List.of());

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("Failed to get permissions for user {}: {}", effectiveUserId, e.getMessage());
            return ApiResponse.success(Map.of(
                "buBoundedRoles", List.of(),
                "buUnboundedRoles", List.of(),
                "businessUnits", List.of(),
                "virtualGroups", List.of()
            ));
        }
    }

    @GetMapping("/unactivated-roles")
    @Operation(summary = "Get unactivated BU-Bounded roles")
    public ApiResponse<List<Map<String, Object>>> getUnactivatedRoles(
            @CurrentUserId String userId) {
        String effectiveUserId = SecurityContextUtils.getCurrentUserId().orElse(userId);
        if (effectiveUserId == null || effectiveUserId.isEmpty()) {
            return ApiResponse.error("UNAUTHORIZED", "User identity not available");
        }
        log.info("Getting unactivated roles for user: {}", effectiveUserId);
        return ApiResponse.success(fetchUnactivatedRoles(effectiveUserId));
    }

    @GetMapping("/should-show-reminder")
    @Operation(summary = "Check if should show reminder")
    public ApiResponse<Map<String, Object>> shouldShowReminder(
            @CurrentUserId String userId) {
        String effectiveUserId = SecurityContextUtils.getCurrentUserId().orElse(userId);
        if (effectiveUserId == null || effectiveUserId.isEmpty()) {
            return ApiResponse.error("UNAUTHORIZED", "User identity not available");
        }
        log.info("Checking reminder status for user: {}", effectiveUserId);

        try {
            List<Map<String, Object>> unactivatedRoles = fetchUnactivatedRoles(effectiveUserId);

            if (unactivatedRoles.isEmpty()) {
                return ApiResponse.success(Map.of(
                    "shouldShow", false,
                    "unactivatedRoles", List.of()
                ));
            }

            boolean dontRemind = getDontRemindPreference(effectiveUserId);

            return ApiResponse.success(Map.of(
                "shouldShow", !dontRemind,
                "unactivatedRoles", unactivatedRoles
            ));

        } catch (Exception e) {
            log.error("Failed to check reminder status for user {}: {}", effectiveUserId, e.getMessage());
            return ApiResponse.success(Map.of(
                "shouldShow", false,
                "unactivatedRoles", List.of()
            ));
        }
    }

    @PostMapping("/dont-remind")
    @Operation(summary = "Set don't remind preference")
    public ApiResponse<Map<String, Object>> setDontRemind(
            @CurrentUserId String userId) {
        String effectiveUserId = SecurityContextUtils.getCurrentUserId().orElse(userId);
        if (effectiveUserId == null || effectiveUserId.isEmpty()) {
            return ApiResponse.error("UNAUTHORIZED", "User identity not available");
        }
        log.info("Setting don't remind preference for user: {}", effectiveUserId);

        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(effectiveUserId) + "/preferences";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("key", "dont_remind_bu_application");
            requestBody.put("value", "true");

            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            restTemplate.exchange(url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});

            return ApiResponse.success(Map.of(
                "success", true,
                "message", "Preference saved successfully"
            ));

        } catch (Exception e) {
            log.error("Failed to set preference for user {}: {}", effectiveUserId, e.getMessage());
            return ApiResponse.success(Map.of(
                "success", true,
                "message", "Preference saved"
            ));
        }
    }

    @GetMapping("/roles/{roleId}/status")
    @Operation(summary = "Get role status")
    public ApiResponse<Map<String, Object>> getRoleStatus(
            @PathVariable String roleId,
            @CurrentUserId String userId) {
        String effectiveUserId = SecurityContextUtils.getCurrentUserId().orElse(userId);
        if (effectiveUserId == null || effectiveUserId.isEmpty()) {
            return ApiResponse.error("UNAUTHORIZED", "User identity not available");
        }
        log.info("Getting role {} status for user: {}", roleId, effectiveUserId);

        try {
            List<Map<String, Object>> roles = getUserRoles(effectiveUserId);

            Map<String, Object> targetRole = null;
            for (Map<String, Object> role : roles) {
                if (roleId.equals(role.get("id"))) {
                    targetRole = role;
                    break;
                }
            }

            if (targetRole == null) {
                return ApiResponse.success(Map.of(
                    "roleId", roleId,
                    "roleName", "",
                    "roleType", "",
                    "isActive", false,
                    "activatedInBusinessUnits", List.of()
                ));
            }

            String roleType = (String) targetRole.get("type");
            boolean isActive = !"BU_BOUNDED".equals(roleType);
            List<Map<String, Object>> activatedBus = List.of();

            if ("BU_BOUNDED".equals(roleType)) {
                List<Map<String, Object>> businessUnits = getUserBusinessUnits(effectiveUserId);
                isActive = !businessUnits.isEmpty();
                activatedBus = businessUnits;
            }

            return ApiResponse.success(Map.of(
                "roleId", roleId,
                "roleName", targetRole.get("name"),
                "roleType", roleType,
                "isActive", isActive,
                "activatedInBusinessUnits", activatedBus
            ));

        } catch (Exception e) {
            log.error("Failed to get role status for user {}: {}", effectiveUserId, e.getMessage());
            return ApiResponse.success(Map.of(
                "roleId", roleId,
                "roleName", "",
                "roleType", "",
                "isActive", false,
                "activatedInBusinessUnits", List.of()
            ));
        }
    }

    // Helper methods

    /**
     * Create HttpHeaders with authentication information from SecurityContext
     * for service-to-service RestTemplate calls to admin-center.
     */
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        SecurityContextUtils.getCurrentUserId().ifPresent(id -> headers.set("X-User-Id", id));
        SecurityContextUtils.getCurrentUsername().ifPresent(name -> headers.set("X-Username", name));
        return headers;
    }

    private List<Map<String, Object>> fetchUnactivatedRoles(String userId) {
        try {
            List<Map<String, Object>> roles = getUserRoles(userId);
            List<Map<String, Object>> businessUnits = getUserBusinessUnits(userId);
            List<Map<String, Object>> unactivatedRoles = new ArrayList<>();
            if (businessUnits.isEmpty()) {
                for (Map<String, Object> role : roles) {
                    if ("BU_BOUNDED".equals(role.get("type"))) {
                        unactivatedRoles.add(role);
                    }
                }
            }
            return unactivatedRoles;
        } catch (Exception e) {
            log.error("Failed to get unactivated roles for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> getUserRoles(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId) + "/roles?profileContext=PORTAL";
            HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get roles for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> getUserBusinessUnits(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId) + "/business-units?profileContext=PORTAL";
            HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get business units for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 用户在业务单元下的角色（UBR），与 admin {@code UserBusinessUnitRoleController} 一致。
     */
    private List<Map<String, Object>> fetchUserBusinessUnitRoles(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId) + "/business-unit-roles";
            HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get business unit roles for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean getDontRemindPreference(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId) + "/preferences/dont_remind_bu_application";
            HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> body = response.getBody() != null
                    ? ApiResponseBodyUnwrap.unwrapDataMap(response.getBody())
                    : Collections.emptyMap();
            if (!body.isEmpty() && body.containsKey("value")) {
                return "true".equals(String.valueOf(body.get("value")));
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
