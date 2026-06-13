package com.developer.controller;

import com.developer.component.TableRelationComponent;
import com.platform.common.dto.ApiResponse;
import com.developer.dto.TableRelationDTO;
import com.developer.security.RequireDeveloperPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 表关系控制器
 */
@RestController
@RequestMapping("/function-units/{functionUnitId}/table-relations")
@Slf4j
@Tag(name = "Table Relations", description = "Table relation management operations")
public class TableRelationController extends BaseController {

    private final TableRelationComponent tableRelationComponent;

    public TableRelationController(TableRelationComponent tableRelationComponent) {
        this.tableRelationComponent = tableRelationComponent;
    }

    @GetMapping
    @Operation(summary = "List all table relations of a function unit")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<TableRelationDTO>>> list(
            @PathVariable Long functionUnitId) {
        return handleRequest(() -> tableRelationComponent.getByFunctionUnitId(functionUnitId));
    }

    @PostMapping
    @Operation(summary = "Batch save table relations (replaces existing)")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<List<TableRelationDTO>>> saveAll(
            @PathVariable Long functionUnitId,
            @Valid @RequestBody List<TableRelationDTO> relations) {
        return handleRequest(() -> tableRelationComponent.saveAll(functionUnitId, relations));
    }

    @DeleteMapping
    @Operation(summary = "Delete all table relations of a function unit")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Void>> deleteAll(
            @PathVariable Long functionUnitId) {
        return handleRequest(() -> {
            tableRelationComponent.deleteByFunctionUnitId(functionUnitId);
            return null;
        });
    }
}
