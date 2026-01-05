package com.admin.properties;

import com.admin.component.DeploymentManagerComponent;
import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitApproval;
import com.admin.entity.FunctionUnitDeployment;
import com.admin.enums.*;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.FunctionUnitApprovalRepository;
import com.admin.repository.FunctionUnitDeploymentRepository;
import com.admin.repository.FunctionUnitRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 部署回滚正确性属性测试
 * 属性 10: 部署回滚正确性
 * 验证需求: 需求 5.8
 */
class DeploymentRollbackProperties {

    private FunctionUnitRepository functionUnitRepository;
    private FunctionUnitDeploymentRepository deploymentRepository;
    private FunctionUnitApprovalRepository approvalRepository;
    private DeploymentManagerComponent component;

    @BeforeTry
    void setUp() {
        functionUnitRepository = Mockito.mock(FunctionUnitRepository.class);
        deploymentRepository = Mockito.mock(FunctionUnitDeploymentRepository.class);
        approvalRepository = Mockito.mock(FunctionUnitApprovalRepository.class);
        component = new DeploymentManagerComponent(
                functionUnitRepository, deploymentRepository, approvalRepository);
    }

    // ==================== 属性 1: 只有成功或部署中的部署可以回滚 ====================
    
    @Property(tries = 100)
    void onlySuccessOrDeployingCanBeRolledBack(
            @ForAll("rollbackableStatuses") DeploymentStatus status) {
        // Given: 可回滚状态的部署
        String deploymentId = UUID.randomUUID().toString();
        FunctionUnit functionUnit = createFunctionUnit();
        FunctionUnitDeployment deployment = createDeployment(deploymentId, functionUnit, status);
        
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(deploymentRepository.findPreviousSuccessfulDeployment(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(deploymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        
        // When: 执行回滚
        FunctionUnitDeployment result = component.rollbackDeployment(deploymentId, "operator-001", "测试回滚");
        
        // Then: 回滚应该成功
        assertThat(result.getStatus()).isEqualTo(DeploymentStatus.ROLLED_BACK);
        assertThat(result.getRollbackReason()).isEqualTo("测试回滚");
    }
    
    @Property(tries = 100)
    void nonRollbackableStatusShouldFail(
            @ForAll("nonRollbackableStatuses") DeploymentStatus status) {
        // Given: 不可回滚状态的部署
        String deploymentId = UUID.randomUUID().toString();
        FunctionUnit functionUnit = createFunctionUnit();
        FunctionUnitDeployment deployment = createDeployment(deploymentId, functionUnit, status);
        
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        
        // When/Then: 回滚应该失败
        assertThatThrownBy(() -> component.rollbackDeployment(deploymentId, "operator-001", "测试回滚"))
                .isInstanceOf(AdminBusinessException.class);
    }
    
    // ==================== 属性 2: 回滚后状态变为ROLLED_BACK ====================
    
    @Property(tries = 100)
    void rollbackShouldChangeStatusToRolledBack(
            @ForAll("validFunctionUnitIds") String functionUnitId,
            @ForAll("validOperatorIds") String operatorId) {
        // Given: 成功状态的部署
        String deploymentId = UUID.randomUUID().toString();
        FunctionUnit functionUnit = createFunctionUnit(functionUnitId);
        FunctionUnitDeployment deployment = createDeployment(deploymentId, functionUnit, DeploymentStatus.SUCCESS);
        
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(deploymentRepository.findPreviousSuccessfulDeployment(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(deploymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        
        // When: 执行回滚
        FunctionUnitDeployment result = component.rollbackDeployment(deploymentId, operatorId, "回滚原因");
        
        // Then: 状态应该变为ROLLED_BACK
        assertThat(result.getStatus()).isEqualTo(DeploymentStatus.ROLLED_BACK);
        assertThat(result.getRollbackBy()).isEqualTo(operatorId);
        assertThat(result.getRollbackAt()).isNotNull();
    }
    
    // ==================== 属性 3: 生产环境部署需要审批 ====================
    
    @Property(tries = 100)
    void productionDeploymentRequiresApproval() {
        // Given: 生产环境
        DeploymentEnvironment environment = DeploymentEnvironment.PRODUCTION;
        
        // When: 检查是否需要审批
        boolean requiresApproval = component.requiresApproval(environment);
        
        // Then: 应该需要审批
        assertThat(requiresApproval).isTrue();
    }
    
    @Property(tries = 100)
    void devAndTestEnvironmentNoApprovalRequired(
            @ForAll("nonProductionEnvironments") DeploymentEnvironment environment) {
        // When: 检查是否需要审批
        boolean requiresApproval = component.requiresApproval(environment);
        
        // Then: 开发和测试环境不需要审批
        assertThat(requiresApproval).isFalse();
    }
    // ==================== 属性 4: 部署进度计算正确性 ====================
    
    @Property(tries = 100)
    void deploymentProgressShouldBeCorrect(
            @ForAll("allDeploymentStatuses") DeploymentStatus status) {
        // Given: 不同状态的部署
        String deploymentId = UUID.randomUUID().toString();
        FunctionUnit functionUnit = createFunctionUnit();
        FunctionUnitDeployment deployment = createDeployment(deploymentId, functionUnit, status);
        
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        
        // When: 获取部署进度
        DeploymentManagerComponent.DeploymentProgress progress = component.getDeploymentProgress(deploymentId);
        
        // Then: 进度应该正确
        assertThat(progress.getDeploymentId()).isEqualTo(deploymentId);
        assertThat(progress.getStatus()).isEqualTo(status);
        assertThat(progress.getProgress()).isBetween(0, 100);
        assertThat(progress.getCompletedSteps()).isLessThanOrEqualTo(progress.getTotalSteps());
    }
    
    // ==================== 属性 5: 审批流程正确性 ====================
    
    @Property(tries = 100)
    void approvalProcessShouldBeCorrect(
            @ForAll("validApprovalIds") String approvalId,
            @ForAll("validApproverIds") String approverId) {
        // Given: 待审批的记录
        FunctionUnitApproval approval = createApproval(approvalId, ApprovalStatus.PENDING);
        FunctionUnitDeployment deployment = createDeployment(UUID.randomUUID().toString(), 
                createFunctionUnit(), DeploymentStatus.PENDING_APPROVAL);
        approval.setDeployment(deployment);
        
        when(approvalRepository.findById(approvalId)).thenReturn(Optional.of(approval));
        when(approvalRepository.findPendingApprovalsBefore(any(), anyInt())).thenReturn(Collections.emptyList());
        when(approvalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(approvalRepository.findByDeploymentId(any())).thenReturn(Arrays.asList(approval));
        when(deploymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        
        // When: 审批通过
        FunctionUnitApproval result = component.approveDeployment(approvalId, approverId, "审批通过");
        
        // Then: 审批状态应该正确
        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(result.getApproverId()).isEqualTo(approverId);
        assertThat(result.getApprovedAt()).isNotNull();
    }
    
    // ==================== 数据提供者 ====================
    
    @Provide
    Arbitrary<DeploymentStatus> rollbackableStatuses() {
        return Arbitraries.of(DeploymentStatus.SUCCESS, DeploymentStatus.DEPLOYING);
    }
    
    @Provide
    Arbitrary<DeploymentStatus> nonRollbackableStatuses() {
        return Arbitraries.of(
                DeploymentStatus.PENDING,
                DeploymentStatus.PENDING_APPROVAL,
                DeploymentStatus.APPROVED,
                DeploymentStatus.IN_PROGRESS,
                DeploymentStatus.FAILED,
                DeploymentStatus.CANCELLED,
                DeploymentStatus.ROLLED_BACK,
                DeploymentStatus.ROLLBACK_FAILED,
                DeploymentStatus.ROLLING_BACK
        );
    }
    
    @Provide
    Arbitrary<DeploymentStatus> allDeploymentStatuses() {
        return Arbitraries.of(DeploymentStatus.values());
    }
    
    @Provide
    Arbitrary<DeploymentEnvironment> nonProductionEnvironments() {
        // 只有 DEV/DEVELOPMENT 和 TEST 不需要审批
        // PRE_PRODUCTION 和 STAGING 需要审批
        return Arbitraries.of(
                DeploymentEnvironment.DEV,
                DeploymentEnvironment.DEVELOPMENT,
                DeploymentEnvironment.TEST
        );
    }
    
    @Provide
    Arbitrary<String> validFunctionUnitIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(10)
                .ofMaxLength(20)
                .map(s -> "func-" + s);
    }
    
    @Provide
    Arbitrary<String> validOperatorIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(5)
                .ofMaxLength(15)
                .map(s -> "user-" + s);
    }
    
    @Provide
    Arbitrary<String> validApprovalIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(10)
                .ofMaxLength(20)
                .map(s -> "approval-" + s);
    }
    
    @Provide
    Arbitrary<String> validApproverIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(5)
                .ofMaxLength(15)
                .map(s -> "approver-" + s);
    }

    
    // ==================== 辅助方法 ====================
    
    private FunctionUnit createFunctionUnit() {
        return createFunctionUnit(UUID.randomUUID().toString());
    }
    
    private FunctionUnit createFunctionUnit(String id) {
        return FunctionUnit.builder()
                .id(id)
                .name("测试功能单元")
                .code("TEST-FUNC")
                .version("1.0.0")
                .description("测试用功能单元")
                .status(FunctionUnitStatus.VALIDATED)
                .createdAt(Instant.now())
                .build();
    }
    
    private FunctionUnitDeployment createDeployment(String id, FunctionUnit functionUnit, DeploymentStatus status) {
        return FunctionUnitDeployment.builder()
                .id(id)
                .functionUnit(functionUnit)
                .environment(DeploymentEnvironment.DEVELOPMENT)
                .strategy(DeploymentStrategy.FULL)
                .status(status)
                .createdAt(Instant.now())
                .build();
    }
    
    private FunctionUnitApproval createApproval(String id, ApprovalStatus status) {
        return FunctionUnitApproval.builder()
                .id(id)
                .approvalType(ApprovalType.TECHNICAL)
                .approvalOrder(1)
                .status(status)
                .createdAt(Instant.now())
                .build();
    }
}
