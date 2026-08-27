package com.workflow.entity;

import com.workflow.enums.AssignmentType;
import com.workflow.enums.DelegatedTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Extended task info entity
 * Extended data model supporting multi-dimensional task assignment
 * 
 * This entity extends Flowable native task functionality, supporting:
 * 1. Multi-dimensional assignment types (user, virtual group, department role)
 * 2. Task delegation mechanism
 * 3. Task claim mechanism
 * 4. Extended task attributes and index optimization
 */
@Entity
@Table(name = "wf_extended_task_info", indexes = {
    @Index(name = "idx_task_id", columnList = "taskId", unique = true),
    @Index(name = "idx_assignment_type", columnList = "assignmentType"),
    // assignment_target: partial index idx_assignment_target_non_candidate_users is managed in
    // deploy/init-scripts (exclude CANDIDATE_USERS long comma-separated pools); not expressible via @Index
    @Index(name = "idx_delegated_to", columnList = "delegatedTo"),
    @Index(name = "idx_delegated_bu_role", columnList = "delegatedBuCode,delegatedRoleCode"),
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
     * Flowable task ID (linked to ACT_RU_TASK table)
     */
    @Column(name = "task_id", nullable = false, unique = true, length = 64)
    private String taskId;

    /**
     * Process instance ID
     */
    @Column(name = "process_instance_id", nullable = false, length = 64)
    private String processInstanceId;

    /**
     * Process definition ID
     */
    @Column(name = "process_definition_id", nullable = false, length = 64)
    private String processDefinitionId;

    /**
     * Task definition key
     */
    @Column(name = "task_definition_key", length = 255)
    private String taskDefinitionKey;

    /**
     * Task name
     */
    @Column(name = "task_name", length = 255)
    private String taskName;

    /**
     * Task description
     */
    @Column(name = "task_description", length = 4000)
    private String taskDescription;

    /**
     * Task assignment type
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false, length = 20)
    private AssignmentType assignmentType;

    /**
     * Assignment target
     * - When assignmentType is USER, stores user ID
     * - When assignmentType is VIRTUAL_GROUP, stores virtual group ID
     * - When assignmentType is DEPT_ROLE, stores "departmentId:roleId" format
     * - When assignmentType is CANDIDATE_USERS, stores comma-separated user IDs (may exceed 255)
     * TEXT: large candidate pools from role/BU resolution must not be truncated.
     */
    @Column(name = "assignment_target", nullable = false, columnDefinition = "TEXT")
    private String assignmentTarget;

    /**
     * Original assignee (for delegation scenarios)
     * Records the original assignment info of the task, remains unchanged after delegation
     */
    @Column(name = "original_assignee", length = 64)
    private String originalAssignee;

    /**
     * Delegated user ID when {@link #delegatedTargetType} is USER.
     * Null for BU_ROLE targets.
     */
    @Column(name = "delegated_to", length = 64)
    private String delegatedTo;

    /**
     * USER or BU_ROLE. Null on legacy rows is treated as USER.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "delegated_target_type", length = 20)
    private DelegatedTargetType delegatedTargetType;

    @Column(name = "delegated_bu_code", length = 64)
    private String delegatedBuCode;

    @Column(name = "delegated_role_code", length = 64)
    private String delegatedRoleCode;

    /**
     * Delegator ID
     * Records the user who initiated the delegation
     */
    @Column(name = "delegated_by", length = 64)
    private String delegatedBy;

    /**
     * Delegation time
     */
    @Column(name = "delegated_time")
    private LocalDateTime delegatedTime;

    /**
     * Delegation reason
     */
    @Column(name = "delegation_reason", length = 500)
    private String delegationReason;

    /**
     * Claimed user ID
     * When a virtual group or department role task is claimed, records the claiming user
     */
    @Column(name = "claimed_by", length = 64)
    private String claimedBy;

    /**
     * Claim time
     */
    @Column(name = "claimed_time")
    private LocalDateTime claimedTime;

    /**
     * Task priority
     * 0-100, higher value = higher priority
     */
    @Column(name = "priority")
    private Integer priority;

    /**
     * Task due date
     */
    @Column(name = "due_date")
    private LocalDateTime dueDate;

    /**
     * Task status
     * CREATED - Created
     * ASSIGNED - Assigned
     * CLAIMED - Claimed
     * DELEGATED - Delegated
     * IN_PROGRESS - In Progress
     * COMPLETED - Completed
     * CANCELLED - Cancelled
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /**
     * Task creation time
     */
    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;

    /**
     * Task update time
     */
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    /**
     * Task completion time
     */
    @Column(name = "completed_time")
    private LocalDateTime completedTime;

    /**
     * Completion user ID
     */
    @Column(name = "completed_by", length = 64)
    private String completedBy;

    /**
     * Form key
     */
    @Column(name = "form_key", length = 255)
    private String formKey;

    /**
     * Business key
     */
    @Column(name = "business_key", length = 255)
    private String businessKey;

    /**
     * Extended attributes (JSON format)
     * Stores custom attributes and metadata of the task
     */
    @Column(name = "extended_properties", columnDefinition = "TEXT")
    private String extendedProperties;

    /**
     * Tenant ID (multi-tenant support)
     */
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    /**
     * Version number (optimistic locking)
     */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * Whether deleted (soft delete)
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    /**
     * Creator
     */
    @Column(name = "created_by", length = 64)
    private String createdBy;

    /**
     * Updater
     */
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    // ==================== Business methods ====================

    /**
     * Check if task has been delegated (USER target or paired BU+Role).
     */
    public boolean isDelegated() {
        if (delegatedTo != null && !delegatedTo.trim().isEmpty()) {
            return true;
        }
        return isBuRoleDelegated();
    }

    public boolean isBuRoleDelegated() {
        return delegatedTargetType == DelegatedTargetType.BU_ROLE
                && delegatedBuCode != null && !delegatedBuCode.isBlank()
                && delegatedRoleCode != null && !delegatedRoleCode.isBlank();
    }

    /**
     * Check if task has been claimed
     */
    public boolean isClaimed() {
        return claimedBy != null && !claimedBy.trim().isEmpty();
    }

    /**
     * Check if task has been completed
     */
    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    /**
     * Check if task has expired
     */
    public boolean isOverdue() {
        return dueDate != null && LocalDateTime.now().isAfter(dueDate) && !isCompleted();
    }

    /**
     * Current handler (Flowable assignee identity). Delegation does not change this:
     * still the claimer or original USER target, never {@code delegatedTo}.
     */
    public String getCurrentAssignee() {
        if (isClaimed()) {
            return claimedBy;
        }
        if (assignmentType == AssignmentType.USER) {
            return assignmentTarget;
        }
        return null;
    }

    /**
     * Get the display label of the task
     */
    public String getAssignmentTypeLabel() {
        return assignmentType.getDescription();
    }

    /**
     * Update task status and timestamp
     */
    public void updateStatus(String newStatus, String updatedBy) {
        this.status = newStatus;
        this.updatedTime = LocalDateTime.now();
        this.updatedBy = updatedBy;
    }

    /**
     * Delegate this task to a user. Does not change Flowable assignee.
     */
    public void delegateTask(String delegatedTo, String delegatedBy, String reason) {
        this.delegatedTargetType = DelegatedTargetType.USER;
        this.delegatedTo = delegatedTo;
        this.delegatedBuCode = null;
        this.delegatedRoleCode = null;
        this.delegatedBy = delegatedBy;
        this.delegatedTime = LocalDateTime.now();
        this.delegationReason = reason;
        updateStatus("DELEGATED", delegatedBy);
    }

    /**
     * Delegate this task to a paired BU+Role. Does not change Flowable assignee.
     */
    public void delegateTaskToBuRole(String buCode, String roleCode, String delegatedBy, String reason) {
        this.delegatedTargetType = DelegatedTargetType.BU_ROLE;
        this.delegatedTo = null;
        this.delegatedBuCode = buCode;
        this.delegatedRoleCode = roleCode;
        this.delegatedBy = delegatedBy;
        this.delegatedTime = LocalDateTime.now();
        this.delegationReason = reason;
        updateStatus("DELEGATED", delegatedBy);
    }

    public void clearDelegation() {
        this.delegatedTargetType = null;
        this.delegatedTo = null;
        this.delegatedBuCode = null;
        this.delegatedRoleCode = null;
        this.delegatedBy = null;
        this.delegatedTime = null;
        this.delegationReason = null;
    }

    /**
     * Claim task
     */
    public void claimTask(String claimedBy) {
        this.claimedBy = claimedBy;
        this.claimedTime = LocalDateTime.now();
        updateStatus("CLAIMED", claimedBy);
    }
    
    /**
     * Unclaim task
     */
    public void unclaimTask() {
        this.claimedBy = null;
        this.claimedTime = null;
        updateStatus("ASSIGNED", null);
    }

    /**
     * Complete task
     */
    public void completeTask(String completedBy) {
        this.completedBy = completedBy;
        this.completedTime = LocalDateTime.now();
        updateStatus("COMPLETED", completedBy);
    }
}
