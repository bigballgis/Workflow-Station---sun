package com.admin.bi.controller;

import com.admin.bi.component.BiRbacListQueryComponent;
import com.admin.bi.dto.request.RbacMappingCreateRequest;
import com.admin.bi.dto.request.RbacMappingUpdateRequest;
import com.admin.bi.dto.response.RbacMappingResponse;
import com.admin.bi.dto.response.RoleOptionResponse;
import com.admin.bi.dto.response.SupersetRoleResponse;
import com.admin.bi.dto.response.SyncResultResponse;
import com.admin.bi.service.BiRbacMappingService;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.BiRbacListQueryRequest;
import com.platform.security.util.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RBAC mapping management controller
 */
@RestController
@RequestMapping("/bi/rbac")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "RBAC Mapping Management", description = "Superset role sync, RBAC mapping query and update endpoints")
public class BiRbacMappingController {

    private final BiRbacMappingService rbacMappingService;
    private final BiRbacListQueryComponent rbacListQueryComponent;

    @PostMapping("/superset-roles/sync")
    @Operation(summary = "Manually sync Superset roles", description = "Immediately execute a Superset_Role_Sync_Operation and return sync result summary")
    public ResponseEntity<SyncResultResponse> syncSupersetRoles() {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException("Unauthenticated user"));
        log.info("User {} triggered manual Superset role sync", userId);
        SyncResultResponse result = rbacMappingService.syncSupersetRoles();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/superset-roles")
    @Operation(summary = "Get all synced Superset roles")
    public ResponseEntity<List<SupersetRoleResponse>> listSupersetRoles() {
        List<SupersetRoleResponse> roles = rbacMappingService.listSupersetRoles();
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/mappings")
    @Operation(summary = "Get RBAC mapping list", description = "Supports filtering by roleName and roleType")
    public ResponseEntity<List<RbacMappingResponse>> listMappings(
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) String roleType) {
        List<RbacMappingResponse> mappings = rbacMappingService.listMappings(roleName, roleType);
        return ResponseEntity.ok(mappings);
    }

    @PostMapping("/mappings/query")
    @Operation(summary = "Query RBAC mappings (true paging; column filters and sort)")
    public ResponseEntity<AdminListPage<RbacMappingResponse>> queryMappings(
            @RequestBody @Valid BiRbacListQueryRequest request) {
        return ResponseEntity.ok(rbacListQueryComponent.query(request));
    }

    @GetMapping("/unmapped-roles")
    @Operation(summary = "Get unmapped active system roles", description = "Returns active system roles without RBAC mappings, for dropdown selection when creating mappings")
    public ResponseEntity<List<RoleOptionResponse>> listUnmappedRoles() {
        List<RoleOptionResponse> roles = rbacMappingService.listUnmappedRoles();
        return ResponseEntity.ok(roles);
    }

    @PostMapping("/mappings")
    @Operation(summary = "Create RBAC mapping", description = "Create Superset role mapping for a specified system role")
    public ResponseEntity<Void> createMapping(
            @RequestBody @Valid RbacMappingCreateRequest request) {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException("Unauthenticated user"));
        log.info("User {} creating RBAC mapping for sysRoleId {}", userId, request.getSysRoleId());
        rbacMappingService.createMapping(request);
        return ResponseEntity.status(201).build();
    }

    @PutMapping("/mappings/{sysRoleId}")
    @Operation(summary = "Update Sys_Role Superset_Role mapping", description = "Fully replace all Superset_Role mappings for the given Sys_Role")
    public ResponseEntity<Void> updateMapping(
            @PathVariable String sysRoleId,
            @RequestBody @Valid RbacMappingUpdateRequest request) {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException("Unauthenticated user"));
        log.info("User {} updating RBAC mapping for sysRoleId {}", userId, sysRoleId);
        rbacMappingService.updateMapping(sysRoleId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/mappings/{sysRoleId}")
    @Operation(summary = "Delete all RBAC mappings for Sys_Role", description = "Delete all Superset role mapping records for the given system role")
    public ResponseEntity<Void> deleteMapping(
            @PathVariable String sysRoleId) {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException("Unauthenticated user"));
        log.info("User {} deleting RBAC mapping for sysRoleId {}", userId, sysRoleId);
        rbacMappingService.deleteMapping(sysRoleId);
        return ResponseEntity.noContent().build();
    }
}
