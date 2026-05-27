package com.admin.controller;

import com.platform.security.entity.Role;
import com.admin.service.BusinessUnitRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Business unit role binding controller
 */
@RestController
@RequestMapping("/business-units/{unitId}/roles")
@RequiredArgsConstructor
@Tag(name = "Business Unit Role Binding", description = "Binding management between business units and business roles")
public class BusinessUnitRoleController {
    
    private final BusinessUnitRoleService businessUnitRoleService;
    
    @GetMapping
    @Operation(summary = "Get bound roles for business unit")
    public ResponseEntity<List<Role>> getBoundRoles(@PathVariable String unitId) {
        List<Role> roles = businessUnitRoleService.getBoundRoles(unitId);
        return ResponseEntity.ok(roles);
    }
    
    @PostMapping
    @Operation(summary = "Bind role to business unit")
    public ResponseEntity<Void> bindRole(
            @PathVariable String unitId,
            @RequestBody Map<String, String> request) {
        String roleId = request.get("roleId");
        if (roleId == null || roleId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        businessUnitRoleService.bindRole(unitId, roleId);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{roleId}")
    @Operation(summary = "Unbind role from business unit")
    public ResponseEntity<Void> unbindRole(
            @PathVariable String unitId,
            @PathVariable String roleId) {
        businessUnitRoleService.unbindRole(unitId, roleId);
        return ResponseEntity.ok().build();
    }
}
