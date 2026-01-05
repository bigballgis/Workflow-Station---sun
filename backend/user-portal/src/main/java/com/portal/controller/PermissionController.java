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

    @GetMapping("/my")
    @Operation(summary = "获取我的权限列表")
    public ApiResponse<List<Map<String, Object>>> getMyPermissions(
            @RequestHeader("X-User-Id") String userId) {
        List<Map<String, Object>> permissions = permissionComponent.getUserPermissions(userId);
        return ApiResponse.success(permissions);
    }

    @PostMapping("/request")
    @Operation(summary = "提交权限申请")
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
    public ApiResponse<List<Map<String, Object>>> getExpiringPermissions(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "30") int days) {
        List<Map<String, Object>> expiring = permissionComponent.getExpiringPermissions(userId, days);
        return ApiResponse.success(expiring);
    }
}
