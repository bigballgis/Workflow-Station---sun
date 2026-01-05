package com.developer.controller;

import com.developer.component.FormDesignComponent;
import com.developer.dto.ApiResponse;
import com.developer.dto.FormDefinitionRequest;
import com.developer.dto.ValidationResult;
import com.developer.entity.FormDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 表单设计控制器
 */
@RestController
@RequestMapping("/api/v1/function-units/{functionUnitId}/forms")
@RequiredArgsConstructor
@Tag(name = "表单设计", description = "表单设计相关操作")
public class FormDesignController {
    
    private final FormDesignComponent formDesignComponent;
    
    @GetMapping
    @Operation(summary = "获取功能单元的所有表单")
    public ResponseEntity<ApiResponse<List<FormDefinition>>> list(@PathVariable Long functionUnitId) {
        List<FormDefinition> result = formDesignComponent.getByFunctionUnitId(functionUnitId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping
    @Operation(summary = "创建表单")
    public ResponseEntity<ApiResponse<FormDefinition>> create(
            @PathVariable Long functionUnitId,
            @Valid @RequestBody FormDefinitionRequest request) {
        FormDefinition result = formDesignComponent.create(functionUnitId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PutMapping("/{formId}")
    @Operation(summary = "更新表单")
    public ResponseEntity<ApiResponse<FormDefinition>> update(
            @PathVariable Long functionUnitId,
            @PathVariable Long formId,
            @Valid @RequestBody FormDefinitionRequest request) {
        FormDefinition result = formDesignComponent.update(formId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @DeleteMapping("/{formId}")
    @Operation(summary = "删除表单")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long functionUnitId,
            @PathVariable Long formId) {
        formDesignComponent.delete(formId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    @GetMapping("/{formId}")
    @Operation(summary = "获取表单详情")
    public ResponseEntity<ApiResponse<FormDefinition>> getById(
            @PathVariable Long functionUnitId,
            @PathVariable Long formId) {
        FormDefinition result = formDesignComponent.getById(formId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @GetMapping("/{formId}/form-create-config")
    @Operation(summary = "生成Form-Create配置")
    public ResponseEntity<ApiResponse<String>> generateFormCreateConfig(
            @PathVariable Long functionUnitId,
            @PathVariable Long formId) {
        String result = formDesignComponent.generateFormConfig(formId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @GetMapping("/{formId}/validate")
    @Operation(summary = "验证表单配置")
    public ResponseEntity<ApiResponse<ValidationResult>> validate(
            @PathVariable Long functionUnitId,
            @PathVariable Long formId) {
        ValidationResult result = formDesignComponent.validate(formId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
