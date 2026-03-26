package com.admin.controller;

import com.admin.entity.RelationTableAuditLog;
import com.admin.dto.response.RelationTableResponse;
import com.admin.service.RelationTableAuditService;
import com.admin.service.RelationTableDataService;
import com.platform.common.dto.RelationTableDataRowDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Relation Table 表数据管理 RESTful API
 */
@Slf4j
@RestController
@RequestMapping("/relation-tables/data")
@RequiredArgsConstructor
@Tag(name = "Relation Table 表数据管理", description = "已部署表的数据查询、新增、修改、删除及状态变更")
public class RelationTableDataController {

    private final RelationTableDataService dataService;
    private final RelationTableAuditService auditService;

    // ==================== 已部署表列表 ====================

    @GetMapping("/tables")
    @Operation(summary = "获取已部署的表列表", description = "仅返回 DEPLOYED 状态的 Relation Table")
    public ResponseEntity<List<RelationTableResponse>> getDeployedTables() {
        log.info("Getting deployed relation tables");
        List<RelationTableResponse> tables = dataService.getDeployedTables();
        return ResponseEntity.ok(tables);
    }

    // ==================== 表数据 CRUD ====================

    @GetMapping("/{tableId}")
    @Operation(summary = "分页查询表数据", description = "根据已部署的最新表结构动态查询物理表数据，支持搜索过滤")
    public ResponseEntity<Page<RelationTableDataRowDTO>> queryData(
            @Parameter(description = "表定义ID") @PathVariable Long tableId,
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String search,
            Pageable pageable) {
        log.info("Querying data for table: tableId={}, search={}, page={}", tableId, search, pageable);
        Page<RelationTableDataRowDTO> page = dataService.queryData(tableId, search, pageable);
        return ResponseEntity.ok(page);
    }

    @PostMapping("/{tableId}")
    @Operation(summary = "新增数据", description = "向指定表中新增一条数据")
    public ResponseEntity<RelationTableDataRowDTO> addData(
            @Parameter(description = "表定义ID") @PathVariable Long tableId,
            @RequestBody Map<String, Object> data) {
        log.info("Adding data to table: tableId={}", tableId);
        RelationTableDataRowDTO row = dataService.addData(tableId, data);
        return ResponseEntity.status(HttpStatus.CREATED).body(row);
    }

    @PutMapping("/{tableId}/{rowId}")
    @Operation(summary = "修改数据", description = "修改指定表中的一条数据")
    public ResponseEntity<RelationTableDataRowDTO> updateData(
            @Parameter(description = "表定义ID") @PathVariable Long tableId,
            @Parameter(description = "行ID") @PathVariable String rowId,
            @RequestBody Map<String, Object> data) {
        log.info("Updating data in table: tableId={}, rowId={}", tableId, rowId);
        RelationTableDataRowDTO row = dataService.updateData(tableId, rowId, data);
        return ResponseEntity.ok(row);
    }

    @DeleteMapping("/{tableId}/{rowId}")
    @Operation(summary = "删除数据", description = "删除指定表中的一条数据")
    public ResponseEntity<Void> deleteData(
            @Parameter(description = "表定义ID") @PathVariable Long tableId,
            @Parameter(description = "行ID") @PathVariable String rowId) {
        log.info("Deleting data from table: tableId={}, rowId={}", tableId, rowId);
        dataService.deleteData(tableId, rowId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 状态变更 ====================

    @PutMapping("/{tableId}/{rowId}/status")
    @Operation(summary = "变更数据状态", description = "变更数据的 Active/Inactive 状态")
    public ResponseEntity<RelationTableDataRowDTO> changeStatus(
            @Parameter(description = "表定义ID") @PathVariable Long tableId,
            @Parameter(description = "行ID") @PathVariable String rowId,
            @RequestBody Map<String, String> request) {
        String status = request.get("status");
        if (status == null) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Changing status for table: tableId={}, rowId={}, status={}", tableId, rowId, status);
        RelationTableDataRowDTO row = dataService.changeStatus(tableId, rowId, status);
        return ResponseEntity.ok(row);
    }

    // ==================== 审计日志 ====================

    @GetMapping("/{tableId}/audit-logs")
    @Operation(summary = "查询审计日志", description = "查询指定表的审计日志，支持按操作时间、操作人、操作类型过滤")
    public ResponseEntity<Page<RelationTableAuditLog>> queryAuditLogs(
            @Parameter(description = "表定义ID") @PathVariable Long tableId,
            @Parameter(description = "操作类型") @RequestParam(required = false) String action,
            @Parameter(description = "操作人ID") @RequestParam(required = false) String operatorId,
            @Parameter(description = "开始时间") @RequestParam(required = false) Instant startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) Instant endTime,
            Pageable pageable) {
        log.info("Querying audit logs for table: tableId={}, action={}, operatorId={}", tableId, action, operatorId);
        Page<RelationTableAuditLog> logs = auditService.queryAuditLogs(tableId, action, operatorId, startTime, endTime, pageable);
        return ResponseEntity.ok(logs);
    }
}
