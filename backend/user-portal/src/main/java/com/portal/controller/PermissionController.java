package com.portal.controller;

import com.portal.component.PermissionComponent;
import com.portal.component.RoleAccessComponent;
import com.portal.dto.ApiResponse;
import com.portal.security.CurrentUserId;
import com.portal.dto.PageResponse;
import com.portal.dto.PermissionRequestDto;
import com.portal.dto.PermissionRequestListItem;
import com.portal.entity.PermissionRequest;
import com.portal.enums.PermissionRequestStatus;
import com.platform.common.i18n.I18nService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "权限管理", description = "权限申请和管理相关接口")
public class PermissionController {

    private final PermissionComponent permissionComponent;
    private final RoleAccessComponent roleAccessComponent;
    private final I18nService i18nService;

    // ==================== 新的 API 端点 ====================

    @GetMapping("/available-roles")
    @Operation(summary = "获取可申请的业务角色", description = "获取用户可以申请的业务角色列表（排除已拥有的）")
    public ApiResponse<List<Map<String, Object>>> getAvailableRoles(
            @CurrentUserId String userId) {
        List<Map<String, Object>> roles = permissionComponent.getAvailableRoles(userId);
        return ApiResponse.success(roles);
    }

    @GetMapping("/available-virtual-groups")
    @Operation(summary = "获取可加入的虚拟组", description = "已禁用：虚拟组不在 User Portal 提供")
    public ApiResponse<List<Map<String, Object>>> getAvailableVirtualGroups(
            @CurrentUserId String userId) {
        return ApiResponse.error(i18nService.getMessage("portal.virtual_group_not_in_portal"));
    }

    @GetMapping("/users/search")
    @Operation(summary = "搜索启用用户", description = "代办选人：仅返回 ACTIVE 用户（分页）")
    public ApiResponse<PageResponse<Map<String, Object>>> searchUsersForDelegation(
            @CurrentUserId String userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(roleAccessComponent.searchActiveUsersForPortal(keyword, page, size));
    }

    @GetMapping("/available-business-units")
    @Operation(summary = "获取可加入的业务单元", description = "获取用户可以加入的业务单元列表（排除已加入的）")
    public ApiResponse<List<Map<String, Object>>> getAvailableBusinessUnits(
            @CurrentUserId String userId) {
        List<Map<String, Object>> businessUnits = permissionComponent.getAvailableBusinessUnits(userId);
        return ApiResponse.success(businessUnits);
    }

    @GetMapping("/business-units")
    @Operation(summary = "获取业务单元目录", description = "获取平台业务单元扁平列表（成员管理等下拉）")
    public ApiResponse<List<Map<String, Object>>> getBusinessUnitsCatalog(@CurrentUserId String userId) {
        log.debug("Business units catalog requested by user {}", userId);
        return ApiResponse.success(permissionComponent.getBusinessUnitsCatalog());
    }

    @GetMapping("/business-units/{businessUnitId}/roles")
    @Operation(summary = "获取业务单元绑定角色", description = "获取业务单元已绑定的业务角色列表")
    public ApiResponse<List<Map<String, Object>>> getBusinessUnitRoles(
            @CurrentUserId String userId,
            @PathVariable String businessUnitId) {
        log.debug("Business unit roles requested by user {} for unit {}", userId, businessUnitId);
        return ApiResponse.success(permissionComponent.getBusinessUnitRoles(businessUnitId));
    }

