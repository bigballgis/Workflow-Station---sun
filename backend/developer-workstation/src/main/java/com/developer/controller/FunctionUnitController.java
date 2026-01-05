package com.developer.controller;

import com.developer.component.FunctionUnitComponent;
import com.developer.dto.ApiResponse;
import com.developer.dto.FunctionUnitRequest;
import com.developer.dto.FunctionUnitResponse;
import com.developer.dto.ValidationResult;
import com.developer.entity.FunctionUnit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 功能单元控制器
 */
@RestController
@RequestMapping("/api/v1/function-units")
@RequiredArgsConstructor
@Tag(name = "功能单元管理", description = "功能单元CRUD、发布、克隆等操作")
public class FunctionUnitController {
    
    private final FunctionUnitComponent functionUnitComponent;
    
    @PostMapping
    @Operation(summary = "创建功能单元")
    public ResponseEntity<ApiResponse<FunctionUnit>> create(@Valid @RequestBody FunctionUnitRequest request) {
        FunctionUnit result = functionUnitComponent.create(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "更新功能单元")
    public ResponseEntity<ApiResponse<FunctionUnit>> update(
            @PathVariable Long id, 
            @Valid @RequestBody FunctionUnitRequest request) {
        FunctionUnit result = functionUnitComponent.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "删除功能单元")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        functionUnitComponent.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "获取功能单元详情")
    public ResponseEntity<ApiResponse<FunctionUnit>> getById(@PathVariable Long id) {
        FunctionUnit result = functionUnitComponent.getById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @GetMapping
    @Operation(summary = "分页查询功能单元")
    public ResponseEntity<ApiResponse<Page<FunctionUnitResponse>>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        Page<FunctionUnitResponse> result = functionUnitComponent.list(name, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/{id}/publish")
    @Operation(summary = "发布功能单元")
    public ResponseEntity<ApiResponse<FunctionUnit>> publish(
            @PathVariable Long id,
            @RequestParam(required = false) String changeLog) {
        FunctionUnit result = functionUnitComponent.publish(id, changeLog);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/{id}/clone")
    @Operation(summary = "克隆功能单元")
    public ResponseEntity<ApiResponse<FunctionUnit>> clone(
            @PathVariable Long id,
            @RequestParam String newName) {
        FunctionUnit result = functionUnitComponent.clone(id, newName);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @GetMapping("/{id}/validate")
    @Operation(summary = "验证功能单元完整性")
    public ResponseEntity<ApiResponse<ValidationResult>> validate(@PathVariable Long id) {
        ValidationResult result = functionUnitComponent.validate(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
