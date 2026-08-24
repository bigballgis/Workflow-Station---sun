package com.admin.controller;

import com.admin.component.DeploymentManagerComponent;
import com.admin.component.FunctionUnitDeploymentListQueryComponent;
import com.admin.component.FunctionUnitListQueryComponent;
import com.admin.component.FunctionUnitManagerComponent;
import com.admin.component.ProcessDeploymentComponent;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.FunctionUnitAccessRequest;
import com.admin.dto.request.FunctionUnitDeploymentListQueryRequest;
import com.admin.dto.request.FunctionUnitImportRequest;
import com.admin.dto.request.FunctionUnitListQueryRequest;
import com.admin.dto.response.DeploymentInfo;
import com.admin.dto.response.FunctionUnitAccessInfo;
import com.admin.dto.response.FunctionUnitAuditAccessInfo;
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
import com.admin.service.FunctionUnitAuditAccessService;
import com.admin.service.UserReferenceResolver;
import com.platform.common.dto.ApiResponse;
import com.platform.common.i18n.I18nService;
import com.platform.common.resource.AbstractBaseController;
import com.platform.security.util.SecurityContextUtils;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for Function Unit management.
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
@Tag(name = "Function unit management", description = "Function package import, deployment, and version APIs")
public class FunctionUnitController extends AbstractBaseController {
    
    /** DOS mitigation: maximum number of IDs per batch operation. */
    private static final int MAX_BATCH_IDS = 100;

    private final FunctionUnitManagerComponent functionUnitManager;
    private final DeploymentManagerComponent deploymentManager;
    private final ProcessDeploymentComponent processDeploymentComponent;
    private final FunctionUnitAccessService accessService;
    private final FunctionUnitAuditAccessService auditAccessService;
    private final UserReferenceResolver userReferenceResolver;
    private final I18nService i18nService;
    private final FunctionUnitListQueryComponent functionUnitListQueryComponent;
    private final FunctionUnitDeploymentListQueryComponent functionUnitDeploymentListQueryComponent;

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
    
    // ==================== Function package import ====================
    
    @PostMapping("/import")
    @Operation(summary = "Import function package", description = "Import a function package file")
    public ResponseEntity<ImportResult> importFunctionPackage(
            @Valid @RequestBody FunctionUnitImportRequest request) {
        String importerId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthorized")));
        log.info("Importing function package: {}", request.getFileName());
        ImportResult result = functionUnitManager.importFunctionPackage(request, importerId);
        return ResponseEntity.status(result.isSuccess() ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST).body(result);
    }
    
    @PostMapping("/validate")
    @Operation(summary = "Validate function package", description = "Validate package format and contents")
    public ResponseEntity<ValidationResult> validatePackage(
            @Valid @RequestBody FunctionUnitImportRequest request) {
        log.info("Validating function package: {}", request.getFileName());
        ValidationResult result = functionUnitManager.validatePackage(request);
        return ResponseEntity.ok(result);
    }

    
    // ==================== Function unit CRUD ====================
    
    @GetMapping
    @Operation(summary = "List function units", description = "Paginated list of function units")
    public ResponseEntity<Page<FunctionUnitInfo>> listFunctionUnits(
            @Parameter(description = "Optional status filter") @RequestParam(required = false) FunctionUnitStatus status,
            Pageable pageable) {
        log.info("Listing function units, status: {}", status);
        Page<FunctionUnit> units = status != null 
                ? functionUnitManager.listFunctionUnitsByStatus(status, pageable)
                : functionUnitManager.listFunctionUnits(pageable);
        return ResponseEntity.ok(units.map(FunctionUnitInfo::fromEntity));
    }

    @PostMapping("/query")
    @Operation(summary = "Page function units",
            description = "Shared list: latest version per code, then COUNT(*) and the page share one predicate")
    public ResponseEntity<AdminListPage<FunctionUnitInfo>> queryFunctionUnits(
            @RequestBody FunctionUnitListQueryRequest request) {
        return ResponseEntity.ok(functionUnitListQueryComponent.queryList(request));
    }

