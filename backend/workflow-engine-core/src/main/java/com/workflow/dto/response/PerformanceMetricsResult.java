package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 性能指标结果
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceMetricsResult {
    
    /**
     * 平均流程执行时间（秒）
     */
    private Double averageProcessDuration;
    
    /**
     * 最长流程执行时间（秒）
     */
    private Long maxProcessDuration;
    
    /**
     * 最短流程执行时间（秒）
     */
    private Long minProcessDuration;
    
    /**
     * 流程成功率（百分比）
     */
    private Double processSuccessRate;
    
    /**
     * 任务平均等待时间（秒）
     */
    private Double averageTaskWaitTime;
    
    /**
     * 系统吞吐量（每小时处理的流程数）
     */
    private Double throughputPerHour;
    
    /**
     * 资源利用率
     * key: 资源类型（cpu、memory、disk、network）
     * value: 利用率百分比
     */
    private Map<String, Double> resourceUtilization;
}