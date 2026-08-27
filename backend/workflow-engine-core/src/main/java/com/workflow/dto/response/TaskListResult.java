package com.workflow.dto.response;

import com.workflow.enums.AssignmentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Task list query result
 */
@Data
@Builder
public class TaskListResult {
    
    /**
     * Task list
     */
    private List<TaskInfo> tasks;
    
    /**
     * Total count
     */
    private Long totalCount;
    
    /**
     * Current page number
     */
    private Integer currentPage;
    
    /**
     * Page size
     */
    private Integer pageSize;
    
    /**
     * Total pages
     */
    private Integer totalPages;
    
    /**
     * Whether successful
     */
    @Builder.Default
    private Boolean success = true;
    
    /**
     * Message
     */
    private String message;
    
    /**
     * Task information
     */
    @Data
    @Builder
    public static class TaskInfo {
        
        /**
         * Task ID
         */
        private String taskId;
        
        /**
         * Task name
         */
        private String taskName;

        /**
         * 「当前步骤」名（MI 感知）：普通节点=任务名；多实例子任务=外层多实例 subProcess 的 name（如 "multi"）。
         * 供 To Do/Completed 列表与详情展示「进到多实例这一大步」，区别于 taskName（具体内层子任务名）。
         */
        private String currentStepName;

        /**
         * Task description
         */
        private String taskDescription;
        
        /**
         * Process instance ID
         */
        private String processInstanceId;
        
        /**
         * Process definition ID
         */
        private String processDefinitionId;
        
        /**
         * Process definition key (extracted from processDefinitionId)
         */
        private String processDefinitionKey;
        
        /**
         * Process definition name
         */
        private String processDefinitionName;
        
        /**
         * Assignment type
         */
        private AssignmentType assignmentType;

        /**
         * Original value of the BPMN user task extension attribute assigneeType (read from deployed BPMN XML, e.g. INITIATOR, ROLE).
         * Separate from runtime {@link #assignmentType}, used to display designer semantics; if normalized to initiator assignment, {@link #assignmentType} may be USER.
         */
        private String bpmnAssigneeType;

        /**
         * BPMN extension {@code businessUnitId} (e.g. fixed BU specified by FIXED_BU_ROLE), used by portal to filter tasks by current workspace.
         */
        private String bpmnBusinessUnitId;

        /**
         * MI 按角色分派信号（来自 ExtendedTaskInfo.extendedProperties）：
         * assigneeMode=role 表示该 MI 子任务是按角色分派（共享认领池），portal 据此做 workspace 可见性收敛
         * （role 分派只在用户切到该 role 的 workspace 时可见）；roleCode/businessUnitCode 是分派的角色/BU code。
         */
        private String miAssigneeMode;
        private String miRoleCode;
        private String miBusinessUnitCode;

        /**
         * Assignment target
         */
        private String assignmentTarget;
        
        /**
         * Current assignee
         */
        private String currentAssignee;
        
        /**
         * Current assignee name
         */
        private String currentAssigneeName;
        
        /**
         * Priority
         */
        private Integer priority;
        
        /**
         * Due date
         */
        private LocalDateTime dueDate;
        
        /**
         * Task status
         */
        private String status;
        
        /**
         * Created time
         */
        private LocalDateTime createdTime;
        
        /**
         * Whether delegated
         */
        private Boolean isDelegated;

        private String delegatedTo;
        private String delegatedBy;
        private String delegatedTargetType;
        private String delegatedBuCode;
        private String delegatedRoleCode;
        
        /**
         * Whether claimed
         */
        private Boolean isClaimed;
        
        /**
         * Whether overdue
         */
        private Boolean isOverdue;
        
        /**
         * Form key
         */
        private String formKey;
        
        /**
         * Business key
         */
        private String businessKey;
        
        /**
         * Process initiator ID
         */
        private String initiatorId;
        
        /**
         * Process initiator name
         */
        private String initiatorName;
        
        /**
         * Process variables (for form data binding)
         */
        private java.util.Map<String, Object> variables;

        /**
         * Flowable candidate user IDs (resolved from identity links at runtime when no assignee)
         */
        private java.util.List<String> candidateUserIds;

        /**
         * Flowable candidate group IDs
         */
        private java.util.List<String> candidateGroupIds;
        
        /**
         * Task definition key (BPMN element ID, e.g. Activity_1abc)
         */
        private String taskDefinitionKey;
        
        /**
         * Available action IDs for the task (extracted from BPMN extensionElements)
         */
        private java.util.List<String> actionIds;
        
        /**
         * Assignment type description
         */
        public String getAssignmentTypeDescription() {
            return assignmentType != null ? assignmentType.getDescription() : "Unknown";
        }
        
        /**
         * Get task status label
         */
        public String getStatusLabel() {
            if (isDelegated != null && isDelegated) {
                return "Delegated";
            }
            if (isClaimed != null && isClaimed) {
                return "Claimed";
            }
            if (isOverdue != null && isOverdue) {
                return "Overdue";
            }
            return switch (status != null ? status : "UNKNOWN") {
                case "CREATED" -> "Created";
                case "ASSIGNED" -> "Assigned";
                case "IN_PROGRESS" -> "In Progress";
                case "COMPLETED" -> "Completed";
                case "CANCELLED" -> "Cancelled";
                default -> "Unknown status";
            };
        }
    }
}