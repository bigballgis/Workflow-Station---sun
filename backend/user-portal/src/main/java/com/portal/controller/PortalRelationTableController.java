package com.portal.controller;

import com.platform.common.dto.RelationTableDTO;
import com.portal.dto.ApiResponse;
import com.portal.dto.PageResponse;
import com.portal.service.PortalRelationTableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Portal Relation Table 控制器
 * 提供只读数据查看接口
 */
@RestController
@RequestMapping("/relation-tables")
@RequiredArgsConstructor
@Tag(name = "Portal Relation Tables", description = "Portal 只读数据查看")
public class PortalRelationTableController {

    private final PortalRelationTableService service;

    @GetMapping
    @Operation(summary = "获取用户可见的表列表")
    public ResponseEntity<ApiResponse<List<RelationTableDTO>>> getVisibleTables(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        try {
            List<RelationTableDTO> result = service.getVisibleTables(userId);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            // Gracefully handle any unexpected errors (e.g., table not yet created, DB connection issues)
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
    }

    @GetMapping("/{tableId}")
    @Operation(summary = "分页查询表数据（只读）")
    public ResponseEntity<ApiResponse<PageResponse<Map<String, Object>>>> queryTableData(
            @PathVariable Long tableId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        try {
            PageResponse<Map<String, Object>> result = service.queryTableData(tableId, userId, page, size, search);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(PageResponse.of(List.of(), page, size, 0)));
        }
    }

    @GetMapping("/{tableId}/export")
    @Operation(summary = "导出 CSV")
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable Long tableId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(defaultValue = "10000") int maxRows) {
        try {
            String csv = service.exportCsv(tableId, userId, maxRows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=export.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv.getBytes());
        } catch (Exception e) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=export.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(new byte[0]);
        }
    }

    @GetMapping("/{tableId}/search")
    @Operation(summary = "Lookup 搜索")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> searchForLookup(
            @PathVariable Long tableId,
            @RequestParam String keyword,
            @RequestParam List<String> searchFields,
            @RequestParam String displayField,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<Map<String, Object>> result = service.searchForLookup(tableId, keyword, searchFields, displayField, limit);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
    }

    // Write operations are forbidden in Portal
    @PostMapping("/{tableId}")
    @Operation(summary = "写操作 - 禁止")
    public ResponseEntity<ApiResponse<Void>> forbiddenPost(@PathVariable Long tableId) {
        return ResponseEntity.status(403).body(ApiResponse.error("403", "Write operations are not allowed in Portal"));
    }

    @PutMapping("/{tableId}/{rowId}")
    @Operation(summary = "写操作 - 禁止")
    public ResponseEntity<ApiResponse<Void>> forbiddenPut(@PathVariable Long tableId, @PathVariable String rowId) {
        return ResponseEntity.status(403).body(ApiResponse.error("403", "Write operations are not allowed in Portal"));
    }

    @DeleteMapping("/{tableId}/{rowId}")
    @Operation(summary = "写操作 - 禁止")
    public ResponseEntity<ApiResponse<Void>> forbiddenDelete(@PathVariable Long tableId, @PathVariable String rowId) {
        return ResponseEntity.status(403).body(ApiResponse.error("403", "Write operations are not allowed in Portal"));
    }
}
