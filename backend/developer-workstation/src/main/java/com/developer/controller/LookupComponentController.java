package com.developer.controller;

import com.developer.dto.ApiResponse;
import com.developer.entity.RelationLookupConfig;
import com.developer.security.RequireDeveloperPermission;
import com.developer.service.RelationLookupService;
import com.developer.service.RelationLookupService.BoundViewDTO;
import com.developer.service.RelationLookupService.LookupConfigDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Lookup 组件配置控制器
 */
@RestController
@RequestMapping("/api/forms/{formId}/lookup-config")
@RequiredArgsConstructor
    @Tag(name = "Lookup Component", description = "Lookup component configuration management")
public class LookupComponentController {

    private final RelationLookupService lookupService;

    @GetMapping("/{componentId}")
    @Operation(summary = "Get Lookup Configuration")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<RelationLookupConfig>> getLookupConfig(
            @PathVariable Long formId,
            @PathVariable String componentId) {
        RelationLookupConfig config = lookupService.getLookupConfig(formId, componentId);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PutMapping("/{componentId}")
    @Operation(summary = "Save Lookup Configuration")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<RelationLookupConfig>> saveLookupConfig(
            @PathVariable Long formId,
            @PathVariable String componentId,
            @RequestBody LookupConfigDTO config) {
        RelationLookupConfig result = lookupService.saveLookupConfig(formId, componentId, config);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{componentId}/bound-views")
    @Operation(summary = "Get Bound Views List")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<BoundViewDTO>>> getBoundViews(
            @PathVariable Long formId,
            @PathVariable String componentId) {
        List<BoundViewDTO> result = lookupService.getBoundViews(formId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
