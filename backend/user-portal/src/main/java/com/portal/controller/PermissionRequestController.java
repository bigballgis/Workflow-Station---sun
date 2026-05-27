package com.portal.controller;

import com.platform.common.i18n.I18nService;
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
    
    private final AdminCenterClient adminCenterClient;
    private final I18nService i18nService;
    
    @PostMapping("/virtual-group")
    @Operation(summary = "Apply to join virtual group", description = "Submit application to join a virtual group")
    public ResponseEntity<ApiResponse<Map<String, Object>>> applyForVirtualGroup(
            @RequestBody Map<String, Object> request,
            @CurrentUserId String userId) {
        log.info("Blocked legacy permission-requests virtual group apply for user {}", userId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("403", i18nService.getMessage("portal.virtual_group_not_in_portal")));
    }
    
    @PostMapping("/business-unit")
    @Operation(summary = "Apply to join business unit", 
               description = "Submit application to join a business unit. " +
                           "User must have BU-Bounded roles from virtual groups. " +
                           "No role selection needed - joining activates user's BU-Bounded roles.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> applyForBusinessUnit(
            @RequestBody Map<String, Object> request,
            @CurrentUserId String userId) {
        log.info("Blocked legacy permission-requests BU apply for user {} — use /permissions/request-business-unit", userId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("403", i18nService.getMessage("portal.use_permissions_center_api")));
    }
    
    @PostMapping("/{requestId}/cancel")
    @Operation(summary = "Cancel request", description = "Cancel a pending permission request")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelRequest(
            @PathVariable String requestId,
            @CurrentUserId String userId) {
        log.info("Blocked legacy cancel for user {} request {} — use DELETE /permissions/requests/{{id}}", userId, requestId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("403", i18nService.getMessage("portal.use_permissions_center_api")));
    }
    
    @GetMapping("/my")
    @Operation(summary = "Get my requests", description = "Get current user's permission request history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMyRequests(
            @CurrentUserId String userId,
            @RequestParam(required = false) String status) {
        log.info("Getting requests for user: {}, status: {}", userId, status);

        return adminCenterClient.getUserPermissionRequests(userId, status)
                .<ResponseEntity<ApiResponse<List<Map<String, Object>>>>>map(data -> ResponseEntity.ok(ApiResponse.success(data)))
                .orElse(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error("503", "Admin center service unavailable")));
    }
    
    @GetMapping("/available-virtual-groups")
    @Operation(summary = "Get available virtual groups", description = "Get virtual groups that user can apply to join")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAvailableVirtualGroups(
            @CurrentUserId String userId) {
        log.info("Blocked available virtual groups list for user {}", userId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("403", i18nService.getMessage("portal.virtual_group_not_in_portal")));
    }
    
    @GetMapping("/applicable-business-units")
    @Operation(summary = "Get applicable business units", 
               description = "Get business units that user can apply to join. " +
                           "Only returns business units associated with user's BU-Bounded roles.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getApplicableBusinessUnits(
            @CurrentUserId String userId) {
        log.info("Getting applicable business units for user: {}", userId);

        return adminCenterClient.getApplicableBusinessUnits(userId)
                .<ResponseEntity<ApiResponse<List<Map<String, Object>>>>>map(data -> ResponseEntity.ok(ApiResponse.success(data)))
                .orElse(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error("503", "Admin center service unavailable")));
    }
    
    @GetMapping("/business-units/{businessUnitId}/activatable-roles")
    @Operation(summary = "Get activatable roles", 
               description = "Get BU-Bounded roles that will be activated when user joins this business unit")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getActivatableRoles(
            @PathVariable String businessUnitId,
            @CurrentUserId String userId) {
        log.info("Getting activatable roles for user {} in business unit: {}", userId, businessUnitId);

        return adminCenterClient.getActivatableRoles(businessUnitId, userId)
                .<ResponseEntity<ApiResponse<List<Map<String, Object>>>>>map(data -> ResponseEntity.ok(ApiResponse.success(data)))
                .orElse(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error("503", "Admin center service unavailable")));
    }
}
