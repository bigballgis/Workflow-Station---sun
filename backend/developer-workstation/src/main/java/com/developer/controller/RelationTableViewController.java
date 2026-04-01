package com.developer.controller;

import com.developer.dto.ApiResponse;
import com.developer.entity.FormTableBinding;
import com.developer.entity.RelationViewConfig;
import com.developer.repository.FormTableBindingRepository;
import com.developer.security.RequireDeveloperPermission;
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
    private final FormTableBindingRepository formTableBindingRepository;

    @GetMapping("/{bindingId}")
    @Operation(summary = "获取 View 配置")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<RelationViewConfig>> getViewConfig(
            @PathVariable Long formId,
            @PathVariable Long bindingId) {
        RelationViewConfig config = viewService.getViewConfig(bindingId);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PutMapping("/{bindingId}")
    @Operation(summary = "保存 View 字段配置")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<RelationViewConfig>> saveViewConfig(
            @PathVariable Long formId,
            @PathVariable Long bindingId,
            @RequestBody List<ViewFieldDTO> fields) {
        RelationViewConfig config = viewService.saveViewConfig(bindingId, fields);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @GetMapping("/{bindingId}/fields")
    @Operation(summary = "获取可用字段列表")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<RelationFieldDTO>>> getAvailableFields(
            @PathVariable Long formId,
            @PathVariable Long bindingId) {
        // Get tableId from binding directly (works even without view config)
        FormTableBinding binding = formTableBindingRepository.findById(bindingId)
                .orElseThrow(() -> new IllegalArgumentException("Binding not found: " + bindingId));
        Long tableId = binding.getTableId();
        if (tableId == null) {
            throw new IllegalArgumentException("Binding has no associated table: " + bindingId);
        }
        List<RelationFieldDTO> fields = viewService.getAvailableFields(tableId);
        return ResponseEntity.ok(ApiResponse.success(fields));
    }

    @GetMapping("/fields-by-table")
    @Operation(summary = "通过 tableId 直接获取可用字段列表")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<RelationFieldDTO>>> getFieldsByTableId(
            @PathVariable Long formId,
            @RequestParam Long tableId) {
        List<RelationFieldDTO> fields = viewService.getAvailableFields(tableId);
        return ResponseEntity.ok(ApiResponse.success(fields));
    }
}
