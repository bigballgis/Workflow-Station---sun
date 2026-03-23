package com.developer.controller;

import com.developer.component.DeploymentComponent;
import com.developer.component.ExportImportComponent;
import com.developer.dto.ApiResponse;
import com.developer.dto.DeployRequest;
import com.developer.dto.DeployResponse;
import com.developer.security.RequireDeveloperPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部署控制器
 */
@RestController
@RequestMapping("/function-units")
@RequiredArgsConstructor
@Tag(name = "Deployment", description = "Function unit export and one-click deployment")
public class DeploymentController {
    
    private final ExportImportComponent exportImportComponent;
    private final DeploymentComponent deploymentComponent;
    
    @GetMapping("/{id}/export")
    @Operation(summary = "Export function unit", description = "Export function unit as ZIP package")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<byte[]> exportFunctionUnit(@PathVariable Long id) {
        byte[] data = exportImportComponent.exportFunctionUnit(id);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "function-unit-" + id + ".zip");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(data);
    }
    
    @PostMapping("/{id}/deploy")
    @Operation(summary = "One-click deploy", description = "Deploy function unit to admin center")
    @RequireDeveloperPermission("FUNCTION_UNIT_PUBLISH")
    public ResponseEntity<ApiResponse<DeployResponse>> deploy(
            @PathVariable Long id,
            @Valid @RequestBody DeployRequest request) {
        DeployResponse response = deploymentComponent.deployToAdminCenter(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @GetMapping("/deployments/{deploymentId}/status")
    @Operation(summary = "Get deployment status")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<DeployResponse>> getDeploymentStatus(
            @PathVariable String deploymentId) {
        DeployResponse response = deploymentComponent.getDeploymentStatus(deploymentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @GetMapping("/{id}/deployments")
    @Operation(summary = "Get deployment history")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<DeployResponse>>> getDeploymentHistory(
            @PathVariable Long id) {
        List<DeployResponse> history = deploymentComponent.getDeploymentHistory(id);
        return ResponseEntity.ok(ApiResponse.success(history));
    }
}