    @PostMapping("/request-role")
    @Operation(summary = "申请角色", description = "申请某个组织单元的业务角色（自动批准）")
    public ApiResponse<PermissionRequest> requestRole(
            @CurrentUserId String userId,
            @RequestBody Map<String, String> body) {
        String roleId = body.get("roleId");
        String organizationUnitId = body.get("organizationUnitId");
        String reason = body.get("reason");
        
        if (roleId == null || roleId.isEmpty()) {
            return ApiResponse.error(i18nService.getMessage("validation.role_id_required"));
        }
        if (organizationUnitId == null || organizationUnitId.isEmpty()) {
            return ApiResponse.error(i18nService.getMessage("portal.org_unit_id_required"));
        }
        if (reason == null || reason.isEmpty()) {
            return ApiResponse.error(i18nService.getMessage("portal.reason_required"));
        }
        
        try {
            PermissionRequest request = permissionComponent.requestRoleAssignment(userId, roleId, organizationUnitId, reason);
            return ApiResponse.success(request);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/request-virtual-group")
    @Operation(summary = "申请加入虚拟组", description = "已禁用：虚拟组不在 User Portal 提供")
    public ApiResponse<PermissionRequest> requestVirtualGroup(
            @CurrentUserId String userId,
            @RequestBody Map<String, String> body) {
        return ApiResponse.error(i18nService.getMessage("portal.virtual_group_not_in_portal"));
    }

    @PostMapping("/request-business-unit")
    @Operation(summary = "申请加入业务单元", description = "可申请为他人代办（beneficiaryUserId，可选）")
    public ApiResponse<PermissionRequest> requestBusinessUnit(
            @CurrentUserId String userId,
            @RequestBody Map<String, Object> body) {
        String businessUnitId = body.get("businessUnitId") != null ? body.get("businessUnitId").toString() : null;
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;
        String beneficiaryUserId = beneficiaryUserIdFromBody(body, userId);

        if (businessUnitId == null || businessUnitId.isEmpty()) {
            return ApiResponse.error(i18nService.getMessage("portal.bu_id_required"));
        }
        if (reason == null || reason.isEmpty()) {
            return ApiResponse.error(i18nService.getMessage("portal.reason_required"));
        }

        try {
            PermissionRequest request = permissionComponent.requestBusinessUnitJoin(userId, beneficiaryUserId, businessUnitId, reason);
            return ApiResponse.success(request);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/request-business-unit-role")
    @Operation(summary = "申请加入业务单元并指定角色", description = "申请加入业务单元，并选择该业务单元下的一条 Eligible Role")
    public ApiResponse<PermissionRequest> requestBusinessUnitWithRole(
            @CurrentUserId String userId,
            @RequestBody Map<String, Object> body) {
        Object buIdObj = body.get("businessUnitId");
        String businessUnitId = buIdObj != null ? buIdObj.toString() : null;
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;
        String beneficiaryUserId = beneficiaryUserIdFromBody(body, userId);

        String roleId = null;
        Object roleIdsObj = body.get("roleIds");
        if (roleIdsObj instanceof List<?> list && !list.isEmpty()) {
            roleId = Objects.toString(list.get(0), null);
        }
        if (roleId == null || roleId.isBlank()) {
            Object single = body.get("roleId");
            if (single != null) {
                roleId = single.toString();
            }
        }

        if (businessUnitId == null || businessUnitId.isEmpty()) {
            return ApiResponse.error(i18nService.getMessage("portal.bu_id_required"));
        }
        if (reason == null || reason.isEmpty()) {
            return ApiResponse.error(i18nService.getMessage("portal.reason_required"));
        }
        if (roleId == null || roleId.isBlank()) {
            return ApiResponse.error(i18nService.getMessage("validation.role_id_required"));
        }

        try {
            PermissionRequest request = permissionComponent.requestBusinessUnitJoinWithRole(userId, beneficiaryUserId, businessUnitId, roleId, reason);
            return ApiResponse.success(request);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/request-business-unit-exit")
    @Operation(summary = "申请退出业务单元", description = "退出成员身份（审批通过后执行）；可代办 beneficiaryUserId")
    public ApiResponse<PermissionRequest> requestBusinessUnitExit(
            @CurrentUserId String userId,
            @RequestBody Map<String, Object> body) {
        String businessUnitId = body.get("businessUnitId") != null ? body.get("businessUnitId").toString() : null;
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;
        String beneficiaryUserId = beneficiaryUserIdFromBody(body, userId);
        if (businessUnitId == null || businessUnitId.isEmpty()) {
            return ApiResponse.error(i18nService.getMessage("portal.bu_id_required"));
        }
        if (reason == null || reason.isEmpty()) {
            return ApiResponse.error(i18nService.getMessage("portal.reason_required"));
        }
        try {
            PermissionRequest request = permissionComponent.requestBusinessUnitExit(userId, beneficiaryUserId, businessUnitId, reason);
            return ApiResponse.success(request);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/removal-options-by-function-unit")
    @Operation(summary = "按功能单元查看可移除的业务单元角色", description = "根据受益人当前 BU 角色与已部署功能单元的访问配置（角色门槛）聚合，供门户移除权限多选。")
    public ApiResponse<Map<String, Object>> getRemovalOptionsByFunctionUnit(
            @CurrentUserId String userId,
            @RequestParam(required = false) String beneficiaryUserId) {
        String beneficiary = (beneficiaryUserId == null || beneficiaryUserId.isBlank())
                ? userId
                : beneficiaryUserId.trim();
        try {
            return ApiResponse.success(permissionComponent.buildRoleRemovalOptionsByFunctionUnit(beneficiary));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/request-business-unit-role-removal")
    @Operation(summary = "申请移除业务单元下的业务角色", description = "提交申请，经业务单元审批人批准后移除该角色绑定")
    public ApiResponse<PermissionRequest> requestBusinessUnitRoleRemoval(
            @CurrentUserId String userId,
            @RequestBody Map<String, Object> body) {
        Object buIdObj = body.get("businessUnitId");
        String businessUnitId = buIdObj != null ? buIdObj.toString() : null;
        Object roleIdObj = body.get("roleId");
        String roleId = roleIdObj != null ? roleIdObj.toString() : null;
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;
        String beneficiaryUserId = beneficiaryUserIdFromBody(body, userId);

        if (businessUnitId == null || businessUnitId.isEmpty()) {
            return ApiResponse.error(i18nService.getMessage("portal.bu_id_required"));
        }
        if (roleId == null || roleId.isBlank()) {
            return ApiResponse.error(i18nService.getMessage("validation.role_id_required"));
        }
        if (reason == null || reason.isEmpty()) {
            return ApiResponse.error(i18nService.getMessage("portal.reason_required"));
        }

        try {
            PermissionRequest request = permissionComponent.requestBusinessUnitRoleRemoval(userId, beneficiaryUserId, businessUnitId, roleId, reason);
            return ApiResponse.success(request);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/my-roles")
    @Operation(summary = "获取我的角色", description = "获取用户当前拥有的业务角色列表")
    public ApiResponse<List<Map<String, Object>>> getMyRoles(
            @CurrentUserId String userId) {
        List<Map<String, Object>> roles = permissionComponent.getUserCurrentRoles(userId);
        return ApiResponse.success(roles);
    }

    @GetMapping("/my-virtual-groups")
    @Operation(summary = "获取我的虚拟组", description = "已禁用：虚拟组不在 User Portal 提供")
    public ApiResponse<List<Map<String, Object>>> getMyVirtualGroups(
            @CurrentUserId String userId) {
        return ApiResponse.error(i18nService.getMessage("portal.virtual_group_not_in_portal"));
    }

    // ==================== 审批 API 端点 ====================

    @GetMapping("/approvals/pending")
    @Operation(summary = "获取待审批列表", description = "获取当前用户可以审批的权限申请")
    public ApiResponse<PageResponse<PermissionRequest>> getPendingApprovals(
            @CurrentUserId String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 检查审批权限
        if (!permissionComponent.isApprover(userId)) {
            return ApiResponse.error(i18nService.getMessage("portal.no_approval_permission"));
        }

        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Page<PermissionRequest> result = permissionComponent.getPendingApprovalsForUser(
                userId, PageRequest.of(safePage, safeSize));
        return ApiResponse.success(PageResponse.of(result));
    }

    @PostMapping("/approvals/{requestId}/approve")
    @Operation(summary = "批准申请", description = "批准权限申请")
    public ApiResponse<PermissionRequest> approveRequest(
            @CurrentUserId String userId,
            @PathVariable Long requestId,
            @RequestBody(required = false) Map<String, String> body) {
        // 检查审批权限
        if (!permissionComponent.isApprover(userId)) {
            return ApiResponse.error(i18nService.getMessage("portal.no_approval_permission"));
        }
        
        String comment = body != null ? body.get("comment") : null;
        
        try {
            PermissionRequest request = permissionComponent.approveRequest(requestId, userId, comment);
            return ApiResponse.success(request);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/approvals/{requestId}/reject")
    @Operation(summary = "拒绝申请", description = "拒绝权限申请（必须填写原因）")
    public ApiResponse<PermissionRequest> rejectRequest(
            @CurrentUserId String userId,
            @PathVariable Long requestId,
            @RequestBody Map<String, String> body) {
        // 检查审批权限
        if (!permissionComponent.isApprover(userId)) {
            return ApiResponse.error(i18nService.getMessage("portal.no_approval_permission"));
        }
        
        String comment = body != null ? body.get("comment") : null;
        if (comment == null || comment.trim().isEmpty()) {
            return ApiResponse.error(i18nService.getMessage("portal.reject_reason_required"));
        }
        
        try {
            PermissionRequest request = permissionComponent.rejectRequest(requestId, userId, comment);
            return ApiResponse.success(request);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/approvals/is-approver")
    @Operation(summary = "检查审批权限", description = "检查当前用户是否有审批权限")
    public ApiResponse<Map<String, Object>> isApprover(
            @CurrentUserId String userId) {
        boolean isApprover = permissionComponent.isApprover(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("isApprover", isApprover);
        return ApiResponse.success(result);
    }

    @GetMapping("/approvals/history")
    @Operation(summary = "获取审批历史", description = "获取当前用户的审批历史记录")
    public ApiResponse<PageResponse<PermissionRequest>> getApprovalHistory(
            @CurrentUserId String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 检查审批权限
        if (!permissionComponent.isApprover(userId)) {
            return ApiResponse.error(i18nService.getMessage("portal.no_approval_permission"));
        }

        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Page<PermissionRequest> result = permissionComponent.getApprovalHistoryForUser(
                userId, PageRequest.of(safePage, safeSize));
        return ApiResponse.success(PageResponse.of(result));
    }

    // ==================== 旧的 API 端点（保留兼容） ====================

    @GetMapping("/my")
    @Operation(summary = "获取我的权限列表")
    @Deprecated
    public ApiResponse<List<Map<String, Object>>> getMyPermissions(
            @CurrentUserId String userId) {
        List<Map<String, Object>> permissions = permissionComponent.getUserPermissions(userId);
        return ApiResponse.success(permissions);
    }

    @PostMapping("/request")
    @Operation(summary = "提交权限申请")
    @Deprecated
    public ApiResponse<PermissionRequest> submitRequest(
            @CurrentUserId String userId,
            @RequestBody @Valid PermissionRequestDto dto) {
        PermissionRequest request = permissionComponent.submitRequest(userId, dto);
        return ApiResponse.success(request);
    }

    @GetMapping("/requests")
    @Operation(summary = "获取我的申请记录")
    public ApiResponse<PageResponse<PermissionRequestListItem>> getMyRequests(
            @CurrentUserId String userId,
            @RequestParam(required = false) PermissionRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Page<PermissionRequestListItem> result = permissionComponent.getMyRequests(
                userId, status, PageRequest.of(safePage, safeSize));
        return ApiResponse.success(PageResponse.of(result));
    }

    @GetMapping("/requests/{requestId}")
    @Operation(summary = "获取申请详情")
    public ApiResponse<PermissionRequest> getRequestDetail(
            @CurrentUserId String userId,
            @PathVariable Long requestId) {
        return permissionComponent.getRequestDetailForViewer(requestId, userId)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(i18nService.getMessage("portal.request_not_found_or_forbidden")));
    }

    @DeleteMapping("/requests/{requestId}")
    @Operation(summary = "取消申请")
    public ApiResponse<Void> cancelRequest(
            @CurrentUserId String userId,
            @PathVariable Long requestId) {
        boolean success = permissionComponent.cancelRequest(userId, requestId);
        if (success) {
            return ApiResponse.success(null);
        }
        return ApiResponse.error(i18nService.getMessage("portal.cancel_request_failed"));
    }

    @PostMapping("/renew")
    @Operation(summary = "续期申请")
    @Deprecated
    public ApiResponse<PermissionRequest> renewPermission(
            @CurrentUserId String userId,
            @RequestBody Map<String, Object> body) {
        String permissionId = (String) body.get("permissionId");
        String validToStr = (String) body.get("validTo");
        String reason = (String) body.get("reason");
        
        LocalDateTime newValidTo = LocalDateTime.parse(validToStr);
        PermissionRequest request = permissionComponent.renewPermission(userId, permissionId, newValidTo, reason);
        return ApiResponse.success(request);
    }

    @GetMapping("/expiring")
    @Operation(summary = "获取即将过期的权限")
    @Deprecated
    public ApiResponse<List<Map<String, Object>>> getExpiringPermissions(
            @CurrentUserId String userId,
            @RequestParam(defaultValue = "30") int days) {
        List<Map<String, Object>> expiring = permissionComponent.getExpiringPermissions(userId, days);
        return ApiResponse.success(expiring);
    }

    private static String beneficiaryUserIdFromBody(Map<String, Object> body, String currentUserId) {
        if (body == null) {
            return currentUserId;
        }
        Object o = body.get("beneficiaryUserId");
        if (o == null || o.toString().isBlank()) {
            return currentUserId;
        }
        return o.toString().trim();
    }
}
