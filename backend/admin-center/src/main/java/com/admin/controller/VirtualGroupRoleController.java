package com.admin.controller;

import com.platform.security.entity.Role;
import com.admin.service.VirtualGroupRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Virtual group role binding controller.
 * Each virtual group can only bind one role (single role binding).
 */
@RestController
@RequestMapping("/virtual-groups/{groupId}")
@RequiredArgsConstructor
@Tag(name = "Virtual Group Role Binding", description = "Manage bindings between virtual groups and business roles (single role binding)")
public class VirtualGroupRoleController {
    
    private final VirtualGroupRoleService virtualGroupRoleService;
    
    @GetMapping("/role")
    @Operation(summary = "Get bound role for virtual group")
    public ResponseEntity<Role> getBoundRole(@PathVariable String groupId) {
        Optional<Role> role = virtualGroupRoleService.getBoundRole(groupId);
        return role.map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
    
    @PostMapping("/role")
    @Operation(summary = "Bind a role to the virtual group (replaces existing binding)")
    public ResponseEntity<Void> bindRole(
            @PathVariable String groupId,
            @RequestBody Map<String, String> request) {
        String roleId = request.get("roleId");
        if (roleId == null || roleId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        virtualGroupRoleService.bindRole(groupId, roleId);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/role")
    @Operation(summary = "Unbind role from virtual group")
    public ResponseEntity<Void> unbindRole(@PathVariable String groupId) {
        virtualGroupRoleService.unbindRole(groupId);
        return ResponseEntity.ok().build();
    }
}
