package com.developer.controller;

import com.developer.component.ActionDesignComponent;
import com.developer.dto.ActionDefinitionRequest;
import com.developer.dto.ApiResponse;
import com.developer.entity.ActionDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 动作设计控制器
 */
@RestController
@RequestMapping("/function-units/{functionUnitId}/actions")
@RequiredArgsConstructor
@Tag(name = "Action Design", description = "Action design operations")
public class ActionDesignController {
    
    private final ActionDesignComponent actionDesignComponent;
    
    @GetMapping
    @Operation(summary = "List all actions of a function unit")
    public ResponseEntity<ApiResponse<List<ActionDefinition>>> list(@PathVariable Long functionUnitId) {
        List<ActionDefinition> result = actionDesignComponent.getByFunctionUnitId(functionUnitId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping
    @Operation(summary = "Create action")
    public ResponseEntity<ApiResponse<ActionDefinition>> create(
            @PathVariable Long functionUnitId,
            @Valid @RequestBody ActionDefinitionRequest request) {
        ActionDefinition result = actionDesignComponent.create(functionUnitId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PutMapping("/{actionId}")
    @Operation(summary = "Update action")
    public ResponseEntity<ApiResponse<ActionDefinition>> update(
            @PathVariable Long functionUnitId,
            @PathVariable Long actionId,
            @Valid @RequestBody ActionDefinitionRequest request) {
        ActionDefinition result = actionDesignComponent.update(actionId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @DeleteMapping("/{actionId}")
    @Operation(summary = "Delete action")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long functionUnitId,
            @PathVariable Long actionId) {
        actionDesignComponent.delete(actionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    @GetMapping("/{actionId}")
    @Operation(summary = "Get action details")
    public ResponseEntity<ApiResponse<ActionDefinition>> getById(
            @PathVariable Long functionUnitId,
            @PathVariable Long actionId) {
        ActionDefinition result = actionDesignComponent.getById(actionId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/{actionId}/test")
    @Operation(summary = "Test action execution")
    public ResponseEntity<ApiResponse<Map<String, Object>>> test(
            @PathVariable Long functionUnitId,
            @PathVariable Long actionId,
            @RequestBody Map<String, Object> testData) {
        Map<String, Object> result = actionDesignComponent.test(actionId, testData);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
