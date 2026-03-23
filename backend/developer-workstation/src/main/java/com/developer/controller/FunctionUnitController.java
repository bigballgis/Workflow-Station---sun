package com.developer.controller;

import com.developer.component.FunctionUnitComponent;
import com.developer.dto.ApiResponse;
import com.developer.dto.FunctionUnitRequest;
import com.developer.dto.FunctionUnitResponse;
import com.developer.dto.ValidationResult;
import com.developer.dto.VersionResponse;
import com.developer.entity.FunctionUnit;
import com.developer.security.RequireDeveloperPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 功能单元控制器
 */
@RestController
@RequestMapping("/function-units")
@Slf4j
@Tag(name = "功能单元管理", description = "功能单元CRUD、发布、克隆等操作")
public class FunctionUnitController extends BaseController {

    private final FunctionUnitComponent functionUnitComponent;
    
    public FunctionUnitController(FunctionUnitComponent functionUnitComponent) {
        this.functionUnitComponent = functionUnitComponent;
    }
    
    @PostMapping
    @Operation(summary = "创建功能单元")
    @RequireDeveloperPermission("FUNCTION_UNIT_CREATE")
    public ResponseEntity<ApiResponse<FunctionUnit>> create(@Valid @RequestBody FunctionUnitRequest request) {
        return handleRequest(() -> functionUnitComponent.create(request));
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "更新功能单元")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<FunctionUnit>> update(
            @PathVariable Long id, 
            @Valid @RequestBody FunctionUnitRequest request) {
        return handleRequest(() -> functionUnitComponent.update(id, request));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "删除功能单元")
    @RequireDeveloperPermission("FUNCTION_UNIT_DELETE")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        return handleRequest(() -> {
            functionUnitComponent.delete(id);
            return null;
        });
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "获取功能单元详情")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<FunctionUnitResponse>> getById(@PathVariable Long id) {
        return handleRequest(() -> functionUnitComponent.getByIdAsResponse(id));
    }
    
    @GetMapping
    @Operation(summary = "分页查询功能单元")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<Page<FunctionUnitResponse>>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return handleRequest(() -> functionUnitComponent.list(name, status, pageable));
    }
    
    @PostMapping("/{id}/publish")
    @Operation(summary = "发布功能单元")
    @RequireDeveloperPermission("FUNCTION_UNIT_PUBLISH")
    public ResponseEntity<ApiResponse<FunctionUnit>> publish(
            @PathVariable Long id,
            @RequestParam(required = false) String changeLog) {
        return handleRequest(() -> functionUnitComponent.publish(id, changeLog));
    }
    
    @PostMapping("/{id}/clone")
    @Operation(summary = "克隆功能单元")
    @RequireDeveloperPermission("FUNCTION_UNIT_CREATE")
    public ResponseEntity<ApiResponse<FunctionUnit>> clone(
            @PathVariable Long id,
            @RequestParam String newName) {
        return handleRequest(() -> functionUnitComponent.clone(id, newName));
    }
    
    @GetMapping("/{id}/validate")
    @Operation(summary = "验证功能单元完整性")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<ValidationResult>> validate(@PathVariable Long id) {
        return handleRequest(() -> functionUnitComponent.validate(id));
    }
    
    @GetMapping("/{id}/versions")
    @Operation(summary = "获取版本历史")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<VersionResponse>>> getVersions(@PathVariable Long id) {
        return handleRequest(() -> functionUnitComponent.getVersionHistory(id));
    }
}
