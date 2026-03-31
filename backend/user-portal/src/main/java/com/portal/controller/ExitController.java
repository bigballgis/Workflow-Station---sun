package com.portal.controller;

import com.portal.client.AdminCenterClient;
import com.portal.dto.ApiResponse;
import com.portal.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Exit Controller
 * Handles user self-exit operations from virtual groups and business units
 * 
 * Note: Exit operations have immediate effect without approval.
 * - Exiting a virtual group revokes the associated role
 * - Exiting a business unit deactivates BU-Bounded roles for that business unit
 */
@Slf4j
@RestController
@RequestMapping("/exit")
@RequiredArgsConstructor
@Tag(name = "Exit Operations", description = "User self-exit operations")
public class ExitController {
    
    // TODO: Inject MemberManagementService from admin-center via REST client
    private final AdminCenterClient adminCenterClient;
    
    @PostMapping("/virtual-group/{groupId}")
    @Operation(summary = "Exit virtual group", 
               description = "Exit from a virtual group (immediate effect, no approval needed). " +
                           "This will revoke the role associated with the virtual group.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exitVirtualGroup(
            @PathVariable String groupId,
            @CurrentUserId String userId) {
        log.info("User {} exiting virtual group: {}", userId, groupId);
        
        // TODO: Call admin-center API to exit virtual group
        // POST /api/v1/admin/members/exit/virtual-group/{groupId}?userId={userId}
        // This will:
        // 1. Remove user from virtual group
        // 2. Revoke the role bound to the virtual group

        if (adminCenterClient.exitVirtualGroup(groupId, userId)) {
            return ResponseEntity.ok(ApiResponse.success(Map.of()));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("503", "Admin center service unavailable"));
    }
    
    @PostMapping("/business-unit/{businessUnitId}")
    @Operation(summary = "Exit business unit", 
               description = "Exit from a business unit (immediate effect, no approval needed). " +
                           "This will deactivate all BU-Bounded roles for this business unit.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exitBusinessUnit(
            @PathVariable String businessUnitId,
            @CurrentUserId String userId) {
        log.info("User {} exiting business unit: {}", userId, businessUnitId);
        
        // TODO: Call admin-center API to exit business unit
        // POST /api/v1/admin/members/exit/business-unit/{businessUnitId}?userId={userId}
        // This will:
        // 1. Remove user from business unit
        // 2. Deactivate all BU-Bounded roles for this business unit
        
        if (adminCenterClient.exitBusinessUnit(businessUnitId, userId)) {
            return ResponseEntity.ok(ApiResponse.success(Map.of()));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("503", "Admin center service unavailable"));
    }
    
    @GetMapping("/my-memberships")
    @Operation(summary = "Get my memberships", description = "Get current user's virtual group and business unit memberships")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyMemberships(
            @CurrentUserId String userId) {
        log.info("Getting memberships for user: {}", userId);
        
        // TODO: Call admin-center API to get user's memberships
        // GET /api/v1/admin/users/{userId}/memberships
        
        return adminCenterClient.getUserMemberships(userId)
                .<ResponseEntity<ApiResponse<Map<String, Object>>>>map(data -> ResponseEntity.ok(ApiResponse.success(data)))
                .orElse(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error("503", "Admin center service unavailable")));
    }
    
    @GetMapping("/exit-history")
    @Operation(summary = "Get exit history", description = "Get current user's exit history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getExitHistory(
            @CurrentUserId String userId) {
        log.info("Getting exit history for user: {}", userId);
        
        // TODO: Call admin-center API to get user's exit history
        // GET /api/v1/admin/member-change-logs?userId={userId}&changeType=EXIT
        
        return adminCenterClient.getExitHistory(userId)
                .<ResponseEntity<ApiResponse<List<Map<String, Object>>>>>map(data -> ResponseEntity.ok(ApiResponse.success(data)))
                .orElse(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error("503", "Admin center service unavailable")));
    }
}
