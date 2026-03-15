package com.developer.controller;

import com.developer.component.CommonTableComponent;
import com.developer.dto.ApiResponse;
import com.developer.dto.CommonTableRequest;
import com.developer.entity.CommonTableDefinition;
import com.developer.entity.CommonTableDeployment;
import com.developer.security.RequireDeveloperPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 公共表管理控制器
 * 提供与 function unit 无关的共享表结构的 CRUD 操作
 */
@RestController
@RequestMapping("/common-tables")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "公共表管理", description = "共享表结构的增删改查操作")
public class CommonTableController {

    private final CommonTableComponent commonTableComponent;

    @GetMapping
    @Operation(summary = "获取所有公共表")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<CommonTableDefinition>>> list() {
        List<CommonTableDefinition> result = commonTableComponent.findAll();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取公共表详情")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<CommonTableDefinition>> getById(@PathVariable Long id) {
        CommonTableDefinition result = commonTableComponent.findById(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/by-code/{code}")
    @Operation(summary = "通过编码获取公共表详情（含字段）")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<CommonTableDefinition>> getByCode(@PathVariable String code) {
        CommonTableDefinition result = commonTableComponent.findByCode(code);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    @Operation(summary = "创建公共表")
    @RequireDeveloperPermission("FUNCTION_UNIT_CREATE")
    public ResponseEntity<ApiResponse<CommonTableDefinition>> create(
            @Valid @RequestBody CommonTableRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        CommonTableDefinition result = commonTableComponent.create(request, userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新公共表")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<CommonTableDefinition>> update(
            @PathVariable Long id,
            @Valid @RequestBody CommonTableRequest request) {
        CommonTableDefinition result = commonTableComponent.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除公共表")
    @RequireDeveloperPermission("FUNCTION_UNIT_DELETE")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        commonTableComponent.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/deploy")
    @Operation(summary = "部署公共表（发布到 Admin Center）")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<CommonTableDefinition>> deploy(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        CommonTableDefinition result = commonTableComponent.deploy(id, userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PutMapping("/{id}/enabled")
    @Operation(summary = "切换公共表 enabled 状态")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<CommonTableDefinition>> updateEnabled(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        CommonTableDefinition result = commonTableComponent.updateEnabled(id, enabled);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}/deployments")
    @Operation(summary = "获取公共表部署记录")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<CommonTableDeployment>>> getDeployments(@PathVariable Long id) {
        List<CommonTableDeployment> result = commonTableComponent.findDeployments(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/deployments")
    @Operation(summary = "获取所有公共表部署记录")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<CommonTableDeployment>>> getAllDeployments() {
        List<CommonTableDeployment> result = commonTableComponent.findAllDeployments();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
