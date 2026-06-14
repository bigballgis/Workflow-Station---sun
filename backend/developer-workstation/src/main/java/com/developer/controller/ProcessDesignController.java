package com.developer.controller;

import com.developer.component.ProcessDesignComponent;
import com.platform.common.dto.ApiResponse;
import com.platform.common.exception.ErrorResponse;
import com.developer.dto.ValidationResult;
import com.developer.entity.ProcessDefinition;
import com.developer.security.RequireDeveloperPermission;
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
@Tag(name = "Process Design", description = "BPMN process design operations")
public class ProcessDesignController {
    
    private final ProcessDesignComponent processDesignComponent;
    
    @GetMapping
    @Operation(summary = "Get process definition")
    public ResponseEntity<ApiResponse<ProcessDefinition>> get(@PathVariable Long functionUnitId) {
        ProcessDefinition result = processDesignComponent.getByFunctionUnitId(functionUnitId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping
    @Operation(summary = "Save process definition")
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
    @Operation(summary = "Validate process definition")
    public ResponseEntity<ApiResponse<ValidationResult>> validate(
            @PathVariable Long functionUnitId) {
        ProcessDefinition process = processDesignComponent.getByFunctionUnitId(functionUnitId);
        ValidationResult result = process != null ? 
                processDesignComponent.validate(process.getBpmnXml()) : new ValidationResult();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/simulate")
    @Operation(summary = "Simulate process execution")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<Map<String, Object>>> simulate(
            @PathVariable Long functionUnitId,
            @RequestBody Map<String, Object> variables) {
        ProcessDefinition process = processDesignComponent.getByFunctionUnitId(functionUnitId);
        Map<String, Object> result = process != null ?
                processDesignComponent.simulate(functionUnitId, process.getBpmnXml(), variables) : Map.of();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/debug/lookup/probe")
    @Operation(summary = "Debug lookup live probe")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<Map<String, Object>>> debugLookupProbe(
            @PathVariable Long functionUnitId,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> result = processDesignComponent.debugLookupProbe(functionUnitId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/debug/actions/run")
    @Operation(summary = "Debug action runner")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Map<String, Object>>> debugRunAction(
            @PathVariable Long functionUnitId,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> result = processDesignComponent.debugRunAction(functionUnitId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
