package com.admin.component;

import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitApproval;
import com.admin.entity.FunctionUnitDeployment;
import com.admin.enums.*;
import com.admin.exception.AdminBusinessException;
import com.admin.dto.response.DeploymentInfo;
import com.admin.exception.DeploymentFailedException;
import com.admin.exception.DeploymentNotFoundException;
import com.admin.exception.FunctionUnitNotFoundException;
import com.admin.repository.FunctionUnitApprovalRepository;
import com.admin.repository.FunctionUnitDeploymentRepository;
import com.admin.repository.FunctionUnitRepository;
import com.admin.service.UserReferenceResolver;
import com.platform.common.i18n.I18nService;
import com.platform.messaging.support.NotificationDispatchHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;

/**
 * Orchestrates multi-environment deployments, strategies, approvals, and rollbacks for function units.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeploymentManagerComponent {
    
    private final FunctionUnitRepository functionUnitRepository;
    private final FunctionUnitDeploymentRepository deploymentRepository;
    private final FunctionUnitApprovalRepository approvalRepository;
    private final NotificationDispatchHelper notificationDispatchHelper;
    private final UserReferenceResolver userReferenceResolver;
    private final I18nService i18nService;
    
    /** Environments where approval workflow is enforced. */
    private static final Set<DeploymentEnvironment> APPROVAL_REQUIRED_ENVIRONMENTS = 
            EnumSet.of(DeploymentEnvironment.PRODUCTION, DeploymentEnvironment.PRE_PRODUCTION, DeploymentEnvironment.STAGING);

    /**
     * Creates a deployment request (optionally routed through approval workflow).
     */
    @Transactional
    public FunctionUnitDeployment createDeployment(String functionUnitId, 
                                                    DeploymentEnvironment environment,
                                                    DeploymentStrategy strategy,
                                                    String deployerId) {
        log.info("Creating deployment for function unit {} to environment {}", functionUnitId, environment);
        
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new FunctionUnitNotFoundException(functionUnitId));
        
        if (!functionUnit.isDeployable()) {
            throw new AdminBusinessException("INVALID_STATUS",
                    i18nService.getMessage("admin.fu.deploy_status_invalid", functionUnit.getStatus()));
        }
        
        Optional<FunctionUnitDeployment> activeDeployment = 
                deploymentRepository.findActiveDeployment(functionUnitId, environment);
        if (activeDeployment.isPresent()) {
            throw new AdminBusinessException("DEPLOYMENT_IN_PROGRESS",
                    i18nService.getMessage("admin.deploy.error.deployment_already_in_progress",
                            activeDeployment.get().getId()));
        }
        
        // Create persistence record for this deployment
        FunctionUnitDeployment deployment = FunctionUnitDeployment.builder()
                .id(UUID.randomUUID().toString())
                .functionUnit(functionUnit)
                .environment(environment)
                .strategy(strategy)
                .status(DeploymentStatus.PENDING)
                .deployedBy(deployerId)
                .build();
        
        deployment = deploymentRepository.save(deployment);
        
        if (requiresApproval(environment)) {
            createApprovalRecords(deployment);
            deployment.setStatus(DeploymentStatus.PENDING_APPROVAL);
            deployment = deploymentRepository.save(deployment);
        }
        
        log.info("Deployment created: {}", deployment.getId());

        if (deployment.getStatus() == DeploymentStatus.PENDING_APPROVAL && StringUtils.hasText(deployerId)) {
            notificationDispatchHelper.publishToUserAfterCommit(
                    deployerId,
                    "APPROVAL",
                    i18nService.getMessage("admin.deploy.notification.submitted_title"),
                    i18nService.getMessage("admin.deploy.notification.submitted_body",
                            functionUnit.getName(), environment, deployment.getId()),
                    null,
                    "admin-center");
        }

        return deployment;
    }
    
    public boolean requiresApproval(DeploymentEnvironment environment) {
        return APPROVAL_REQUIRED_ENVIRONMENTS.contains(environment);
    }
    
    private void createApprovalRecords(FunctionUnitDeployment deployment) {
        if (deployment.getEnvironment() == DeploymentEnvironment.PRODUCTION) {
            createApprovalRecord(deployment, ApprovalType.TECHNICAL, 1);
            createApprovalRecord(deployment, ApprovalType.BUSINESS, 2);
            createApprovalRecord(deployment, ApprovalType.SECURITY, 3);
        } else {
            createApprovalRecord(deployment, ApprovalType.TECHNICAL, 1);
        }
    }
    
    private void createApprovalRecord(FunctionUnitDeployment deployment, ApprovalType type, int order) {
        FunctionUnitApproval approval = FunctionUnitApproval.builder()
                .id(UUID.randomUUID().toString())
                .deployment(deployment)
                .approvalType(type)
                .approvalOrder(order)
                .status(ApprovalStatus.PENDING)
                .build();
        approvalRepository.save(approval);
    }

    @Transactional
    public FunctionUnitApproval approveDeployment(String approvalId, String approverId, String comment) {
        log.info("Approving deployment approval: {}", approvalId);
        
        FunctionUnitApproval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new AdminBusinessException("APPROVAL_NOT_FOUND", i18nService.getMessage("admin.deploy.error.approval_not_found", approvalId)));
        
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new AdminBusinessException("INVALID_STATUS", i18nService.getMessage("admin.deploy.error.approval_invalid_status", approval.getStatus()));
        }
        
        FunctionUnitDeployment deployment = approval.getDeployment();
        List<FunctionUnitApproval> pendingApprovals = 
                approvalRepository.findPendingApprovalsBefore(deployment.getId(), approval.getApprovalOrder());
        if (!pendingApprovals.isEmpty()) {
            throw new AdminBusinessException("PENDING_APPROVAL", i18nService.getMessage("admin.deploy.error.pending_prerequisite_approvals"));
        }
        
        approval.approve(approverId, comment);
        approval = approvalRepository.save(approval);

        checkAndUpdateDeploymentStatus(deployment);

        FunctionUnitDeployment dep = approval.getDeployment();
        if (dep != null && StringUtils.hasText(dep.getDeployedBy())) {
            String fuName = dep.getFunctionUnit() != null ? dep.getFunctionUnit().getName() : "";
            String commentSuffix = "";
            if (comment != null && !comment.isBlank()) {
                commentSuffix = i18nService.getMessage("admin.deploy.notification.comment_line", comment).trim();
            }
            notificationDispatchHelper.publishToUserAfterCommit(
                    dep.getDeployedBy(),
                    "APPROVAL",
                    i18nService.getMessage("admin.deploy.notification.approval_progress_title"),
                    i18nService.getMessage("admin.deploy.notification.approval_progress_body",
                            fuName, approval.getApprovalType().name(), commentSuffix).trim(),
                    null,
                    "admin-center");
        }
        
        return approval;
    }
    
    @Transactional
    public FunctionUnitApproval rejectDeployment(String approvalId, String approverId, String comment) {
        log.info("Rejecting deployment approval: {}", approvalId);
        
        FunctionUnitApproval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new AdminBusinessException("APPROVAL_NOT_FOUND", i18nService.getMessage("admin.deploy.error.approval_not_found", approvalId)));
        
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new AdminBusinessException("INVALID_STATUS", i18nService.getMessage("admin.deploy.error.approval_invalid_status", approval.getStatus()));
        }
        
        approval.reject(approverId, comment);
        approval = approvalRepository.save(approval);

        FunctionUnitDeployment deployment = approval.getDeployment();
        deployment.setStatus(DeploymentStatus.FAILED);
        deployment.setErrorMessage(i18nService.getMessage("admin.deploy.error.approval_rejected", comment));
        deploymentRepository.save(deployment);

        if (StringUtils.hasText(deployment.getDeployedBy())) {
            String fuName = deployment.getFunctionUnit() != null ? deployment.getFunctionUnit().getName() : "";
            String remark = comment != null ? comment : "";
            notificationDispatchHelper.publishToUserAfterCommit(
                    deployment.getDeployedBy(),
                    "APPROVAL",
                    i18nService.getMessage("admin.deploy.notification.rejected_title"),
                    i18nService.getMessage("admin.deploy.notification.rejected_body",
                            fuName, deployment.getId(), remark),
                    null,
                    "admin-center");
        }
        
        return approval;
    }
    
    private void checkAndUpdateDeploymentStatus(FunctionUnitDeployment deployment) {
        List<FunctionUnitApproval> allApprovals = approvalRepository.findByDeploymentId(deployment.getId());
        
        boolean allApproved = allApprovals.stream()
                .allMatch(a -> a.getStatus() == ApprovalStatus.APPROVED);
        
        if (allApproved) {
            deployment.setStatus(DeploymentStatus.APPROVED);
            deploymentRepository.save(deployment);
            log.info("All approvals completed for deployment: {}", deployment.getId());
            if (StringUtils.hasText(deployment.getDeployedBy())) {
                String fuName = deployment.getFunctionUnit() != null ? deployment.getFunctionUnit().getName() : "";
                notificationDispatchHelper.publishToUserAfterCommit(
                        deployment.getDeployedBy(),
                        "APPROVAL",
                        i18nService.getMessage("admin.deploy.notification.all_approved_title"),
                        i18nService.getMessage("admin.deploy.notification.all_approved_body",
                                fuName, deployment.getId()),
                        null,
                        "admin-center");
            }
        }
    }
    
    /**
     * Runs deployment logic after prerequisites are satisfied (may be synchronous or delegated to integrations).
     */
    @Transactional
    public FunctionUnitDeployment executeDeployment(String deploymentId) {
        log.info("Executing deployment: {}", deploymentId);
        
        FunctionUnitDeployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new AdminBusinessException("DEPLOYMENT_NOT_FOUND",
                        i18nService.getMessage("admin.deploy.error.deployment_not_found", deploymentId)));
        
        if (deployment.getStatus() != DeploymentStatus.PENDING && 
            deployment.getStatus() != DeploymentStatus.APPROVED) {
            throw new AdminBusinessException("INVALID_STATUS", 
                    i18nService.getMessage("admin.deploy.error.deployment_invalid_status_exec", deployment.getStatus()));
        }
        
        if (requiresApproval(deployment.getEnvironment()) && 
            deployment.getStatus() != DeploymentStatus.APPROVED) {
            throw new AdminBusinessException("APPROVAL_REQUIRED", i18nService.getMessage("admin.deploy.error.approval_required"));
        }
        
        try {
            deployment.setStatus(DeploymentStatus.DEPLOYING);
            deployment.setStartedAt(Instant.now());
            deployment = deploymentRepository.save(deployment);

            executeDeploymentStrategy(deployment);

            deployment.setStatus(DeploymentStatus.SUCCESS);
            deployment.setCompletedAt(Instant.now());
            if (deployment.getDeployedAt() == null) {
                deployment.setDeployedAt(deployment.getCompletedAt());
            }
            if (deployment.getDeployedBy() == null || deployment.getDeployedBy().isBlank()) {
                deployment.setDeployedBy(deployment.getCreatedBy());
            }
            deployment = deploymentRepository.save(deployment);

            FunctionUnit functionUnit = deployment.getFunctionUnit();
            functionUnit.markAsDeployed();
            functionUnitRepository.save(functionUnit);
            
            log.info("Deployment completed successfully: {}", deploymentId);
            
        } catch (Exception e) {
            log.error("Deployment failed: {}", deploymentId, e);
            deployment.setStatus(DeploymentStatus.FAILED);
            deployment.setErrorMessage(e.getMessage());
            deployment.setCompletedAt(Instant.now());
            deploymentRepository.save(deployment);
            throw new DeploymentFailedException(i18nService.getMessage("admin.deploy.error.execution_failed", e.getMessage()), e);
        }
        
        return deployment;
    }

    /**
     * Delegates deployment work by strategy variant (currently placeholder hooks for orchestration backends).
     */
    private void executeDeploymentStrategy(FunctionUnitDeployment deployment) {
        log.info("Executing deployment strategy: {} for deployment: {}", 
                deployment.getStrategy(), deployment.getId());
        
        switch (deployment.getStrategy()) {
            case FULL:
                executeFullDeployment(deployment);
                break;
            case INCREMENTAL:
                executeIncrementalDeployment(deployment);
                break;
            case CANARY:
                executeCanaryDeployment(deployment);
                break;
            case BLUE_GREEN:
                executeBlueGreenDeployment(deployment);
                break;
            default:
                throw new AdminBusinessException("UNKNOWN_STRATEGY", 
                        i18nService.getMessage("admin.deploy.error.unknown_strategy", deployment.getStrategy()));
        }
    }
    
    private void executeFullDeployment(FunctionUnitDeployment deployment) {
        log.info("Executing full deployment for: {}", deployment.getId());
        // Placeholder for full rollout (replace instances / rollout controller).
    }
    
    private void executeIncrementalDeployment(FunctionUnitDeployment deployment) {
        log.info("Executing incremental deployment for: {}", deployment.getId());
        // Placeholder for staged rollout updates.
    }
    
    private void executeCanaryDeployment(FunctionUnitDeployment deployment) {
        log.info("Executing canary deployment for: {}", deployment.getId());
        // Placeholder for canary traffic slice.
    }
    
    private void executeBlueGreenDeployment(FunctionUnitDeployment deployment) {
        log.info("Executing blue-green deployment for: {}", deployment.getId());
        // Placeholder for blue-green switch.
    }

    /**
     * Rolls deployment back toward the previously successful baseline for the same environment.
     */
    @Transactional
    public FunctionUnitDeployment rollbackDeployment(String deploymentId, String operatorId, String reason) {
        log.info("Rolling back deployment: {}", deploymentId);
        
        FunctionUnitDeployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new AdminBusinessException("DEPLOYMENT_NOT_FOUND",
                        i18nService.getMessage("admin.deploy.error.deployment_not_found", deploymentId)));
        
        if (deployment.getStatus() != DeploymentStatus.SUCCESS && 
            deployment.getStatus() != DeploymentStatus.DEPLOYING) {
            throw new AdminBusinessException("INVALID_STATUS", 
                    i18nService.getMessage("admin.deploy.error.rollback_invalid_status", deployment.getStatus()));
        }
        
        Optional<FunctionUnitDeployment> previousDeployment =
                deploymentRepository.findPreviousSuccessfulDeployment(
                        deployment.getFunctionUnit().getId(), 
                        deployment.getEnvironment(),
                        deployment.getStartedAt());
        
        try {
            deployment.setStatus(DeploymentStatus.ROLLING_BACK);
            deployment = deploymentRepository.save(deployment);

            if (previousDeployment.isPresent()) {
                executeRollback(deployment, previousDeployment.get());
            } else {
                executeUninstall(deployment);
            }
            
            deployment.setStatus(DeploymentStatus.ROLLED_BACK);
            deployment.setRollbackReason(reason);
            deployment.setRollbackBy(operatorId);
            deployment.setRollbackAt(Instant.now());
            deployment = deploymentRepository.save(deployment);
            
            log.info("Deployment rolled back successfully: {}", deploymentId);
            
        } catch (Exception e) {
            log.error("Rollback failed: {}", deploymentId, e);
            deployment.setStatus(DeploymentStatus.ROLLBACK_FAILED);
            deployment.setErrorMessage(i18nService.getMessage("admin.deploy.error.rollback_failed_message", e.getMessage()));
            deploymentRepository.save(deployment);
            throw new DeploymentFailedException(i18nService.getMessage("admin.deploy.error.rollback_failed", e.getMessage()), e);
        }
        
        return deployment;
    }
    
    private void executeRollback(FunctionUnitDeployment current, FunctionUnitDeployment previous) {
        log.info("Rolling back from {} to {}", current.getId(), previous.getId());
        // Placeholder: trigger integration to restore artifacts from baseline deployment.
    }
    
    private void executeUninstall(FunctionUnitDeployment deployment) {
        log.info("Uninstalling deployment: {}", deployment.getId());
        // Placeholder: uninstall when no predecessor exists.
    }

    /** Loads persisted deployment aggregate by identifier. */
    public FunctionUnitDeployment getDeployment(String deploymentId) {
        return deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new AdminBusinessException("DEPLOYMENT_NOT_FOUND",
                        i18nService.getMessage("admin.deploy.error.deployment_not_found", deploymentId)));
    }
    
    /** Lists deployment audits for a particular function unit, newest-first. */
    public List<FunctionUnitDeployment> getDeploymentHistory(String functionUnitId) {
        return deploymentRepository.findByFunctionUnitIdOrderByCreatedAtDesc(functionUnitId);
    }
    
    /** Loads deployments scoped to environment with pagination. */
    public Page<FunctionUnitDeployment> getDeploymentsByEnvironment(
            DeploymentEnvironment environment, Pageable pageable) {
        return deploymentRepository.findByEnvironmentOrderByCreatedAtDesc(environment, pageable);
    }

    /**
     * Global deployment listing (functional requirement Req 15.2).
     */
    public Page<DeploymentInfo> listAllDeployments(Pageable pageable) {
        Page<DeploymentInfo> page = deploymentRepository.findByConditions(null, null, null, pageable)
                .map(DeploymentInfo::fromEntity);
        enrichDeployedByUsernames(page.getContent());
        return page;
    }

    private void enrichDeployedByUsernames(List<DeploymentInfo> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        var cache = userReferenceResolver.resolveUsernames(
                items.stream().map(DeploymentInfo::getDeployedBy).toList());
        for (DeploymentInfo item : items) {
            if (item.getDeployedBy() != null) {
                item.setDeployedBy(userReferenceResolver.resolveWithCache(item.getDeployedBy(), cache));
            }
        }
    }
    
    /** Approvals queued for designated approvers. */
    public List<FunctionUnitApproval> getPendingApprovals(String approverId) {
        return approvalRepository.findPendingApprovalsByApprover(approverId);
    }
    
    /** Loads approval checkpoints for a deployment. */
    public List<FunctionUnitApproval> getDeploymentApprovals(String deploymentId) {
        return approvalRepository.findByDeploymentIdOrderByApprovalOrder(deploymentId);
    }
    
    @Transactional
    public FunctionUnitDeployment cancelDeployment(String deploymentId, String operatorId, String reason) {
        log.info("Cancelling deployment: {}", deploymentId);
        
        FunctionUnitDeployment deployment = getDeployment(deploymentId);
        
        if (deployment.getStatus() != DeploymentStatus.PENDING && 
            deployment.getStatus() != DeploymentStatus.PENDING_APPROVAL) {
            throw new AdminBusinessException("INVALID_STATUS",
                    i18nService.getMessage("admin.deploy.cancel.invalid_status", deployment.getStatus()));
        }
        
        deployment.setStatus(DeploymentStatus.CANCELLED);
        deployment.setErrorMessage(i18nService.getMessage("admin.deploy.cancel.error_message", reason));
        deployment = deploymentRepository.save(deployment);
        
        List<FunctionUnitApproval> approvals = approvalRepository.findByDeploymentId(deploymentId);
        for (FunctionUnitApproval approval : approvals) {
            if (approval.getStatus() == ApprovalStatus.PENDING) {
                approval.setStatus(ApprovalStatus.CANCELLED);
                approvalRepository.save(approval);
            }
        }
        
        log.info("Deployment cancelled: {}", deploymentId);
        return deployment;
    }
    
    /** Most recent SUCCESS deployment snapshot for optional environment pinning. */
    public Optional<FunctionUnitDeployment> getCurrentDeployment(
            String functionUnitId, DeploymentEnvironment environment) {
        return deploymentRepository.findLatestSuccessfulDeployment(functionUnitId, environment);
    }
    
    /**
     * Environment gatekeeping (requires successful deployment sequence before production rollout).
     */
    public boolean canDeployToEnvironment(String functionUnitId, DeploymentEnvironment targetEnvironment) {
        if (targetEnvironment == DeploymentEnvironment.PRODUCTION) {
            Optional<FunctionUnitDeployment> preProductionDeployment = 
                    deploymentRepository.findLatestSuccessfulDeployment(
                            functionUnitId, DeploymentEnvironment.PRE_PRODUCTION);
            return preProductionDeployment.isPresent();
        }
        
        if (targetEnvironment == DeploymentEnvironment.PRE_PRODUCTION) {
            Optional<FunctionUnitDeployment> testDeployment = 
                    deploymentRepository.findLatestSuccessfulDeployment(
                            functionUnitId, DeploymentEnvironment.TEST);
            return testDeployment.isPresent();
        }
        
        return true;
    }
    
    /** Coarse UX-friendly progress aggregation for dashboards. */
    public DeploymentProgress getDeploymentProgress(String deploymentId) {
        FunctionUnitDeployment deployment = getDeployment(deploymentId);

        int totalSteps = 5; // Discovery, prepare, deploy, verify, complete
        int completedSteps = 0;
        String currentStep = i18nService.getMessage("admin.deploy.progress.initializing");

        switch (deployment.getStatus()) {
            case PENDING, PENDING_APPROVAL -> {
                completedSteps = 0;
                currentStep = i18nService.getMessage("admin.deploy.progress.waiting_approval");
            }
            case APPROVED -> {
                completedSteps = 1;
                currentStep = i18nService.getMessage("admin.deploy.progress.approved_pending_execution");
            }
            case IN_PROGRESS, DEPLOYING -> {
                completedSteps = 2;
                currentStep = i18nService.getMessage("admin.deploy.progress.deploying");
            }
            case SUCCESS -> {
                completedSteps = 5;
                currentStep = i18nService.getMessage("admin.deploy.progress.completed");
            }
            case FAILED, ROLLBACK_FAILED ->
                    currentStep = i18nService.getMessage("admin.deploy.progress.failed");
            case ROLLING_BACK -> currentStep = i18nService.getMessage("admin.deploy.progress.rolling_back");
            case ROLLED_BACK -> currentStep = i18nService.getMessage("admin.deploy.progress.rolled_back");
            case CANCELLED -> currentStep = i18nService.getMessage("admin.deploy.progress.cancelled");
        }
        
        return DeploymentProgress.builder()
                .deploymentId(deploymentId)
                .status(deployment.getStatus())
                .totalSteps(totalSteps)
                .completedSteps(completedSteps)
                .currentStep(currentStep)
                .progress(totalSteps > 0 ? (completedSteps * 100 / totalSteps) : 0)
                .build();
    }
    
    /**
     * DTO projecting coarse deployment checkpoints for dashboards.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DeploymentProgress {
        private String deploymentId;
        private DeploymentStatus status;
        private int totalSteps;
        private int completedSteps;
        private String currentStep;
        private int progress;
    }
}
