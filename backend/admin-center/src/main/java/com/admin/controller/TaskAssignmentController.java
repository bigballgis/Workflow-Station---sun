package com.admin.controller;

import com.platform.security.entity.Role;
import com.admin.service.TaskAssignmentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务分配查询控制器
 * 提供工作流任务分配所需的查询接口
 */
@RestController
@RequestMapping("/task-assignment")
@RequiredArgsConstructor
@Tag(name = "任务分配查询", description = "工作流任务分配相关的查询接口")
public class TaskAssignmentController {
    
    private final TaskAssignmentQueryService taskAssignmentQueryService;
    
    // ==================== 用户业务单元查询 ====================
    
    @GetMapping("/users/{userId}/business-unit")
    @Operation(summary = "获取用户的业务单元ID")
    public ResponseEntity<Map<String, String>> getUserBusinessUnitId(@PathVariable String userId) {
        String businessUnitId = taskAssignmentQueryService.getUserBusinessUnitId(userId);
        return ResponseEntity.ok(Map.of("businessUnitId", businessUnitId != null ? businessUnitId : ""));
    }
    
    @GetMapping("/business-units/{businessUnitId}/parent")
    @Operation(summary = "获取业务单元的父业务单元ID")
    public ResponseEntity<Map<String, String>> getParentBusinessUnitId(@PathVariable String businessUnitId) {
        String parentId = taskAssignmentQueryService.getParentBusinessUnitId(businessUnitId);
        return ResponseEntity.ok(Map.of("parentBusinessUnitId", parentId != null ? parentId : ""));
    }
    
    // ==================== 业务单元角色用户查询 ====================
    
    @GetMapping("/business-units/{businessUnitId}/roles/{roleId}/users")
    @Operation(summary = "获取业务单元中拥有指定角色的用户ID列表")
    public ResponseEntity<List<String>> getUsersByBusinessUnitAndRole(
            @PathVariable String businessUnitId,
            @PathVariable String roleId) {
        List<String> userIds = taskAssignmentQueryService.getUsersByBusinessUnitAndRole(businessUnitId, roleId);
        return ResponseEntity.ok(userIds);
    }
    
    // ==================== BU无关型角色用户查询 ====================
    
    @GetMapping("/roles/{roleId}/users")
    @Operation(summary = "获取拥有指定BU无关型角色的用户ID列表", 
               description = "通过查询绑定了该角色的虚拟组的所有成员")
    public ResponseEntity<List<String>> getUsersByUnboundedRole(@PathVariable String roleId) {
        List<String> userIds = taskAssignmentQueryService.getUsersByUnboundedRole(roleId);
        return ResponseEntity.ok(userIds);
    }
    
    // ==================== 业务单元准入角色查询 ====================
    
    @GetMapping("/business-units/{businessUnitId}/eligible-roles")
    @Operation(summary = "获取业务单元的准入角色ID列表")
    public ResponseEntity<List<String>> getEligibleRoleIds(@PathVariable String businessUnitId) {
        List<String> roleIds = taskAssignmentQueryService.getEligibleRoleIds(businessUnitId);
        return ResponseEntity.ok(roleIds);
    }
    
    @GetMapping("/business-units/{businessUnitId}/roles/{roleId}/eligible")
    @Operation(summary = "检查角色是否是业务单元的准入角色")
    public ResponseEntity<Map<String, Boolean>> isEligibleRole(
            @PathVariable String businessUnitId,
            @PathVariable String roleId) {
        boolean eligible = taskAssignmentQueryService.isEligibleRole(businessUnitId, roleId);
        return ResponseEntity.ok(Map.of("eligible", eligible));
    }
    
    // ==================== 角色类型查询 ====================
    
    @GetMapping("/roles/bu-bounded")
    @Operation(summary = "获取所有BU绑定型角色")
    public ResponseEntity<List<Role>> getBuBoundedRoles() {
        List<Role> roles = taskAssignmentQueryService.getBuBoundedRoles();
        return ResponseEntity.ok(roles);
    }
    
    @GetMapping("/roles/bu-unbounded")
    @Operation(summary = "获取所有BU无关型角色")
    public ResponseEntity<List<Role>> getBuUnboundedRoles() {
        List<Role> roles = taskAssignmentQueryService.getBuUnboundedRoles();
        return ResponseEntity.ok(roles);
    }
}
