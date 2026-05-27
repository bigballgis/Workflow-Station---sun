package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Multi-instance subprocess execution status response.
 * 
 * Returns multi-instance subprocess execution progress and sub-task details.
 * 
 * **Validates: Requirements 7.1, 7.2**
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiInstanceStatusResponse {
    
    /**
     * Process instance ID
     */
    private String processInstanceId;
    
    /**
     * Multi-instance activity ID
     */
    private String multiInstanceActivityId;
    
    /**
     * Multi-instance activity name
     */
    private String multiInstanceActivityName;
    
    /**
     * Total instances
     */
    private Integer totalInstances;
    
    /**
     * Completed instances
     */
    private Integer completedInstances;
    
    /**
     * Active instances
     */
    private Integer activeInstances;
    
    /**
     * Cancelled instances
     */
    private Integer cancelledInstances;
    
    /**
     * Multi-instance status (ACTIVE, COMPLETED, CANCELLED)
     */
    private String status;
    
    /**
     * Start time
     */
    private LocalDateTime startedTime;
    
    /**
     * Completion time
     */
    private LocalDateTime completedTime;
    
    /**
     * Sub-task detail list
     */
    private List<SubTaskDetail> tasks;
    
    /**
     * Sub-task detail
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTaskDetail {
        
        /**
         * Task ID
         */
        private String taskId;
        
        /**
         * Task name
         */
        private String taskName;

        /**
         * BPMN UserTask id (used to distinguish steps when task display names collide,
         * preventing orphaned CREATED records from overwriting completed steps).
         */
        private String taskDefinitionKey;

        /**
         * Assignee user ID
         */
        private String assignee;
        
        /**
         * Assignee display name
         */
        private String assigneeName;
        
        /**
         * Task status (CREATED, ASSIGNED, COMPLETED, CANCELLED)
         */
        private String status;
        
        /**
         * Sub-table row ID
         */
        private Long subTableRowId;

        /**
         * Sub-table physical row primary key (multiple columns for composite keys;
         * consistent with engine extended_properties.subTableRowKey).
         */
        private Map<String, Object> subTableRowKey;

        /**
         * Sub-table name (from multi-instance sub-task extendedProperties.subTableName)
         */
        private String subTableName;

        /**
         * Progress status column name (from multi-instance subprocess extension properties)
         */
        private String miTaskStatusField;

        /**
         * Current step column name (from multi-instance subprocess extension properties)
         */
        private String miTaskCurrentNodeField;
        
        /**
         * Task creation time
         */
        private LocalDateTime createdTime;
        
        /**
         * Task completion time
         */
        private LocalDateTime completedTime;
        
        /**
         * Completed by user ID
         */
        private String completedBy;
        
        /**
         * Completed by display name
         */
        private String completedByName;
    }
}
