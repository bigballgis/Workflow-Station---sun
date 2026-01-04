package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 性能分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceAnalysisResult {
    
    /**
     * 分析是否成功
     */
    private boolean success;
    
    /**
     * 分析消息
     */
    private String message;
    
    /**
     * 系统健康状态
     */
    private HealthStatus healthStatus;
    
    /**
     * 性能评分（0-100）
     */
    private Integer performanceScore;
    
    /**
     * 数据库性能指标
     */
    private DatabaseMetrics databaseMetrics;
    
    /**
     * 缓存性能指标
     */
    private CacheMetrics cacheMetrics;
    
    /**
     * 流程引擎性能指标
     */
    private EngineMetrics engineMetrics;
    
    /**
     * 慢查询列表
     */
    private List<SlowQueryInfo> slowQueries;
    
    /**
     * 性能优化建议
     */
    private List<OptimizationSuggestion> suggestions;
    
    /**
     * 分析时间
     */
    private LocalDateTime analysisTime;
    
    /**
     * 健康状态枚举
     */
    public enum HealthStatus {
        HEALTHY,
        WARNING,
        CRITICAL,
        UNKNOWN
    }
    
    /**
     * 数据库性能指标
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatabaseMetrics {
        private Integer activeConnections;
        private Integer idleConnections;
        private Integer maxConnections;
        private Double connectionUtilization;
        private Double averageQueryTime;
        private Long totalQueries;
        private Long slowQueryCount;
        private Double connectionPoolHitRate;
    }
    
    /**
     * 缓存性能指标
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheMetrics {
        private Long totalKeys;
        private Long hitCount;
        private Long missCount;
        private Double hitRate;
        private Long memoryUsage;
        private Long evictedKeys;
        private Double averageResponseTime;
    }
    
    /**
     * 流程引擎性能指标
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EngineMetrics {
        private Long activeProcessInstances;
        private Long pendingTasks;
        private Double averageProcessStartTime;
        private Double averageTaskCompletionTime;
        private Long asyncJobsWaiting;
        private Double throughputPerSecond;
    }
    
    /**
     * 慢查询信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlowQueryInfo {
        private String queryType;
        private String queryDescription;
        private Long executionTimeMs;
        private LocalDateTime executionTime;
        private String suggestion;
    }
    
    /**
     * 优化建议
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizationSuggestion {
        private String category;
        private String priority;
        private String description;
        private String action;
        private String expectedImprovement;
    }
}
