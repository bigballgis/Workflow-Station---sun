package com.admin.bi.controller;

import com.admin.bi.dto.request.RbacMappingUpdateRequest;
import com.admin.bi.dto.response.RbacMappingResponse;
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
}
