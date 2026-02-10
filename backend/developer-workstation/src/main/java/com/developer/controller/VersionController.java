package com.developer.controller;

import com.developer.dto.*;
import com.developer.entity.FunctionUnit;
import com.developer.service.DeploymentService;
import com.developer.service.RollbackService;
import com.developer.service.UIService;
import com.developer.service.VersionService;
import com.platform.common.exception.VersionValidationError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * REST API controller for function unit version management.
 * Provides endpoints for deployment, version queries, and rollback operations.
 * 
 * Requirements: 1.1, 2.5, 3.1, 3.2, 3.3, 6.2, 7.1
 */
@RestController
@RequestMapping("/api/function-units")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "版本管理", description = "功能单元版本管理相关操作")
public class VersionController {
    
    private final DeploymentService deploymentService;
    private final VersionService versionService;
    private final RollbackService rollbackService;
    private final UIService uiService;
    
    /**
     * Deploy a new version of a function unit.
     * 
     * Requirements: 1.1, 7.1
     */
    @PostMapping("/{functionUnitName}/deploy")
    @Operation(summary = "部署新版本", description = "部署功能单元的新版本")
    public ResponseEntity<ApiResponse<DeploymentResult>> deploy(
            @PathVariable String functionUnitName,
            @Valid @RequestBody DeploymentRequest request) {
        
        log.info("Deploying new version of function unit: {}, changeType: {}", 
                functionUnitName, request.getChangeType());
        
        try {
            DeploymentResult result = deploymentService.deployFunctionUnit(
                    functionUnitName,
                    request.getBpmnXml(),
                    request.getChangeType(),
                    request.getMetadata()
            );
            
            log.info("Successfully deployed version {} for function unit: {}", 
                    result.getVersion(), functionUnitName);
            
            return ResponseEntity.ok(ApiResponse.success(result));
            
        } catch (VersionValidationError e) {
            log.warn("Deployment validation failed for {}: {}", functionUnitName, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ErrorResponse.builder()
                            .code("VALIDATION_ERROR")
                            .message(e.getMessage())
                            .timestamp(Instant.now())
                            .build()));
        } catch (Exception e) {
            log.error("Deployment failed for {}: {}", functionUnitName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(
                    ErrorResponse.builder()
                            .code("DEPLOYMENT_ERROR")
                            .message("Deployment failed: " + e.getMessage())
                            .timestamp(Instant.now())
                            .build()));
        }
    }
    
    /**
     * Get version history for a function unit.
     * 
     * Requirements: 3.3
     */
    @GetMapping("/{functionUnitName}/versions")
    @Operation(summary = "获取版本历史", description = "获取功能单元的所有版本历史")
    public ResponseEntity<ApiResponse<List<FunctionUnit>>> getVersionHistory(
            @PathVariable String functionUnitName) {
        
        log.debug("Fetching version history for function unit: {}", functionUnitName);
        
        try {
            List<FunctionUnit> versions = versionService.getVersionHistory(functionUnitName);
            return ResponseEntity.ok(ApiResponse.success(versions));
            
        } catch (Exception e) {
            log.error("Failed to fetch version history for {}: {}", functionUnitName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(
                    ErrorResponse.builder()
                            .code("QUERY_ERROR")
                            .message("Failed to fetch version history: " + e.getMessage())
                            .timestamp(Instant.now())
                            .build()));
        }
    }
    
    /**
     * Get the active version of a function unit.
     * 
     * Requirements: 2.5
     */
    @GetMapping("/{functionUnitName}/versions/active")
    @Operation(summary = "获取活动版本", description = "获取功能单元的当前活动版本")
    public ResponseEntity<ApiResponse<FunctionUnit>> getActiveVersion(
            @PathVariable String functionUnitName) {
        
        log.debug("Fetching active version for function unit: {}", functionUnitName);
        
        try {
            FunctionUnit activeVersion = versionService.getActiveVersion(functionUnitName);
            
            if (activeVersion == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(
                        ErrorResponse.builder()
                                .code("NOT_FOUND")
                                .message("No active version found for function unit: " + functionUnitName)
                                .timestamp(Instant.now())
                                .build()));
            }
            
            return ResponseEntity.ok(ApiResponse.success(activeVersion));
            
        } catch (Exception e) {
            log.error("Failed to fetch active version for {}: {}", functionUnitName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(
                    ErrorResponse.builder()
                            .code("QUERY_ERROR")
                            .message("Failed to fetch active version: " + e.getMessage())
                            .timestamp(Instant.now())
                            .build()));
        }
    }
    
    /**
     * Rollback to a previous version.
     * 
     * Requirements: 6.2
     */
    @PostMapping("/{functionUnitName}/rollback")
    @Operation(summary = "回滚版本", description = "回滚到指定的历史版本")
    public ResponseEntity<ApiResponse<RollbackResult>> rollback(
            @PathVariable String functionUnitName,
            @Valid @RequestBody RollbackRequest request) {
        
        log.info("Rollback requested for function unit: {}, targetVersion: {}", 
                functionUnitName, request.getTargetVersion());
        
        try {
            // First, find the target version ID
            List<FunctionUnit> versions = versionService.getVersionHistory(functionUnitName);
            FunctionUnit targetVersion = versions.stream()
                    .filter(v -> v.getVersion().equals(request.getTargetVersion()))
                    .findFirst()
                    .orElseThrow(() -> VersionValidationError.invalidRollback(
                            request.getTargetVersion(),
                            "Version not found for function unit: " + functionUnitName));
            
            Long targetVersionId = targetVersion.getId();
            
            // Calculate the impact
            RollbackImpact impact = rollbackService.calculateRollbackImpact(targetVersionId);
            
            // If not confirmed, return the impact for user review
            if (!request.isConfirmed()) {
                log.info("Rollback impact calculated for {}: {} versions, {} processes will be deleted",
                        functionUnitName, impact.getVersionsToDelete().size(), 
                        impact.getTotalProcessInstancesToDelete());
                
                return ResponseEntity.ok(ApiResponse.error(
                        ErrorResponse.builder()
                                .code("CONFIRMATION_REQUIRED")
                                .message("Rollback requires confirmation. " +
                                        impact.getVersionsToDelete().size() + " versions and " +
                                        impact.getTotalProcessInstancesToDelete() + " process instances will be deleted.")
                                .suggestion("Set 'confirmed' to true to proceed with rollback")
                                .timestamp(Instant.now())
                                .build()));
            }
            
            // Execute the rollback
            RollbackResult result = rollbackService.rollbackToVersion(targetVersionId);
            
            log.info("Successfully rolled back {} to version {}", functionUnitName, result.getRolledBackToVersion());
            
            return ResponseEntity.ok(ApiResponse.success(result));
            
        } catch (VersionValidationError e) {
            log.warn("Rollback validation failed for {}: {}", functionUnitName, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    ErrorResponse.builder()
                            .code("VALIDATION_ERROR")
                            .message(e.getMessage())
                            .timestamp(Instant.now())
                            .build()));
        } catch (Exception e) {
            log.error("Rollback failed for {}: {}", functionUnitName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(
                    ErrorResponse.builder()
                            .code("ROLLBACK_ERROR")
                            .message("Rollback failed: " + e.getMessage())
                            .timestamp(Instant.now())
                            .build()));
        }
    }
    
    /**
     * Get function units for UI display (only active versions).
     * 
     * Requirements: 3.1, 3.2
     */
    @GetMapping
    @Operation(summary = "获取功能单元列表", description = "获取所有功能单元的活动版本用于UI显示")
    public ResponseEntity<ApiResponse<List<FunctionUnitDisplay>>> getFunctionUnitsForDisplay() {
        
        log.debug("Fetching function units for UI display");
        
        try {
            List<FunctionUnitDisplay> functionUnits = uiService.getFunctionUnitsForDisplay();
            return ResponseEntity.ok(ApiResponse.success(functionUnits));
            
        } catch (Exception e) {
            log.error("Failed to fetch function units for display: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(
                    ErrorResponse.builder()
                            .code("QUERY_ERROR")
                            .message("Failed to fetch function units: " + e.getMessage())
                            .timestamp(Instant.now())
                            .build()));
        }
    }
    
    /**
     * Get version history for UI display.
     * 
     * Requirements: 3.3, 3.4, 3.5
     */
    @GetMapping("/{functionUnitName}/history")
    @Operation(summary = "获取版本历史（UI）", description = "获取功能单元的版本历史用于UI显示")
    public ResponseEntity<ApiResponse<VersionHistoryDisplay>> getVersionHistoryForUI(
            @PathVariable String functionUnitName) {
        
        log.debug("Fetching version history for UI: {}", functionUnitName);
        
        try {
            VersionHistoryDisplay history = uiService.getVersionHistoryForUI(functionUnitName);
            return ResponseEntity.ok(ApiResponse.success(history));
            
        } catch (Exception e) {
            log.error("Failed to fetch version history for UI {}: {}", functionUnitName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(
                    ErrorResponse.builder()
                            .code("QUERY_ERROR")
                            .message("Failed to fetch version history: " + e.getMessage())
                            .timestamp(Instant.now())
                            .build()));
        }
    }
}
