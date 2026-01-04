package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 流程统计结果
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessStatisticsResult {
    
    /**
     * 总流程实例数
     */
    private Long totalCount;
    
    /**
     * 运行中的流程实例数
     */
    private Long activeCount;
    
    /**
     * 已完成的流程实例数
     */
    private Long completedCount;
    
    /**
     * 已终止的流程实例数
     */
    private Long terminatedCount;
    
    /**
     * 按状态分组统计
     * key: 状态（ACTIVE、COMPLETED、TERMINATED）
     * value: 数量
     */
    private Map<String, Long> statusStatistics;
    
    /**
     * 按流程定义分组统计
     * key: 流程定义键
     * value: 数量
     */
    private Map<String, Long> processDefinitionStatistics;
    
    /**
     * 按时间分组统计
     * key: 日期（yyyy-MM-dd）
     * value: 数量
     */
    private Map<String, Long> timeStatistics;
}