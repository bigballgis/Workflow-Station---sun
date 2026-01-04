package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 扩展指标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScalingMetrics {
    
    /**
     * 总节点数
     */
    private int totalNodes;
    
    /**
     * 活跃节点数
     */
    private int activeNodes;
    
    /**
     * 平均负载
     */
    private double averageLoad;
    
    /**
     * 负载方差
     */
    private double loadVariance;
    
    /**
     * 总处理任务数
     */
    private long totalProcessedTasks;
    
    /**
     * 当前节点处理任务数
     */
    private long currentNodeProcessedTasks;
    
    /**
     * 是否需要扩展
     */
    private boolean needsScaleOut;
    
    /**
     * 是否需要缩减
     */
    private boolean needsScaleIn;
    
    /**
     * 推荐节点数
     */
    private int recommendedNodeCount;
    
    /**
     * 指标时间
     */
    private LocalDateTime metricsTime;
}
