package com.admin.entity;

import com.admin.enums.DeploymentEnvironment;
import com.admin.enums.DeploymentStatus;
import com.admin.enums.DeploymentStrategy;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * 功能单元部署记录实体
 */
@Entity
@Table(name = "admin_function_unit_deployments")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FunctionUnitDeployment {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "function_unit_id", nullable = false)
    private FunctionUnit functionUnit;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 20)
    private DeploymentEnvironment environment;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "strategy", nullable = false, length = 20)
    private DeploymentStrategy strategy;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DeploymentStatus status = DeploymentStatus.PENDING;
    
    @Column(name = "deployed_at")
    private Instant deployedAt;
    
    @Column(name = "deployed_by", length = 64)
    private String deployedBy;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @Column(name = "rollback_to_id", length = 64)
    private String rollbackToId;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "deployment_log", columnDefinition = "TEXT")
    private String deploymentLog;
    
    @Column(name = "started_at")
    private Instant startedAt;
    
    @Column(name = "rollback_reason", columnDefinition = "TEXT")
    private String rollbackReason;
    
    @Column(name = "rollback_by", length = 64)
    private String rollbackBy;
    
    @Column(name = "rollback_at")
    private Instant rollbackAt;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;
    
    @OneToMany(mappedBy = "deployment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<FunctionUnitApproval> approvals = new HashSet<>();
    
    /**
     * 检查是否需要审批
     */
    public boolean requiresApproval() {
        return environment == DeploymentEnvironment.PRODUCTION;
    }
    
    /**
     * 检查是否所有审批都已通过
     */
    public boolean isFullyApproved() {
        if (!requiresApproval()) {
            return true;
        }
        if (approvals == null || approvals.isEmpty()) {
            return false;
        }
        return approvals.stream()
                .allMatch(a -> a.getStatus() == com.admin.enums.ApprovalStatus.APPROVED);
    }
    
    /**
     * 检查是否可以开始部署
     */
    public boolean canStartDeployment() {
        return status == DeploymentStatus.PENDING && isFullyApproved();
    }
    
    /**
     * 开始部署
     */
    public void startDeployment(String deployerId) {
        this.status = DeploymentStatus.IN_PROGRESS;
        this.deployedAt = Instant.now();
        this.deployedBy = deployerId;
    }
    
    /**
     * 标记部署成功
     */
    public void markAsSuccess() {
        this.status = DeploymentStatus.SUCCESS;
        this.completedAt = Instant.now();
    }
    
    /**
     * 标记部署失败
     */
    public void markAsFailed(String errorMessage) {
        this.status = DeploymentStatus.FAILED;
        this.completedAt = Instant.now();
        this.errorMessage = errorMessage;
    }
    
    /**
     * 标记为已回滚
     */
    public void markAsRolledBack(String rollbackToId) {
        this.status = DeploymentStatus.ROLLED_BACK;
        this.rollbackToId = rollbackToId;
        this.completedAt = Instant.now();
    }
    
    /**
     * 添加审批记录
     */
    public void addApproval(FunctionUnitApproval approval) {
        if (approvals == null) {
            approvals = new HashSet<>();
        }
        approval.setDeployment(this);
        approvals.add(approval);
    }
    
    /**
     * 追加部署日志
     */
    public void appendLog(String log) {
        if (this.deploymentLog == null) {
            this.deploymentLog = log;
        } else {
            this.deploymentLog += "\n" + log;
        }
    }
}
