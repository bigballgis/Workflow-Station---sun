package com.admin.controller;

import com.admin.component.PermissionConflictComponent;
import com.admin.component.PermissionDelegationComponent;
import com.admin.component.RolePermissionManagerComponent;
import com.admin.dto.request.ConflictResolutionRequest;
import com.admin.dto.request.PermissionDelegationRequest;
import com.admin.dto.response.ConflictDetectionResult;
import com.admin.dto.response.PermissionCheckResult;
import com.admin.dto.response.PermissionDelegationResult;
import com.platform.security.entity.Permission;
import com.platform.security.util.SecurityContextUtils;
import com.admin.entity.PermissionConflict;
import com.admin.repository.PermissionRepository;
import com.platform.common.i18n.I18nService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * Permission management controller
 */
@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Permission Management", description = "Permission query, check, delegation, and conflict resolution APIs")
public class PermissionController {
    
    private final PermissionRepository permissionRepository;
    private final RolePermissionManagerComponent rolePermissionManager;
    private final PermissionDelegationComponent delegationComponent;
    private final PermissionConflictComponent conflictComponent;
    private final I18nService i18nService;
    
    // ==================== Permission Query ====================
    
    @GetMapping
    @Operation(summary = "Get all permissions")
    public ResponseEntity<List<Permission>> getAllPermissions() {
        List<Permission> permissions = permissionRepository.findAll();
        return ResponseEntity.ok(permissions);
    }
    
    @GetMapping("/{permissionId}")
    @Operation(summary = "Get permission detail")
    public ResponseEntity<Permission> getPermission(@PathVariable String permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new RuntimeException(
                        i18nService.getMessage("admin.permission.not_found_by_id", permissionId)));
        return ResponseEntity.ok(permission);
    }
    
    @GetMapping("/resource/{resource}")
    @Operation(summary = "Get permissions by resource")
    public ResponseEntity<List<Permission>> getPermissionsByResource(@PathVariable String resource) {
        List<Permission> permissions = permissionRepository.findByResource(resource);
        return ResponseEntity.ok(permissions);
    }
    
    // ==================== Permission Check ====================
    
    @GetMapping("/check")
    @Operation(summary = "Check user permission")
    public ResponseEntity<PermissionCheckResult> checkPermission(
            @RequestParam String userId,
            @RequestParam String resource,
            @RequestParam String action) {
        PermissionCheckResult result = rolePermissionManager.checkPermission(userId, resource, action);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all effective permissions for user")
    public ResponseEntity<Set<Permission>> getUserPermissions(@PathVariable String userId) {
        Set<Permission> permissions = rolePermissionManager.getUserEffectivePermissions(userId);
        return ResponseEntity.ok(permissions);
    }

    
    // ==================== Permission Delegation ====================
    
    @PostMapping("/delegations")
    @Operation(summary = "Create permission delegation")
    public ResponseEntity<PermissionDelegationResult> createDelegation(
            @RequestBody @Valid PermissionDelegationRequest request) {
        String currentUserId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(
                        i18nService.getMessage("auth.unauthorized")));
        // Prevent delegator identity spoofing: delegator must be the authenticated user (super admin exempt)
        if (!SecurityContextUtils.isSuperAdmin()
                && !currentUserId.equals(request.getDelegatorId())) {
            log.warn("createDelegation denied: delegator mismatch (auth={}, requested={})",
                    currentUserId, request.getDelegatorId());
            throw new RuntimeException(
                    i18nService.getMessage("admin.permission.can_only_delegate_own"));
        }
        PermissionDelegationResult result = delegationComponent.createDelegation(request);
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/delegations/{delegationId}")
    @Operation(summary = "Revoke permission delegation")
    public ResponseEntity<Void> revokeDelegation(
            @PathVariable String delegationId,
            @RequestParam String reason) {
        String revokedBy = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> {
                    log.warn("revokeDelegation denied: no authenticated user for delegation {}", delegationId);
                    return new RuntimeException(
                            i18nService.getMessage("auth.unauthorized"));
                });
        delegationComponent.revokeDelegation(delegationId, revokedBy, reason);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/delegations/user/{userId}")
    @Operation(summary = "Get user's delegated permissions")
    public ResponseEntity<List<PermissionDelegationResult>> getUserDelegatedPermissions(
            @PathVariable String userId) {
        List<PermissionDelegationResult> delegations = delegationComponent.getUserDelegatedPermissions(userId);
        return ResponseEntity.ok(delegations);
    }
    
    @GetMapping("/delegations/user/{userId}/out")
    @Operation(summary = "Get permissions the user has delegated out")
    public ResponseEntity<List<PermissionDelegationResult>> getUserDelegatedOutPermissions(
            @PathVariable String userId) {
        List<PermissionDelegationResult> delegations = delegationComponent.getUserDelegatedOutPermissions(userId);
        return ResponseEntity.ok(delegations);
    }
    
    // ==================== Permission Conflict ====================
    
    @GetMapping("/conflicts/detect/{userId}")
    @Operation(summary = "Detect user permission conflicts")
    public ResponseEntity<ConflictDetectionResult> detectConflicts(@PathVariable String userId) {
        ConflictDetectionResult result = conflictComponent.detectUserPermissionConflicts(userId);
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/conflicts/resolve")
    @Operation(summary = "Resolve permission conflict")
    public ResponseEntity<Void> resolveConflict(@RequestBody @Valid ConflictResolutionRequest request) {
        conflictComponent.resolveConflict(request);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/conflicts/pending")
    @Operation(summary = "Get pending permission conflicts")
    public ResponseEntity<List<PermissionConflict>> getPendingConflicts() {
        List<PermissionConflict> conflicts = conflictComponent.getPendingConflicts();
        return ResponseEntity.ok(conflicts);
    }
    
    @GetMapping("/conflicts/user/{userId}")
    @Operation(summary = "Get user's permission conflicts")
    public ResponseEntity<List<PermissionConflict>> getUserConflicts(@PathVariable String userId) {
        List<PermissionConflict> conflicts = conflictComponent.getUserConflicts(userId);
        return ResponseEntity.ok(conflicts);
    }
}
