package com.admin.controller;

import com.admin.component.UserBusinessUnitRoleManagerComponent;
import com.admin.dto.request.UserBusinessUnitRoleAssignRequest;
import com.admin.dto.response.UserBusinessUnitRoleInfo;
import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.Role;
import com.platform.security.entity.User;
import com.platform.security.entity.UserBusinessUnitRole;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserBusinessUnitRoleRepository;
import com.admin.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.platform.security.util.SecurityContextUtils;
import com.platform.common.i18n.I18nService;

/**
 * User Business Unit Role Assignment Controller
 */
@RestController
@RequestMapping("/users/{userId}/business-unit-roles")
@RequiredArgsConstructor
@Tag(name = "User Business Unit Roles", description = "Manage role assignments for users in business units")
public class UserBusinessUnitRoleController {
    
    private final UserBusinessUnitRoleManagerComponent userBusinessUnitRoleManagerComponent;
    private final UserBusinessUnitRoleRepository userBusinessUnitRoleRepository;
    private final UserRepository userRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final RoleRepository roleRepository;
    private final I18nService i18nService;
    
    @GetMapping
    @Operation(summary = "Get user business unit role list")
    public ResponseEntity<List<UserBusinessUnitRoleInfo>> getUserBusinessUnitRoles(@PathVariable String userId) {
        List<UserBusinessUnitRole> roles = userBusinessUnitRoleRepository.findByUserId(userId);
        // Fetch related entities
        List<String> userIds = roles.stream().map(UserBusinessUnitRole::getUserId).distinct().collect(Collectors.toList());
        List<String> businessUnitIds = roles.stream().map(UserBusinessUnitRole::getBusinessUnitId).distinct().collect(Collectors.toList());
        List<String> roleIds = roles.stream().map(UserBusinessUnitRole::getRoleId).distinct().collect(Collectors.toList());
        
        Map<String, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<String, BusinessUnit> businessUnitMap = businessUnitRepository.findAllById(businessUnitIds).stream()
                .collect(Collectors.toMap(BusinessUnit::getId, bu -> bu));
        Map<String, Role> roleMap = roleRepository.findAllById(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, r -> r));
        
        List<UserBusinessUnitRoleInfo> result = roles.stream()
                .map(ubur -> UserBusinessUnitRoleInfo.fromEntity(
                        ubur,
                        userMap.get(ubur.getUserId()),
                        businessUnitMap.get(ubur.getBusinessUnitId()),
                        roleMap.get(ubur.getRoleId())
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
    
    @PostMapping
    @Operation(summary = "Assign business unit role to user")
    public ResponseEntity<Void> assignRole(
            @PathVariable String userId,
            @RequestBody @Valid UserBusinessUnitRoleAssignRequest request) {
        String operatedBy = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        userBusinessUnitRoleManagerComponent.assign(
                userId, request.getBusinessUnitId(), request.getRoleId(), operatedBy,
                request.getMembershipType());
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{businessUnitId}/{roleId}")
    @Operation(summary = "Remove user business unit role")
    public ResponseEntity<Void> removeRole(
            @PathVariable String userId,
            @PathVariable String businessUnitId,
            @PathVariable String roleId) {
        String operatedBy = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        userBusinessUnitRoleManagerComponent.remove(userId, businessUnitId, roleId, operatedBy);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/by-business-unit/{businessUnitId}")
    @Operation(summary = "Get user roles in specific business unit")
    public ResponseEntity<List<UserBusinessUnitRoleInfo>> getUserRolesInBusinessUnit(
            @PathVariable String userId,
            @PathVariable String businessUnitId) {
        
        List<UserBusinessUnitRole> roles = userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId);
        
        // Fetch related entities
        List<String> userIds = roles.stream().map(UserBusinessUnitRole::getUserId).distinct().collect(Collectors.toList());
        List<String> businessUnitIds = roles.stream().map(UserBusinessUnitRole::getBusinessUnitId).distinct().collect(Collectors.toList());
        List<String> roleIds = roles.stream().map(UserBusinessUnitRole::getRoleId).distinct().collect(Collectors.toList());
        
        Map<String, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<String, BusinessUnit> businessUnitMap = businessUnitRepository.findAllById(businessUnitIds).stream()
                .collect(Collectors.toMap(BusinessUnit::getId, bu -> bu));
        Map<String, Role> roleMap = roleRepository.findAllById(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, r -> r));
        
        List<UserBusinessUnitRoleInfo> result = roles.stream()
                .map(ubur -> UserBusinessUnitRoleInfo.fromEntity(
                        ubur,
                        userMap.get(ubur.getUserId()),
                        businessUnitMap.get(ubur.getBusinessUnitId()),
                        roleMap.get(ubur.getRoleId())
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
