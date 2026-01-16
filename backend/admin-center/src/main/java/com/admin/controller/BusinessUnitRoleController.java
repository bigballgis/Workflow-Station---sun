package com.admin.controller;

import com.admin.entity.Role;
import com.admin.service.BusinessUnitRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 业务单元角色绑定控制器
 */
@RestController
@RequestMapping("/business-units/{unitId}/roles")
@RequiredArgsConstructor
@Tag(name = "业务单元角色绑定", description = "业务单元与业务角色的绑定管理")
public class BusinessUnitRoleController {
    
    private final BusinessUnitRoleService businessUnitRoleService;
    
    @GetMapping
    @Operation(summary = "获取业务单元绑定的角色列表")
    public ResponseEntity<List<Role>> getBoundRoles(@PathVariable String unitId) {
        List<Role> roles = businessUnitRoleService.getBoundRoles(unitId);
        return ResponseEntity.ok(roles);
    }
    
    @PostMapping
    @Operation(summary = "绑定角色到业务单元")
    public ResponseEntity<Void> bindRole(
            @PathVariable String unitId,
            @RequestBody Map<String, String> request) {
        String roleId = request.get("roleId");
        businessUnitRoleService.bindRole(unitId, roleId);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{roleId}")
    @Operation(summary = "解绑业务单元的角色")
    public ResponseEntity<Void> unbindRole(
            @PathVariable String unitId,
            @PathVariable String roleId) {
        businessUnitRoleService.unbindRole(unitId, roleId);
        return ResponseEntity.ok().build();
    }
}
