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
    @Operation(summary = "获取用户的业务单元 code",
            description = "任务分配链路统一用 BU code。多 BU 场景下可传 activeBusinessUnitId（BU code，须与用户 UBR 一致）；否则仅在唯一 BU 时返回。返回值为 BU code。")
    public ResponseEntity<Map<String, String>> getUserBusinessUnitId(
            @PathVariable String userId,
            @RequestParam(required = false) String activeBusinessUnitId) {
        String businessUnitCode = taskAssignmentQueryService.getUserBusinessUnitId(userId, activeBusinessUnitId);
        return ResponseEntity.ok(Map.of("businessUnitId", businessUnitCode != null ? businessUnitCode : ""));
    }

    @GetMapping("/business-units/by-id/{businessUnitId}/code")
    @Operation(summary = "业务单元 id → code",
            description = "供运行时把工作台上下文 activeBusinessUnitId（仍为 id）转成 code，再进入任务分配 code 链路")
    public ResponseEntity<Map<String, String>> getBusinessUnitCodeById(@PathVariable String businessUnitId) {
        String code = taskAssignmentQueryService.getBusinessUnitCodeById(businessUnitId);
        return ResponseEntity.ok(Map.of("code", code != null ? code : ""));
    }

    @GetMapping("/business-units/{businessUnitId}/parent")
    @Operation(summary = "获取业务单元的父业务单元 code",
            description = "路径参数为 BU code；返回父 BU 的 code（无父级时为空）")
    public ResponseEntity<Map<String, String>> getParentBusinessUnitId(@PathVariable("businessUnitId") String businessUnitCode) {
        String parentCode = taskAssignmentQueryService.getParentBusinessUnitId(businessUnitCode);
        return ResponseEntity.ok(Map.of("parentBusinessUnitId", parentCode != null ? parentCode : ""));
    }
    
    // ==================== 业务单元角色用户查询 ====================
    
    @GetMapping("/business-units/{businessUnitId}/roles/{roleId}/users")
    @Operation(summary = "获取业务单元中拥有指定角色的用户ID列表",
               description = "路径参数 businessUnitId / roleId 均为 code（任务分配链路统一 code）")
    public ResponseEntity<List<String>> getUsersByBusinessUnitAndRole(
            @PathVariable("businessUnitId") String businessUnitCode,
            @PathVariable("roleId") String roleCode) {
        List<String> userIds = taskAssignmentQueryService.getUsersByBusinessUnitAndRole(businessUnitCode, roleCode);
        return ResponseEntity.ok(userIds);
    }

    // ==================== BU无关型角色用户查询 ====================

    @GetMapping("/roles/{roleId}/users")
    @Operation(summary = "获取拥有指定BU无关型角色的用户ID列表",
               description = "路径参数 roleId 为 role code；通过查询绑定了该角色的虚拟组的所有成员")
    public ResponseEntity<List<String>> getUsersByUnboundedRole(@PathVariable("roleId") String roleCode) {
        List<String> userIds = taskAssignmentQueryService.getUsersByUnboundedRole(roleCode);
        return ResponseEntity.ok(userIds);
    }

    @GetMapping("/virtual-groups/by-code/{code}/users")
    @Operation(summary = "按虚拟组编码获取成员用户ID列表",
               description = "用于 BPMN 中 VIRTUAL_GROUP 的 assigneeValue（如 DOCUMENT_VERIFIERS）解析候选人")
    public ResponseEntity<List<String>> getUsersByVirtualGroupCode(@PathVariable String code) {
        List<String> userIds = taskAssignmentQueryService.getUsersByVirtualGroupCode(code);
        return ResponseEntity.ok(userIds);
    }
    
    // ==================== 业务单元准入角色查询 ====================
    
    @GetMapping("/business-units/{businessUnitId}/eligible-roles")
    @Operation(summary = "获取业务单元的准入角色 code 列表",
               description = "路径参数 businessUnitId 为 BU code；返回准入角色的 code 列表")
    public ResponseEntity<List<String>> getEligibleRoleIds(@PathVariable("businessUnitId") String businessUnitCode) {
        List<String> roleCodes = taskAssignmentQueryService.getEligibleRoleIds(businessUnitCode);
        return ResponseEntity.ok(roleCodes);
    }

    @GetMapping("/business-units/{businessUnitId}/roles/{roleId}/eligible")
    @Operation(summary = "检查角色是否是业务单元的准入角色",
               description = "路径参数 businessUnitId / roleId 均为 code")
    public ResponseEntity<Map<String, Boolean>> isEligibleRole(
            @PathVariable("businessUnitId") String businessUnitCode,
            @PathVariable("roleId") String roleCode) {
        boolean eligible = taskAssignmentQueryService.isEligibleRole(businessUnitCode, roleCode);
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
