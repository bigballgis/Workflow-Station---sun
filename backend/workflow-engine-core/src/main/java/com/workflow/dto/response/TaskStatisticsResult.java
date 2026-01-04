package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 任务统计结果
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
     * 总任务数
     */
    private Long totalCount;
    
    /**
     * 待办任务数
     */
    private Long pendingCount;
    
    /**
     * 已完成任务数
     */
    private Long completedCount;
    
    /**
     * 超时任务数
     */
    private Long overdueCount;
    
    /**
     * 按任务名称分组统计
     * key: 任务名称
     * value: 数量
     */
    private Map<String, Long> taskNameStatistics;
    
    /**
     * 按分配人分组统计
     * key: 分配人
     * value: 数量
     */
    private Map<String, Long> assigneeStatistics;
    
    /**
     * 平均处理时间（秒）
     */
    private Double averageProcessingTime;
}