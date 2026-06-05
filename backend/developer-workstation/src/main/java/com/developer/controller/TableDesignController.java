package com.developer.controller;

import com.developer.component.TableDesignComponent;
import com.developer.dto.ApiResponse;
import com.developer.dto.ForeignKeyDTO;
import com.developer.dto.TableDefinitionRequest;
import com.developer.dto.TableNameAvailabilityResponse;
import com.developer.dto.ValidationResult;
import com.developer.entity.TableDefinition;
import com.developer.enums.DatabaseDialect;
import com.developer.security.RequireDeveloperPermission;
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
@Tag(name = "Table Design", description = "Table definition design operations")
public class TableDesignController extends BaseController {
    
    private final TableDesignComponent tableDesignComponent;
    private final com.developer.component.PrimaryKeyAllocationComponent primaryKeyAllocationComponent;
    
    public TableDesignController(TableDesignComponent tableDesignComponent,
                                 com.developer.component.PrimaryKeyAllocationComponent primaryKeyAllocationComponent) {
        this.tableDesignComponent = tableDesignComponent;
        this.primaryKeyAllocationComponent = primaryKeyAllocationComponent;
    }
    
    @GetMapping
    @Operation(summary = "List all tables of a function unit")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<TableDefinition>>> list(@PathVariable Long functionUnitId) {
        return handleRequest(() -> tableDesignComponent.getByFunctionUnitId(functionUnitId));
    }
    
    @PostMapping
    @Operation(summary = "Create table")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<TableDefinition>> create(
            @PathVariable Long functionUnitId,
            @Valid @RequestBody TableDefinitionRequest request) {
        return handleRequest(() -> tableDesignComponent.create(functionUnitId, request));
    }
    
    @PutMapping("/{tableId}")
    @Operation(summary = "Update table")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<TableDefinition>> update(
            @PathVariable Long functionUnitId,
            @PathVariable Long tableId,
            @Valid @RequestBody TableDefinitionRequest request) {
        return handleRequest(() -> tableDesignComponent.update(tableId, request));
    }
    
    @DeleteMapping("/{tableId}")
    @Operation(summary = "Delete table")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long functionUnitId,
            @PathVariable Long tableId) {
        return handleRequest(() -> {
            tableDesignComponent.delete(tableId);
            return null;
        });
    }
    
    @GetMapping("/{tableId}")
    @Operation(summary = "Get table details")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<TableDefinition>> getById(
            @PathVariable Long functionUnitId,
            @PathVariable Long tableId) {
        return handleRequest(() -> tableDesignComponent.getById(tableId));
    }
    
    @GetMapping("/{tableId}/ddl")
    @Operation(summary = "Generate DDL")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<String>> generateDDL(
            @PathVariable Long functionUnitId,
            @PathVariable Long tableId,
            @RequestParam(defaultValue = "POSTGRESQL") DatabaseDialect dialect) {
        return handleRequest(() -> tableDesignComponent.generateDDL(tableId, dialect));
    }
    
    @GetMapping("/validate")
    @Operation(summary = "Validate table structure")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<ValidationResult>> validate(@PathVariable Long functionUnitId) {
        return handleRequest(() -> tableDesignComponent.validateRelationships(functionUnitId));
    }

    @GetMapping("/name-available")
    @Operation(summary = "Check if table name is globally available")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<TableNameAvailabilityResponse>> checkTableNameAvailable(
            @PathVariable Long functionUnitId,
            @RequestParam String tableName,
            @RequestParam(required = false) Long excludeTableId) {
        return handleRequest(() -> TableNameAvailabilityResponse.builder()
                .tableName(tableName)
                .available(tableDesignComponent.isTableNameAvailable(tableName, excludeTableId))
                .build());
    }
    
    @GetMapping("/foreign-keys")
    @Operation(summary = "Get all foreign key relationships")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<ForeignKeyDTO>>> getForeignKeys(@PathVariable Long functionUnitId) {
        return handleRequest(() -> tableDesignComponent.getForeignKeys(functionUnitId));
    }

    @PostMapping("/primary-keys/allocate")
    @Operation(summary = "Allocate primary key value(s) for a table field")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<com.developer.dto.AllocatePrimaryKeyResponse>> allocatePrimaryKeys(
            @PathVariable Long functionUnitId,
            @Valid @RequestBody com.developer.dto.AllocatePrimaryKeyRequest request) {
        return handleRequest(() -> primaryKeyAllocationComponent.allocate(request, functionUnitId));
    }
}
