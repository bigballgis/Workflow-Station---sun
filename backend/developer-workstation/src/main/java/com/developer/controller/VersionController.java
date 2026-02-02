package com.developer.controller;

import com.developer.component.VersionComponent;
import com.developer.dto.ApiResponse;
import com.developer.entity.Version;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 版本管理控制器
 */
@RestController
@RequestMapping("/function-units/{functionUnitId}/versions")
@RequiredArgsConstructor
@Tag(name = "版本管理", description = "版本历史、比较、回滚等操作")
public class VersionController {
    
    private final VersionComponent versionComponent;
    
    @GetMapping
    @Operation(summary = "获取版本历史")
    public ResponseEntity<ApiResponse<List<Version>>> list(@PathVariable Long functionUnitId) {
        List<Version> result = versionComponent.getVersionHistory(functionUnitId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @GetMapping("/{versionId}")
    @Operation(summary = "获取版本详情")
    public ResponseEntity<ApiResponse<Version>> getById(
            @PathVariable Long functionUnitId,
            @PathVariable Long versionId) {
        Version result = versionComponent.getById(versionId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/compare")
    @Operation(summary = "比较两个版本")
    public ResponseEntity<ApiResponse<Map<String, Object>>> compare(
            @PathVariable Long functionUnitId,
            @RequestParam Long versionId1,
            @RequestParam Long versionId2) {
        Map<String, Object> result = versionComponent.compare(versionId1, versionId2);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/{versionId}/rollback")
    @Operation(summary = "回滚到指定版本")
    public ResponseEntity<ApiResponse<Void>> rollback(
            @PathVariable Long functionUnitId,
            @PathVariable Long versionId) {
        versionComponent.rollback(functionUnitId, versionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    @GetMapping("/{versionId}/export")
    @Operation(summary = "导出版本")
    public ResponseEntity<byte[]> export(
            @PathVariable Long functionUnitId,
            @PathVariable Long versionId) {
        byte[] data = versionComponent.exportVersion(versionId);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=version-" + versionId + ".zip")
                .header("Content-Type", "application/zip")
                .body(data);
    }
}
