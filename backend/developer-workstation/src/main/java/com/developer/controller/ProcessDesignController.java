package com.developer.controller;

import com.developer.component.ProcessDesignComponent;
import com.developer.dto.ApiResponse;
import com.developer.dto.ErrorResponse;
import com.developer.dto.ValidationResult;
import com.developer.entity.ProcessDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 流程设计控制器
 */
@RestController
@RequestMapping("/function-units/{functionUnitId}/process")
@RequiredArgsConstructor
@Tag(name = "流程设计", description = "BPMN流程设计相关操作")
public class ProcessDesignController {
    
    private final ProcessDesignComponent processDesignComponent;
    
    @GetMapping
    @Operation(summary = "获取流程定义")
    public ResponseEntity<ApiResponse<ProcessDefinition>> get(@PathVariable Long functionUnitId) {
        ProcessDefinition result = processDesignComponent.getByFunctionUnitId(functionUnitId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping
    @Operation(summary = "保存流程定义")
    public ResponseEntity<ApiResponse<ProcessDefinition>> save(
            @PathVariable Long functionUnitId,
            @RequestBody Map<String, String> request) {
        String bpmnXml = request.get("bpmnXml");
        if (bpmnXml == null || bpmnXml.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ErrorResponse.builder()
                            .code("400")
                            .message("bpmnXml is required")
                            .build()));
        }
        ProcessDefinition result = processDesignComponent.save(functionUnitId, bpmnXml);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @GetMapping("/validate")
    @Operation(summary = "验证流程定义")
    public ResponseEntity<ApiResponse<ValidationResult>> validate(
            @PathVariable Long functionUnitId) {
        ProcessDefinition process = processDesignComponent.getByFunctionUnitId(functionUnitId);
        ValidationResult result = process != null ? 
                processDesignComponent.validate(process.getBpmnXml()) : new ValidationResult();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/simulate")
    @Operation(summary = "模拟流程执行")
    public ResponseEntity<ApiResponse<Map<String, Object>>> simulate(
            @PathVariable Long functionUnitId,
            @RequestBody Map<String, Object> variables) {
        ProcessDefinition process = processDesignComponent.getByFunctionUnitId(functionUnitId);
        Map<String, Object> result = process != null ?
                processDesignComponent.simulate(process.getBpmnXml(), variables) : Map.of();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
