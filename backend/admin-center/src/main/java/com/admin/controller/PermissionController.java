package com.admin.controller;

import com.admin.component.PermissionConflictComponent;
import com.admin.component.PermissionDelegationComponent;
import com.admin.component.RolePermissionManagerComponent;
import com.admin.dto.request.ConflictResolutionRequest;
import com.admin.dto.request.PermissionDelegationRequest;
import com.admin.dto.response.ConflictDetectionResult;
import com.admin.dto.response.PermissionCheckResult;
import com.admin.dto.response.PermissionDelegationResult;
import com.admin.entity.Permission;
import com.admin.entity.PermissionConflict;
import com.admin.repository.PermissionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 权限管理控制器
 */
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@Tag(name = "权限管理", description = "权限查询、检查、委托、冲突解决等接口")
public class PermissionController {
    
    private final PermissionRepository permissionRepository;
    private final RolePermissionManagerComponent rolePermissionManager;
    private final PermissionDelegationComponent delegationComponent;
    private final PermissionConflictComponent conflictComponent;
    
    // ==================== 权限查询 ====================
    
    @GetMapping
    @Operation(summary = "获取所有权限")
    public ResponseEntity<List<Permission>> getAllPermissions() {
        List<Permission> permissions = permissionRepository.findAll();
        return ResponseEntity.ok(permissions);
    }
    
    @GetMapping("/{permissionId}")
    @Operation(summary = "获取权限详情")
    public ResponseEntity<Permission> getPermission(@PathVariable String permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new RuntimeException("权限不存在: " + permissionId));
        return ResponseEntity.ok(permission);
    }
    
    @GetMapping("/tree")
    @Operation(summary = "获取权限树")
    public ResponseEntity<List<Permission>> getPermissionTree() {
        List<Permission> rootPermissions = permissionRepository.findRootPermissions();
        return ResponseEntity.ok(rootPermissions);
    }
    
    @GetMapping("/resource/{resource}")
    @Operation(summary = "根据资源获取权限")
    public ResponseEntity<List<Permission>> getPermissionsByResource(@PathVariable String resource) {
        List<Permission> permissions = permissionRepository.findByResource(resource);
        return ResponseEntity.ok(permissions);
    }
    
    // ==================== 权限检查 ====================
    
    @GetMapping("/check")
    @Operation(summary = "检查用户权限")
    public ResponseEntity<PermissionCheckResult> checkPermission(
            @RequestParam String userId,
            @RequestParam String resource,
            @RequestParam String action) {
        PermissionCheckResult result = rolePermissionManager.checkPermission(userId, resource, action);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户的所有有效权限")
    public ResponseEntity<Set<Permission>> getUserPermissions(@PathVariable String userId) {
        Set<Permission> permissions = rolePermissionManager.getUserEffectivePermissions(userId);
        return ResponseEntity.ok(permissions);
    }

    
    // ==================== 权限委托 ====================
    
    @PostMapping("/delegations")
    @Operation(summary = "创建权限委托")
    public ResponseEntity<PermissionDelegationResult> createDelegation(
            @RequestBody @Valid PermissionDelegationRequest request) {
        PermissionDelegationResult result = delegationComponent.createDelegation(request);
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/delegations/{delegationId}")
    @Operation(summary = "撤销权限委托")
    public ResponseEntity<Void> revokeDelegation(
            @PathVariable String delegationId,
            @RequestParam String reason,
            @RequestHeader("X-User-Id") String revokedBy) {
        delegationComponent.revokeDelegation(delegationId, revokedBy, reason);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/delegations/user/{userId}")
    @Operation(summary = "获取用户的委托权限")
    public ResponseEntity<List<PermissionDelegationResult>> getUserDelegatedPermissions(
            @PathVariable String userId) {
        List<PermissionDelegationResult> delegations = delegationComponent.getUserDelegatedPermissions(userId);
        return ResponseEntity.ok(delegations);
    }
    
    @GetMapping("/delegations/user/{userId}/out")
    @Operation(summary = "获取用户委托出去的权限")
    public ResponseEntity<List<PermissionDelegationResult>> getUserDelegatedOutPermissions(
            @PathVariable String userId) {
        List<PermissionDelegationResult> delegations = delegationComponent.getUserDelegatedOutPermissions(userId);
        return ResponseEntity.ok(delegations);
    }
    
    // ==================== 权限冲突 ====================
    
    @GetMapping("/conflicts/detect/{userId}")
    @Operation(summary = "检测用户权限冲突")
    public ResponseEntity<ConflictDetectionResult> detectConflicts(@PathVariable String userId) {
        ConflictDetectionResult result = conflictComponent.detectUserPermissionConflicts(userId);
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/conflicts/resolve")
    @Operation(summary = "解决权限冲突")
    public ResponseEntity<Void> resolveConflict(@RequestBody @Valid ConflictResolutionRequest request) {
        conflictComponent.resolveConflict(request);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/conflicts/pending")
    @Operation(summary = "获取待解决的权限冲突")
    public ResponseEntity<List<PermissionConflict>> getPendingConflicts() {
        List<PermissionConflict> conflicts = conflictComponent.getPendingConflicts();
        return ResponseEntity.ok(conflicts);
    }
    
    @GetMapping("/conflicts/user/{userId}")
    @Operation(summary = "获取用户的权限冲突")
    public ResponseEntity<List<PermissionConflict>> getUserConflicts(@PathVariable String userId) {
        List<PermissionConflict> conflicts = conflictComponent.getUserConflicts(userId);
        return ResponseEntity.ok(conflicts);
    }
}