    @GetMapping("/deployed")
    @Operation(summary = "List deployed function units", description = "All deployed function units (for user portal)")
    public ResponseEntity<ApiResponse<List<FunctionUnitInfo>>> getDeployedFunctionUnits() {
        log.info("Getting deployed function units");
        return handleRequest(() -> {
            var units = functionUnitManager.listFunctionUnitsByStatus(
                    FunctionUnitStatus.DEPLOYED,
                    org.springframework.data.domain.Pageable.unpaged());
            return units.map(FunctionUnitInfo::fromEntity).getContent();
        });
    }
    
    @GetMapping("/deployed/latest")
    @Operation(summary = "Latest deployed per code", description = "Highest version per code (for user portal)")
    public ResponseEntity<ApiResponse<List<FunctionUnitInfo>>> getLatestDeployedFunctionUnits() {
        log.info("Getting latest deployed function units (deduplicated by code)");
        return handleRequest(() -> {
            var units = functionUnitManager.listLatestDeployedFunctionUnits();
            return units.stream().map(FunctionUnitInfo::fromEntity).toList();
        });
    }
    
    @GetMapping("/archived")
    @Operation(summary = "List archived function units", description = "Paginated archived (soft-deleted) function units")
    public ResponseEntity<Page<FunctionUnitInfo>> listArchivedFunctionUnits(Pageable pageable) {
        log.info("Listing archived function units");
        Page<FunctionUnitInfo> page = functionUnitManager.listArchivedFunctionUnits(pageable)
                .map(FunctionUnitInfo::fromEntity);
        enrichUpdatedByUsernames(page.getContent());
        return ResponseEntity.ok(page);
    }

    @PostMapping("/archived/query")
    @Operation(summary = "Page archived function units",
            description = "Shared list: latest archived version per code, then COUNT(*) and the page share one predicate")
    public ResponseEntity<AdminListPage<FunctionUnitInfo>> queryArchivedFunctionUnits(
            @RequestBody FunctionUnitListQueryRequest request) {
        return ResponseEntity.ok(functionUnitListQueryComponent.queryArchived(request));
    }

