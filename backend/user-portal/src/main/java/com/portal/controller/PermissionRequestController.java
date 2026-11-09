package com.portal.controller;

import com.platform.common.i18n.I18nService;
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
 * Permission Request Controller
 * Legacy entry points kept only to refuse: requests are made, listed and withdrawn through {@code /permissions}.
 */
@Slf4j
@RestController
@RequestMapping("/permission-requests")
@RequiredArgsConstructor
@Tag(name = "Permission Requests", description = "User permission request operations")
public class PermissionRequestController {
    
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
    
    @GetMapping("/available-virtual-groups")
    @Operation(summary = "Get available virtual groups", description = "Get virtual groups that user can apply to join")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAvailableVirtualGroups(
            @CurrentUserId String userId) {
        log.info("Blocked available virtual groups list for user {}", userId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("403", i18nService.getMessage("portal.virtual_group_not_in_portal")));
    }
}
