package com.admin.controller;

import com.admin.dto.request.UserBusinessUnitRoleAssignRequest;
import com.admin.dto.response.UserBusinessUnitRoleInfo;
import com.admin.entity.UserBusinessUnitRole;
import com.admin.repository.UserBusinessUnitRoleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户业务单元角色分配控制器
 */
@RestController
@RequestMapping("/users/{userId}/business-unit-roles")
@RequiredArgsConstructor
@Tag(name = "用户业务单元角色", description = "用户在业务单元中的角色分配管理")
public class UserBusinessUnitRoleController {
    
    private final UserBusinessUnitRoleRepository userBusinessUnitRoleRepository;
    
    @GetMapping
    @Operation(summary = "获取用户的业务单元角色列表")
    public ResponseEntity<List<UserBusinessUnitRoleInfo>> getUserBusinessUnitRoles(@PathVariable String userId) {
        List<UserBusinessUnitRole> roles = userBusinessUnitRoleRepository.findByUserIdWithDetails(userId);
        List<UserBusinessUnitRoleInfo> result = roles.stream()
                .map(UserBusinessUnitRoleInfo::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
    
    @PostMapping
    @Operation(summary = "分配业务单元角色给用户")
    @Transactional
    public ResponseEntity<Void> assignRole(
            @PathVariable String userId,
            @RequestBody UserBusinessUnitRoleAssignRequest request) {
        
        // 检查是否已存在
        if (userBusinessUnitRoleRepository.existsByUserIdAndBusinessUnitIdAndRoleId(
                userId, request.getBusinessUnitId(), request.getRoleId())) {
            return ResponseEntity.ok().build();
        }
        
        UserBusinessUnitRole assignment = UserBusinessUnitRole.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .businessUnitId(request.getBusinessUnitId())
                .roleId(request.getRoleId())
                .build();
        
        userBusinessUnitRoleRepository.save(assignment);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{businessUnitId}/{roleId}")
    @Operation(summary = "移除用户的业务单元角色")
    @Transactional
    public ResponseEntity<Void> removeRole(
            @PathVariable String userId,
            @PathVariable String businessUnitId,
            @PathVariable String roleId) {
        
        userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitIdAndRoleId(userId, businessUnitId, roleId)
                .ifPresent(userBusinessUnitRoleRepository::delete);
        
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/by-business-unit/{businessUnitId}")
    @Operation(summary = "获取用户在指定业务单元的角色列表")
    public ResponseEntity<List<UserBusinessUnitRoleInfo>> getUserRolesInBusinessUnit(
            @PathVariable String userId,
            @PathVariable String businessUnitId) {
        
        List<UserBusinessUnitRole> roles = userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId);
        List<UserBusinessUnitRoleInfo> result = roles.stream()
                .map(UserBusinessUnitRoleInfo::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
