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