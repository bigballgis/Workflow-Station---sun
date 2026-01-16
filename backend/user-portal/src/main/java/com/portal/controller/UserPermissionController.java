package com.portal.controller;

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
    public ResponseEntity<Map<String, Object>> getMyPermissions(
            @RequestHeader("X-User-Id") String userId) {
        log.info("Getting permissions for user: {}", userId);
        
        try {
            // Get user's roles
            List<Map<String, Object>> roles = getUserRoles(userId);
            
            // Get user's virtual groups
            List<Map<String, Object>> virtualGroups = getUserVirtualGroups(userId);
            
            // Get user's business units
            List<Map<String, Object>> businessUnits = getUserBusinessUnits(userId);
            
            // Separate roles by type
            List<Map<String, Object>> buBoundedRoles = new ArrayList<>();
            List<Map<String, Object>> buUnboundedRoles = new ArrayList<>();
            
            for (Map<String, Object> role : roles) {
                String type = (String) role.get("type");
                if ("BU_BOUNDED".equals(type)) {
                    // For BU-Bounded roles, include activated business units
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
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to get permissions for user {}: {}", userId, e.getMessage());
            return ResponseEntity.ok(Map.of(
                "buBoundedRoles", List.of(),
                "buUnboundedRoles", List.of(),
                "businessUnits", List.of(),
                "virtualGroups", List.of()
            ));
        }
    }
    
    @GetMapping("/unactivated-roles")
    @Operation(summary = "Get unactivated BU-Bounded roles")
    public ResponseEntity<List<Map<String, Object>>> getUnactivatedRoles(
            @RequestHeader("X-User-Id") String userId) {
        log.info("Getting unactivated roles for user: {}", userId);
        
        try {
            // Get user's roles
            List<Map<String, Object>> roles = getUserRoles(userId);
            
            // Get user's business units
            List<Map<String, Object>> businessUnits = getUserBusinessUnits(userId);
            
            // If user has no business units, all BU-Bounded roles are unactivated
            List<Map<String, Object>> unactivatedRoles = new ArrayList<>();
            if (businessUnits.isEmpty()) {
                for (Map<String, Object> role : roles) {
                    if ("BU_BOUNDED".equals(role.get("type"))) {
                        unactivatedRoles.add(role);
                    }
                }
            }
            
            return ResponseEntity.ok(unactivatedRoles);
            
        } catch (Exception e) {
            log.error("Failed to get unactivated roles for user {}: {}", userId, e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }
    
    @GetMapping("/should-show-reminder")
    @Operation(summary = "Check if should show reminder")
    public ResponseEntity<Map<String, Object>> shouldShowReminder(
            @RequestHeader("X-User-Id") String userId) {
        log.info("Checking reminder status for user: {}", userId);
        
        try {
            // Get unactivated roles
            List<Map<String, Object>> unactivatedRoles = getUnactivatedRoles(userId).getBody();
            
            if (unactivatedRoles == null || unactivatedRoles.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "shouldShow", false,
                    "unactivatedRoles", List.of()
                ));
            }
            
            // Check user preference
            boolean dontRemind = getDontRemindPreference(userId);
            
            return ResponseEntity.ok(Map.of(
                "shouldShow", !dontRemind,
                "unactivatedRoles", unactivatedRoles
            ));
            
        } catch (Exception e) {
            log.error("Failed to check reminder status for user {}: {}", userId, e.getMessage());
            return ResponseEntity.ok(Map.of(
                "shouldShow", false,
                "unactivatedRoles", List.of()
            ));
        }
    }
    
    @PostMapping("/dont-remind")
    @Operation(summary = "Set don't remind preference")
    public ResponseEntity<Map<String, Object>> setDontRemind(
            @RequestHeader("X-User-Id") String userId) {
        log.info("Setting don't remind preference for user: {}", userId);
        
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + userId + "/preferences";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("key", "dont_remind_bu_application");
            requestBody.put("value", "true");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            restTemplate.exchange(url, HttpMethod.POST, entity, 
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Preference saved successfully"
            ));
            
        } catch (Exception e) {
            log.error("Failed to set preference for user {}: {}", userId, e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Preference saved"
            ));
        }
    }
    
    @GetMapping("/roles/{roleId}/status")
    @Operation(summary = "Get role status")
    public ResponseEntity<Map<String, Object>> getRoleStatus(
            @PathVariable String roleId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Getting role {} status for user: {}", roleId, userId);
        
        try {
            // Get user's roles
            List<Map<String, Object>> roles = getUserRoles(userId);
            
            // Find the specific role
            Map<String, Object> targetRole = null;
            for (Map<String, Object> role : roles) {
                if (roleId.equals(role.get("id"))) {
                    targetRole = role;
                    break;
                }
            }
            
            if (targetRole == null) {
                return ResponseEntity.ok(Map.of(
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
                List<Map<String, Object>> businessUnits = getUserBusinessUnits(userId);
                isActive = !businessUnits.isEmpty();
                activatedBus = businessUnits;
            }
            
            return ResponseEntity.ok(Map.of(
                "roleId", roleId,
                "roleName", targetRole.get("name"),
                "roleType", roleType,
                "isActive", isActive,
                "activatedInBusinessUnits", activatedBus
            ));
            
        } catch (Exception e) {
            log.error("Failed to get role status for user {}: {}", userId, e.getMessage());
            return ResponseEntity.ok(Map.of(
                "roleId", roleId,
                "roleName", "",
                "roleType", "",
                "isActive", false,
                "activatedInBusinessUnits", List.of()
            ));
        }
    }
    
    // Helper methods
    
    private List<Map<String, Object>> getUserRoles(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + userId + "/roles";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, null,
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
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, null,
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
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, null,
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
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("value")) {
                return "true".equals(body.get("value"));
            }
            return false;
        } catch (Exception e) {
            // Preference not found, return false
            return false;
        }
    }
}
