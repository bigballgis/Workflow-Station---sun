package com.portal.controller;

import com.portal.component.PermissionComponent;
import com.portal.dto.ApiResponse;
import com.portal.dto.PageResponse;
import com.portal.dto.PermissionRequestDto;
import com.portal.entity.PermissionRequest;
import com.portal.enums.PermissionRequestStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
@Tag(name = "权限管理", description = "权限申请和管理相关接口")
public class PermissionController {

    private final PermissionComponent permissionComponent;

    // ==================== 新的 API 端点 ====================

    @GetMapping("/available-roles")
    @Operation(summary = "获取可申请的业务角色", description = "获取用户可以申请的业务角色列表（排除已拥有的）")
    public ApiResponse<List<Map<String, Object>>> getAvailableRoles(
            @RequestHeader("X-User-Id") String userId) {
        List<Map<String, Object>> roles = permissionComponent.getAvailableRoles(userId);
        return ApiResponse.success(roles);
    }

    @GetMapping("/available-virtual-groups")
    @Operation(summary = "获取可加入的虚拟组", description = "获取用户可以加入的虚拟组列表（排除已加入的）")
    public ApiResponse<List<Map<String, Object>>> getAvailableVirtualGroups(
            @RequestHeader("X-User-Id") String userId) {
        List<Map<String, Object>> groups = permissionComponent.getAvailableVirtualGroups(userId);
        return ApiResponse.success(groups);
    }

    @GetMapping("/available-business-units")
    @Operation(summary = "获取可加入的业务单元", description = "获取用户可以加入的业务单元列表（排除已加入的）")
    public ApiResponse<List<Map<String, Object>>> getAvailableBusinessUnits(
            @RequestHeader("X-User-Id") String userId) {
        List<Map<String, Object>> businessUnits = permissionComponent.getAvailableBusinessUnits(userId);
        return ApiResponse.success(businessUnits);
    }

