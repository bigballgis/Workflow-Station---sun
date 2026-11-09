package com.admin.controller;

import com.admin.component.RoleHolderQueryComponent;
import com.admin.component.RoleListQueryComponent;
import com.admin.component.RoleReferenceFinder;
import com.admin.component.RolePermissionManagerComponent;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.RoleListQueryRequest;
import com.admin.dto.response.ReferenceNotice;
import com.admin.dto.response.RoleHolder;
import com.admin.dto.response.RoleListItem;
import com.admin.util.EntityTypeConverter;
import com.platform.security.entity.Role;
import com.admin.enums.RoleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.admin.security.AdminAccess;
import com.admin.security.AdminAudience;

/**
 * Role Management Controller
 */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Tag(name = "Role Management", description = "Role creation, configuration and holder APIs")
@AdminAccess(value = AdminAudience.WORKSTATION, services = true)
public class RoleController {
    
    private final RolePermissionManagerComponent rolePermissionManager;
    private final RoleListQueryComponent roleListQueryComponent;
    private final RoleReferenceFinder roleReferenceFinder;
    private final RoleHolderQueryComponent roleHolderQuery;
    
    // ==================== Role CRUD ====================
    
    @AdminAccess(AdminAudience.LEAD_WRITE)
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
    @Operation(summary = "Get role list", description = "Supports filtering roles by type, paginated")
    public ResponseEntity<com.admin.dto.response.PageResult<Role>> getRoles(
            @RequestParam(required = false) RoleType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String typeStr = type != null ? EntityTypeConverter.fromRoleType(type) : null;
        org.springframework.data.domain.Page<Role> rolePage = rolePermissionManager.getRolesPaged(page, size, typeStr);
        com.admin.dto.response.PageResult<Role> result = com.admin.dto.response.PageResult.of(
                rolePage.getContent(),
                rolePage.getNumber(),
                rolePage.getSize(),
                rolePage.getTotalElements());
        return ResponseEntity.ok(result);
    }

    @AdminAccess(AdminAudience.SHARED_READ)
    @PostMapping("/query")
    @Operation(summary = "Page roles", description = "Shared list: COUNT(*) and the page share one predicate including SYSTEM/CUSTOM tab")
    public ResponseEntity<AdminListPage<RoleListItem>> queryRoles(
            @RequestBody @Valid RoleListQueryRequest request) {
        return ResponseEntity.ok(roleListQueryComponent.query(request));
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
    
    @AdminAccess(AdminAudience.LEAD_WRITE)
    @DeleteMapping("/{roleId}")
    @Operation(summary = "Delete role")
    public ResponseEntity<Void> deleteRole(@PathVariable String roleId) {
        rolePermissionManager.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }
    
    @AdminAccess(AdminAudience.LEAD_WRITE)
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

    
    // ==================== References and Holders ====================
    
    /** Function units and other places that use the role, shown before it is changed or deleted (§8.3). */
    @AdminAccess(AdminAudience.LEAD_WRITE)
    @GetMapping("/{roleId}/references")
    @Operation(summary = "Where the role is used (notice before change or delete)")
    public ResponseEntity<ReferenceNotice> getRoleReferences(@PathVariable String roleId) {
        return ResponseEntity.ok(roleReferenceFinder.findForNotice(rolePermissionManager.getRole(roleId)));
    }

    /** Who holds the role: SYSTEM group members, team lists, or business unit memberships by role type. */
    @AdminAccess(AdminAudience.SHARED_READ)
    @GetMapping("/{roleId}/holders")
    @Operation(summary = "Holders of the role")
    public ResponseEntity<List<RoleHolder>> getRoleHolders(@PathVariable String roleId) {
        return ResponseEntity.ok(roleHolderQuery.holders(roleId));
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
