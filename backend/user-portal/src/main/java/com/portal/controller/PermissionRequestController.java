package com.portal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Permission Request Controller
 * Handles user permission requests for virtual groups and business units
 * 
 * Note: Business unit applications no longer require role selection.
 * Users first join virtual groups to get roles, then apply to business units
 * to activate BU-Bounded roles.
 */
@Slf4j
@RestController
@RequestMapping("/permission-requests")
@RequiredArgsConstructor
@Tag(name = "Permission Requests", description = "User permission request operations")
public class PermissionRequestController {
    
    // TODO: Inject PermissionRequestService from admin-center via REST client
    
    @PostMapping("/virtual-group")
    @Operation(summary = "Apply to join virtual group", description = "Submit application to join a virtual group")
    public ResponseEntity<Map<String, Object>> applyForVirtualGroup(
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("User {} applying for virtual group: {}", userId, request.get("virtualGroupId"));
        
        // TODO: Call admin-center API to create virtual group request
        // POST /api/v1/admin/permission-requests/virtual-group
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Application submitted successfully"
        ));
    }
    
    @PostMapping("/business-unit")
    @Operation(summary = "Apply to join business unit", 
               description = "Submit application to join a business unit. " +
                           "User must have BU-Bounded roles from virtual groups. " +
                           "No role selection needed - joining activates user's BU-Bounded roles.")
    public ResponseEntity<Map<String, Object>> applyForBusinessUnit(
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-User-Id") String userId) {
        String businessUnitId = (String) request.get("businessUnitId");
        String reason = (String) request.get("reason");
        
        log.info("User {} applying for business unit: {}", userId, businessUnitId);
        
        // TODO: Call admin-center API to create business unit request
        // POST /api/v1/admin/permission-requests/business-unit
        // Body: { businessUnitId, reason }
        // Note: No roleIds needed - user's BU-Bounded roles will be activated upon approval
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Application submitted successfully"
        ));
    }
    
    @PostMapping("/{requestId}/cancel")
    @Operation(summary = "Cancel request", description = "Cancel a pending permission request")
    public ResponseEntity<Map<String, Object>> cancelRequest(
            @PathVariable String requestId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("User {} cancelling request: {}", userId, requestId);
        
        // TODO: Call admin-center API to cancel request
        // POST /api/v1/admin/permission-requests/{requestId}/cancel
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Request cancelled successfully"
        ));
    }
    
    @GetMapping("/my")
    @Operation(summary = "Get my requests", description = "Get current user's permission request history")
    public ResponseEntity<List<Map<String, Object>>> getMyRequests(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String status) {
        log.info("Getting requests for user: {}, status: {}", userId, status);
        
        // TODO: Call admin-center API to get user's requests
        // GET /api/v1/admin/permission-requests?applicantId={userId}&status={status}
        
        return ResponseEntity.ok(List.of());
    }
    
    @GetMapping("/available-virtual-groups")
    @Operation(summary = "Get available virtual groups", description = "Get virtual groups that user can apply to join")
    public ResponseEntity<List<Map<String, Object>>> getAvailableVirtualGroups(
            @RequestHeader("X-User-Id") String userId) {
        log.info("Getting available virtual groups for user: {}", userId);
        
        // TODO: Call admin-center API to get virtual groups with approvers configured
        // GET /api/v1/admin/virtual-groups?hasApprovers=true
        
        return ResponseEntity.ok(List.of());
    }
    
    @GetMapping("/applicable-business-units")
    @Operation(summary = "Get applicable business units", 
               description = "Get business units that user can apply to join. " +
                           "Only returns business units associated with user's BU-Bounded roles.")
    public ResponseEntity<List<Map<String, Object>>> getApplicableBusinessUnits(
            @RequestHeader("X-User-Id") String userId) {
        log.info("Getting applicable business units for user: {}", userId);
        
        // TODO: Call admin-center API to get applicable business units
        // GET /api/v1/admin/permission-requests/applicable-business-units?userId={userId}
        // Returns business units that:
        // 1. Have approvers configured
        // 2. Are associated with roles that user has (BU-Bounded roles from virtual groups)
        // 3. User is not already a member of
        
        return ResponseEntity.ok(List.of());
    }
    
    @GetMapping("/business-units/{businessUnitId}/activatable-roles")
    @Operation(summary = "Get activatable roles", 
               description = "Get BU-Bounded roles that will be activated when user joins this business unit")
    public ResponseEntity<List<Map<String, Object>>> getActivatableRoles(
            @PathVariable String businessUnitId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Getting activatable roles for user {} in business unit: {}", userId, businessUnitId);
        
        // TODO: Call admin-center API to get activatable roles
        // GET /api/v1/admin/permission-requests/business-units/{businessUnitId}/activatable-roles?userId={userId}
        // Returns user's BU-Bounded roles that are associated with this business unit
        
        return ResponseEntity.ok(List.of());
    }
}
