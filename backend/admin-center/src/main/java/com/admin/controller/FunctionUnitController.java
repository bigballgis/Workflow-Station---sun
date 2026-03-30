package com.admin.controller;

import com.admin.component.DeploymentManagerComponent;
import com.admin.component.FunctionUnitManagerComponent;
import com.admin.dto.request.FunctionUnitAccessRequest;
import com.admin.dto.request.FunctionUnitImportRequest;
import com.admin.dto.response.FunctionUnitAccessInfo;
import com.admin.dto.response.FunctionUnitInfo;
import com.admin.dto.response.ImportResult;
import com.admin.dto.response.ValidationResult;
import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitApproval;
import com.admin.entity.FunctionUnitDeployment;
import com.admin.enums.DeploymentEnvironment;
import com.admin.enums.DeploymentStrategy;
import com.admin.enums.FunctionUnitStatus;
import com.admin.exception.AdminBusinessException;
import com.admin.service.FunctionUnitAccessService;
import com.platform.common.dto.ApiResponse;
import com.platform.common.resource.AbstractBaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 功能单元管理 RESTful API
 *
 * <p>Extends {@link AbstractBaseController} for unified {@link ApiResponse} wrapping
 * and HTTP status code mapping. Overrides {@link #handleError(Exception)} to also
 * handle admin-center specific {@link AdminBusinessException}.
 *
 * <p><b>Validates: Requirements 5.1, 5.2, 5.3, 5.4, 32.1, 32.2, 32.3</b>
 */
@Slf4j
@RestController
@RequestMapping("/function-units")
@RequiredArgsConstructor
@Tag(name = "功能单元管理", description = "功能包导入、部署管理和版本查询接口")
public class FunctionUnitController extends AbstractBaseController {
    
    private final FunctionUnitManagerComponent functionUnitManager;
    private final DeploymentManagerComponent deploymentManager;
    private final FunctionUnitAccessService accessService;

    /**
     * Extends base error handling to also map {@link AdminBusinessException}
     * (which does not extend platform-common's BusinessException) to HTTP 400.
     */
    @Override
    protected <T> ResponseEntity<ApiResponse<T>> handleError(Exception e) {
        if (e instanceof AdminBusinessException abe) {
            log.warn("Admin business error [{}]: {}", abe.getErrorCode(), abe.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(abe.getErrorCode(), abe.getMessage()));
        }
        return super.handleError(e);
    }
    
    // ==================== 功能包导入 ====================
    
    @PostMapping("/import")
    @Operation(summary = "导入功能包", description = "导入功能包文件")
    public ResponseEntity<ImportResult> importFunctionPackage(
            @Valid @RequestBody FunctionUnitImportRequest request,
            @Parameter(description = "导入者ID") @RequestHeader("X-User-Id") String importerId) {
        log.info("Importing function package: {}", request.getFileName());
        ImportResult result = functionUnitManager.importFunctionPackage(request, importerId);
        return ResponseEntity.status(result.isSuccess() ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST).body(result);
    }
    
    @PostMapping("/validate")
    @Operation(summary = "验证功能包", description = "验证功能包格式和内容")
    public ResponseEntity<ValidationResult> validatePackage(
            @Valid @RequestBody FunctionUnitImportRequest request) {
        log.info("Validating function package: {}", request.getFileName());
        ValidationResult result = functionUnitManager.validatePackage(request);
        return ResponseEntity.ok(result);
    }

    
    // ==================== 功能单元 CRUD ====================
    
    @GetMapping
    @Operation(summary = "获取功能单元列表", description = "分页获取功能单元列表")
    public ResponseEntity<Page<FunctionUnitInfo>> listFunctionUnits(
            @Parameter(description = "状态筛选") @RequestParam(required = false) FunctionUnitStatus status,
            Pageable pageable) {
        log.info("Listing function units, status: {}", status);
        Page<FunctionUnit> units = status != null 
                ? functionUnitManager.listFunctionUnitsByStatus(status, pageable)
                : functionUnitManager.listFunctionUnits(pageable);
        return ResponseEntity.ok(units.map(FunctionUnitInfo::fromEntity));
    }
    
    // TODO: [Req 28.2] Refactor to return ApiResponse<Page<FunctionUnitInfo>> instead of Map<String, Object>
    @GetMapping("/deployed")
    @Operation(summary = "获取已部署的功能单元", description = "获取所有已部署的功能单元列表（供用户门户使用）")
    public ResponseEntity<ApiResponse<List<FunctionUnitInfo>>> getDeployedFunctionUnits() {
        log.info("Getting deployed function units");
        return handleRequest(() -> {
            var units = functionUnitManager.listFunctionUnitsByStatus(
                    FunctionUnitStatus.DEPLOYED,
                    org.springframework.data.domain.Pageable.unpaged());
            return units.map(FunctionUnitInfo::fromEntity).getContent();
        });
    }
    
    // TODO: [Req 28.3] Refactor to return ApiResponse<List<FunctionUnitInfo>> instead of Map<String, Object>
    @GetMapping("/deployed/latest")
    @Operation(summary = "获取每个功能单元的最新已部署版本", description = "每个 code 仅返回版本号最高的一条记录（供用户门户使用）")
    public ResponseEntity<ApiResponse<List<FunctionUnitInfo>>> getLatestDeployedFunctionUnits() {
        log.info("Getting latest deployed function units (deduplicated by code)");
        return handleRequest(() -> {
            var units = functionUnitManager.listLatestDeployedFunctionUnits();
            return units.stream().map(FunctionUnitInfo::fromEntity).toList();
        });
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "获取功能单元详情", description = "根据ID获取功能单元详细信息")
    public ResponseEntity<FunctionUnitInfo> getFunctionUnit(
            @Parameter(description = "功能单元ID") @PathVariable String id) {
        log.info("Getting function unit: {}", id);
        FunctionUnit unit = functionUnitManager.getFunctionUnitById(id);
        return ResponseEntity.ok(FunctionUnitInfo.fromEntity(unit));
    }
    
    @GetMapping("/{id}/delete-preview")
    @Operation(summary = "获取删除预览", description = "获取功能单元删除预览，显示将被删除的关联数据统计")
    public ResponseEntity<com.admin.dto.response.DeletePreviewResponse> getDeletePreview(
            @Parameter(description = "功能单元ID") @PathVariable String id) {
        log.info("Getting delete preview for function unit: {}", id);
        com.admin.dto.response.DeletePreviewResponse preview = functionUnitManager.getDeletePreview(id);
        // 补充访问配置数量
        int accessConfigCount = accessService.getAccessConfigs(id).size();
        preview.setAccessConfigCount(accessConfigCount);
        return ResponseEntity.ok(preview);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "删除功能单元", description = "级联删除功能单元及其所有关联内容")
    public ResponseEntity<Void> deleteFunctionUnit(
            @Parameter(description = "功能单元ID") @PathVariable String id) {
        log.info("Deleting function unit cascade: {}", id);
        // 先删除访问配置
        accessService.deleteAllAccessConfigs(id);
        // 级联删除功能单元
        functionUnitManager.deleteFunctionUnitCascade(id);
        return ResponseEntity.noContent().build();
    }
    
    @PutMapping("/{id}/enabled")
    @Operation(summary = "切换启用状态", description = "切换功能单元的启用/禁用状态")
    public ResponseEntity<ApiResponse<FunctionUnitInfo>> setEnabled(
            @Parameter(description = "功能单元ID") @PathVariable String id,
            @RequestBody @Valid com.admin.dto.request.SetEnabledRequest request) {
        log.info("Setting enabled status for function unit {}: {}", id, request.getEnabled());
        return handleRequest(() -> {
            FunctionUnit unit = functionUnitManager.setEnabled(id, request.getEnabled());
            return FunctionUnitInfo.fromEntity(unit);
        });
    }

    // ==================== 批量操作 (Req 20) ====================

    @PutMapping("/batch/enabled")
    @Operation(summary = "批量启用/禁用", description = "批量切换功能单元的启用/禁用状态")
    public ResponseEntity<ApiResponse<List<FunctionUnitInfo>>> batchSetEnabled(
            @RequestBody @Valid com.admin.dto.request.BatchEnabledRequest request) {
        log.info("Batch setting enabled={} for {} function units", request.getEnabled(), request.getIds().size());
        return handleRequest(() -> request.getIds().stream()
                .map(id -> {
                    FunctionUnit unit = functionUnitManager.setEnabled(id, request.getEnabled());
                    return FunctionUnitInfo.fromEntity(unit);
                })
                .collect(Collectors.toList()));
    }

    @DeleteMapping("/batch")
    @Operation(summary = "批量删除", description = "批量删除功能单元及其关联内容")
    public ResponseEntity<ApiResponse<Void>> batchDelete(
            @RequestBody @Valid com.admin.dto.request.BatchDeleteRequest request) {
        log.info("Batch deleting {} function units", request.getIds().size());
        return handleRequest(() -> {
            for (String id : request.getIds()) {
                accessService.deleteAllAccessConfigs(id);
                functionUnitManager.deleteFunctionUnitCascade(id);
            }
            return null;
        });
    }
    
    @DeleteMapping("/{id}/legacy")
    @Operation(summary = "删除功能单元（旧版）", description = "删除指定的功能单元（旧版API，保留兼容）")
    public ResponseEntity<Void> deleteFunctionUnitLegacy(
            @Parameter(description = "功能单元ID") @PathVariable String id) {
        log.info("Deleting function unit: {}", id);
        FunctionUnit unit = functionUnitManager.getFunctionUnitById(id);
        functionUnitManager.deleteExistingVersion(unit.getCode(), unit.getVersion());
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/validate")
    @Operation(summary = "验证功能单元", description = "将功能单元标记为已验证")
    public ResponseEntity<FunctionUnitInfo> validateFunctionUnit(
            @Parameter(description = "功能单元ID") @PathVariable String id,
            @Parameter(description = "验证者ID") @RequestHeader("X-User-Id") String validatorId) {
        log.info("Validating function unit: {}", id);
        FunctionUnit unit = functionUnitManager.validateFunctionUnit(id, validatorId);
        return ResponseEntity.ok(FunctionUnitInfo.fromEntity(unit));
    }
    
    @PostMapping("/{id}/deprecate")
    @Operation(summary = "废弃功能单元", description = "将功能单元标记为已废弃")
    public ResponseEntity<FunctionUnitInfo> deprecateFunctionUnit(
            @Parameter(description = "功能单元ID") @PathVariable String id) {
        log.info("Deprecating function unit: {}", id);
        FunctionUnit unit = functionUnitManager.deprecateFunctionUnit(id);
        return ResponseEntity.ok(FunctionUnitInfo.fromEntity(unit));
    }

    
    // ==================== 部署管理 ====================

    /**
     * 获取所有部署记录（全局分页查询，不限定功能单元）
     * Req 15.2
     */
    @GetMapping("/deployments")
    @Operation(summary = "获取所有部署记录", description = "分页获取所有功能单元的部署记录")
    public ResponseEntity<ApiResponse<Page<FunctionUnitDeployment>>> getAllDeployments(Pageable pageable) {
        log.info("Getting all deployments, page: {}", pageable);
        return handleRequest(() -> deploymentManager.listAllDeployments(pageable));
    }
    
    @PostMapping("/{id}/deployments")
    @Operation(summary = "创建部署", description = "创建功能单元部署请求")
    public ResponseEntity<FunctionUnitDeployment> createDeployment(
            @Parameter(description = "功能单元ID") @PathVariable String id,
            @Parameter(description = "目标环境") @RequestParam DeploymentEnvironment environment,
            @Parameter(description = "部署策略") @RequestParam(defaultValue = "FULL") DeploymentStrategy strategy,
            @Parameter(description = "部署者ID") @RequestHeader("X-User-Id") String deployerId) {
        log.info("Creating deployment for function unit {} to {}", id, environment);
        FunctionUnitDeployment deployment = deploymentManager.createDeployment(id, environment, strategy, deployerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(deployment);
    }
    
    @GetMapping("/{id}/deployments")
    @Operation(summary = "获取部署历史", description = "获取功能单元的部署历史")
    public ResponseEntity<List<FunctionUnitDeployment>> getDeploymentHistory(
            @Parameter(description = "功能单元ID") @PathVariable String id) {
        log.info("Getting deployment history for function unit: {}", id);
        List<FunctionUnitDeployment> history = deploymentManager.getDeploymentHistory(id);
        return ResponseEntity.ok(history);
    }
    
    @PostMapping("/deployments/{deploymentId}/execute")
    @Operation(summary = "执行部署", description = "执行已审批的部署")
    public ResponseEntity<FunctionUnitDeployment> executeDeployment(
            @Parameter(description = "部署ID") @PathVariable String deploymentId) {
        log.info("Executing deployment: {}", deploymentId);
        FunctionUnitDeployment deployment = deploymentManager.executeDeployment(deploymentId);
        return ResponseEntity.ok(deployment);
    }
    
    @PostMapping("/deployments/{deploymentId}/rollback")
    @Operation(summary = "回滚部署", description = "回滚已部署的功能单元")
    public ResponseEntity<FunctionUnitDeployment> rollbackDeployment(
            @Parameter(description = "部署ID") @PathVariable String deploymentId,
            @Parameter(description = "操作者ID") @RequestHeader("X-User-Id") String operatorId,
            @Parameter(description = "回滚原因") @RequestParam String reason) {
        log.info("Rolling back deployment: {}", deploymentId);
        FunctionUnitDeployment deployment = deploymentManager.rollbackDeployment(deploymentId, operatorId, reason);
        return ResponseEntity.ok(deployment);
    }
    
    @PostMapping("/deployments/{deploymentId}/cancel")
    @Operation(summary = "取消部署", description = "取消待执行的部署")
    public ResponseEntity<FunctionUnitDeployment> cancelDeployment(
            @Parameter(description = "部署ID") @PathVariable String deploymentId,
            @Parameter(description = "操作者ID") @RequestHeader("X-User-Id") String operatorId,
            @Parameter(description = "取消原因") @RequestParam String reason) {
        log.info("Cancelling deployment: {}", deploymentId);
        FunctionUnitDeployment deployment = deploymentManager.cancelDeployment(deploymentId, operatorId, reason);
        return ResponseEntity.ok(deployment);
    }
    
    @GetMapping("/deployments/{deploymentId}")
    @Operation(summary = "获取部署详情", description = "获取部署记录详情")
    public ResponseEntity<FunctionUnitDeployment> getDeployment(
            @Parameter(description = "部署ID") @PathVariable String deploymentId) {
        log.info("Getting deployment: {}", deploymentId);
        FunctionUnitDeployment deployment = deploymentManager.getDeployment(deploymentId);
        return ResponseEntity.ok(deployment);
    }
    
    @GetMapping("/deployments/{deploymentId}/progress")
    @Operation(summary = "获取部署进度", description = "获取部署执行进度")
    public ResponseEntity<DeploymentManagerComponent.DeploymentProgress> getDeploymentProgress(
            @Parameter(description = "部署ID") @PathVariable String deploymentId) {
        log.info("Getting deployment progress: {}", deploymentId);
        DeploymentManagerComponent.DeploymentProgress progress = deploymentManager.getDeploymentProgress(deploymentId);
        return ResponseEntity.ok(progress);
    }

    
    // ==================== 审批管理 ====================
    
    @GetMapping("/deployments/{deploymentId}/approvals")
    @Operation(summary = "获取部署审批记录", description = "获取部署的审批记录列表")
    public ResponseEntity<List<FunctionUnitApproval>> getDeploymentApprovals(
            @Parameter(description = "部署ID") @PathVariable String deploymentId) {
        log.info("Getting approvals for deployment: {}", deploymentId);
        List<FunctionUnitApproval> approvals = deploymentManager.getDeploymentApprovals(deploymentId);
        return ResponseEntity.ok(approvals);
    }
    
    @PostMapping("/approvals/{approvalId}/approve")
    @Operation(summary = "审批通过", description = "审批通过部署请求")
    public ResponseEntity<FunctionUnitApproval> approveDeployment(
            @Parameter(description = "审批ID") @PathVariable String approvalId,
            @Parameter(description = "审批者ID") @RequestHeader("X-User-Id") String approverId,
            @Parameter(description = "审批意见") @RequestParam(required = false) String comment) {
        log.info("Approving deployment: {}", approvalId);
        FunctionUnitApproval approval = deploymentManager.approveDeployment(approvalId, approverId, comment);
        return ResponseEntity.ok(approval);
    }
    
    @PostMapping("/approvals/{approvalId}/reject")
    @Operation(summary = "审批拒绝", description = "拒绝部署请求")
    public ResponseEntity<FunctionUnitApproval> rejectDeployment(
            @Parameter(description = "审批ID") @PathVariable String approvalId,
            @Parameter(description = "审批者ID") @RequestHeader("X-User-Id") String approverId,
            @Parameter(description = "拒绝原因") @RequestParam String comment) {
        log.info("Rejecting deployment: {}", approvalId);
        FunctionUnitApproval approval = deploymentManager.rejectDeployment(approvalId, approverId, comment);
        return ResponseEntity.ok(approval);
    }
    
    @GetMapping("/approvals/pending")
    @Operation(summary = "获取待审批列表", description = "获取当前用户待审批的部署列表")
    public ResponseEntity<List<FunctionUnitApproval>> getPendingApprovals(
            @Parameter(description = "审批者ID") @RequestHeader("X-User-Id") String approverId) {
        log.info("Getting pending approvals for: {}", approverId);
        List<FunctionUnitApproval> approvals = deploymentManager.getPendingApprovals(approverId);
        return ResponseEntity.ok(approvals);
    }

    
    // ==================== 版本管理 ====================
    
    @GetMapping("/code/{code}/versions")
    @Operation(summary = "获取版本列表", description = "获取功能单元的所有版本")
    public ResponseEntity<List<FunctionUnitInfo>> getAllVersions(
            @Parameter(description = "功能单元代码") @PathVariable String code) {
        log.info("Getting all versions for: {}", code);
        List<FunctionUnit> versions = functionUnitManager.getAllVersions(code);
        return ResponseEntity.ok(versions.stream().map(FunctionUnitInfo::fromEntity).toList());
    }
    
    @GetMapping("/code/{code}/latest")
    @Operation(summary = "获取最新版本", description = "获取功能单元的最新版本")
    public ResponseEntity<FunctionUnitInfo> getLatestVersion(
            @Parameter(description = "功能单元代码") @PathVariable String code) {
        log.info("Getting latest version for: {}", code);
        return functionUnitManager.getLatestVersion(code)
                .map(unit -> ResponseEntity.ok(FunctionUnitInfo.fromEntity(unit)))
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/code/{code}/latest-stable")
    @Operation(summary = "获取最新稳定版本", description = "获取功能单元的最新稳定版本")
    public ResponseEntity<FunctionUnitInfo> getLatestStableVersion(
            @Parameter(description = "功能单元代码") @PathVariable String code) {
        log.info("Getting latest stable version for: {}", code);
        return functionUnitManager.getLatestStableVersion(code)
                .map(unit -> ResponseEntity.ok(FunctionUnitInfo.fromEntity(unit)))
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/{id}/new-version")
    @Operation(summary = "创建新版本", description = "基于现有版本创建新版本")
    public ResponseEntity<FunctionUnitInfo> createNewVersion(
            @Parameter(description = "源功能单元ID") @PathVariable String id,
            @Parameter(description = "新版本号") @RequestParam String newVersion,
            @Parameter(description = "创建者ID") @RequestHeader("X-User-Id") String creatorId) {
        log.info("Creating new version {} from {}", newVersion, id);
        FunctionUnit unit = functionUnitManager.createNewVersion(id, newVersion, creatorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(FunctionUnitInfo.fromEntity(unit));
    }
    
    @GetMapping("/code/{code}/history")
    @Operation(summary = "获取版本历史", description = "获取功能单元的版本变更历史")
    public ResponseEntity<List<FunctionUnitManagerComponent.VersionHistory>> getVersionHistory(
            @Parameter(description = "功能单元代码") @PathVariable String code) {
        log.info("Getting version history for: {}", code);
        List<FunctionUnitManagerComponent.VersionHistory> history = functionUnitManager.getVersionHistory(code);
        return ResponseEntity.ok(history);
    }
    
    @GetMapping("/code/{code}/upgrade-check")
    @Operation(summary = "检查版本升级", description = "检查是否可以从一个版本升级到另一个版本")
    public ResponseEntity<FunctionUnitManagerComponent.VersionUpgradeCheck> checkVersionUpgrade(
            @Parameter(description = "功能单元代码") @PathVariable String code,
            @Parameter(description = "源版本") @RequestParam String fromVersion,
            @Parameter(description = "目标版本") @RequestParam String toVersion) {
        log.info("Checking upgrade from {} to {} for {}", fromVersion, toVersion, code);
        FunctionUnitManagerComponent.VersionUpgradeCheck check = 
                functionUnitManager.checkVersionUpgrade(code, fromVersion, toVersion);
        return ResponseEntity.ok(check);
    }
    
    // ==================== 访问权限管理 ====================
    
    @GetMapping("/{id}/access")
    @Operation(summary = "获取访问权限配置", description = "获取功能单元的访问权限配置列表")
    public ResponseEntity<List<FunctionUnitAccessInfo>> getAccessConfigs(
            @Parameter(description = "功能单元ID") @PathVariable String id) {
        log.info("Getting access configs for function unit: {}", id);
        List<FunctionUnitAccessInfo> configs = accessService.getAccessConfigs(id);
        return ResponseEntity.ok(configs);
    }
    
    @PostMapping("/{id}/access")
    @Operation(summary = "添加访问权限配置", description = "为功能单元添加业务角色访问权限配置")
    public ResponseEntity<FunctionUnitAccessInfo> addAccessConfig(
            @Parameter(description = "功能单元ID") @PathVariable String id,
            @Valid @RequestBody FunctionUnitAccessRequest request) {
        log.info("Adding access config for function unit {}: roleId={}", 
                id, request.getRoleId());
        FunctionUnitAccessInfo config = accessService.addAccessConfig(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(config);
    }
    
    @DeleteMapping("/{id}/access/{accessId}")
    @Operation(summary = "删除访问权限配置", description = "删除功能单元的指定访问权限配置")
    public ResponseEntity<Void> removeAccessConfig(
            @Parameter(description = "功能单元ID") @PathVariable String id,
            @Parameter(description = "访问配置ID") @PathVariable String accessId) {
        log.info("Removing access config {} from function unit {}", accessId, id);
        accessService.removeAccessConfig(id, accessId);
        return ResponseEntity.noContent().build();
    }
    
    @PutMapping("/{id}/access")
    @Operation(summary = "批量设置访问权限配置", description = "批量设置功能单元的访问权限配置（替换现有配置）")
    public ResponseEntity<List<FunctionUnitAccessInfo>> setAccessConfigs(
            @Parameter(description = "功能单元ID") @PathVariable String id,
            @Valid @RequestBody List<FunctionUnitAccessRequest> requests) {
        log.info("Setting {} access configs for function unit {}", requests.size(), id);
        List<FunctionUnitAccessInfo> configs = accessService.setAccessConfigs(id, requests);
        return ResponseEntity.ok(configs);
    }
    
    @GetMapping("/{id}/access/check")
    @Operation(summary = "检查用户访问权限", description = "检查指定用户是否有权限访问功能单元")
    public ResponseEntity<Boolean> checkUserAccess(
            @Parameter(description = "功能单元ID") @PathVariable String id,
            @Parameter(description = "用户ID") @RequestParam String userId) {
        log.info("Checking access for user {} to function unit {}", userId, id);
        boolean hasAccess = accessService.hasAccess(id, userId);
        return ResponseEntity.ok(hasAccess);
    }
    
    // ==================== 功能单元内容获取（供用户门户使用） ====================
    
    @GetMapping("/by-process-key/{processKey}")
    @Operation(summary = "根据流程定义Key获取功能单元", description = "根据BPMN流程定义Key查找对应的功能单元")
    public ResponseEntity<FunctionUnitInfo> getFunctionUnitByProcessKey(
            @Parameter(description = "流程定义Key") @PathVariable String processKey) {
        log.info("Getting function unit by process key: {}", processKey);
        
        try {
            FunctionUnit unit = functionUnitManager.getFunctionUnitByProcessKey(processKey);
            return ResponseEntity.ok(FunctionUnitInfo.fromEntity(unit));
        } catch (Exception e) {
            log.error("Failed to get function unit by process key {}: {}", processKey, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/{id}/content")
    @Operation(summary = "获取功能单元完整内容", description = "获取功能单元的BPMN流程、表单定义、动作绑定等完整内容（供用户门户使用）")
    public ResponseEntity<ApiResponse<com.admin.dto.response.FunctionUnitContentResponse>> getFunctionUnitContent(
            @Parameter(description = "功能单元ID") @PathVariable String id) {
        log.info("Getting function unit content for: {}", id);
        return handleRequest(() -> functionUnitManager.assembleFunctionUnitContent(id));
    }
    
    // ==================== 合并内容端点 (Req 35) ====================

    @GetMapping("/{id}/contents")
    @Operation(summary = "获取功能单元内容", description = "获取功能单元的内容列表，可按类型过滤。type 为空时返回所有类型。")
    public ResponseEntity<ApiResponse<java.util.List<com.admin.dto.response.FunctionUnitContentItemDTO>>> getContents(
            @Parameter(description = "功能单元ID") @PathVariable String id,
            @Parameter(description = "内容类型（可选）：FORM, PROCESS, DATA_TABLE, SCRIPT, ACTION") @RequestParam(required = false) String type) {
        log.info("Getting function unit contents for: {}, type: {}", id, type);
        return handleRequest(() -> functionUnitManager.getContentsByType(id, type));
    }

    // ==================== 旧端点（已废弃，下一版本移除） ====================

    @Deprecated
    @PostMapping(value = "/formcontent", produces = "application/json")
    @Operation(summary = "获取功能单元表单内容（已废弃）", description = "已废弃，请使用 GET /{id}/contents?type=FORM")
    public ResponseEntity<ApiResponse<java.util.List<com.admin.dto.response.FunctionUnitContentItemDTO>>> getFunctionUnitFormContent(
            @RequestBody java.util.Map<String, String> request) {
        String id = request.get("id");
        log.info("[DEPRECATED] Getting function unit form content for: {}", id);
        return handleRequest(() -> functionUnitManager.getContentsByType(id, "FORM"));
    }
    
    @Deprecated
    @GetMapping(value = "/fu-content/{id}/type/{contentType}", produces = "application/json")
    @Operation(summary = "获取功能单元特定类型的内容（已废弃）", description = "已废弃，请使用 GET /{id}/contents?type={contentType}")
    public ResponseEntity<ApiResponse<java.util.List<com.admin.dto.response.FunctionUnitContentItemDTO>>> getFunctionUnitContentByType(
            @Parameter(description = "功能单元ID") @PathVariable String id,
            @Parameter(description = "内容类型：FORM, PROCESS, DATA_TABLE") @PathVariable String contentType) {
        log.info("[DEPRECATED] Getting function unit content by type for: {}, contentType: {}", id, contentType);
        return handleRequest(() -> functionUnitManager.getContentsByType(id, contentType));
    }
    
    @Deprecated
    @GetMapping(value = "/{id}/content-items", produces = "application/json")
    @Operation(summary = "获取功能单元特定类型的内容（已废弃）", description = "已废弃，请使用 GET /{id}/contents?type={contentType}")
    public ResponseEntity<ApiResponse<java.util.List<com.admin.dto.response.FunctionUnitContentItemDTO>>> getFunctionUnitContents(
            @Parameter(description = "功能单元ID") @PathVariable String id,
            @Parameter(description = "内容类型") @RequestParam String contentType) {
        log.info("[DEPRECATED] Getting function unit content items for: {}, contentType: {}", id, contentType);
        return handleRequest(() -> functionUnitManager.getContentsByType(id, contentType));
    }
}
