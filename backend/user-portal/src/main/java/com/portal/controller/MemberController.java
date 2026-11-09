package com.portal.controller;

import com.portal.client.AdminCenterClient;
import com.platform.common.dto.ApiResponse;
import com.portal.component.BusinessUnitMemberQuery;
import com.platform.common.i18n.I18nService;
import com.platform.security.util.SecurityContextUtils;
import com.portal.component.VirtualGroupAccessComponent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Member Controller
 * Handles member management operations for approvers
 *
 * <p>The Admin Center call behind BU-role removal goes out as a trusted service call, so the checks here are the
 * only thing standing between a caller and it: the member list and BU-role removal are limited to an approver of the
 * business unit, taken from the authenticated session (never from a client-supplied header). Virtual groups are not
 * available in the portal, and removing a member from a whole business unit is not either (SYS_ADMIN in Admin
 * Center).
 */
@Slf4j
@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
@Tag(name = "Member Management", description = "Member management operations for approvers")
public class MemberController {

    private final AdminCenterClient adminCenterClient;
    private final VirtualGroupAccessComponent virtualGroupAccessComponent;
    private final BusinessUnitMemberQuery businessUnitMemberQuery;
    private final I18nService i18nService;

    @GetMapping("/business-units/{businessUnitId}")
    @Operation(summary = "Get business unit members",
            description = "Members of a business unit with their roles there (approver only)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBusinessUnitMembers(
            @PathVariable String businessUnitId) {
        return asBusinessUnitApprover(businessUnitId, approverId -> {
            log.info("Approver {} getting members of business unit: {}", approverId, businessUnitId);
            return ResponseEntity.ok(ApiResponse.success(businessUnitMemberQuery.members(businessUnitId)));
        });
    }

    /**
     * Virtual-group membership is maintained by SYS_ADMIN in Admin Center only (platform access model §5.5), as
     * for every other virtual-group operation in the portal. Refused here without calling Admin Center.
     */
    @DeleteMapping("/virtual-groups/{groupId}/users/{targetUserId}")
    @Operation(summary = "Remove virtual group member", description = "Not available in the portal")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeVirtualGroupMember(
            @PathVariable String groupId,
            @PathVariable String targetUserId) {
        log.warn("Refused portal removal of user {} from virtual group {}: not available in the portal",
                targetUserId, groupId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(
                "PERM_ACCESS_DENIED", i18nService.getMessage("portal.virtual_group_not_in_portal")));
    }

    @DeleteMapping("/business-units/{businessUnitId}/users/{targetUserId}/roles/{roleId}")
    @Operation(summary = "Remove business unit role", description = "Remove a role from user in business unit (approver only)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeBusinessUnitRole(
            @PathVariable String businessUnitId,
            @PathVariable String targetUserId,
            @PathVariable String roleId,
            @RequestBody(required = false) Map<String, Object> request) {
        return asBusinessUnitApprover(businessUnitId, approverId -> {
            log.info("Approver {} removing role {} from user {} in business unit: {}",
                    approverId, roleId, targetUserId, businessUnitId);

            if (request != null && request.get("reason") != null) {
                log.debug("Remove BU role reason (not forwarded to admin): {}", request.get("reason"));
            }

            if (adminCenterClient.removeUserBusinessUnitRole(targetUserId, businessUnitId, roleId)) {
                return ResponseEntity.ok(ApiResponse.success(Map.of("removed", true)));
            }
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("503", "Admin center service unavailable"));
        });
    }

    /**
     * Business-unit membership is maintained by SYS_ADMIN in Admin Center (platform access model §5.5); in the
     * portal a member leaves through an exit request. Refused here without calling Admin Center.
     */
    @DeleteMapping("/business-units/{businessUnitId}/users/{targetUserId}")
    @Operation(summary = "Remove business unit member", description = "Not available in the portal")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeBusinessUnitMember(
            @PathVariable String businessUnitId,
            @PathVariable String targetUserId) {
        log.warn("Refused portal removal of user {} from business unit {}: not available in the portal",
                targetUserId, businessUnitId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(
                "PERM_ACCESS_DENIED", i18nService.getMessage("portal.business_unit_member_removal_not_in_portal")));
    }

    private interface ApproverAction<T> {
        ResponseEntity<ApiResponse<T>> run(String approverId);
    }

    private <T> ResponseEntity<ApiResponse<T>> asBusinessUnitApprover(String businessUnitId, ApproverAction<T> action) {
        return asApprover(approverId -> virtualGroupAccessComponent.isApproverForBusinessUnit(approverId, businessUnitId),
                action);
    }

    private <T> ResponseEntity<ApiResponse<T>> asApprover(Predicate<String> isApprover, ApproverAction<T> action) {
        Optional<String> approverId = SecurityContextUtils.getCurrentUserId().filter(id -> !id.isBlank());
        if (approverId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("AUTH_FAILED", i18nService.getMessage("auth.unauthorized")));
        }
        if (!isApprover.test(approverId.get())) {
            log.warn("Member management refused: {} is not an approver of the target", approverId.get());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("PERM_ACCESS_DENIED", i18nService.getMessage("auth.no_permission")));
        }
        return action.run(approverId.get());
    }
}
