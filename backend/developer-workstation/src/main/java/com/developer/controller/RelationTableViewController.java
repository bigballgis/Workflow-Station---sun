package com.developer.controller;

import com.developer.dto.ApiResponse;
import com.developer.entity.RelationViewConfig;
import com.developer.service.RelationViewService;
import com.developer.service.RelationViewService.ViewFieldDTO;
import com.platform.common.dto.RelationFieldDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Relation Table View 配置控制器
 */
@RestController
@RequestMapping("/api/forms/{formId}/relation-views")
@RequiredArgsConstructor
@Tag(name = "Relation Table View", description = "View 配置管理")
public class RelationTableViewController {

    private final RelationViewService viewService;

    @GetMapping("/{bindingId}")
    @Operation(summary = "获取 View 配置")
    public ResponseEntity<ApiResponse<RelationViewConfig>> getViewConfig(
            @PathVariable Long formId,
            @PathVariable Long bindingId) {
        RelationViewConfig config = viewService.getViewConfig(bindingId);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PutMapping("/{bindingId}")
    @Operation(summary = "保存 View 字段配置")
    public ResponseEntity<ApiResponse<RelationViewConfig>> saveViewConfig(
            @PathVariable Long formId,
            @PathVariable Long bindingId,
            @RequestBody List<ViewFieldDTO> fields) {
        RelationViewConfig config = viewService.saveViewConfig(bindingId, fields);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @GetMapping("/{bindingId}/fields")
    @Operation(summary = "获取可用字段列表")
    public ResponseEntity<ApiResponse<List<RelationFieldDTO>>> getAvailableFields(
            @PathVariable Long formId,
            @PathVariable Long bindingId) {
        RelationViewConfig config = viewService.getViewConfig(bindingId);
        List<RelationFieldDTO> fields = viewService.getAvailableFields(config.getTableId());
        return ResponseEntity.ok(ApiResponse.success(fields));
    }
}