    private void enrichUpdatedByUsernames(List<FunctionUnitInfo> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        var cache = userReferenceResolver.resolveUsernames(
                items.stream().map(FunctionUnitInfo::getUpdatedBy).toList());
        for (FunctionUnitInfo item : items) {
            if (item.getUpdatedBy() != null) {
                item.setUpdatedBy(userReferenceResolver.resolveWithCache(item.getUpdatedBy(), cache));
            }
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get function unit by id", description = "Function unit detail by id")
    public ResponseEntity<FunctionUnitInfo> getFunctionUnit(
            @Parameter(description = "Function unit id") @PathVariable String id) {
        log.info("Getting function unit: {}", id);
        FunctionUnit unit = functionUnitManager.getFunctionUnitById(id);
        return ResponseEntity.ok(FunctionUnitInfo.fromEntity(unit));
    }
    
    @GetMapping("/{id}/delete-preview")
    @Operation(summary = "Delete preview", description = "Preview related data counts before delete")
    public ResponseEntity<com.admin.dto.response.DeletePreviewResponse> getDeletePreview(
            @Parameter(description = "Function unit id") @PathVariable String id) {
        log.info("Getting delete preview for function unit: {}", id);
        com.admin.dto.response.DeletePreviewResponse preview = functionUnitManager.getDeletePreview(id);
        // Augment with access-config count
        int accessConfigCount = accessService.getAccessConfigs(id).size();
        preview.setAccessConfigCount(accessConfigCount);
        return ResponseEntity.ok(preview);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Archive function unit", description = "Archive all versions by code and remove from portal")
    public ResponseEntity<Void> deleteFunctionUnit(
            @Parameter(description = "Function unit id") @PathVariable String id) {
        log.info("Archiving function unit: {}", id);
        functionUnitManager.archiveFunctionUnitByCode(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restore archived function unit", description = "Restore archived unit to DRAFT")
    public ResponseEntity<ApiResponse<FunctionUnitInfo>> restoreFunctionUnit(
            @Parameter(description = "Function unit id") @PathVariable String id) {
        log.info("Restoring archived function unit: {}", id);
        return handleRequest(() -> FunctionUnitInfo.fromEntity(functionUnitManager.restoreFunctionUnit(id)));
    }

    @PostMapping("/{id}/deploy")
    @Operation(summary = "Deploy function unit", description = "One-click deploy to user portal")
    public ResponseEntity<ApiResponse<FunctionUnitInfo>> deployFunctionUnit(
            @Parameter(description = "Function unit id") @PathVariable String id,
            @Parameter(description = "Deployer id header") @RequestHeader(value = "X-User-Id", defaultValue = "system") String deployerId) {
        log.info("Deploying function unit to user portal: {}", id);
        // C-3 (docs/ap-integration/DECISIONS.md#d6): trust only the authenticated username
        // (JWT, or a service-token-gated header mint — see SecurityConfig). The bare
        // X-User-Id header is attacker-controllable from a shared-network caller, so it must
        // not be used as the audit operator when no authenticated identity is present.
        String operator = SecurityContextUtils.getCurrentUsername().orElse("system");
        return handleRequest(() -> {
            FunctionUnit functionUnit = functionUnitManager.getFunctionUnitById(id);

            if (functionUnit.getStatus() == FunctionUnitStatus.ARCHIVED) {
                throw new AdminBusinessException("INVALID_STATUS", "Archived function units cannot be deployed");
            }
            if (!functionUnit.isDeployable()) {
                if (functionUnit.getStatus() == FunctionUnitStatus.DRAFT) {
                    throw new AdminBusinessException("VALIDATION_REQUIRED",
                            i18nService.getMessage("admin.fu.deploy_validation_required"));
                }
                throw new AdminBusinessException("INVALID_STATUS",
                        i18nService.getMessage("admin.fu.deploy_status_invalid", functionUnit.getStatus()));
            }

            ProcessDeploymentComponent.ProcessDeploymentResult processResult =
                    processDeploymentComponent.deployFunctionUnitProcess(id);
            if (!processResult.isSuccess() && !processResult.isPartialSuccess()) {
                if (processResult.getMessage() != null && processResult.getMessage().contains("Flowable")) {
                    throw new AdminBusinessException("FLOWABLE_UNAVAILABLE", processResult.getMessage());
                }
                throw new AdminBusinessException("PROCESS_DEPLOY_FAILED", processResult.getMessage());
            }

            functionUnit.markAsDeployed();
            functionUnitManager.saveFunctionUnit(functionUnit);
            functionUnitManager.disableOtherVersions(functionUnit.getCode(), functionUnit.getVersion(), operator);

            FunctionUnitDeployment deployment = deploymentManager.createDeployment(
                    id, DeploymentEnvironment.DEVELOPMENT, DeploymentStrategy.FULL, operator);
            if (!deploymentManager.requiresApproval(DeploymentEnvironment.DEVELOPMENT)) {
                deploymentManager.executeDeployment(deployment.getId());
            }

            FunctionUnit deployed = functionUnitManager.finalizeOneClickDeployEnable(id, operator);
            return FunctionUnitInfo.fromEntity(deployed);
        });
    }

    @PostMapping("/{id}/purge-runtime-data")
    @Operation(summary = "Purge portal runtime data", description = "Remove portal instances and histories by catalog row id")
    public ResponseEntity<ApiResponse<Map<String, Object>>> purgeRuntimeData(
            @Parameter(description = "Function unit catalog row id") @PathVariable String id) {
        log.info("Purging portal runtime data for catalog id: {}", id);
        return handleRequest(() -> functionUnitManager.purgeRuntimeDataForCatalog(id));
    }
    
    @PutMapping("/{id}/enabled")
    @Operation(summary = "Set enabled flag", description = "Enable or disable a function unit")
    public ResponseEntity<ApiResponse<FunctionUnitInfo>> setEnabled(
            @Parameter(description = "Function unit id") @PathVariable String id,
            @RequestBody @Valid com.admin.dto.request.SetEnabledRequest request) {
        String operatorId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthorized")));
        log.info("Setting enabled status for function unit {}: {}", id, request.getEnabled());
        return handleRequest(() -> {
            String op = operatorId != null && !operatorId.isBlank() ? operatorId : "system";
            FunctionUnit unit = functionUnitManager.setEnabled(id, request.getEnabled(), op, "Manual status change");
            return FunctionUnitInfo.fromEntity(unit);
        });
    }

    // ==================== Batch operations (Req 20) ====================

    @PutMapping("/batch/enabled")
    @Operation(summary = "Batch enable/disable", description = "Bulk toggle enabled flag")
    public ResponseEntity<ApiResponse<List<FunctionUnitInfo>>> batchSetEnabled(
            @RequestBody @Valid com.admin.dto.request.BatchEnabledRequest request) {
        String operatorId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthorized")));
        log.info("Batch setting enabled={} for {} function units", request.getEnabled(), request.getIds().size());

        // DOS mitigation: enforce batch ID cap
        if (request.getIds().size() > MAX_BATCH_IDS) {
            throw new AdminBusinessException("ID_COUNT_EXCEEDED",
                    i18nService.getMessage("admin.batch.id_count_exceeded", request.getIds().size(), MAX_BATCH_IDS));
        }

        String op = operatorId != null && !operatorId.isBlank() ? operatorId : "system";
        return handleRequest(() -> request.getIds().stream()
                .map(id -> {
                    FunctionUnit unit = functionUnitManager.setEnabled(id, request.getEnabled(), op, "Batch status change");
                    return FunctionUnitInfo.fromEntity(unit);
                })
                .collect(Collectors.toList()));
    }

    @DeleteMapping("/batch")
    @Operation(summary = "Batch archive", description = "Archive function units by code")
    public ResponseEntity<ApiResponse<Void>> batchDelete(
            @RequestBody @Valid com.admin.dto.request.BatchDeleteRequest request) {
        log.info("Batch archiving {} function units", request.getIds().size());

        // DOS mitigation: enforce batch ID cap
        if (request.getIds().size() > MAX_BATCH_IDS) {
            throw new AdminBusinessException("ID_COUNT_EXCEEDED",
                    i18nService.getMessage("admin.batch.id_count_exceeded", request.getIds().size(), MAX_BATCH_IDS));
        }
        return handleRequest(() -> {
            for (String id : request.getIds()) {
                functionUnitManager.archiveFunctionUnitByCode(id);
            }
            return null;
        });
    }
    
    @DeleteMapping("/{id}/legacy")
    @Operation(summary = "Delete function unit (legacy)", description = "Legacy API retained for compatibility")
    public ResponseEntity<Void> deleteFunctionUnitLegacy(
            @Parameter(description = "Function unit id") @PathVariable String id) {
        log.info("Deleting function unit: {}", id);
        FunctionUnit unit = functionUnitManager.getFunctionUnitById(id);
        functionUnitManager.deleteExistingVersion(unit.getCode(), unit.getVersion());
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/validate")
    @Operation(summary = "Validate function unit", description = "Structural checks and Flowable deployability")
    public ResponseEntity<ApiResponse<ValidationResult>> validateFunctionUnit(
            @Parameter(description = "Function unit id") @PathVariable String id) {
        String validatorId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthorized")));
        log.info("Validating function unit: {}", id);
        String operator = SecurityContextUtils.getCurrentUsername().orElse(validatorId);
        return handleRequest(() -> functionUnitManager.validateFunctionUnit(id, operator));
    }
    
    @PostMapping("/{id}/deprecate")
    @Operation(summary = "Deprecate function unit", description = "Mark function unit as deprecated")
    public ResponseEntity<FunctionUnitInfo> deprecateFunctionUnit(
            @Parameter(description = "Function unit id") @PathVariable String id) {
        log.info("Deprecating function unit: {}", id);
        FunctionUnit unit = functionUnitManager.deprecateFunctionUnit(id);
        return ResponseEntity.ok(FunctionUnitInfo.fromEntity(unit));
    }

    
    // ==================== Deployment management ====================

    /**
     * List all deployments (global paging, any function unit).
     * Req 15.2
     */
    @GetMapping("/deployments")
    @Operation(summary = "List all deployments", description = "Paginated deployments across units")
    public ResponseEntity<Page<DeploymentInfo>> getAllDeployments(Pageable pageable) {
        log.info("Getting all deployments, page: {}", pageable);
        return ResponseEntity.ok(deploymentManager.listAllDeployments(pageable));
    }

    @PostMapping("/deployments/query")
    @Operation(summary = "Page deployment records",
            description = "Shared list: COUNT(*) and the page share one predicate")
    public ResponseEntity<AdminListPage<DeploymentInfo>> queryDeployments(
            @RequestBody FunctionUnitDeploymentListQueryRequest request) {
        return ResponseEntity.ok(functionUnitDeploymentListQueryComponent.query(request));
    }

    @PostMapping("/{id}/deployments")
    @Operation(summary = "Create deployment", description = "Submit a deployment request")
    public ResponseEntity<FunctionUnitDeployment> createDeployment(
            @Parameter(description = "Function unit id") @PathVariable String id,
            @Parameter(description = "Target environment") @RequestParam DeploymentEnvironment environment,
            @Parameter(description = "Deployment strategy") @RequestParam(defaultValue = "FULL") DeploymentStrategy strategy) {
        String deployerId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthorized")));
        log.info("Creating deployment for function unit {} to {}", id, environment);
        FunctionUnitDeployment deployment = deploymentManager.createDeployment(id, environment, strategy, deployerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(deployment);
    }
    
    @GetMapping("/{id}/deployments")
    @Operation(summary = "Deployment history", description = "History for one function unit")
    public ResponseEntity<List<FunctionUnitDeployment>> getDeploymentHistory(
            @Parameter(description = "Function unit id") @PathVariable String id) {
        log.info("Getting deployment history for function unit: {}", id);
        List<FunctionUnitDeployment> history = deploymentManager.getDeploymentHistory(id);
        return ResponseEntity.ok(history);
    }
    
    @PostMapping("/deployments/{deploymentId}/execute")
    @Operation(summary = "Execute deployment", description = "Execute an approved deployment")
    public ResponseEntity<FunctionUnitDeployment> executeDeployment(
            @Parameter(description = "Deployment id") @PathVariable String deploymentId) {
        log.info("Executing deployment: {}", deploymentId);
        FunctionUnitDeployment deployment = deploymentManager.executeDeployment(deploymentId);
        return ResponseEntity.ok(deployment);
    }
    
    @PostMapping("/deployments/{deploymentId}/rollback")
    @Operation(summary = "Rollback deployment", description = "Rollback a completed deployment")
    public ResponseEntity<FunctionUnitDeployment> rollbackDeployment(
            @Parameter(description = "Deployment id") @PathVariable String deploymentId,
            @Parameter(description = "Rollback reason") @RequestParam String reason) {
        String operatorId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthorized")));
        log.info("Rolling back deployment: {}", deploymentId);
        FunctionUnitDeployment deployment = deploymentManager.rollbackDeployment(deploymentId, operatorId, reason);
        return ResponseEntity.ok(deployment);
    }
    
    @PostMapping("/deployments/{deploymentId}/cancel")
    @Operation(summary = "Cancel deployment", description = "Cancel pending deployment")
    public ResponseEntity<FunctionUnitDeployment> cancelDeployment(
            @Parameter(description = "Deployment id") @PathVariable String deploymentId,
            @Parameter(description = "Cancel reason") @RequestParam String reason) {
        String operatorId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthorized")));
        log.info("Cancelling deployment: {}", deploymentId);
        FunctionUnitDeployment deployment = deploymentManager.cancelDeployment(deploymentId, operatorId, reason);
        return ResponseEntity.ok(deployment);
    }
    
    @GetMapping("/deployments/{deploymentId}")
    @Operation(summary = "Get deployment", description = "Single deployment detail")
    public ResponseEntity<FunctionUnitDeployment> getDeployment(
            @Parameter(description = "Deployment id") @PathVariable String deploymentId) {
        log.info("Getting deployment: {}", deploymentId);
        FunctionUnitDeployment deployment = deploymentManager.getDeployment(deploymentId);
        return ResponseEntity.ok(deployment);
    }
    
    @GetMapping("/deployments/{deploymentId}/progress")
    @Operation(summary = "Deployment progress", description = "Poll deployment execution progress")
    public ResponseEntity<DeploymentManagerComponent.DeploymentProgress> getDeploymentProgress(
            @Parameter(description = "Deployment id") @PathVariable String deploymentId) {
        log.info("Getting deployment progress: {}", deploymentId);
        DeploymentManagerComponent.DeploymentProgress progress = deploymentManager.getDeploymentProgress(deploymentId);
        return ResponseEntity.ok(progress);
    }

    
    // ==================== Approval workflows ====================
    
    @GetMapping("/deployments/{deploymentId}/approvals")
    @Operation(summary = "List deployment approvals", description = "Approvals tied to deployment")
    public ResponseEntity<List<FunctionUnitApproval>> getDeploymentApprovals(
            @Parameter(description = "Deployment id") @PathVariable String deploymentId) {
        log.info("Getting approvals for deployment: {}", deploymentId);
        List<FunctionUnitApproval> approvals = deploymentManager.getDeploymentApprovals(deploymentId);
        return ResponseEntity.ok(approvals);
    }
    
    @PostMapping("/approvals/{approvalId}/approve")
    @Operation(summary = "Approve deployment", description = "Approve a deployment request")
    public ResponseEntity<FunctionUnitApproval> approveDeployment(
            @Parameter(description = "Approval id") @PathVariable String approvalId,
            @Parameter(description = "Optional comment") @RequestParam(required = false) String comment) {
        String approverId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthorized")));
        log.info("Approving deployment: {}", approvalId);
        FunctionUnitApproval approval = deploymentManager.approveDeployment(approvalId, approverId, comment);
        return ResponseEntity.ok(approval);
    }
    
    @PostMapping("/approvals/{approvalId}/reject")
    @Operation(summary = "Reject deployment", description = "Reject a deployment request")
    public ResponseEntity<FunctionUnitApproval> rejectDeployment(
            @Parameter(description = "Approval id") @PathVariable String approvalId,
            @Parameter(description = "Rejection comment") @RequestParam String comment) {
        String approverId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthorized")));
        log.info("Rejecting deployment: {}", approvalId);
        FunctionUnitApproval approval = deploymentManager.rejectDeployment(approvalId, approverId, comment);
        return ResponseEntity.ok(approval);
    }
    
    @GetMapping("/approvals/pending")
    @Operation(summary = "Pending approvals", description = "Deployments awaiting current user's approval")
    public ResponseEntity<List<FunctionUnitApproval>> getPendingApprovals() {
        String approverId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthorized")));
        log.info("Getting pending approvals for: {}", approverId);
        List<FunctionUnitApproval> approvals = deploymentManager.getPendingApprovals(approverId);
        return ResponseEntity.ok(approvals);
    }

    
    // ==================== Version management ====================
    
    @GetMapping("/code/{code}/versions")
    @Operation(summary = "List versions", description = "All versions for code")
    public ResponseEntity<List<FunctionUnitInfo>> getAllVersions(
            @Parameter(description = "Function unit code") @PathVariable String code) {
        log.info("Getting all versions for: {}", code);
        List<FunctionUnit> versions = functionUnitManager.getAllVersions(code);
        return ResponseEntity.ok(versions.stream().map(FunctionUnitInfo::fromEntity).toList());
    }
    
    @GetMapping("/code/{code}/latest")
    @Operation(summary = "Latest version", description = "Latest semantic version")
    public ResponseEntity<FunctionUnitInfo> getLatestVersion(
            @Parameter(description = "Function unit code") @PathVariable String code) {
        log.info("Getting latest version for: {}", code);
        return functionUnitManager.getLatestVersion(code)
                .map(unit -> ResponseEntity.ok(FunctionUnitInfo.fromEntity(unit)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/code/{code}/active-for-start")
    @Operation(summary = "Active catalog for portal start", description = "Highest deployed+enabled semver for pinning catalog row")
    public ResponseEntity<ApiResponse<FunctionUnitInfo>> getActiveCatalogForPortalStart(
            @Parameter(description = "Function unit code") @PathVariable String code) {
        log.info("Getting active catalog for portal start, code: {}", code);
        return functionUnitManager.getActiveCatalogForPortalStart(code)
                .map(u -> ResponseEntity.ok(ApiResponse.success(FunctionUnitInfo.fromEntity(u))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("NO_ACTIVE_FOR_START",
                                i18nService.getMessage("admin.fu.no_active_for_start"))));
    }
    
    @GetMapping("/code/{code}/latest-stable")
    @Operation(summary = "Latest stable version", description = "Highest stable release")
    public ResponseEntity<FunctionUnitInfo> getLatestStableVersion(
            @Parameter(description = "Function unit code") @PathVariable String code) {
        log.info("Getting latest stable version for: {}", code);
        return functionUnitManager.getLatestStableVersion(code)
                .map(unit -> ResponseEntity.ok(FunctionUnitInfo.fromEntity(unit)))
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/{id}/new-version")
    @Operation(summary = "Fork new version", description = "Clone from existing unit with semver")
    public ResponseEntity<FunctionUnitInfo> createNewVersion(
            @Parameter(description = "Source function unit id") @PathVariable String id,
            @Parameter(description = "New version label") @RequestParam String newVersion) {
        String creatorId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthorized")));
        log.info("Creating new version {} from {}", newVersion, id);
        FunctionUnit unit = functionUnitManager.createNewVersion(id, newVersion, creatorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(FunctionUnitInfo.fromEntity(unit));
    }
    
    @GetMapping("/code/{code}/history")
    @Operation(summary = "Version history", description = "Historical changes for code")
    public ResponseEntity<List<FunctionUnitManagerComponent.VersionHistory>> getVersionHistory(
            @Parameter(description = "Function unit code") @PathVariable String code) {
        log.info("Getting version history for: {}", code);
        List<FunctionUnitManagerComponent.VersionHistory> history = functionUnitManager.getVersionHistory(code);
        return ResponseEntity.ok(history);
    }
    
    @GetMapping("/code/{code}/upgrade-check")
    @Operation(summary = "Upgrade check", description = "Evaluate upgrade path between versions")
    public ResponseEntity<FunctionUnitManagerComponent.VersionUpgradeCheck> checkVersionUpgrade(
            @Parameter(description = "Function unit code") @PathVariable String code,
            @Parameter(description = "From version") @RequestParam String fromVersion,
            @Parameter(description = "To version") @RequestParam String toVersion) {
        log.info("Checking upgrade from {} to {} for {}", fromVersion, toVersion, code);
        FunctionUnitManagerComponent.VersionUpgradeCheck check = 
                functionUnitManager.checkVersionUpgrade(code, fromVersion, toVersion);
        return ResponseEntity.ok(check);
    }
    
    // ==================== Access control ====================
    
    @GetMapping("/{id}/access")
    @Operation(summary = "List access rules", description = "Access configs for unit")
    public ResponseEntity<List<FunctionUnitAccessInfo>> getAccessConfigs(
            @Parameter(description = "Function unit id") @PathVariable String id) {
        log.info("Getting access configs for function unit: {}", id);
        List<FunctionUnitAccessInfo> configs = accessService.getAccessConfigs(id);
        return ResponseEntity.ok(configs);
    }
    
    @PostMapping("/{id}/access")
    @Operation(summary = "Add access rule", description = "Grant business role access")
    public ResponseEntity<FunctionUnitAccessInfo> addAccessConfig(
            @Parameter(description = "Function unit id") @PathVariable String id,
            @Valid @RequestBody FunctionUnitAccessRequest request) {
        log.info("Adding access config for function unit {}: roleId={}", 
                id, request.getRoleId());
        FunctionUnitAccessInfo config = accessService.addAccessConfig(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(config);
    }
    
    @DeleteMapping("/{id}/access/{accessId}")
    @Operation(summary = "Remove access rule", description = "Delete concrete access-config row")
    public ResponseEntity<Void> removeAccessConfig(
            @Parameter(description = "Function unit id") @PathVariable String id,
            @Parameter(description = "Access config id") @PathVariable String accessId) {
        log.info("Removing access config {} from function unit {}", accessId, id);
        accessService.removeAccessConfig(id, accessId);
        return ResponseEntity.noContent().build();
    }
    
    @PutMapping("/{id}/access")
    @Operation(summary = "Replace access rules", description = "Bulk replace configs")
    public ResponseEntity<List<FunctionUnitAccessInfo>> setAccessConfigs(
            @Parameter(description = "Function unit id") @PathVariable String id,
            @Valid @RequestBody List<FunctionUnitAccessRequest> requests) {
        log.info("Setting {} access configs for function unit {}", requests.size(), id);
        List<FunctionUnitAccessInfo> configs = accessService.setAccessConfigs(id, requests);
        return ResponseEntity.ok(configs);
    }
    
    @GetMapping("/{id}/access/check")
    @Operation(summary = "Check access", description = "Whether user can access unit")
    public ResponseEntity<Boolean> checkUserAccess(
            @Parameter(description = "Function unit id") @PathVariable String id,
            @Parameter(description = "User id") @RequestParam String userId) {
        log.info("Checking access for user {} to function unit {}", userId, id);
        boolean hasAccess = accessService.hasAccess(id, userId);
        return ResponseEntity.ok(hasAccess);
    }

    // ==================== Audit access ====================
    // Kept apart from the launch grants above on purpose: an audit grant lets a
    // reviewer read every request of the unit, and must never imply the right to
    // start one.

    @GetMapping("/{id}/audit-access")
    @Operation(summary = "List audit grants", description = "Roles allowed to review all requests of this unit")
    public ResponseEntity<List<FunctionUnitAuditAccessInfo>> getAuditAccessConfigs(
            @Parameter(description = "Function unit id") @PathVariable String id) {
        log.info("Getting audit access configs for function unit: {}", id);
        return ResponseEntity.ok(auditAccessService.getAuditAccessConfigs(id));
    }

    @PostMapping("/{id}/audit-access")
    @Operation(summary = "Add audit grant", description = "Grant a business role review access")
    public ResponseEntity<FunctionUnitAuditAccessInfo> addAuditAccessConfig(
            @Parameter(description = "Function unit id") @PathVariable String id,
            @Valid @RequestBody FunctionUnitAccessRequest request) {
        log.info("Adding audit access for function unit {}: roleId={}", id, request.getRoleId());
        FunctionUnitAuditAccessInfo config = auditAccessService.addAuditAccessConfig(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(config);
    }

    @DeleteMapping("/{id}/audit-access/{accessId}")
    @Operation(summary = "Remove audit grant", description = "Delete concrete audit-access row")
    public ResponseEntity<Void> removeAuditAccessConfig(
            @Parameter(description = "Function unit id") @PathVariable String id,
            @Parameter(description = "Audit access config id") @PathVariable String accessId) {
        log.info("Removing audit access {} from function unit {}", accessId, id);
        auditAccessService.removeAuditAccessConfig(id, accessId);
        return ResponseEntity.noContent().build();
    }

    // ==================== Content payloads (portal consumers) ====================
    
    @GetMapping("/by-process-key/{processKey}")
    @Operation(summary = "Find by process key", description = "Locate unit via BPMN processDefinitionKey")
    public ResponseEntity<FunctionUnitInfo> getFunctionUnitByProcessKey(
            @Parameter(description = "Process definition key") @PathVariable String processKey) {
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
    @Operation(summary = "Full bundled content", description = "Forms, BPMN, actions for portal")
    public ResponseEntity<ApiResponse<com.admin.dto.response.FunctionUnitContentResponse>> getFunctionUnitContent(
            @Parameter(description = "Function unit id") @PathVariable String id) {
        log.info("Getting function unit content for: {}", id);
        return handleRequest(() -> functionUnitManager.assembleFunctionUnitContent(id));
    }
    
    // ==================== Merge content endpoint (Req 35) ====================

    @GetMapping("/{id}/contents")
    @Operation(summary = "List typed contents", description = "Filter by FORM, PROCESS, etc.; empty type returns all")
    public ResponseEntity<ApiResponse<java.util.List<com.admin.dto.response.FunctionUnitContentItemDTO>>> getContents(
            @Parameter(description = "Function unit id") @PathVariable String id,
            @Parameter(description = "Optional filter: FORM, PROCESS, DATA_TABLE, SCRIPT, ACTION") @RequestParam(required = false) String type) {
        log.info("Getting function unit contents for: {}, type: {}", id, type);
        return handleRequest(() -> functionUnitManager.getContentsByType(id, type));
    }

}
