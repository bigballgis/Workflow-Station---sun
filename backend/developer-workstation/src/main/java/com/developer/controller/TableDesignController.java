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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 表设计控制器
 */
@RestController
@RequestMapping("/function-units/{functionUnitId}/tables")
@Slf4j
@Tag(name = "表设计", description = "数据表设计相关操作")
public class TableDesignController extends BaseController {
    
    private final TableDesignComponent tableDesignComponent;
    
    public TableDesignController(TableDesignComponent tableDesignComponent) {
        this.tableDesignComponent = tableDesignComponent;
    }
    
    @GetMapping
    @Operation(summary = "获取功能单元的所有表")
    public ResponseEntity<ApiResponse<List<TableDefinition>>> list(@PathVariable Long functionUnitId) {
        return handleRequest(() -> tableDesignComponent.getByFunctionUnitId(functionUnitId));
    }
    
    @PostMapping
    @Operation(summary = "创建表")
    public ResponseEntity<ApiResponse<TableDefinition>> create(
            @PathVariable Long functionUnitId,
            @Valid @RequestBody TableDefinitionRequest request) {
        return handleRequest(() -> tableDesignComponent.create(functionUnitId, request));
    }
    
    @PutMapping("/{tableId}")
    @Operation(summary = "更新表")
    public ResponseEntity<ApiResponse<TableDefinition>> update(
            @PathVariable Long functionUnitId,
            @PathVariable Long tableId,
            @Valid @RequestBody TableDefinitionRequest request) {
        return handleRequest(() -> tableDesignComponent.update(tableId, request));
    }
    
    @DeleteMapping("/{tableId}")
    @Operation(summary = "删除表")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long functionUnitId,
            @PathVariable Long tableId) {
        return handleRequest(() -> {
            tableDesignComponent.delete(tableId);
            return null;
        });
    }
    
    @GetMapping("/{tableId}")
    @Operation(summary = "获取表详情")
    public ResponseEntity<ApiResponse<TableDefinition>> getById(
            @PathVariable Long functionUnitId,
            @PathVariable Long tableId) {
        return handleRequest(() -> tableDesignComponent.getById(tableId));
    }
    
    @GetMapping("/{tableId}/ddl")
    @Operation(summary = "生成DDL")
    public ResponseEntity<ApiResponse<String>> generateDDL(
            @PathVariable Long functionUnitId,
            @PathVariable Long tableId,
            @RequestParam(defaultValue = "POSTGRESQL") DatabaseDialect dialect) {
        return handleRequest(() -> tableDesignComponent.generateDDL(tableId, dialect));
    }
    
    @GetMapping("/validate")
    @Operation(summary = "验证表结构")
    public ResponseEntity<ApiResponse<ValidationResult>> validate(@PathVariable Long functionUnitId) {
        return handleRequest(() -> tableDesignComponent.validateRelationships(functionUnitId));
    }
    
    @GetMapping("/foreign-keys")
    @Operation(summary = "获取功能单元的所有外键关系")
    public ResponseEntity<ApiResponse<List<ForeignKeyDTO>>> getForeignKeys(@PathVariable Long functionUnitId) {
        return handleRequest(() -> tableDesignComponent.getForeignKeys(functionUnitId));
    }
}
