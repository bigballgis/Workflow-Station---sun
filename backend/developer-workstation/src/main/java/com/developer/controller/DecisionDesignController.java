package com.developer.controller;

import com.developer.component.DecisionDesignComponent;
import com.platform.common.dto.ApiResponse;
import com.developer.dto.DecisionDefinitionRequest;
import com.developer.dto.DecisionTableModel;
import com.developer.dto.ValidationResult;
import com.developer.entity.DecisionDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 决策设计控制器
 */
@RestController
@RequestMapping("/function-units/{functionUnitId}/decisions")
@Slf4j
@Tag(name = "Decision Design", description = "Decision table design operations")
public class DecisionDesignController extends BaseController {

    private final DecisionDesignComponent decisionDesignComponent;

    public DecisionDesignController(DecisionDesignComponent decisionDesignComponent) {
        this.decisionDesignComponent = decisionDesignComponent;
    }

    @GetMapping
    @Operation(summary = "List all decisions of a function unit")
    public ResponseEntity<ApiResponse<List<DecisionDefinition>>> list(@PathVariable Long functionUnitId) {
        return handleRequest(() -> decisionDesignComponent.list(functionUnitId));
    }

    @PostMapping
    @Operation(summary = "Create decision definition")
    public ResponseEntity<ApiResponse<DecisionDefinition>> create(
            @PathVariable Long functionUnitId,
            @Valid @RequestBody DecisionDefinitionRequest request) {
        return handleRequest(() -> decisionDesignComponent.create(functionUnitId, request));
    }

    @GetMapping("/{decisionId}")
    @Operation(summary = "Get decision definition details")
    public ResponseEntity<ApiResponse<DecisionDefinition>> getById(
            @PathVariable Long functionUnitId,
            @PathVariable Long decisionId) {
        return handleRequest(() -> decisionDesignComponent.getById(functionUnitId, decisionId));
    }

    @PutMapping("/{decisionId}")
    @Operation(summary = "Update decision definition")
    public ResponseEntity<ApiResponse<DecisionDefinition>> update(
            @PathVariable Long functionUnitId,
            @PathVariable Long decisionId,
            @Valid @RequestBody DecisionDefinitionRequest request) {
        return handleRequest(() -> decisionDesignComponent.update(functionUnitId, decisionId, request));
    }

    @DeleteMapping("/{decisionId}")
    @Operation(summary = "Delete decision definition")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long functionUnitId,
            @PathVariable Long decisionId) {
        return handleRequest(() -> {
            decisionDesignComponent.delete(functionUnitId, decisionId);
            return null;
        });
    }

    @GetMapping("/{decisionId}/validate")
    @Operation(summary = "Validate stored DMN XML")
    public ResponseEntity<ApiResponse<ValidationResult>> validate(
            @PathVariable Long functionUnitId,
            @PathVariable Long decisionId) {
        return handleRequest(() -> decisionDesignComponent.validate(decisionId));
    }

    @GetMapping("/{decisionId}/model")
    @Operation(summary = "Get structured JSON model of decision table")
    public ResponseEntity<ApiResponse<DecisionTableModel>> getModel(
            @PathVariable Long functionUnitId,
            @PathVariable Long decisionId) {
        return handleRequest(() -> decisionDesignComponent.getModel(decisionId));
    }

    @PostMapping("/{decisionId}/model")
    @Operation(summary = "Update decision definition from structured JSON model")
    public ResponseEntity<ApiResponse<DecisionDefinition>> updateFromModel(
            @PathVariable Long functionUnitId,
            @PathVariable Long decisionId,
            @Valid @RequestBody DecisionTableModel model) {
        return handleRequest(() -> decisionDesignComponent.updateFromModel(decisionId, model));
    }
}
