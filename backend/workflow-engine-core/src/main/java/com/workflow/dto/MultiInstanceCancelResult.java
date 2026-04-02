package com.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 多实例子任务取消结果
 * 
 * 包含被取消的子任务数量、各子任务的处理人和取消前状态，用于审计日志记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiInstanceCancelResult {
    
    /**
     * 被取消的子任务总数
     */
    private int cancelledCount;
    
    /**
     * 取消失败的子任务数量
     */
    private int failedCount;
    
    /**
     * 各子任务的详细信息
     */
    private List<CancelledTaskDetail> cancelledTasks;
    
    /**
     * 单个被取消子任务的详细信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelledTaskDetail {
        
        /**
         * 任务 ID
         */
        private String taskId;
        
        /**
         * 处理人 ID
         */
        private String assigneeId;
        
        /**
         * 取消前的状态
         */
        private String previousStatus;
        
        /**
         * 子表行 ID
         */
        private Long subTableRowId;
        
        /**
         * 子表名称
         */
        private String subTableName;
    }
}
