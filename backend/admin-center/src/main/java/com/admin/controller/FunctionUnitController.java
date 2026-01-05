package com.admin.controller;

import com.admin.component.DeploymentManagerComponent;
import com.admin.component.FunctionUnitManagerComponent;
import com.admin.dto.request.FunctionUnitImportRequest;
import com.admin.dto.response.FunctionUnitInfo;
import com.admin.dto.response.ImportResult;
import com.admin.dto.response.ValidationResult;
import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitApproval;
import com.admin.entity.FunctionUnitDeployment;
import com.admin.enums.DeploymentEnvironment;
import com.admin.enums.DeploymentStrategy;
import com.admin.enums.FunctionUnitStatus;
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
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/function-units")
@RequiredArgsConstructor
@Tag(name = "功能单元管理", description = "功能包导入、部署管理和版本查询接口")
public class FunctionUnitController {
    
    private final FunctionUnitManagerComponent functionUnitManager;
    private final DeploymentManagerComponent deploymentManager;
    
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
    
    @GetMapping("/{id}")
    @Operation(summary = "获取功能单元详情", description = "根据ID获取功能单元详细信息")
    public ResponseEntity<FunctionUnitInfo> getFunctionUnit(
            @Parameter(description = "功能单元ID") @PathVariable String id) {
        log.info("Getting function unit: {}", id);
        FunctionUnit unit = functionUnitManager.getFunctionUnitById(id);
        return ResponseEntity.ok(FunctionUnitInfo.fromEntity(unit));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "删除功能单元", description = "删除指定的功能单元")
    public ResponseEntity<Void> deleteFunctionUnit(
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
}
