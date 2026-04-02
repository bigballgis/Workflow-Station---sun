package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 多实例子流程执行状态响应
 * 
 * 用于返回多实例子流程的执行进度和子任务详情
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
     * 流程实例ID
     */
    private String processInstanceId;
    
    /**
     * 多实例活动ID
     */
    private String multiInstanceActivityId;
    
    /**
     * 多实例活动名称
     */
    private String multiInstanceActivityName;
    
    /**
     * 总实例数
     */
    private Integer totalInstances;
    
    /**
     * 已完成实例数
     */
    private Integer completedInstances;
    
    /**
     * 进行中实例数
     */
    private Integer activeInstances;
    
    /**
     * 已取消实例数
     */
    private Integer cancelledInstances;
    
    /**
     * 多实例状态（ACTIVE, COMPLETED, CANCELLED）
     */
    private String status;
    
    /**
     * 开始时间
     */
    private LocalDateTime startedTime;
    
    /**
     * 完成时间
     */
    private LocalDateTime completedTime;
    
    /**
     * 子任务详情列表
     */
    private List<SubTaskDetail> tasks;
    
    /**
     * 子任务详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTaskDetail {
        
        /**
         * 任务ID
         */
        private String taskId;
        
        /**
         * 任务名称
         */
        private String taskName;
        
        /**
         * 处理人用户ID
         */
        private String assignee;
        
        /**
         * 处理人姓名
         */
        private String assigneeName;
        
        /**
         * 任务状态（CREATED, ASSIGNED, COMPLETED, CANCELLED）
         */
        private String status;
        
        /**
         * 子表行ID
         */
        private Long subTableRowId;
        
        /**
         * 任务创建时间
         */
        private LocalDateTime createdTime;
        
        /**
         * 任务完成时间
         */
        private LocalDateTime completedTime;
        
        /**
         * 完成人用户ID
         */
        private String completedBy;
        
        /**
         * 完成人姓名
         */
        private String completedByName;
    }
}
