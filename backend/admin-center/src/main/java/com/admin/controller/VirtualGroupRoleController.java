package com.admin.controller;

import com.platform.security.entity.Role;
import com.admin.service.VirtualGroupRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import com.admin.security.AdminAccess;
import com.admin.security.AdminAudience;

/**
 * Virtual group role binding controller.
 * Each virtual group can only bind one role (single role binding).
 */
@RestController
@RequestMapping("/virtual-groups/{groupId}")
@RequiredArgsConstructor
@Tag(name = "Virtual Group Role Binding", description = "Read the fixed role of a SYSTEM group; group role bindings are not editable (platform access model §5.1)")
@AdminAccess(value = AdminAudience.WORKSTATION, services = true)
public class VirtualGroupRoleController {
    
    private final VirtualGroupRoleService virtualGroupRoleService;
    
    @GetMapping("/role")
    @Operation(summary = "Get bound role for virtual group")
    public ResponseEntity<Role> getBoundRole(@PathVariable String groupId) {
        Optional<Role> role = virtualGroupRoleService.getBoundRole(groupId);
        return role.map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
