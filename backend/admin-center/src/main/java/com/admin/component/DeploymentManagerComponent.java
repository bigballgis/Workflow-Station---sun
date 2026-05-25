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
 * 部署管理组件
 * 负责多环境部署、部署策略、审批流程和回滚管理
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
    
    // 需要审批的环境
    private static final Set<DeploymentEnvironment> APPROVAL_REQUIRED_ENVIRONMENTS = 
            EnumSet.of(DeploymentEnvironment.PRODUCTION, DeploymentEnvironment.PRE_PRODUCTION, DeploymentEnvironment.STAGING);

    /**
     * 创建部署请求
     */
    @Transactional
    public FunctionUnitDeployment createDeployment(String functionUnitId, 
                                                    DeploymentEnvironment environment,
                                                    DeploymentStrategy strategy,
                                                    String deployerId) {
        log.info("Creating deployment for function unit {} to environment {}", functionUnitId, environment);
        
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new FunctionUnitNotFoundException("功能单元不存在: " + functionUnitId));
        
        // 检查功能单元是否可部署
        if (!functionUnit.isDeployable()) {
            throw new AdminBusinessException("INVALID_STATUS", 
                    "功能单元状态不允许部署: " + functionUnit.getStatus());
        }
        
        // 检查是否已有进行中的部署
        Optional<FunctionUnitDeployment> activeDeployment = 
                deploymentRepository.findActiveDeployment(functionUnitId, environment);
        if (activeDeployment.isPresent()) {
            throw new AdminBusinessException("DEPLOYMENT_IN_PROGRESS", 
                    "该环境已有进行中的部署: " + activeDeployment.get().getId());
        }
        
        // 创建部署记录
        FunctionUnitDeployment deployment = FunctionUnitDeployment.builder()
                .id(UUID.randomUUID().toString())
                .functionUnit(functionUnit)
                .environment(environment)
                .strategy(strategy)
                .status(DeploymentStatus.PENDING)
                .deployedBy(deployerId)
                .build();
        
        deployment = deploymentRepository.save(deployment);
        
        // 如果需要审批，创建审批记录
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
                    "部署已提交审批",
                    String.format("功能单元「%s」至环境 %s 的部署（编号 %s）已进入审批流程，请等待审批结果。",
                            functionUnit.getName(), environment, deployment.getId()),
                    null,
                    "admin-center");
        }

        return deployment;
    }
    
    /**
     * 检查是否需要审批
     */
    public boolean requiresApproval(DeploymentEnvironment environment) {
        return APPROVAL_REQUIRED_ENVIRONMENTS.contains(environment);
    }
    
    /**
     * 创建审批记录
     */
    private void createApprovalRecords(FunctionUnitDeployment deployment) {
        // 生产环境需要多级审批
        if (deployment.getEnvironment() == DeploymentEnvironment.PRODUCTION) {
            // 技术审批
            createApprovalRecord(deployment, ApprovalType.TECHNICAL, 1);
            // 业务审批
            createApprovalRecord(deployment, ApprovalType.BUSINESS, 2);
            // 安全审批
            createApprovalRecord(deployment, ApprovalType.SECURITY, 3);
        } else {
            // 预生产环境只需要技术审批
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

    /**
     * 审批部署
     */
    @Transactional
    public FunctionUnitApproval approveDeployment(String approvalId, String approverId, String comment) {
        log.info("Approving deployment approval: {}", approvalId);
        
        FunctionUnitApproval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new AdminBusinessException("APPROVAL_NOT_FOUND", "Approval record not found: " + approvalId));
        
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new AdminBusinessException("INVALID_STATUS", "Approval status does not allow operation: " + approval.getStatus());
        }
        
        // 检查前置审批是否完成
        FunctionUnitDeployment deployment = approval.getDeployment();
        List<FunctionUnitApproval> pendingApprovals = 
                approvalRepository.findPendingApprovalsBefore(deployment.getId(), approval.getApprovalOrder());
        if (!pendingApprovals.isEmpty()) {
            throw new AdminBusinessException("PENDING_APPROVAL", "There are pending prerequisite approvals");
        }
        
        approval.approve(approverId, comment);
        approval = approvalRepository.save(approval);
        
        // 检查是否所有审批都已完成
        checkAndUpdateDeploymentStatus(deployment);

        FunctionUnitDeployment dep = approval.getDeployment();
        if (dep != null && StringUtils.hasText(dep.getDeployedBy())) {
            String fuName = dep.getFunctionUnit() != null ? dep.getFunctionUnit().getName() : "";
            notificationDispatchHelper.publishToUserAfterCommit(
                    dep.getDeployedBy(),
                    "APPROVAL",
                    "部署审批进度",
                    String.format("功能单元「%s」的部署审批（%s）已由审批人处理：通过。%s",
                            fuName,
                            approval.getApprovalType(),
                            comment != null && !comment.isBlank() ? "意见：" + comment : "").trim(),
                    null,
                    "admin-center");
        }
        
        return approval;
    }
    
    /**
     * 拒绝部署
     */
    @Transactional
    public FunctionUnitApproval rejectDeployment(String approvalId, String approverId, String comment) {
        log.info("Rejecting deployment approval: {}", approvalId);
        
        FunctionUnitApproval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new AdminBusinessException("APPROVAL_NOT_FOUND", "Approval record not found: " + approvalId));
        
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new AdminBusinessException("INVALID_STATUS", "Approval status does not allow operation: " + approval.getStatus());
        }
        
        approval.reject(approverId, comment);
        approval = approvalRepository.save(approval);
        
        // 更新部署状态为失败
        FunctionUnitDeployment deployment = approval.getDeployment();
        deployment.setStatus(DeploymentStatus.FAILED);
        deployment.setErrorMessage("Approval rejected: " + comment);
        deploymentRepository.save(deployment);

        if (StringUtils.hasText(deployment.getDeployedBy())) {
            String fuName = deployment.getFunctionUnit() != null ? deployment.getFunctionUnit().getName() : "";
            notificationDispatchHelper.publishToUserAfterCommit(
                    deployment.getDeployedBy(),
                    "APPROVAL",
                    "部署审批已拒绝",
                    String.format("功能单元「%s」的部署（编号 %s）审批未通过。原因：%s",
                            fuName, deployment.getId(), comment),
                    null,
                    "admin-center");
        }
        
        return approval;
    }
    
    /**
     * 检查并更新部署状态
     */
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
                        "部署审批全部通过",
                        String.format("功能单元「%s」的部署（编号 %s）已完成全部审批，可继续后续发布步骤。",
                                fuName, deployment.getId()),
                        null,
                        "admin-center");
            }
        }
    }
    
    /**
     * 执行部署
     */
    @Transactional
    public FunctionUnitDeployment executeDeployment(String deploymentId) {
        log.info("Executing deployment: {}", deploymentId);
        
        FunctionUnitDeployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new DeploymentNotFoundException("Deployment record not found: " + deploymentId));
        
        // 检查部署状态
        if (deployment.getStatus() != DeploymentStatus.PENDING && 
            deployment.getStatus() != DeploymentStatus.APPROVED) {
            throw new AdminBusinessException("INVALID_STATUS", 
                    "Deployment status does not allow execution: " + deployment.getStatus());
        }
        
        // 如果需要审批但未完成
        if (requiresApproval(deployment.getEnvironment()) && 
            deployment.getStatus() != DeploymentStatus.APPROVED) {
            throw new AdminBusinessException("APPROVAL_REQUIRED", "Deployment requires approval before execution");
        }
        
        try {
            deployment.setStatus(DeploymentStatus.DEPLOYING);
            deployment.setStartedAt(Instant.now());
            deployment = deploymentRepository.save(deployment);
            
            // 执行部署逻辑（根据策略）
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
            
            // 更新功能单元状态
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
            throw new DeploymentFailedException("Deployment execution failed: " + e.getMessage(), e);
        }
        
        return deployment;
    }

    /**
     * 执行部署策略
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
                        "Unknown deployment strategy: " + deployment.getStrategy());
        }
    }
    
    private void executeFullDeployment(FunctionUnitDeployment deployment) {
        log.info("Executing full deployment for: {}", deployment.getId());
        // 全量部署：替换所有实例
        // 实际实现中应该调用部署服务
    }
    
    private void executeIncrementalDeployment(FunctionUnitDeployment deployment) {
        log.info("Executing incremental deployment for: {}", deployment.getId());
        // 增量部署：逐步替换实例
    }
    
    private void executeCanaryDeployment(FunctionUnitDeployment deployment) {
        log.info("Executing canary deployment for: {}", deployment.getId());
        // 灰度部署：先部署到小部分实例
    }
    
    private void executeBlueGreenDeployment(FunctionUnitDeployment deployment) {
        log.info("Executing blue-green deployment for: {}", deployment.getId());
        // 蓝绿部署：部署到备用环境，然后切换流量
    }
    
    /**
     * 回滚部署
     */
    @Transactional
    public FunctionUnitDeployment rollbackDeployment(String deploymentId, String operatorId, String reason) {
        log.info("Rolling back deployment: {}", deploymentId);
        
        FunctionUnitDeployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new DeploymentNotFoundException("Deployment record not found: " + deploymentId));
        
        if (deployment.getStatus() != DeploymentStatus.SUCCESS && 
            deployment.getStatus() != DeploymentStatus.DEPLOYING) {
            throw new AdminBusinessException("INVALID_STATUS", 
                    "Deployment status does not allow rollback: " + deployment.getStatus());
        }
        
        // 查找上一个成功的部署
        Optional<FunctionUnitDeployment> previousDeployment = 
                deploymentRepository.findPreviousSuccessfulDeployment(
                        deployment.getFunctionUnit().getId(), 
                        deployment.getEnvironment(),
                        deployment.getStartedAt());
        
        try {
            deployment.setStatus(DeploymentStatus.ROLLING_BACK);
            deployment = deploymentRepository.save(deployment);
            
            // 执行回滚逻辑
            if (previousDeployment.isPresent()) {
                executeRollback(deployment, previousDeployment.get());
            } else {
                // 没有上一个版本，执行卸载
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
            deployment.setErrorMessage("Rollback failed: " + e.getMessage());
            deploymentRepository.save(deployment);
            throw new DeploymentFailedException("Rollback execution failed: " + e.getMessage(), e);
        }
        
        return deployment;
    }
    
    private void executeRollback(FunctionUnitDeployment current, FunctionUnitDeployment previous) {
        log.info("Rolling back from {} to {}", current.getId(), previous.getId());
        // 实际实现中应该调用部署服务恢复到上一个版本
    }
    
    private void executeUninstall(FunctionUnitDeployment deployment) {
        log.info("Uninstalling deployment: {}", deployment.getId());
        // 实际实现中应该调用部署服务卸载功能单元
    }

    /**
     * 获取部署记录
     */
    public FunctionUnitDeployment getDeployment(String deploymentId) {
        return deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new DeploymentNotFoundException("Deployment record not found: " + deploymentId));
    }
    
    /**
     * 获取功能单元的部署历史
     */
    public List<FunctionUnitDeployment> getDeploymentHistory(String functionUnitId) {
        return deploymentRepository.findByFunctionUnitIdOrderByCreatedAtDesc(functionUnitId);
    }
    
    /**
     * 获取环境的部署历史
     */
    public Page<FunctionUnitDeployment> getDeploymentsByEnvironment(
            DeploymentEnvironment environment, Pageable pageable) {
        return deploymentRepository.findByEnvironmentOrderByCreatedAtDesc(environment, pageable);
    }

    /**
     * 获取所有部署记录（全局分页查询，不限定功能单元）
     * Req 15.2
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
    
    /**
     * 获取待审批的部署
     */
    public List<FunctionUnitApproval> getPendingApprovals(String approverId) {
        return approvalRepository.findPendingApprovalsByApprover(approverId);
    }
    
    /**
     * 获取部署的审批记录
     */
    public List<FunctionUnitApproval> getDeploymentApprovals(String deploymentId) {
        return approvalRepository.findByDeploymentIdOrderByApprovalOrder(deploymentId);
    }
    
    /**
     * 取消部署
     */
    @Transactional
    public FunctionUnitDeployment cancelDeployment(String deploymentId, String operatorId, String reason) {
        log.info("Cancelling deployment: {}", deploymentId);
        
        FunctionUnitDeployment deployment = getDeployment(deploymentId);
        
        if (deployment.getStatus() != DeploymentStatus.PENDING && 
            deployment.getStatus() != DeploymentStatus.PENDING_APPROVAL) {
            throw new AdminBusinessException("INVALID_STATUS", 
                    "部署状态不允许取消: " + deployment.getStatus());
        }
        
        deployment.setStatus(DeploymentStatus.CANCELLED);
        deployment.setErrorMessage("已取消: " + reason);
        deployment = deploymentRepository.save(deployment);
        
        // 取消所有待审批记录
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
    
    /**
     * 获取环境的当前部署
     */
    public Optional<FunctionUnitDeployment> getCurrentDeployment(
            String functionUnitId, DeploymentEnvironment environment) {
        return deploymentRepository.findLatestSuccessfulDeployment(functionUnitId, environment);
    }
    
    /**
     * 检查是否可以部署到指定环境
     */
    public boolean canDeployToEnvironment(String functionUnitId, DeploymentEnvironment targetEnvironment) {
        // 检查是否需要先部署到前置环境
        if (targetEnvironment == DeploymentEnvironment.PRODUCTION) {
            // 生产环境需要先部署到预生产
            Optional<FunctionUnitDeployment> preProductionDeployment = 
                    deploymentRepository.findLatestSuccessfulDeployment(
                            functionUnitId, DeploymentEnvironment.PRE_PRODUCTION);
            return preProductionDeployment.isPresent();
        }
        
        if (targetEnvironment == DeploymentEnvironment.PRE_PRODUCTION) {
            // 预生产需要先部署到测试环境
            Optional<FunctionUnitDeployment> testDeployment = 
                    deploymentRepository.findLatestSuccessfulDeployment(
                            functionUnitId, DeploymentEnvironment.TEST);
            return testDeployment.isPresent();
        }
        
        return true;
    }
    
    /**
     * 获取部署进度
     */
    public DeploymentProgress getDeploymentProgress(String deploymentId) {
        FunctionUnitDeployment deployment = getDeployment(deploymentId);
        
        int totalSteps = 5; // 验证、准备、部署、验证、完成
        int completedSteps = 0;
        String currentStep = "初始化";
        
        switch (deployment.getStatus()) {
            case PENDING, PENDING_APPROVAL -> {
                completedSteps = 0;
                currentStep = "等待审批";
            }
            case APPROVED -> {
                completedSteps = 1;
                currentStep = "已审批，等待执行";
            }
            case IN_PROGRESS, DEPLOYING -> {
                completedSteps = 2;
                currentStep = "正在部署";
            }
            case SUCCESS -> {
                completedSteps = 5;
                currentStep = "部署完成";
            }
            case FAILED, ROLLBACK_FAILED -> currentStep = "部署失败";
            case ROLLING_BACK -> currentStep = "正在回滚";
            case ROLLED_BACK -> currentStep = "已回滚";
            case CANCELLED -> currentStep = "已取消";
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
     * 部署进度信息
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
