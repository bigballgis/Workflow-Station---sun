package com.portal.controller;

import com.platform.common.util.ApiResponseBodyUnwrap;
import com.platform.security.util.SecurityContextUtils;
import com.portal.dto.ApiResponse;
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
               description = "Get current user's complete permission view including roles and business units")
    public ApiResponse<Map<String, Object>> getMyPermissions(
            @CurrentUserId String userId) {
        String effectiveUserId = SecurityContextUtils.getCurrentUserId().orElse(userId);
        if (effectiveUserId == null || effectiveUserId.isEmpty()) {
            return ApiResponse.error("UNAUTHORIZED", "User identity not available");
        }
        log.info("Getting permissions for user: {}", effectiveUserId);

        try {
            // Get user's roles
            List<Map<String, Object>> roles = getUserRoles(effectiveUserId);

            // Get user's virtual groups
            List<Map<String, Object>> virtualGroups = getUserVirtualGroups(effectiveUserId);

            // Get user's business units
            List<Map<String, Object>> businessUnits = getUserBusinessUnits(effectiveUserId);

            // Separate roles by type
            List<Map<String, Object>> buBoundedRoles = new ArrayList<>();
            List<Map<String, Object>> buUnboundedRoles = new ArrayList<>();

            for (Map<String, Object> role : roles) {
                String type = (String) role.get("type");
                if ("BU_BOUNDED".equals(type)) {
                    Map<String, Object> roleWithBu = new HashMap<>();
                    roleWithBu.put("role", role);
                    roleWithBu.put("activatedBusinessUnits", businessUnits);
                    buBoundedRoles.add(roleWithBu);
                } else if ("BU_UNBOUNDED".equals(type)) {
                    buUnboundedRoles.add(role);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("buBoundedRoles", buBoundedRoles);
            result.put("buUnboundedRoles", buUnboundedRoles);
            result.put("businessUnits", businessUnits);
            result.put("virtualGroups", virtualGroups);

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
            String url = adminCenterUrl + "/api/v1/admin/users/" + effectiveUserId + "/preferences";

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
            String url = adminCenterUrl + "/api/v1/admin/users/" + userId + "/roles";
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

    private List<Map<String, Object>> getUserVirtualGroups(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + userId + "/virtual-groups";
            HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get virtual groups for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> getUserBusinessUnits(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + userId + "/business-units";
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

    private boolean getDontRemindPreference(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + userId + "/preferences/dont_remind_bu_application";
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
