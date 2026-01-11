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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/permissions")
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

    @GetMapping("/departments")
    @Operation(summary = "获取部门列表", description = "获取所有部门列表，用于角色申请时选择组织单元")
    public ApiResponse<List<Map<String, Object>>> getDepartments() {
        List<Map<String, Object>> departments = permissionComponent.getDepartments();
        return ApiResponse.success(departments);
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