    @PostMapping("/request-role")
    @Operation(summary = "申请角色", description = "申请某个组织单元的业务角色（自动批准）")
    public ApiResponse<PermissionRequest> requestRole(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> body) {
        String roleId = body.get("roleId");
        String organizationUnitId = body.get("organizationUnitId");
        String reason = body.get("reason");
        
        if (roleId == null || roleId.isEmpty()) {
            return ApiResponse.error("角色ID不能为空");
        }
        if (organizationUnitId == null || organizationUnitId.isEmpty()) {
            return ApiResponse.error("组织单元ID不能为空");
        }
        if (reason == null || reason.isEmpty()) {
            return ApiResponse.error("申请理由不能为空");
        }
        
        try {
            PermissionRequest request = permissionComponent.requestRoleAssignment(userId, roleId, organizationUnitId, reason);
            return ApiResponse.success(request);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/request-virtual-group")
    @Operation(summary = "申请加入虚拟组", description = "申请加入虚拟组（自动批准）")
    public ApiResponse<PermissionRequest> requestVirtualGroup(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> body) {
        String virtualGroupId = body.get("virtualGroupId");
        String reason = body.get("reason");
        
        if (virtualGroupId == null || virtualGroupId.isEmpty()) {
            return ApiResponse.error("虚拟组ID不能为空");
        }
        if (reason == null || reason.isEmpty()) {
            return ApiResponse.error("申请理由不能为空");
        }
        
        try {
            PermissionRequest request = permissionComponent.requestVirtualGroupJoin(userId, virtualGroupId, reason);
            return ApiResponse.success(request);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/request-business-unit")
    @Operation(summary = "申请加入业务单元", description = "申请加入业务单元（自动批准）")
    public ApiResponse<PermissionRequest> requestBusinessUnit(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> body) {
        String businessUnitId = body.get("businessUnitId");
        String reason = body.get("reason");
        
        if (businessUnitId == null || businessUnitId.isEmpty()) {
            return ApiResponse.error("业务单元ID不能为空");
        }
        if (reason == null || reason.isEmpty()) {
            return ApiResponse.error("申请理由不能为空");
        }
        
        try {
            PermissionRequest request = permissionComponent.requestBusinessUnitJoin(userId, businessUnitId, reason);
            return ApiResponse.success(request);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/my-roles")
    @Operation(summary = "获取我的角色", description = "获取用户当前拥有的业务角色列表")
    public ApiResponse<List<Map<String, Object>>> getMyRoles(
            @RequestHeader("X-User-Id") String userId) {
        List<Map<String, Object>> roles = permissionComponent.getUserCurrentRoles(userId);
        return ApiResponse.success(roles);
    }

    @GetMapping("/my-virtual-groups")
    @Operation(summary = "获取我的虚拟组", description = "获取用户当前加入的虚拟组列表")
    public ApiResponse<List<Map<String, Object>>> getMyVirtualGroups(
            @RequestHeader("X-User-Id") String userId) {
        List<Map<String, Object>> groups = permissionComponent.getUserCurrentVirtualGroups(userId);
        return ApiResponse.success(groups);
    }

    // ==================== 审批 API 端点 ====================

    @GetMapping("/approvals/pending")
    @Operation(summary = "获取待审批列表", description = "获取当前用户可以审批的权限申请")
    public ApiResponse<PageResponse<PermissionRequest>> getPendingApprovals(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 检查审批权限
        if (!permissionComponent.isApprover(userId)) {
            return ApiResponse.error("您没有审批权限");
        }
        
        // 只返回用户可以审批的申请
        Page<PermissionRequest> result = permissionComponent.getPendingApprovalsForUser(userId, PageRequest.of(page, size));
        return ApiResponse.success(PageResponse.of(result));
    }

    @PostMapping("/approvals/{requestId}/approve")
    @Operation(summary = "批准申请", description = "批准权限申请")
    public ApiResponse<PermissionRequest> approveRequest(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long requestId,
            @RequestBody(required = false) Map<String, String> body) {
        // 检查审批权限
        if (!permissionComponent.isApprover(userId)) {
            return ApiResponse.error("您没有审批权限");
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
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long requestId,
            @RequestBody Map<String, String> body) {
        // 检查审批权限
        if (!permissionComponent.isApprover(userId)) {
            return ApiResponse.error("您没有审批权限");
        }
        
        String comment = body != null ? body.get("comment") : null;
        if (comment == null || comment.trim().isEmpty()) {
            return ApiResponse.error("拒绝申请必须填写原因");
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
            @RequestHeader("X-User-Id") String userId) {
        boolean isApprover = permissionComponent.isApprover(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("isApprover", isApprover);
        return ApiResponse.success(result);
    }

    @GetMapping("/approvals/history")
    @Operation(summary = "获取审批历史", description = "获取当前用户的审批历史记录")
    public ApiResponse<PageResponse<PermissionRequest>> getApprovalHistory(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 检查审批权限
        if (!permissionComponent.isApprover(userId)) {
            return ApiResponse.error("您没有审批权限");
        }
        
        Page<PermissionRequest> result = permissionComponent.getApprovalHistoryForUser(userId, PageRequest.of(page, size));
        return ApiResponse.success(PageResponse.of(result));
    }

    // ==================== 旧的 API 端点（保留兼容） ====================

    @GetMapping("/my")
    @Operation(summary = "获取我的权限列表")
    @Deprecated
    public ApiResponse<List<Map<String, Object>>> getMyPermissions(
            @RequestHeader("X-User-Id") String userId) {
        List<Map<String, Object>> permissions = permissionComponent.getUserPermissions(userId);
        return ApiResponse.success(permissions);
    }

    @PostMapping("/request")
    @Operation(summary = "提交权限申请")
    @Deprecated
    public ApiResponse<PermissionRequest> submitRequest(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody PermissionRequestDto dto) {
        PermissionRequest request = permissionComponent.submitRequest(userId, dto);
        return ApiResponse.success(request);
    }

    @GetMapping("/requests")
    @Operation(summary = "获取我的申请记录")
    public ApiResponse<PageResponse<PermissionRequest>> getMyRequests(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) PermissionRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PermissionRequest> result = permissionComponent.getMyRequests(userId, status, PageRequest.of(page, size));
        return ApiResponse.success(PageResponse.of(result));
    }

    @GetMapping("/requests/{requestId}")
    @Operation(summary = "获取申请详情")
    public ApiResponse<PermissionRequest> getRequestDetail(@PathVariable Long requestId) {
        return permissionComponent.getRequestDetail(requestId)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error("申请不存在"));
    }

    @DeleteMapping("/requests/{requestId}")
    @Operation(summary = "取消申请")
    public ApiResponse<Void> cancelRequest(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long requestId) {
        boolean success = permissionComponent.cancelRequest(userId, requestId);
        if (success) {
            return ApiResponse.success(null);
        }
        return ApiResponse.error("取消失败");
    }

    @PostMapping("/renew")
    @Operation(summary = "续期申请")
    @Deprecated
    public ApiResponse<PermissionRequest> renewPermission(
            @RequestHeader("X-User-Id") String userId,
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
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "30") int days) {
        List<Map<String, Object>> expiring = permissionComponent.getExpiringPermissions(userId, days);
        return ApiResponse.success(expiring);
    }
}
