package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Task statistics result
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatisticsResult {
    
    /**
     * Total task count
     */
    private Long totalCount;
    
    /**
     * Pending task count
     */
    private Long pendingCount;
    
    /**
     * Completed task count
     */
    private Long completedCount;
    
    /**
     * Overdue task count
     */
    private Long overdueCount;
    
    /**
     * Statistics grouped by task name
     * key: Task name
     * value: Count
     */
    private Map<String, Long> taskNameStatistics;
    
    /**
     * Statistics grouped by assignee
     * key: Assignee
     * value: Count
     */
    private Map<String, Long> assigneeStatistics;
    
    /**
     * Average processing time (seconds)
     */
    private Double averageProcessingTime;
}