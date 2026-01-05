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
@RequestMapping("/api/v1/function-units/{functionUnitId}/actions")
@RequiredArgsConstructor
@Tag(name = "动作设计", description = "动作设计相关操作")
public class ActionDesignController {
    
    private final ActionDesignComponent actionDesignComponent;
    
    @GetMapping
    @Operation(summary = "获取功能单元的所有动作")
    public ResponseEntity<ApiResponse<List<ActionDefinition>>> list(@PathVariable Long functionUnitId) {
        List<ActionDefinition> result = actionDesignComponent.getByFunctionUnitId(functionUnitId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping
    @Operation(summary = "创建动作")
    public ResponseEntity<ApiResponse<ActionDefinition>> create(
            @PathVariable Long functionUnitId,
            @Valid @RequestBody ActionDefinitionRequest request) {
        ActionDefinition result = actionDesignComponent.create(functionUnitId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PutMapping("/{actionId}")
    @Operation(summary = "更新动作")
    public ResponseEntity<ApiResponse<ActionDefinition>> update(
            @PathVariable Long functionUnitId,
            @PathVariable Long actionId,
            @Valid @RequestBody ActionDefinitionRequest request) {
        ActionDefinition result = actionDesignComponent.update(actionId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @DeleteMapping("/{actionId}")
    @Operation(summary = "删除动作")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long functionUnitId,
            @PathVariable Long actionId) {
        actionDesignComponent.delete(actionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    @GetMapping("/{actionId}")
    @Operation(summary = "获取动作详情")
    public ResponseEntity<ApiResponse<ActionDefinition>> getById(
            @PathVariable Long functionUnitId,
            @PathVariable Long actionId) {
        ActionDefinition result = actionDesignComponent.getById(actionId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/{actionId}/test")
    @Operation(summary = "测试动作执行")
    public ResponseEntity<ApiResponse<Map<String, Object>>> test(
            @PathVariable Long functionUnitId,
            @PathVariable Long actionId,
            @RequestBody Map<String, Object> testData) {
        Map<String, Object> result = actionDesignComponent.test(actionId, testData);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
