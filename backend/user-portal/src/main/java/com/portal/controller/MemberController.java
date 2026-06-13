package com.portal.controller;

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
 * Member Controller
 * Handles member management operations for approvers
 */
@Slf4j
@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
@Tag(name = "Member Management", description = "Member management operations for approvers")
public class MemberController {

    private final AdminCenterClient adminCenterClient;
    
    @GetMapping("/virtual-groups/{groupId}")
    @Operation(summary = "Get virtual group members", description = "Get members of a virtual group (approver only)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getVirtualGroupMembers(
            @PathVariable String groupId,
            @CurrentUserId String userId) {
        log.info("Approver {} getting members of virtual group: {}", userId, groupId);

        return adminCenterClient.getVirtualGroupMembers(groupId)
                .<ResponseEntity<ApiResponse<List<Map<String, Object>>>>>map(data -> ResponseEntity.ok(ApiResponse.success(data)))
                .orElse(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error("503", "Admin center service unavailable")));
    }

    @GetMapping("/business-units/{businessUnitId}")
    @Operation(summary = "Get business unit members", description = "Get members of a business unit (approver only)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBusinessUnitMembers(
            @PathVariable String businessUnitId,
            @CurrentUserId String userId) {
        log.info("Approver {} getting members of business unit: {}", userId, businessUnitId);

        return adminCenterClient.getBusinessUnitMembers(businessUnitId)
                .<ResponseEntity<ApiResponse<List<Map<String, Object>>>>>map(data -> ResponseEntity.ok(ApiResponse.success(data)))
                .orElse(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error("503", "Admin center service unavailable")));
    }
    
    @DeleteMapping("/virtual-groups/{groupId}/users/{targetUserId}")
    @Operation(summary = "Remove virtual group member", description = "Remove a member from virtual group (approver only)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeVirtualGroupMember(
            @PathVariable String groupId,
            @PathVariable String targetUserId,
            @RequestBody(required = false) Map<String, Object> request,
            @CurrentUserId String userId) {
        log.info("Approver {} removing user {} from virtual group: {}", userId, targetUserId, groupId);
        
        if (request != null && request.get("reason") != null) {
            log.debug("Remove VG member reason (not forwarded to admin): {}", request.get("reason"));
        }

        if (adminCenterClient.removeVirtualGroupMember(groupId, targetUserId)) {
            return ResponseEntity.ok(ApiResponse.success(Map.of("removed", true)));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("503", "Admin center service unavailable"));
    }
    
    @DeleteMapping("/business-units/{businessUnitId}/users/{targetUserId}/roles/{roleId}")
    @Operation(summary = "Remove business unit role", description = "Remove a role from user in business unit (approver only)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeBusinessUnitRole(
            @PathVariable String businessUnitId,
            @PathVariable String targetUserId,
            @PathVariable String roleId,
            @RequestBody(required = false) Map<String, Object> request,
            @CurrentUserId String userId) {
        log.info("Approver {} removing role {} from user {} in business unit: {}", 
                userId, roleId, targetUserId, businessUnitId);
        
        if (request != null && request.get("reason") != null) {
            log.debug("Remove BU role reason (not forwarded to admin): {}", request.get("reason"));
        }

        if (adminCenterClient.removeUserBusinessUnitRole(targetUserId, businessUnitId, roleId)) {
            return ResponseEntity.ok(ApiResponse.success(Map.of("removed", true)));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("503", "Admin center service unavailable"));
    }

    @DeleteMapping("/business-units/{businessUnitId}/users/{targetUserId}")
    @Operation(summary = "Remove business unit member", description = "Remove a user from business unit (approver only)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeBusinessUnitMember(
            @PathVariable String businessUnitId,
            @PathVariable String targetUserId,
            @RequestBody(required = false) Map<String, Object> request,
            @CurrentUserId String userId) {
        log.info("Approver {} removing user {} from business unit: {}", userId, targetUserId, businessUnitId);

        if (request != null && request.get("reason") != null) {
            log.debug("Remove BU member reason (not forwarded to admin): {}", request.get("reason"));
        }

        if (adminCenterClient.removeBusinessUnitMember(businessUnitId, targetUserId)) {
            return ResponseEntity.ok(ApiResponse.success(Map.of("removed", true)));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("503", "Admin center service unavailable"));
    }
    
    @GetMapping("/my-approval-scope")
    @Operation(summary = "Get approval scope", description = "Get virtual groups and business units where user is approver")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getApprovalScope(
            @CurrentUserId String userId) {
        log.info("Getting approval scope for user: {}", userId);

        return adminCenterClient.getApprovalScope(userId)
                .<ResponseEntity<ApiResponse<Map<String, Object>>>>map(data -> ResponseEntity.ok(ApiResponse.success(data)))
                .orElse(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error("503", "Admin center service unavailable")));
    }
}
