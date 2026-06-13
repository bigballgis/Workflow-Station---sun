package com.developer.controller;

import com.platform.common.dto.ApiResponse;
import com.developer.security.RequireDeveloperPermission;
import com.developer.service.RelationTableBindingService;
import com.developer.service.RelationTableBindingService.RelationTableBindingDTO;
import com.platform.common.dto.RelationTableDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Relation Table 绑定控制器
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Relation Table 绑定", description = "Relation Table 绑定管理")
public class RelationTableBindingController {

    private final RelationTableBindingService bindingService;

    @GetMapping("/api/relation-tables/available")
    @Operation(summary = "获取可绑定的 Relation Table 列表")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<RelationTableDTO>>> getAvailableTables() {
        List<RelationTableDTO> result = bindingService.getAvailableTables();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/api/forms/{formId}/relation-bindings")
    @Operation(summary = "绑定 Relation Table")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Map<String, Long>>> bindRelationTable(
            @PathVariable Long formId,
            @RequestBody Map<String, Long> request) {
        Long tableId = request.get("tableId");
        Long bindingId = bindingService.bindRelationTable(formId, tableId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("bindingId", bindingId)));
    }

    @DeleteMapping("/api/forms/{formId}/relation-bindings/{bindingId}")
    @Operation(summary = "解除绑定")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Void>> unbindRelationTable(
            @PathVariable Long formId,
            @PathVariable Long bindingId) {
        bindingService.unbindRelationTable(formId, bindingId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/api/forms/{formId}/relation-bindings")
    @Operation(summary = "获取绑定列表")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<RelationTableBindingDTO>>> getBindings(
            @PathVariable Long formId) {
        List<RelationTableBindingDTO> result = bindingService.getBindings(formId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
