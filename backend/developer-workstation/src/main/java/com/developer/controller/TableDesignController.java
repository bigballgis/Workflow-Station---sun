package com.developer.controller;

import com.developer.component.TableDesignComponent;
import com.developer.dto.ApiResponse;
import com.developer.dto.ForeignKeyDTO;
import com.developer.dto.TableDefinitionRequest;
import com.developer.dto.ValidationResult;
import com.developer.entity.TableDefinition;
import com.developer.enums.DatabaseDialect;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 表设计控制器
 */
@RestController
@RequestMapping("/function-units/{functionUnitId}/tables")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "表设计", description = "数据表设计相关操作")
public class TableDesignController {
    
    private final TableDesignComponent tableDesignComponent;
    
    @GetMapping
    @Operation(summary = "获取功能单元的所有表")
    public ResponseEntity<ApiResponse<List<TableDefinition>>> list(@PathVariable Long functionUnitId) {
        List<TableDefinition> result = tableDesignComponent.getByFunctionUnitId(functionUnitId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping
    @Operation(summary = "创建表")
    public ResponseEntity<ApiResponse<TableDefinition>> create(
            @PathVariable Long functionUnitId,
            @Valid @RequestBody TableDefinitionRequest request) {
        TableDefinition result = tableDesignComponent.create(functionUnitId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PutMapping("/{tableId}")
    @Operation(summary = "更新表")
    public ResponseEntity<ApiResponse<TableDefinition>> update(
            @PathVariable Long functionUnitId,
            @PathVariable Long tableId,
            @Valid @RequestBody TableDefinitionRequest request) {
        log.info("Received update request for table {}: fields count = {}", 
            tableId, request.getFields() != null ? request.getFields().size() : 0);
        TableDefinition result = tableDesignComponent.update(tableId, request);
        log.info("Table updated successfully: {} fields in result", 
            result.getFieldDefinitions() != null ? result.getFieldDefinitions().size() : 0);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @DeleteMapping("/{tableId}")
    @Operation(summary = "删除表")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long functionUnitId,
            @PathVariable Long tableId) {
        tableDesignComponent.delete(tableId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    @GetMapping("/{tableId}")
    @Operation(summary = "获取表详情")
    public ResponseEntity<ApiResponse<TableDefinition>> getById(
            @PathVariable Long functionUnitId,
            @PathVariable Long tableId) {
        TableDefinition result = tableDesignComponent.getById(tableId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    // DDL生成
    @GetMapping("/{tableId}/ddl")
    @Operation(summary = "生成DDL")
    public ResponseEntity<ApiResponse<String>> generateDDL(
            @PathVariable Long functionUnitId,
            @PathVariable Long tableId,
            @RequestParam(defaultValue = "POSTGRESQL") DatabaseDialect dialect) {
        String result = tableDesignComponent.generateDDL(tableId, dialect);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    // 验证
    @GetMapping("/validate")
    @Operation(summary = "验证表结构")
    public ResponseEntity<ApiResponse<ValidationResult>> validate(@PathVariable Long functionUnitId) {
        ValidationResult result = tableDesignComponent.validateRelationships(functionUnitId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    // 获取外键关系
    @GetMapping("/foreign-keys")
    @Operation(summary = "获取功能单元的所有外键关系")
    public ResponseEntity<ApiResponse<List<ForeignKeyDTO>>> getForeignKeys(@PathVariable Long functionUnitId) {
        List<ForeignKeyDTO> result = tableDesignComponent.getForeignKeys(functionUnitId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
