package com.developer.controller;

import com.developer.component.ExportImportComponent;
import com.developer.security.RequireDeveloperPermission;
import com.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 导入导出控制器
 */
@RestController
@RequestMapping("/export-import")
@RequiredArgsConstructor
@Tag(name = "Export Import", description = "Function unit export and import operations")
public class ExportImportController {
    
    private final ExportImportComponent exportImportComponent;
    
    @GetMapping("/function-units/{id}/export")
    @Operation(summary = "Export function unit")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        byte[] data = exportImportComponent.exportFunctionUnit(id);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=function-unit-" + id + ".zip")
                .header("Content-Type", "application/zip")
                .body(data);
    }
    
    @PostMapping("/import")
    @Operation(summary = "Import function unit",
            description = "Name does not exist → create a new function unit; name exists → add a version "
                    + "(snapshot current content, then replace with the imported package).")
    @RequireDeveloperPermission(value = {"FUNCTION_UNIT_CREATE", "FUNCTION_UNIT_UPDATE"}, mode = RequireDeveloperPermission.Mode.ALL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> importFunctionUnit(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String changeLog) {
        Map<String, Object> result = exportImportComponent.importFunctionUnit(file, changeLog);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate import package")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validate(
            @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = exportImportComponent.validateImportPackage(file);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/check-conflicts")
    @Operation(summary = "Check import conflicts")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkConflicts(
            @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = exportImportComponent.checkConflicts(file);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
