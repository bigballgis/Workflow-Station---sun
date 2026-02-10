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
import com.admin.service.FunctionUnitAccessService;
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
@RequestMapping("/function-units")
@RequiredArgsConstructor
@Tag(name = "功能单元管理", description = "功能包导入、部署管理和版本查询接口")
public class FunctionUnitController {
    
    private final FunctionUnitManagerComponent functionUnitManager;
    private final DeploymentManagerComponent deploymentManager;
    private final FunctionUnitAccessService accessService;
    
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
    
    @GetMapping("/deployed")
    @Operation(summary = "获取已部署的功能单元", description = "获取所有已部署的功能单元列表（供用户门户使用）")
    public ResponseEntity<java.util.Map<String, Object>> getDeployedFunctionUnits() {
        log.info("Getting deployed function units");
        
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        
        try {
            var units = functionUnitManager.listFunctionUnitsByStatus(
                    FunctionUnitStatus.DEPLOYED, 
                    org.springframework.data.domain.Pageable.unpaged());
            
            result.put("content", units.map(FunctionUnitInfo::fromEntity).getContent());
            result.put("totalElements", units.getTotalElements());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to get deployed function units", e);
            result.put("content", java.util.Collections.emptyList());
            result.put("totalElements", 0);
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
    
    @GetMapping("/deployed/latest")
    @Operation(summary = "获取每个功能单元的最新已部署版本", description = "每个 code 仅返回版本号最高的一条记录（供用户门户使用）")
    public ResponseEntity<java.util.Map<String, Object>> getLatestDeployedFunctionUnits() {
        log.info("Getting latest deployed function units (deduplicated by code)");
        
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        
        try {
            var units = functionUnitManager.listLatestDeployedFunctionUnits();
            var infos = units.stream().map(FunctionUnitInfo::fromEntity).toList();
            
            result.put("content", infos);
            result.put("totalElements", infos.size());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to get latest deployed function units", e);
            result.put("content", java.util.Collections.emptyList());
            result.put("totalElements", 0);
            result.put("error", e.getMessage());
            return ResponseEntity.ok(result);
        }
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
    public ResponseEntity<java.util.Map<String, Object>> setEnabled(
            @Parameter(description = "功能单元ID") @PathVariable String id,
            @RequestBody java.util.Map<String, Boolean> request) {
        log.info("Setting enabled status for function unit {}: {}", id, request.get("enabled"));
        Boolean enabled = request.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().build();
        }
        FunctionUnit unit = functionUnitManager.setEnabled(id, enabled);
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", unit.getId());
        response.put("enabled", unit.getEnabled());
        response.put("updatedAt", unit.getUpdatedAt());
        return ResponseEntity.ok(response);
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
    public ResponseEntity<java.util.Map<String, Object>> getFunctionUnitContent(
            @Parameter(description = "功能单元ID") @PathVariable String id) {
        log.info("Getting function unit content for: {}", id);
        
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        
        try {
            FunctionUnit unit = functionUnitManager.getFunctionUnitById(id);
            
            // 基本信息
            result.put("id", unit.getId());
            result.put("name", unit.getName());
            result.put("code", unit.getCode());
            result.put("version", unit.getVersion());
            result.put("description", unit.getDescription());
            result.put("status", unit.getStatus().name());
            
            // 获取内容
            java.util.List<com.admin.entity.FunctionUnitContent> contents = 
                    functionUnitManager.getFunctionUnitContents(id);
            
            // 没有过滤器，返回所有内容（按类型分类）
            java.util.List<java.util.Map<String, Object>> forms = new java.util.ArrayList<>();
            java.util.List<java.util.Map<String, Object>> processes = new java.util.ArrayList<>();
            java.util.List<java.util.Map<String, Object>> dataTables = new java.util.ArrayList<>();
            
            for (com.admin.entity.FunctionUnitContent content : contents) {
                java.util.Map<String, Object> contentMap = new java.util.HashMap<>();
                contentMap.put("id", content.getId());
                contentMap.put("name", content.getContentName());
                // 添加原始ID（用于 BPMN 中的 formId 匹配）
                contentMap.put("sourceId", content.getSourceId());
                
                // 对于流程定义，尝试解码 Base64
                String data = content.getContentData();
                if (content.getContentType() == com.admin.enums.ContentType.PROCESS && data != null) {
                    try {
                        // 尝试 Base64 解码
                        byte[] decoded = java.util.Base64.getDecoder().decode(data);
                        data = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
                        log.info("Decoded BPMN XML, length: {}", data.length());
                    } catch (IllegalArgumentException e) {
                        // 不是 Base64 编码，使用原始数据
                        log.info("BPMN data is not Base64 encoded, using raw data");
                    }
                }
                contentMap.put("data", data);
                contentMap.put("type", content.getContentType().name());
                
                // 使用 if-else 替代 switch 避免 ClassNotFoundException
                com.admin.enums.ContentType ct = content.getContentType();
                if (ct == com.admin.enums.ContentType.FORM) {
                    forms.add(contentMap);
                } else if (ct == com.admin.enums.ContentType.PROCESS) {
                    processes.add(contentMap);
                } else if (ct == com.admin.enums.ContentType.DATA_TABLE) {
                    dataTables.add(contentMap);
                }
            }
            
            result.put("forms", forms);
            result.put("processes", processes);
            result.put("dataTables", dataTables);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to get function unit content for {}: {}", id, e.getMessage(), e);
            result.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @PostMapping(value = "/formcontent", produces = "application/json")
    @Operation(summary = "获取功能单元表单内容", description = "获取功能单元的表单定义内容，用于表单弹窗等场景")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getFunctionUnitFormContent(
            @RequestBody java.util.Map<String, String> request) {
        String id = request.get("id");
        log.info("Getting function unit form content for: {}", id);
        
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        
        try {
            // 获取所有内容
            java.util.List<com.admin.entity.FunctionUnitContent> contents = 
                    functionUnitManager.getFunctionUnitContents(id);
            
            // 过滤并返回 FORM 类型的内容
            for (com.admin.entity.FunctionUnitContent content : contents) {
                if (content.getContentType() == com.admin.enums.ContentType.FORM) {
                    java.util.Map<String, Object> contentMap = new java.util.HashMap<>();
                    contentMap.put("id", content.getId());
                    contentMap.put("contentType", content.getContentType().name());
                    contentMap.put("contentName", content.getContentName());
                    contentMap.put("contentData", content.getContentData());
                    contentMap.put("sourceId", content.getSourceId());
                    result.add(contentMap);
                }
            }
            
            log.info("Returning {} form contents", result.size());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to get function unit form contents for {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @GetMapping(value = "/fu-content/{id}/type/{contentType}", produces = "application/json")
    @Operation(summary = "获取功能单元特定类型的内容", description = "获取功能单元的特定类型内容（如表单、流程等），用于表单弹窗等场景")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getFunctionUnitContentByType(
            @Parameter(description = "功能单元ID") @PathVariable String id,
            @Parameter(description = "内容类型：FORM, PROCESS, DATA_TABLE") @PathVariable String contentType) {
        log.info("Getting function unit content by type for: {}, contentType: {}", id, contentType);
        
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        
        try {
            // 获取所有内容
            java.util.List<com.admin.entity.FunctionUnitContent> contents = 
                    functionUnitManager.getFunctionUnitContents(id);
            
            // 解析请求的内容类型
            com.admin.enums.ContentType requestedType;
            try {
                requestedType = com.admin.enums.ContentType.valueOf(contentType.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.error("Invalid content type: {}", contentType);
                return ResponseEntity.badRequest().build();
            }
            
            // 过滤并返回指定类型的内容
            for (com.admin.entity.FunctionUnitContent content : contents) {
                if (content.getContentType() == requestedType) {
                    java.util.Map<String, Object> contentMap = new java.util.HashMap<>();
                    contentMap.put("id", content.getId());
                    contentMap.put("contentType", content.getContentType().name());
                    contentMap.put("contentName", content.getContentName());
                    contentMap.put("contentData", content.getContentData());
                    contentMap.put("sourceId", content.getSourceId());
                    result.add(contentMap);
                }
            }
            
            log.info("Returning {} contents of type {}", result.size(), contentType);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to get function unit contents for {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @GetMapping(value = "/{id}/content-items", produces = "application/json")
    @Operation(summary = "获取功能单元特定类型的内容", description = "获取功能单元的特定类型内容（如表单、流程等），用于表单弹窗等场景")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getFunctionUnitContents(
            @Parameter(description = "功能单元ID") @PathVariable String id,
            @Parameter(description = "内容类型") @RequestParam String contentType) {
        log.info("Getting function unit content items for: {}, contentType: {}", id, contentType);
        
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        
        try {
            // 获取所有内容
            java.util.List<com.admin.entity.FunctionUnitContent> contents = 
                    functionUnitManager.getFunctionUnitContents(id);
            
            // 解析请求的内容类型
            com.admin.enums.ContentType requestedType;
            try {
                requestedType = com.admin.enums.ContentType.valueOf(contentType.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.error("Invalid content type: {}", contentType);
                return ResponseEntity.badRequest().build();
            }
            
            // 过滤指定类型的内容
            for (com.admin.entity.FunctionUnitContent content : contents) {
                if (content.getContentType() == requestedType) {
                    java.util.Map<String, Object> contentMap = new java.util.HashMap<>();
                    contentMap.put("id", content.getId());
                    contentMap.put("contentType", content.getContentType().name());
                    contentMap.put("contentName", content.getContentName());
                    contentMap.put("contentData", content.getContentData());
                    contentMap.put("sourceId", content.getSourceId());
                    result.add(contentMap);
                }
            }
            
            log.info("Found {} contents of type {}", result.size(), contentType);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to get function unit contents for {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}
