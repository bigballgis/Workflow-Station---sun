package com.admin.controller;

import com.admin.component.RoleAssignmentComponent;
import com.admin.dto.request.CreateAssignmentRequest;
import com.admin.dto.response.EffectiveUserResponse;
import com.admin.dto.response.RoleAssignmentResponse;
import com.platform.security.entity.RoleAssignment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色分配控制器
 * 管理角色的分配记录和有效用户
 */
@Slf4j
@RestController
@RequestMapping("/roles/{roleId}/assignments")
@RequiredArgsConstructor
@Tag(name = "角色分配管理", description = "角色分配的创建、删除、查询")
public class RoleAssignmentController {
    
    private final RoleAssignmentComponent roleAssignmentComponent;
    
    @PostMapping
    @Operation(summary = "创建角色分配", description = "将角色分配给用户、部门、部门层级或虚拟组")
    public ResponseEntity<RoleAssignmentResponse> createAssignment(
            @PathVariable String roleId,
            @Valid @RequestBody CreateAssignmentRequest request,
            @RequestHeader("X-User-Id") String operatorId) {
        
        request.setRoleId(roleId);
        RoleAssignment assignment = roleAssignmentComponent.createAssignment(request, operatorId);
        
        // 获取完整响应
        List<RoleAssignmentResponse> assignments = roleAssignmentComponent.getAssignmentsForRole(roleId);
        RoleAssignmentResponse response = assignments.stream()
                .filter(a -> a.getId().equals(assignment.getId()))
                .findFirst()
                .orElseThrow();
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    @Operation(summary = "获取角色分配列表", description = "获取角色的所有分配记录")
    public ResponseEntity<List<RoleAssignmentResponse>> getAssignments(
            @PathVariable String roleId) {
        
        List<RoleAssignmentResponse> assignments = roleAssignmentComponent.getAssignmentsForRole(roleId);
        return ResponseEntity.ok(assignments);
    }
    
    @DeleteMapping("/{assignmentId}")
    @Operation(summary = "删除角色分配", description = "删除指定的角色分配记录")
    public ResponseEntity<Void> deleteAssignment(
            @PathVariable String roleId,
            @PathVariable String assignmentId,
            @RequestHeader("X-User-Id") String operatorId) {
        
        roleAssignmentComponent.deleteAssignment(assignmentId, operatorId);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/effective-users")
    @Operation(summary = "获取有效用户列表", description = "获取拥有该角色的所有用户及其来源")
    public ResponseEntity<List<EffectiveUserResponse>> getEffectiveUsers(
            @PathVariable String roleId) {
        
        List<EffectiveUserResponse> users = roleAssignmentComponent.getEffectiveUsers(roleId);
        return ResponseEntity.ok(users);
    }
}
