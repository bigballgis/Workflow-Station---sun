package com.admin.controller;

import com.admin.enums.DeveloperPermission;
import com.admin.service.DeveloperPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 开发者权限控制器
 */
@RestController
@RequestMapping("/developer-permissions")
@RequiredArgsConstructor
@Tag(name = "开发者权限", description = "开发者权限查询和管理接口")
public class DeveloperPermissionController {
    
    private final DeveloperPermissionService permissionService;
    
    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户的开发者权限", description = "返回用户拥有的所有开发者权限代码列表")
    public ResponseEntity<List<String>> getUserPermissions(@PathVariable String userId) {
        List<String> permissions = permissionService.getUserPermissionCodes(userId);
        return ResponseEntity.ok(permissions);
    }
    
    @GetMapping("/user/{userId}/check")
    @Operation(summary = "检查用户是否有指定权限")
    public ResponseEntity<Boolean> checkPermission(
            @PathVariable String userId,
            @RequestParam String permission) {
        boolean hasPermission = permissionService.hasPermission(userId, permission);
        return ResponseEntity.ok(hasPermission);
    }
    
    @GetMapping("/role/{roleId}")
    @Operation(summary = "获取角色的开发者权限")
    public ResponseEntity<Set<DeveloperPermission>> getRolePermissions(@PathVariable String roleId) {
        Set<DeveloperPermission> permissions = permissionService.getRolePermissions(roleId);
        return ResponseEntity.ok(permissions);
    }
    
    @GetMapping("/all")
    @Operation(summary = "获取所有可用的开发者权限")
    public ResponseEntity<List<PermissionInfo>> getAllPermissions() {
        List<PermissionInfo> permissions = Arrays.stream(DeveloperPermission.values())
            .map(p -> new PermissionInfo(p.getCode(), p.getDescription()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(permissions);
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class PermissionInfo {
        private String code;
        private String description;
    }
}
