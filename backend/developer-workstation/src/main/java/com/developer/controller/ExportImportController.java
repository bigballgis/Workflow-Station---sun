package com.developer.controller;

import com.developer.component.ExportImportComponent;
import com.developer.dto.ApiResponse;
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
@RequestMapping("/api/v1/export-import")
@RequiredArgsConstructor
@Tag(name = "导入导出", description = "功能单元导入导出操作")
public class ExportImportController {
    
    private final ExportImportComponent exportImportComponent;
    
    @GetMapping("/function-units/{id}/export")
    @Operation(summary = "导出功能单元")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        byte[] data = exportImportComponent.exportFunctionUnit(id);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=function-unit-" + id + ".zip")
                .header("Content-Type", "application/zip")
                .body(data);
    }
    
    @PostMapping("/import")
    @Operation(summary = "导入功能单元")
    public ResponseEntity<ApiResponse<Map<String, Object>>> importFunctionUnit(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "SKIP") String conflictStrategy) {
        Map<String, Object> result = exportImportComponent.importFunctionUnit(file, conflictStrategy);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/validate")
    @Operation(summary = "验证导入包")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validate(
            @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = exportImportComponent.validateImportPackage(file);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/check-conflicts")
    @Operation(summary = "检查导入冲突")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkConflicts(
            @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = exportImportComponent.checkConflicts(file);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
