package com.admin.controller;

import com.admin.component.RoleMemberManagerComponent;
import com.admin.component.RolePermissionManagerComponent;
import com.admin.dto.request.BatchRoleMemberRequest;
import com.admin.dto.request.PermissionConfig;
import com.admin.dto.response.BatchRoleMemberResult;
import com.admin.util.EntityTypeConverter;
import com.platform.security.entity.Permission;
import com.admin.entity.PermissionChangeHistory;
import com.platform.security.entity.Role;
import com.platform.security.entity.UserRole;
import com.platform.security.util.SecurityContextUtils;
import com.admin.enums.RoleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import com.platform.common.i18n.I18nService;

/**
 * Role Management Controller
 */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Tag(name = "Role Management", description = "Role creation, configuration, member management APIs")
public class RoleController {
    
    private final RolePermissionManagerComponent rolePermissionManager;
    private final RoleMemberManagerComponent roleMemberManager;
    private final I18nService i18nService;
    
    // ==================== Role CRUD ====================
    
    @PostMapping
    @Operation(summary = "Create role")
    public ResponseEntity<Role> createRole(@RequestBody @Valid CreateRoleRequest request) {
        Role role = rolePermissionManager.createRole(
                request.getName(),
                request.getCode(),
                request.getType(),
                request.getDisplayName()
        );
        return ResponseEntity.ok(role);
    }
    
    @GetMapping("/{roleId}")
    @Operation(summary = "Get role detail")
    public ResponseEntity<Role> getRole(@PathVariable String roleId) {
        Role role = rolePermissionManager.getRole(roleId);
        return ResponseEntity.ok(role);
    }
    
    @GetMapping
    @Operation(summary = "Get role list", description = "Supports filtering roles by type")
    public ResponseEntity<List<Role>> getRoles(
            @RequestParam(required = false) RoleType type) {
        List<Role> roles;
        if (type != null) {
            // Convert enum to String since Role.type is String
            String typeStr = EntityTypeConverter.fromRoleType(type);
            roles = rolePermissionManager.getRolesByType(typeStr);
        } else {
            roles = rolePermissionManager.getAllRoles();
        }
        return ResponseEntity.ok(roles);
    }
    
    @GetMapping("/business")
    @Operation(summary = "Get business role list", description = "Get all business roles for function unit access configuration")
    public ResponseEntity<List<Role>> getBusinessRoles() {
        List<Role> roles = rolePermissionManager.getBusinessRoles();
        return ResponseEntity.ok(roles);
    }
    
    @GetMapping("/developer")
    @Operation(summary = "Get developer role list", description = "Get all developer roles")
    public ResponseEntity<List<Role>> getDeveloperRoles() {
        List<Role> roles = rolePermissionManager.getDeveloperRoles();
        return ResponseEntity.ok(roles);
    }
    
    @DeleteMapping("/{roleId}")
    @Operation(summary = "Delete role")
    public ResponseEntity<Void> deleteRole(@PathVariable String roleId) {
        rolePermissionManager.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }
    
    @PutMapping("/{roleId}")
    @Operation(summary = "Update role")
    public ResponseEntity<Role> updateRole(
            @PathVariable String roleId,
            @RequestBody @Valid UpdateRoleRequest request) {
        Role role = rolePermissionManager.updateRole(
                roleId,
                request.getName(),
                request.getDisplayName(),
                request.getStatus()
        );
        return ResponseEntity.ok(role);
    }

    
    // ==================== Permission Configuration ====================
    
