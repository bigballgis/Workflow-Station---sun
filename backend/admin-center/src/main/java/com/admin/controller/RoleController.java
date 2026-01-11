package com.admin.controller;

import com.admin.component.RoleMemberManagerComponent;
import com.admin.component.RolePermissionManagerComponent;
import com.admin.dto.request.BatchRoleMemberRequest;
import com.admin.dto.request.PermissionConfig;
import com.admin.dto.response.BatchRoleMemberResult;
import com.admin.entity.Permission;
import com.admin.entity.PermissionChangeHistory;
import com.admin.entity.Role;
import com.admin.entity.UserRole;
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

/**
 * 角色管理控制器
 */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Tag(name = "角色管理", description = "角色的创建、配置、成员管理等接口")
public class RoleController {
    
    private final RolePermissionManagerComponent rolePermissionManager;
    private final RoleMemberManagerComponent roleMemberManager;
    
    // ==================== 角色 CRUD ====================
    
    @PostMapping
    @Operation(summary = "创建角色")
    public ResponseEntity<Role> createRole(@RequestBody @Valid CreateRoleRequest request) {
        Role role = rolePermissionManager.createRole(
                request.getName(),
                request.getCode(),
                request.getType(),
                request.getDescription()
        );
        return ResponseEntity.ok(role);
    }
    
    @GetMapping("/{roleId}")
    @Operation(summary = "获取角色详情")
    public ResponseEntity<Role> getRole(@PathVariable String roleId) {
        Role role = rolePermissionManager.getRole(roleId);
        return ResponseEntity.ok(role);
    }
    
    @GetMapping
    @Operation(summary = "获取角色列表", description = "支持按类型筛选角色")
    public ResponseEntity<List<Role>> getRoles(
            @RequestParam(required = false) RoleType type) {
        List<Role> roles;
        if (type != null) {
            roles = rolePermissionManager.getRolesByType(type);
        } else {
            roles = rolePermissionManager.getAllRoles();
        }
        return ResponseEntity.ok(roles);
    }
    
    @GetMapping("/business")
    @Operation(summary = "获取业务角色列表", description = "获取所有业务角色，用于功能单元访问配置")
    public ResponseEntity<List<Role>> getBusinessRoles() {
        List<Role> roles = rolePermissionManager.getBusinessRoles();
        return ResponseEntity.ok(roles);
    }
    
    @GetMapping("/developer")
    @Operation(summary = "获取开发角色列表", description = "获取所有开发角色")
    public ResponseEntity<List<Role>> getDeveloperRoles() {
        List<Role> roles = rolePermissionManager.getDeveloperRoles();
        return ResponseEntity.ok(roles);
    }
    
    @DeleteMapping("/{roleId}")
    @Operation(summary = "删除角色")
    public ResponseEntity<Void> deleteRole(@PathVariable String roleId) {
        rolePermissionManager.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }

    
    // ==================== 权限配置 ====================
    
    @PutMapping("/{roleId}/permissions")
    @Operation(summary = "配置角色权限")
    public ResponseEntity<Void> configurePermissions(
            @PathVariable String roleId,
            @RequestBody @Valid List<PermissionConfig> permissions) {
        rolePermissionManager.configureRolePermissions(roleId, permissions);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/{roleId}/permissions")
    @Operation(summary = "获取角色权限")
    public ResponseEntity<Set<Permission>> getRolePermissions(@PathVariable String roleId) {
        Set<Permission> permissions = rolePermissionManager.getEffectivePermissions(roleId);
        return ResponseEntity.ok(permissions);
    }
    
    // ==================== 成员管理 ====================
    
    @GetMapping("/{roleId}/members")
    @Operation(summary = "获取角色成员列表")
    public ResponseEntity<List<UserRole>> getRoleMembers(@PathVariable String roleId) {
        List<UserRole> members = roleMemberManager.getRoleMembers(roleId);
        return ResponseEntity.ok(members);
    }
    
    @GetMapping("/{roleId}/members/paged")
    @Operation(summary = "分页获取角色成员")
    public ResponseEntity<Page<UserRole>> getRoleMembersPaged(
            @PathVariable String roleId,
            Pageable pageable) {
        Page<UserRole> members = roleMemberManager.getRoleMembersPaged(roleId, pageable);
        return ResponseEntity.ok(members);
    }
    
    @PostMapping("/{roleId}/members/{userId}")
    @Operation(summary = "添加角色成员")
    public ResponseEntity<Void> addMember(
            @PathVariable String roleId,
            @PathVariable String userId,
            @RequestParam(required = false) String reason,
            @RequestHeader("X-User-Id") String operatedBy) {
        roleMemberManager.assignRoleToUser(userId, roleId, operatedBy, reason);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{roleId}/members/{userId}")
    @Operation(summary = "移除角色成员")
    public ResponseEntity<Void> removeMember(
            @PathVariable String roleId,
            @PathVariable String userId,
            @RequestParam(required = false) String reason,
            @RequestHeader("X-User-Id") String operatedBy) {
        roleMemberManager.removeRoleFromUser(userId, roleId, operatedBy, reason);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{roleId}/members/batch")
    @Operation(summary = "批量添加角色成员")
    public ResponseEntity<BatchRoleMemberResult> batchAddMembers(
            @PathVariable String roleId,
            @RequestBody @Valid BatchRoleMemberRequest request,
            @RequestHeader("X-User-Id") String operatedBy) {
        request.setRoleId(roleId);
        BatchRoleMemberResult result = roleMemberManager.batchAddMembers(request, operatedBy);
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/{roleId}/members/batch")
    @Operation(summary = "批量移除角色成员")
    public ResponseEntity<BatchRoleMemberResult> batchRemoveMembers(
            @PathVariable String roleId,
            @RequestBody @Valid BatchRoleMemberRequest request,
            @RequestHeader("X-User-Id") String operatedBy) {
        request.setRoleId(roleId);
        BatchRoleMemberResult result = roleMemberManager.batchRemoveMembers(request, operatedBy);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{roleId}/members/count")
    @Operation(summary = "获取角色成员数量")
    public ResponseEntity<Long> getMemberCount(@PathVariable String roleId) {
        long count = roleMemberManager.getRoleMemberCount(roleId);
        return ResponseEntity.ok(count);
    }

    
    // ==================== 变更历史 ====================
    
    @GetMapping("/{roleId}/history")
    @Operation(summary = "获取角色变更历史")
    public ResponseEntity<List<PermissionChangeHistory>> getRoleHistory(@PathVariable String roleId) {
        List<PermissionChangeHistory> history = roleMemberManager.getRoleChangeHistory(roleId);
        return ResponseEntity.ok(history);
    }
    
    @GetMapping("/{roleId}/history/paged")
    @Operation(summary = "分页获取角色变更历史")
    public ResponseEntity<Page<PermissionChangeHistory>> getRoleHistoryPaged(
            @PathVariable String roleId,
            Pageable pageable) {
        Page<PermissionChangeHistory> history = roleMemberManager.getRoleChangeHistoryPaged(roleId, pageable);
        return ResponseEntity.ok(history);
    }
    
    // ==================== 请求对象 ====================
    
    @lombok.Data
    public static class CreateRoleRequest {
        @jakarta.validation.constraints.NotBlank(message = "角色名称不能为空")
        private String name;
        
        @jakarta.validation.constraints.NotBlank(message = "角色编码不能为空")
        private String code;
        
        @jakarta.validation.constraints.NotNull(message = "角色类型不能为空")
        private RoleType type;
        
        private String description;
    }
}
