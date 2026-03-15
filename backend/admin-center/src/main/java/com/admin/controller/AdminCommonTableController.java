package com.admin.controller;

import com.admin.entity.AdminCommonTable;
import com.admin.entity.AdminCommonTableAccess;
import com.admin.entity.AdminCommonTableDeployment;
import com.admin.repository.AdminCommonTableAccessRepository;
import com.admin.repository.AdminCommonTableDeploymentRepository;
import com.admin.repository.AdminCommonTableRepository;
import com.admin.repository.RoleRepository;
import com.platform.security.entity.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin Center Common Table management API
 * Reads from dw_common_table_* tables (shared DB with Developer Workstation)
 */
@Slf4j
@RestController
@RequestMapping("/admin/common-tables")
@RequiredArgsConstructor
@Tag(name = "公共表管理（Admin）", description = "Admin Center 对已部署公共表的管理接口")
public class AdminCommonTableController {

    private final AdminCommonTableRepository tableRepository;
    private final AdminCommonTableDeploymentRepository deploymentRepository;
    private final AdminCommonTableAccessRepository accessRepository;
    private final RoleRepository roleRepository;

    // ==================== Common Table List ====================

    @GetMapping
    @Operation(summary = "获取已部署的公共表列表")
    public ResponseEntity<Map<String, Object>> listDeployed() {
        List<AdminCommonTable> tables = tableRepository.findByStatusOrderByDeployedAtDesc("PUBLISHED");
        return ok(tables);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取单个公共表详情")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        AdminCommonTable table = tableRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Common table not found: " + id));
        return ok(table);
    }

    // ==================== Enable / Disable ====================

    @PutMapping("/{id}/enabled")
    @Operation(summary = "切换公共表启用状态")
    public ResponseEntity<Map<String, Object>> updateEnabled(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        tableRepository.updateEnabled(id, enabled);
        AdminCommonTable updated = tableRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Common table not found: " + id));
        log.info("Common table {} enabled={}", id, enabled);
        return ok(updated);
    }

    // ==================== Delete (soft) ====================

    @DeleteMapping("/{id}")
    @Operation(summary = "软删除公共表（设为 ARCHIVED）")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        tableRepository.archiveById(id);
        log.info("Archived common table {}", id);
        return ok(null);
    }

    // ==================== Deployment Records ====================

    @GetMapping("/deployments")
    @Operation(summary = "获取所有部署记录")
    public ResponseEntity<Map<String, Object>> listAllDeployments() {
        List<AdminCommonTableDeployment> records = deploymentRepository.findAllByOrderByDeployedAtDesc();
        return ok(records);
    }

    @GetMapping("/{id}/deployments")
    @Operation(summary = "获取单表的部署历史")
    public ResponseEntity<Map<String, Object>> listDeployments(@PathVariable Long id) {
        List<AdminCommonTableDeployment> records = deploymentRepository.findByCommonTableIdOrderByDeployedAtDesc(id);
        return ok(records);
    }

    @PostMapping("/deployments/{deploymentId}/rollback")
    @Operation(summary = "回滚部署（将该记录状态标为 ROLLED_BACK）")
    public ResponseEntity<Map<String, Object>> rollback(
            @PathVariable Long deploymentId,
            @RequestBody(required = false) Map<String, String> body) {
        AdminCommonTableDeployment dep = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new RuntimeException("Deployment not found: " + deploymentId));
        dep.setStatus("ROLLED_BACK");
        if (body != null && body.containsKey("notes")) {
            dep.setNotes(body.get("notes"));
        }
        deploymentRepository.save(dep);

        // Disable the common table when rolling back
        tableRepository.updateEnabled(dep.getCommonTableId(), false);
        log.info("Rolled back deployment {} for common table {}", deploymentId, dep.getCommonTableId());
        return ok(dep);
    }

    // ==================== Versions ====================

    @GetMapping("/code/{code}/versions")
    @Operation(summary = "按 code 查询历史部署版本")
    public ResponseEntity<Map<String, Object>> getVersionsByCode(@PathVariable String code) {
        List<AdminCommonTable> tables = tableRepository.findByCodeOrderByDeployedAtDesc(code);
        return ok(tables);
    }

    // ==================== Access Control ====================

    @GetMapping("/{id}/access")
    @Operation(summary = "获取访问控制列表（含角色名称）")
    public ResponseEntity<Map<String, Object>> getAccess(@PathVariable Long id) {
        List<AdminCommonTableAccess> records = accessRepository.findByCommonTableId(id);
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (AdminCommonTableAccess rec : records) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", rec.getId());
            item.put("commonTableId", rec.getCommonTableId());
            item.put("accessType", rec.getAccessType());
            item.put("targetType", rec.getTargetType());
            item.put("targetId", rec.getTargetId());
            item.put("createdAt", rec.getCreatedAt());
            item.put("createdBy", rec.getCreatedBy());
            // Resolve role name for display
            String roleName = rec.getTargetId();
            if ("ROLE".equals(rec.getTargetType())) {
                roleName = roleRepository.findById(rec.getTargetId())
                        .map(Role::getName)
                        .orElse(rec.getTargetId());
            }
            item.put("roleName", roleName);
            enriched.add(item);
        }
        return ok(enriched);
    }

    @PostMapping("/{id}/access")
    @Operation(summary = "添加业务角色访问控制")
    public ResponseEntity<Map<String, Object>> addAccess(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String roleId = body.get("roleId");
        if (roleId == null || roleId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "roleId is required"));
        }

        Role role = roleRepository.findById(roleId).orElse(null);
        if (role == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Role not found: " + roleId));
        }

        if (accessRepository.existsByCommonTableIdAndTargetId(id, roleId)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "This role already has access"));
        }

        AdminCommonTableAccess access = new AdminCommonTableAccess();
        access.setCommonTableId(id);
        access.setTargetId(roleId);
        access.setTargetType("ROLE");
        access.setAccessType("VIEW");
        access.setCreatedBy(userId);
        access.setCreatedAt(Instant.now());
        accessRepository.save(access);

        Map<String, Object> result = new HashMap<>();
        result.put("id", access.getId());
        result.put("commonTableId", access.getCommonTableId());
        result.put("targetId", access.getTargetId());
        result.put("targetType", access.getTargetType());
        result.put("accessType", access.getAccessType());
        result.put("createdAt", access.getCreatedAt());
        result.put("roleName", role.getName());
        log.info("Added access for common table {} roleId={} roleName={}", id, roleId, role.getName());
        return ok(result);
    }

    @DeleteMapping("/{id}/access/{accessId}")
    @Operation(summary = "删除访问控制记录")
    public ResponseEntity<Map<String, Object>> deleteAccess(
            @PathVariable Long id,
            @PathVariable Long accessId) {
        accessRepository.deleteById(accessId);
        return ok(null);
    }

    // ==================== Helper ====================

    private ResponseEntity<Map<String, Object>> ok(Object data) {
        return ResponseEntity.ok(Map.of("success", true, "data", data != null ? data : Map.of()));
    }
}
