package com.admin.controller;

import com.admin.component.RelationTableStructureListQueryComponent;
import com.admin.dto.list.RelationTableStructureListPage;
import com.admin.dto.request.CreateRelationTableRequest;
import com.admin.dto.request.RelationTableStructureListQueryRequest;
import com.admin.dto.request.RollbackRequest;
import com.admin.dto.request.UpdateRelationTableRequest;
import com.admin.dto.response.RelationTableResponse;
import com.admin.dto.response.RelationTableVersionResponse;
import com.admin.dto.response.TableNameAvailabilityResponse;
import com.admin.entity.RelationTableAccess;
import com.admin.service.RelationTableAccessService;
import com.admin.service.RelationTableDeployService;
import com.admin.service.RelationTableStructureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Relation Table 表结构管理 RESTful API
 */
@Slf4j
@RestController
@RequestMapping("/relation-tables/structures")
@RequiredArgsConstructor
@Tag(name = "Relation Table 表结构管理", description = "表结构的创建、查询、更新、删除及启用/门户可见性控制")
public class RelationTableStructureController {

    private final RelationTableStructureService structureService;
    private final RelationTableDeployService deployService;
    private final RelationTableAccessService accessService;
    private final RelationTableStructureListQueryComponent structureListQueryComponent;

    // ==================== 表结构 CRUD ====================

