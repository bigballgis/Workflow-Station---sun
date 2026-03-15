package com.admin.bi.controller;

import com.admin.bi.dto.request.RbacMappingCreateRequest;
import com.admin.bi.dto.request.RbacMappingUpdateRequest;
import com.admin.bi.dto.response.RbacMappingResponse;
import com.admin.bi.dto.response.RoleOptionResponse;
import com.admin.bi.dto.response.SupersetRoleResponse;
import com.admin.bi.dto.response.SyncResultResponse;
import com.admin.bi.service.BiRbacMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RBAC 映射管理控制器
 */
@RestController
@RequestMapping("/bi/rbac")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "RBAC 映射管理", description = "Superset 角色同步、RBAC 映射查询与更新接口")
public class BiRbacMappingController {

    private final BiRbacMappingService rbacMappingService;

    @PostMapping("/superset-roles/sync")
    @Operation(summary = "手动同步 Superset 角色", description = "立即执行一次 Superset_Role_Sync_Operation 并返回同步结果摘要")
    public ResponseEntity<SyncResultResponse> syncSupersetRoles(
            @RequestHeader("X-User-Id") String userId) {
        log.info("User {} triggered manual Superset role sync", userId);
        SyncResultResponse result = rbacMappingService.syncSupersetRoles();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/superset-roles")
    @Operation(summary = "获取所有已同步的 Superset 角色列表")
    public ResponseEntity<List<SupersetRoleResponse>> listSupersetRoles() {
        List<SupersetRoleResponse> roles = rbacMappingService.listSupersetRoles();
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/mappings")
    @Operation(summary = "获取 RBAC 映射列表", description = "支持按 roleName、roleType 筛选")
    public ResponseEntity<List<RbacMappingResponse>> listMappings(
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) String roleType) {
        List<RbacMappingResponse> mappings = rbacMappingService.listMappings(roleName, roleType);
        return ResponseEntity.ok(mappings);
    }

    @GetMapping("/unmapped-roles")
    @Operation(summary = "获取未映射的活跃系统角色列表", description = "返回尚未创建 RBAC 映射的活跃系统角色，用于创建映射时的下拉选择")
    public ResponseEntity<List<RoleOptionResponse>> listUnmappedRoles() {
        List<RoleOptionResponse> roles = rbacMappingService.listUnmappedRoles();
        return ResponseEntity.ok(roles);
    }

    @PostMapping("/mappings")
    @Operation(summary = "创建 RBAC 映射", description = "为指定系统角色创建 Superset 角色映射")
    public ResponseEntity<Void> createMapping(
            @RequestBody @Valid RbacMappingCreateRequest request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("User {} creating RBAC mapping for sysRoleId {}", userId, request.getSysRoleId());
        rbacMappingService.createMapping(request);
        return ResponseEntity.status(201).build();
    }

    @PutMapping("/mappings/{sysRoleId}")
    @Operation(summary = "更新 Sys_Role 的 Superset_Role 映射", description = "全量替换该 Sys_Role 的所有 Superset_Role 映射")
    public ResponseEntity<Void> updateMapping(
            @PathVariable String sysRoleId,
            @RequestBody @Valid RbacMappingUpdateRequest request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("User {} updating RBAC mapping for sysRoleId {}", userId, sysRoleId);
        rbacMappingService.updateMapping(sysRoleId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/mappings/{sysRoleId}")
    @Operation(summary = "删除 Sys_Role 的所有 RBAC 映射", description = "删除该系统角色的所有 Superset 角色映射记录")
    public ResponseEntity<Void> deleteMapping(
            @PathVariable String sysRoleId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("User {} deleting RBAC mapping for sysRoleId {}", userId, sysRoleId);
        rbacMappingService.deleteMapping(sysRoleId);
        return ResponseEntity.noContent().build();
    }
}
