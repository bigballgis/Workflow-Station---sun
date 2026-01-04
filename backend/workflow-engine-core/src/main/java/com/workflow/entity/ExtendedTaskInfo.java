package com.workflow.entity;

import com.workflow.enums.AssignmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 扩展任务信息实体
 * 支持多维度任务分配的扩展数据模型
 * 
 * 该实体扩展了Flowable原生任务的功能，支持：
 * 1. 多维度分配类型（用户、虚拟组、部门角色）
 * 2. 任务委托机制
 * 3. 任务认领机制
 * 4. 扩展的任务属性和索引优化
 */
@Entity
@Table(name = "wf_extended_task_info", indexes = {
    @Index(name = "idx_task_id", columnList = "taskId", unique = true),
    @Index(name = "idx_assignment_type", columnList = "assignmentType"),
    @Index(name = "idx_assignment_target", columnList = "assignmentTarget"),
    @Index(name = "idx_delegated_to", columnList = "delegatedTo"),
    @Index(name = "idx_claimed_by", columnList = "claimedBy"),
    @Index(name = "idx_process_instance", columnList = "processInstanceId"),
    @Index(name = "idx_created_time", columnList = "createdTime"),
    @Index(name = "idx_due_date", columnList = "dueDate"),
    @Index(name = "idx_priority", columnList = "priority"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtendedTaskInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Flowable任务ID（关联到ACT_RU_TASK表）
     */
    @Column(name = "task_id", nullable = false, unique = true, length = 64)
    private String taskId;

    /**
     * 流程实例ID
     */
    @Column(name = "process_instance_id", nullable = false, length = 64)
    private String processInstanceId;

    /**
     * 流程定义ID
     */
    @Column(name = "process_definition_id", nullable = false, length = 64)
    private String processDefinitionId;

    /**
     * 任务定义键
     */
    @Column(name = "task_definition_key", length = 255)
    private String taskDefinitionKey;

    /**
     * 任务名称
     */
    @Column(name = "task_name", length = 255)
    private String taskName;

    /**
     * 任务描述
     */
    @Column(name = "task_description", length = 4000)
    private String taskDescription;

    /**
     * 任务分配类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false, length = 20)
    private AssignmentType assignmentType;

    /**
     * 分配目标
     * - 当assignmentType为USER时，存储用户ID
     * - 当assignmentType为VIRTUAL_GROUP时，存储虚拟组ID
     * - 当assignmentType为DEPT_ROLE时，存储"部门ID:角色ID"格式
     */
    @Column(name = "assignment_target", nullable = false, length = 255)
    private String assignmentTarget;

    /**
     * 原始分配人（用于委托场景）
     * 记录任务的原始分配信息，委托后保持不变
     */
    @Column(name = "original_assignee", length = 64)
    private String originalAssignee;

    /**
     * 委托给的用户ID
     * 当任务被委托时，记录委托目标用户
     */
    @Column(name = "delegated_to", length = 64)
    private String delegatedTo;

    /**
     * 委托人ID
     * 记录发起委托的用户
     */
    @Column(name = "delegated_by", length = 64)
    private String delegatedBy;

    /**
     * 委托时间
     */
    @Column(name = "delegated_time")
    private LocalDateTime delegatedTime;

    /**
     * 委托原因
     */
    @Column(name = "delegation_reason", length = 500)
    private String delegationReason;

    /**
     * 认领用户ID
     * 当虚拟组或部门角色任务被认领时，记录认领用户
     */
    @Column(name = "claimed_by", length = 64)
    private String claimedBy;

    /**
     * 认领时间
     */
    @Column(name = "claimed_time")
    private LocalDateTime claimedTime;

    /**
     * 任务优先级
     * 0-100，数值越大优先级越高
     */
    @Column(name = "priority")
    private Integer priority;

    /**
     * 任务到期时间
     */
    @Column(name = "due_date")
    private LocalDateTime dueDate;

    /**
     * 任务状态
     * CREATED - 已创建
     * ASSIGNED - 已分配
     * CLAIMED - 已认领
     * DELEGATED - 已委托
     * IN_PROGRESS - 处理中
     * COMPLETED - 已完成
     * CANCELLED - 已取消
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /**
     * 任务创建时间
     */
    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;

    /**
     * 任务更新时间
     */
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    /**
     * 任务完成时间
     */
    @Column(name = "completed_time")
    private LocalDateTime completedTime;

    /**
     * 完成用户ID
     */
    @Column(name = "completed_by", length = 64)
    private String completedBy;

    /**
     * 表单键
     */
    @Column(name = "form_key", length = 255)
    private String formKey;

    /**
     * 业务键
     */
    @Column(name = "business_key", length = 255)
    private String businessKey;

    /**
     * 扩展属性（JSON格式）
     * 存储任务的自定义属性和元数据
     */
    @Column(name = "extended_properties", columnDefinition = "TEXT")
    private String extendedProperties;

    /**
     * 租户ID（多租户支持）
     */
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    /**
     * 版本号（乐观锁）
     */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * 是否已删除（软删除）
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    /**
     * 创建人
     */
    @Column(name = "created_by", length = 64)
    private String createdBy;

    /**
     * 更新人
     */
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    // ==================== 业务方法 ====================

    /**
     * 检查任务是否已被委托
     */
    public boolean isDelegated() {
        return delegatedTo != null && !delegatedTo.trim().isEmpty();
    }

    /**
     * 检查任务是否已被认领
     */
    public boolean isClaimed() {
        return claimedBy != null && !claimedBy.trim().isEmpty();
    }

    /**
     * 检查任务是否已完成
     */
    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    /**
     * 检查任务是否已过期
     */
    public boolean isOverdue() {
        return dueDate != null && LocalDateTime.now().isAfter(dueDate) && !isCompleted();
    }

    /**
     * 获取当前有效的处理人
     * 优先级：委托人 > 认领人 > 原始分配目标
     */
    public String getCurrentAssignee() {
        if (isDelegated()) {
            return delegatedTo;
        }
        if (isClaimed()) {
            return claimedBy;
        }
        if (assignmentType == AssignmentType.USER) {
            return assignmentTarget;
        }
        return null; // 虚拟组和部门角色任务没有具体的处理人
    }

    /**
     * 获取任务的显示标签
     */
    public String getAssignmentTypeLabel() {
        return assignmentType.getDescription();
    }

    /**
     * 更新任务状态和时间戳
     */
    public void updateStatus(String newStatus, String updatedBy) {
        this.status = newStatus;
        this.updatedTime = LocalDateTime.now();
        this.updatedBy = updatedBy;
    }

    /**
     * 委托任务
     */
    public void delegateTask(String delegatedTo, String delegatedBy, String reason) {
        this.delegatedTo = delegatedTo;
        this.delegatedBy = delegatedBy;
        this.delegatedTime = LocalDateTime.now();
        this.delegationReason = reason;
        updateStatus("DELEGATED", delegatedBy);
    }

    /**
     * 认领任务
     */
    public void claimTask(String claimedBy) {
        this.claimedBy = claimedBy;
        this.claimedTime = LocalDateTime.now();
        updateStatus("CLAIMED", claimedBy);
    }

    /**
     * 完成任务
     */
    public void completeTask(String completedBy) {
        this.completedBy = completedBy;
        this.completedTime = LocalDateTime.now();
        updateStatus("COMPLETED", completedBy);
    }
}