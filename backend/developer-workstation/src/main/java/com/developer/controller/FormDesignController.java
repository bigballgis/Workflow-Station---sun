package com.developer.controller;

import com.developer.component.FormDesignComponent;
import com.developer.dto.ApiResponse;
import com.developer.dto.FormDefinitionRequest;
import com.developer.dto.FormTableBindingRequest;
import com.developer.dto.FormTableBindingResponse;
import com.developer.dto.ValidationResult;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.security.RequireDeveloperPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 表单设计控制器
 */
@RestController
@RequestMapping("/function-units/{functionUnitId}/forms")
@RequiredArgsConstructor
@Tag(name = "Form Design", description = "Form design operations")
public class FormDesignController {
    
    private final FormDesignComponent formDesignComponent;
    
    @GetMapping
    @Operation(summary = "List all forms of a function unit")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<FormDefinition>>> list(@PathVariable Long functionUnitId) {
        List<FormDefinition> result = formDesignComponent.getByFunctionUnitId(functionUnitId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping
    @Operation(summary = "Create form")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<FormDefinition>> create(
            @PathVariable Long functionUnitId,
            @Valid @RequestBody FormDefinitionRequest request) {
        FormDefinition result = formDesignComponent.create(functionUnitId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PutMapping("/{formId}")
    @Operation(summary = "Update form")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<FormDefinition>> update(
            @PathVariable Long functionUnitId,
            @PathVariable Long formId,
            @Valid @RequestBody FormDefinitionRequest request) {
        FormDefinition result = formDesignComponent.update(formId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @DeleteMapping("/{formId}")
    @Operation(summary = "Delete form")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long functionUnitId,
            @PathVariable Long formId) {
        formDesignComponent.delete(formId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    @GetMapping("/{formId}")
    @Operation(summary = "Get form details")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<FormDefinition>> getById(
            @PathVariable Long functionUnitId,
            @PathVariable Long formId) {
        FormDefinition result = formDesignComponent.getById(formId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @GetMapping("/{formId}/form-create-config")
    @Operation(summary = "Generate Form-Create config")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<String>> generateFormCreateConfig(
            @PathVariable Long functionUnitId,
            @PathVariable Long formId) {
        String result = formDesignComponent.generateFormConfig(formId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @GetMapping("/{formId}/validate")
    @Operation(summary = "Validate form config")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<ValidationResult>> validate(
            @PathVariable Long functionUnitId,
            @PathVariable Long formId) {
        ValidationResult result = formDesignComponent.validate(formId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    // ========== 表绑定管理端点 ==========
    
    @GetMapping("/{formId}/bindings")
    @Operation(summary = "List form table bindings")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<FormTableBindingResponse>>> getBindings(
            @PathVariable Long functionUnitId,
            @PathVariable Long formId) {
        List<FormTableBinding> bindings = formDesignComponent.getBindings(formId);
        List<FormTableBindingResponse> result = bindings.stream()
                .map(b -> FormTableBindingResponse.fromEntity(
                        b, formDesignComponent.resolveRelationTableName(b), formId))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/{formId}/bindings")
    @Operation(summary = "Create table binding")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<FormTableBindingResponse>> createBinding(
            @PathVariable Long functionUnitId,
            @PathVariable Long formId,
            @Valid @RequestBody FormTableBindingRequest request) {
        FormTableBindingResponse dto = formDesignComponent.createBinding(formId, request);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }
    
    @PutMapping("/{formId}/bindings/{bindingId}")
    @Operation(summary = "Update table binding")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<FormTableBindingResponse>> updateBinding(
            @PathVariable Long functionUnitId,
            @PathVariable Long formId,
            @PathVariable Long bindingId,
            @Valid @RequestBody FormTableBindingRequest request) {
        FormTableBinding binding = formDesignComponent.updateBinding(bindingId, request);
        return ResponseEntity.ok(ApiResponse.success(
                FormTableBindingResponse.fromEntity(
                        binding, formDesignComponent.resolveRelationTableName(binding), formId)));
    }
    
    @DeleteMapping("/{formId}/bindings/{bindingId}")
    @Operation(summary = "Delete table binding")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Void>> deleteBinding(
            @PathVariable Long functionUnitId,
            @PathVariable Long formId,
            @PathVariable Long bindingId) {
        formDesignComponent.deleteBinding(bindingId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    // ========== Process/Task Form 扩展端点 ==========
    
    @GetMapping("/data-table-columns")
    @Operation(summary = "Get Data_Table column names for autocomplete")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<String>>> getDataTableColumns(
            @PathVariable Long functionUnitId) {
        List<String> columns = formDesignComponent.getDataTableColumns(functionUnitId);
        return ResponseEntity.ok(ApiResponse.success(columns));
    }
    
    @PostMapping("/{formId}/copy")
    @Operation(summary = "Copy a Task Form (without Stage bindings)")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<FormDefinition>> copyTaskForm(
            @PathVariable Long functionUnitId,
            @PathVariable Long formId) {
        FormDefinition copied = formDesignComponent.copyTaskForm(formId);
        return ResponseEntity.ok(ApiResponse.success(copied));
    }

    @PostMapping("/{formId}/copy-to-task")
    @Operation(summary = "Copy a Process Form to a Task Form (full content copy, formType changed to TASK)")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<FormDefinition>> copyProcessToTaskForm(
            @PathVariable Long functionUnitId,
            @PathVariable Long formId) {
        FormDefinition copied = formDesignComponent.copyProcessToTaskForm(formId);
        return ResponseEntity.ok(ApiResponse.success(copied));
    }

}
