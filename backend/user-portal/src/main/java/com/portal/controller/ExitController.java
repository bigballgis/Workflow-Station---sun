package com.portal.controller;

import com.platform.common.i18n.I18nService;
import com.portal.client.AdminCenterClient;
import com.platform.common.dto.ApiResponse;
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

    private final I18nService i18nService;
    private final AdminCenterClient adminCenterClient;
    
    @PostMapping("/virtual-group/{groupId}")
    @Operation(summary = "Exit virtual group", 
               description = "Exit from a virtual group (immediate effect, no approval needed). " +
                           "This will revoke the role associated with the virtual group.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exitVirtualGroup(
            @PathVariable String groupId,
            @CurrentUserId String userId) {
        log.info("Blocked portal virtual group exit for user {} group {}", userId, groupId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("403", i18nService.getMessage("portal.virtual_group_not_in_portal")));
    }
    
    @PostMapping("/business-unit/{businessUnitId}")
    @Operation(summary = "Exit business unit", 
               description = "Exit from a business unit (immediate effect, no approval needed). " +
                           "This will deactivate all BU-Bounded roles for this business unit.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exitBusinessUnit(
            @PathVariable String businessUnitId,
            @CurrentUserId String userId) {
        log.info("Blocked direct BU exit for user {} bu {} — use permission request flow", userId, businessUnitId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("403", i18nService.getMessage("portal.exit_use_permission_request")));
    }
    
    @GetMapping("/my-memberships")
    @Operation(summary = "Get my memberships", description = "Get current user's virtual group and business unit memberships")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyMemberships(
            @CurrentUserId String userId) {
        log.info("Getting memberships for user: {}", userId);

        return adminCenterClient.getUserMemberships(userId)
                .<ResponseEntity<ApiResponse<Map<String, Object>>>>map(data -> ResponseEntity.ok(ApiResponse.success(data)))
                .orElse(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error("503", "Admin center service unavailable")));
    }
    
}
