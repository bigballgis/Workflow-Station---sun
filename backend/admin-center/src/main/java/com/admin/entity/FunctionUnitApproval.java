package com.admin.entity;

import com.admin.enums.ApprovalStatus;
import com.admin.enums.ApprovalType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 功能单元审批记录实体
 */
@Entity
@Table(name = "admin_function_unit_approvals")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FunctionUnitApproval {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id", nullable = false)
    private FunctionUnitDeployment deployment;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_type", nullable = false, length = 20)
    private ApprovalType approvalType;
    
    @Column(name = "approval_order")
    @Builder.Default
    private int approvalOrder = 1;
    
    @Column(name = "approver_id", length = 64)
    private String approverId;
    
    @Column(name = "approver_name", length = 100)
    private String approverName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING;
    
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;
    
    @Column(name = "approved_at")
    private Instant approvedAt;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    /**
     * 批准
     */
    public void approve(String approverId, String comment) {
        this.approverId = approverId;
        this.status = ApprovalStatus.APPROVED;
        this.comment = comment;
        this.approvedAt = Instant.now();
    }
    
    /**
     * 拒绝
     */
    public void reject(String approverId, String comment) {
        this.approverId = approverId;
        this.status = ApprovalStatus.REJECTED;
        this.comment = comment;
        this.approvedAt = Instant.now();
    }
    
    /**
     * 检查是否待审批
     */
    public boolean isPending() {
        return status == ApprovalStatus.PENDING;
    }
    
    /**
     * 检查是否已批准
     */
    public boolean isApproved() {
        return status == ApprovalStatus.APPROVED;
    }
    
    /**
     * 检查是否已拒绝
     */
    public boolean isRejected() {
        return status == ApprovalStatus.REJECTED;
    }
}