    @PostMapping
    @Operation(summary = "创建表定义", description = "创建新的 Relation Table 表定义")
    public ResponseEntity<RelationTableResponse> createTable(
            @Valid @RequestBody CreateRelationTableRequest request) {
        log.info("Creating relation table: {}", request.getTableName());
        RelationTableResponse response = structureService.createTable(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "获取表定义列表", description = "获取所有 Relation Table 表定义列表")
    public ResponseEntity<List<RelationTableResponse>> getTableList() {
        log.info("Getting relation table list");
        List<RelationTableResponse> list = structureService.getTableList();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/query")
    @Operation(summary = "分页查询表定义", description = "共享列表：COUNT(*) 与页数据共用同一谓词")
    public ResponseEntity<RelationTableStructureListPage> queryTables(
            @RequestBody @Valid RelationTableStructureListQueryRequest request) {
        return ResponseEntity.ok(structureListQueryComponent.query(request));
    }

    @GetMapping("/name-available")
    @Operation(summary = "检查表名是否可用", description = "全平台校验：Relation Table 与 Table Design 表名不可重复")
    public ResponseEntity<TableNameAvailabilityResponse> checkTableNameAvailable(
            @RequestParam String tableName,
            @RequestParam(required = false) Long excludeTableId) {
        return ResponseEntity.ok(TableNameAvailabilityResponse.builder()
                .tableName(tableName)
                .available(structureService.isTableNameAvailable(tableName, excludeTableId))
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取表定义详情", description = "根据 ID 获取 Relation Table 表定义详情")
    public ResponseEntity<RelationTableResponse> getTableById(
            @Parameter(description = "表定义ID") @PathVariable Long id) {
        log.info("Getting relation table: id={}", id);
        RelationTableResponse response = structureService.getTableById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新表定义", description = "更新 Relation Table 表定义的基本信息和字段定义")
    public ResponseEntity<RelationTableResponse> updateTable(
            @Parameter(description = "表定义ID") @PathVariable Long id,
            @Valid @RequestBody UpdateRelationTableRequest request) {
        log.info("Updating relation table: id={}", id);
        RelationTableResponse response = structureService.updateTable(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除表定义", description = "删除 Relation Table 表定义，如有绑定关系则拒绝删除")
    public ResponseEntity<Void> deleteTable(
            @Parameter(description = "表定义ID") @PathVariable Long id) {
        log.info("Deleting relation table: id={}", id);
        structureService.deleteTable(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 启用/禁用 & 门户可见性 ====================

    @PutMapping("/{id}/enabled")
    @Operation(summary = "启用/禁用表", description = "切换 Relation Table 的启用/禁用状态")
    public ResponseEntity<RelationTableResponse> toggleEnabled(
            @Parameter(description = "表定义ID") @PathVariable Long id,
            @RequestBody Map<String, Boolean> request) {
        Boolean enabled = request.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Toggling enabled for relation table: id={}, enabled={}", id, enabled);
        RelationTableResponse response = structureService.toggleEnabled(id, enabled);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/portal-visibility")
    @Operation(summary = "门户可见性开关", description = "切换 Relation Table 在 User Portal 中的可见性")
    public ResponseEntity<RelationTableResponse> togglePortalVisibility(
            @Parameter(description = "表定义ID") @PathVariable Long id,
            @RequestBody Map<String, Boolean> request) {
        Boolean portalVisible = request.get("portalVisible");
        if (portalVisible == null) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Toggling portal visibility for relation table: id={}, portalVisible={}", id, portalVisible);
        RelationTableResponse response = structureService.togglePortalVisibility(id, portalVisible);
        return ResponseEntity.ok(response);
    }

    // ==================== 部署与回滚 ====================

    @PostMapping("/{id}/deploy")
    @Operation(summary = "部署表结构", description = "将当前表结构修改应用到实际数据库")
    public ResponseEntity<RelationTableResponse> deploy(
            @Parameter(description = "表定义ID") @PathVariable Long id) {
        log.info("Deploying relation table: id={}", id);
        RelationTableResponse response = deployService.deploy(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/rollback")
    @Operation(summary = "回滚到指定版本", description = "将表结构内容切换为所选历史版本的内容，需重新部署")
    public ResponseEntity<RelationTableResponse> rollback(
            @Parameter(description = "表定义ID") @PathVariable Long id,
            @Valid @RequestBody RollbackRequest request) {
        log.info("Rolling back relation table: id={}, targetVersionId={}", id, request.getTargetVersionId());
        RelationTableResponse response = deployService.rollback(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/versions")
    @Operation(summary = "获取版本历史", description = "获取 Relation Table 的所有历史版本列表")
    public ResponseEntity<List<RelationTableVersionResponse>> getVersionHistory(
            @Parameter(description = "表定义ID") @PathVariable Long id) {
        log.info("Getting version history for relation table: id={}", id);
        List<RelationTableVersionResponse> versions = deployService.getVersionHistory(id);
        return ResponseEntity.ok(versions);
    }

    // ==================== 权限配置 ====================

    @GetMapping("/{id}/access")
    @Operation(summary = "获取访问配置", description = "获取 Relation Table 的 Business Role 访问配置列表")
    public ResponseEntity<List<RelationTableAccess>> getAccessConfig(
            @Parameter(description = "表定义ID") @PathVariable Long id) {
        log.info("Getting access config for relation table: id={}", id);
        List<RelationTableAccess> configs = accessService.getAccessConfig(id);
        return ResponseEntity.ok(configs);
    }

    @PostMapping("/{id}/access")
    @Operation(summary = "添加访问配置", description = "为 Relation Table 添加 Business Role 访问权限")
    public ResponseEntity<RelationTableAccess> addAccess(
            @Parameter(description = "表定义ID") @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String targetType = request.getOrDefault("targetType", "ROLE");
        String targetId = request.get("targetId");
        if (targetId == null) {
            return ResponseEntity.badRequest().build();
        }
        String permissionLevel = request.get("permissionLevel"); // null -> service defaults to READ_WRITE
        log.info("Adding access config for relation table: id={}, targetType={}, targetId={}, level={}",
                id, targetType, targetId, permissionLevel);
        RelationTableAccess access = accessService.addAccess(id, targetType, targetId, permissionLevel);
        return ResponseEntity.status(HttpStatus.CREATED).body(access);
    }

    @PutMapping("/{id}/access")
    @Operation(summary = "批量设置访问配置", description = "替换 Relation Table 的所有 Business Role 访问配置")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Void> batchSetAccess(
            @Parameter(description = "表定义ID") @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        Object rawIds = request.get("targetIds");
        if (!(rawIds instanceof List<?> targetIds)) {
            return ResponseEntity.badRequest().build();
        }
        String permissionLevel = request.get("permissionLevel") != null
                ? String.valueOf(request.get("permissionLevel")) : null;
        log.info("Batch setting access config for relation table: id={}, count={}, level={}",
                id, targetIds.size(), permissionLevel);
        accessService.batchSetAccess(id, (List<String>) targetIds, permissionLevel);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/access/{accessId}")
    @Operation(summary = "修改授权权限级别", description = "原地切换某条角色授权的 READONLY / READ_WRITE")
    public ResponseEntity<RelationTableAccess> updatePermissionLevel(
            @Parameter(description = "表定义ID") @PathVariable Long id,
            @Parameter(description = "访问配置ID") @PathVariable String accessId,
            @RequestBody Map<String, String> request) {
        String permissionLevel = request.get("permissionLevel");
        if (permissionLevel == null) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Updating access permission level: id={}, accessId={}, level={}", id, accessId, permissionLevel);
        RelationTableAccess access = accessService.updatePermissionLevel(accessId, permissionLevel);
        return ResponseEntity.ok(access);
    }

    @DeleteMapping("/{id}/access/{accessId}")
    @Operation(summary = "删除访问配置", description = "删除 Relation Table 的某个 Business Role 访问配置")
    public ResponseEntity<Void> removeAccess(
            @Parameter(description = "表定义ID") @PathVariable Long id,
            @Parameter(description = "访问配置ID") @PathVariable String accessId) {
        log.info("Removing access config for relation table: id={}, accessId={}", id, accessId);
        accessService.removeAccess(accessId);
        return ResponseEntity.noContent().build();
    }
}
