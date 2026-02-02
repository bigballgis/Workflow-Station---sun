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
 * 虚拟组角色绑定控制器
 * 每个虚拟组只能绑定一个角色（单角色绑定）
 */
@RestController
@RequestMapping("/virtual-groups/{groupId}")
@RequiredArgsConstructor
@Tag(name = "虚拟组角色绑定", description = "虚拟组与业务角色的绑定管理（单角色绑定）")
public class VirtualGroupRoleController {
    
    private final VirtualGroupRoleService virtualGroupRoleService;
    
    @GetMapping("/role")
    @Operation(summary = "获取虚拟组绑定的角色（单个）")
    public ResponseEntity<Role> getBoundRole(@PathVariable String groupId) {
        Optional<Role> role = virtualGroupRoleService.getBoundRole(groupId);
        return role.map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
    
    @PostMapping("/role")
    @Operation(summary = "绑定角色到虚拟组（会替换现有绑定）")
    public ResponseEntity<Void> bindRole(
            @PathVariable String groupId,
            @RequestBody Map<String, String> request) {
        String roleId = request.get("roleId");
        virtualGroupRoleService.bindRole(groupId, roleId);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/role")
    @Operation(summary = "解绑虚拟组的角色")
    public ResponseEntity<Void> unbindRole(@PathVariable String groupId) {
        virtualGroupRoleService.unbindRole(groupId);
        return ResponseEntity.ok().build();
    }
}
