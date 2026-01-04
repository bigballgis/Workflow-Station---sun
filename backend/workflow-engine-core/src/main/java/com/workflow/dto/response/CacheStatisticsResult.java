package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 缓存统计结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheStatisticsResult {
    
    /**
     * 缓存命中次数
     */
    private Long hitCount;
    
    /**
     * 缓存未命中次数
     */
    private Long missCount;
    
    /**
     * 缓存命中率
     */
    private Double hitRate;
    
    /**
     * 缓存键总数
     */
    private Long totalKeys;
    
    /**
     * 缓存内存使用量（字节）
     */
    private Long memoryUsage;
    
    /**
     * 各类型缓存统计
     */
    private Map<String, Long> cacheTypeStatistics;
    
    /**
     * 过期键数量
     */
    private Long expiredKeys;
    
    /**
     * 平均TTL（秒）
     */
    private Double averageTtl;
    
    /**
     * 统计时间
     */
    private LocalDateTime statisticsTime;
}