    @PutMapping("/{roleId}/permissions")
    @Operation(summary = "Configure role permissions")
    public ResponseEntity<Void> configurePermissions(
            @PathVariable String roleId,
            @RequestBody @Valid List<PermissionConfig> permissions) {
        rolePermissionManager.configureRolePermissions(roleId, permissions);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/{roleId}/permissions")
    @Operation(summary = "Get role permissions")
    public ResponseEntity<Set<Permission>> getRolePermissions(@PathVariable String roleId) {
        Set<Permission> permissions = rolePermissionManager.getEffectivePermissions(roleId);
        return ResponseEntity.ok(permissions);
    }
    
    // ==================== Member Management ====================
    
    @GetMapping("/{roleId}/members")
    @Operation(summary = "Get role member list")
    public ResponseEntity<List<UserRole>> getRoleMembers(@PathVariable String roleId) {
        List<UserRole> members = roleMemberManager.getRoleMembers(roleId);
        return ResponseEntity.ok(members);
    }
    
    @GetMapping("/{roleId}/members/paged")
    @Operation(summary = "Get role members (paged)")
    public ResponseEntity<Page<UserRole>> getRoleMembersPaged(
            @PathVariable String roleId,
            Pageable pageable) {
        Page<UserRole> members = roleMemberManager.getRoleMembersPaged(roleId, pageable);
        return ResponseEntity.ok(members);
    }
    
    @PostMapping("/{roleId}/members/{userId}")
    @Operation(summary = "Add role member")
    public ResponseEntity<Void> addMember(
            @PathVariable String roleId,
            @PathVariable String userId,
            @RequestParam(required = false) String reason) {
        String operatedBy = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        roleMemberManager.assignRoleToUser(userId, roleId, operatedBy, reason);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{roleId}/members/{userId}")
    @Operation(summary = "Remove role member")
    public ResponseEntity<Void> removeMember(
            @PathVariable String roleId,
            @PathVariable String userId,
            @RequestParam(required = false) String reason) {
        String operatedBy = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        roleMemberManager.removeRoleFromUser(userId, roleId, operatedBy, reason);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{roleId}/members/batch")
    @Operation(summary = "Batch add role members")
    public ResponseEntity<BatchRoleMemberResult> batchAddMembers(
            @PathVariable String roleId,
            @RequestBody @Valid BatchRoleMemberRequest request) {
        String operatedBy = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        request.setRoleId(roleId);
        BatchRoleMemberResult result = roleMemberManager.batchAddMembers(request, operatedBy);
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/{roleId}/members/batch")
    @Operation(summary = "Batch remove role members")
    public ResponseEntity<BatchRoleMemberResult> batchRemoveMembers(
            @PathVariable String roleId,
            @RequestBody @Valid BatchRoleMemberRequest request) {
        String operatedBy = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        request.setRoleId(roleId);
        BatchRoleMemberResult result = roleMemberManager.batchRemoveMembers(request, operatedBy);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{roleId}/members/count")
    @Operation(summary = "Get role member count")
    public ResponseEntity<Long> getMemberCount(@PathVariable String roleId) {
        long count = roleMemberManager.getRoleMemberCount(roleId);
        return ResponseEntity.ok(count);
    }

    
    // ==================== Change History ====================
    
    @GetMapping("/{roleId}/history")
    @Operation(summary = "Get role change history")
    public ResponseEntity<List<PermissionChangeHistory>> getRoleHistory(@PathVariable String roleId) {
        List<PermissionChangeHistory> history = roleMemberManager.getRoleChangeHistory(roleId);
        return ResponseEntity.ok(history);
    }
    
    @GetMapping("/{roleId}/history/paged")
    @Operation(summary = "Get role change history (paged)")
    public ResponseEntity<Page<PermissionChangeHistory>> getRoleHistoryPaged(
            @PathVariable String roleId,
            Pageable pageable) {
        Page<PermissionChangeHistory> history = roleMemberManager.getRoleChangeHistoryPaged(roleId, pageable);
        return ResponseEntity.ok(history);
    }
    
    // ==================== Request Objects ====================
    
    @lombok.Data
    public static class CreateRoleRequest {
        @jakarta.validation.constraints.NotBlank(message = "{validation.role_name_required}")
        private String name;
        
        @jakarta.validation.constraints.NotBlank(message = "{validation.role_code_required}")
        private String code;
        
        @jakarta.validation.constraints.NotNull(message = "{validation.role_type_required}")
        private RoleType type;
        
        private String displayName;
    }
    
    @lombok.Data
    public static class UpdateRoleRequest {
        private String name;
        private String displayName;
        private String status;
    }
}
