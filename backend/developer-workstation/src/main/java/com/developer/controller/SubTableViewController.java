package com.developer.controller;

import com.developer.dto.ApiResponse;
import com.developer.entity.FormTableBinding;
import com.developer.entity.SubTableViewConfig;
import com.developer.repository.FormTableBindingRepository;
import com.developer.security.RequireDeveloperPermission;
import com.developer.service.SubTableViewService;
import com.developer.service.SubTableViewService.ViewConfigDTO;
import com.developer.service.SubTableViewService.ViewFieldDTO;
import com.platform.common.dto.RelationFieldDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Sub-Table View 配置控制器
 */
@RestController
@RequestMapping("/api/forms/{formId}/sub-table-views")
@RequiredArgsConstructor
@Tag(name = "Sub-Table View", description = "Sub-Table List View 配置管理")
public class SubTableViewController {

    private final SubTableViewService viewService;
    private final FormTableBindingRepository bindingRepository;

    @GetMapping("/{bindingId}")
    @Operation(summary = "获取 View 配置")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<ViewConfigDTO>> getViewConfig(
            @PathVariable Long formId,
            @PathVariable Long bindingId) {
        ViewConfigDTO config = viewService.getViewConfigDTO(bindingId);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @GetMapping("/{bindingId}/or-create")
    @Operation(summary = "获取或创建 View 配置")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<ViewConfigDTO>> getOrCreateViewConfig(
            @PathVariable Long formId,
            @PathVariable Long bindingId) {
        SubTableViewConfig config = viewService.getOrCreateViewConfig(bindingId);
        ViewConfigDTO dto = viewService.getViewConfigDTO(bindingId);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PostMapping("/{bindingId}/default")
    @Operation(summary = "创建默认视图（包含所有字段）")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<ViewConfigDTO>> createDefaultView(
            @PathVariable Long formId,
            @PathVariable Long bindingId) {
        SubTableViewConfig config = viewService.createDefaultViewConfig(bindingId);
        ViewConfigDTO dto = viewService.getViewConfigDTO(bindingId);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PutMapping("/{bindingId}")
    @Operation(summary = "保存 View 字段配置")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<ViewConfigDTO>> saveViewConfig(
            @PathVariable Long formId,
            @PathVariable Long bindingId,
            @RequestBody List<ViewFieldDTO> fields) {
        SubTableViewConfig config = viewService.saveViewConfig(bindingId, fields);
        ViewConfigDTO dto = viewService.getViewConfigDTO(bindingId);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/{bindingId}/fields")
    @Operation(summary = "获取可用字段列表")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<RelationFieldDTO>>> getAvailableFields(
            @PathVariable Long formId,
            @PathVariable Long bindingId) {
        FormTableBinding binding = bindingRepository.findById(bindingId)
                .orElseThrow(() -> new IllegalArgumentException("Binding not found: " + bindingId));
        List<RelationFieldDTO> fields = viewService.getAvailableFieldsByBinding(binding);
        return ResponseEntity.ok(ApiResponse.success(fields));
    }
}
