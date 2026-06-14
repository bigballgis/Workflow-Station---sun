package com.workflow.component;

import com.workflow.dto.response.PerformanceAnalysisResult;
import com.workflow.dto.response.PerformanceAnalysisResult.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 性能管理组件 - 性能分析协作类
 *
 * <p>承载数据库/缓存/引擎性能指标采集、慢查询检测、优化建议生成、
 * 健康状态与评分计算。逻辑由 {@link PerformanceManagerComponent} 拆分而来，
 * 行为零变化。缓存命中率与缓存键数量复用 {@link PerformanceCacheManager}。
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceAnalyzer {

    private final DataSource dataSource;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final ManagementService managementService;
    private final PerformanceCacheManager cacheManager;

    // ==================== Enhanced Performance Analysis ====================

    public PerformanceAnalysisResult analyzePerformance() {
        log.info("开始性能分析");

        try {
            // 数据库性能指标
            DatabaseMetrics databaseMetrics = analyzeDatabasePerformance();

            // 缓存性能指标
            CacheMetrics cacheMetrics = analyzeCachePerformance();

            // 流程引擎性能指标
            EngineMetrics engineMetrics = analyzeEnginePerformance();

            // 慢查询检测
            List<SlowQueryInfo> slowQueries = detectSlowQueries();

            // 生成优化建议
            List<OptimizationSuggestion> suggestions = generateOptimizationSuggestions(
                    databaseMetrics, cacheMetrics, engineMetrics, slowQueries);

            // 计算健康状态和性能评分
            HealthStatus healthStatus = calculateHealthStatus(databaseMetrics, cacheMetrics, engineMetrics);
            int performanceScore = calculatePerformanceScore(databaseMetrics, cacheMetrics, engineMetrics);

            return PerformanceAnalysisResult.builder()
                    .success(true)
                    .message("性能分析完成")
                    .healthStatus(healthStatus)
                    .performanceScore(performanceScore)
                    .databaseMetrics(databaseMetrics)
                    .cacheMetrics(cacheMetrics)
                    .engineMetrics(engineMetrics)
                    .slowQueries(slowQueries)
                    .suggestions(suggestions)
                    .analysisTime(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("性能分析失败: {}", e.getMessage(), e);
            return PerformanceAnalysisResult.builder()
                    .success(false)
                    .message("性能分析失败: " + e.getMessage())
                    .healthStatus(HealthStatus.UNKNOWN)
                    .analysisTime(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * 分析数据库性能
     */
    private DatabaseMetrics analyzeDatabasePerformance() {
        try (Connection connection = dataSource.getConnection()) {
            // 获取HikariCP连接池信息
            int activeConnections = 0;
            int idleConnections = 0;
            int maxConnections = 20; // 默认配置

            // 尝试获取HikariCP统计信息
            if (dataSource instanceof com.zaxxer.hikari.HikariDataSource) {
                com.zaxxer.hikari.HikariDataSource hikariDataSource =
                        (com.zaxxer.hikari.HikariDataSource) dataSource;
                com.zaxxer.hikari.HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();

                if (poolMXBean != null) {
                    activeConnections = poolMXBean.getActiveConnections();
                    idleConnections = poolMXBean.getIdleConnections();
                    maxConnections = hikariDataSource.getMaximumPoolSize();
                }
            }

            double connectionUtilization = maxConnections > 0 ?
                    (double) activeConnections / maxConnections : 0.0;

            return DatabaseMetrics.builder()
                    .activeConnections(activeConnections)
                    .idleConnections(idleConnections)
                    .maxConnections(maxConnections)
                    .connectionUtilization(connectionUtilization)
                    .averageQueryTime(0.0) // 需要实际监控数据
                    .totalQueries(0L)
                    .slowQueryCount(0L)
                    .connectionPoolHitRate(0.9) // 默认值
                    .build();

        } catch (SQLException e) {
            log.error("分析数据库性能失败: {}", e.getMessage());
            return DatabaseMetrics.builder()
                    .activeConnections(0)
                    .idleConnections(0)
                    .maxConnections(20)
                    .connectionUtilization(0.0)
                    .build();
        }
    }

    /**
     * 分析缓存性能
     */
    private CacheMetrics analyzeCachePerformance() {
        long hits = cacheManager.getCacheHitCount();
        long misses = cacheManager.getCacheMissCount();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total : 0.0;

        long totalKeys = cacheManager.countKeysByPattern(PerformanceCacheManager.CACHE_PREFIX + "*");

        return CacheMetrics.builder()
                .totalKeys(totalKeys)
                .hitCount(hits)
                .missCount(misses)
                .hitRate(hitRate)
                .memoryUsage(0L) // 需要Redis INFO命令获取
                .evictedKeys(0L)
                .averageResponseTime(0.0)
                .build();
    }

    /**
     * 分析流程引擎性能
     */
    private EngineMetrics analyzeEnginePerformance() {
        try {
            long activeProcessInstances = runtimeService.createProcessInstanceQuery().count();
            long pendingTasks = taskService.createTaskQuery().count();
            long asyncJobsWaiting = managementService.createJobQuery().count();

            return EngineMetrics.builder()
                    .activeProcessInstances(activeProcessInstances)
                    .pendingTasks(pendingTasks)
                    .averageProcessStartTime(0.0) // 需要实际监控数据
                    .averageTaskCompletionTime(0.0)
                    .asyncJobsWaiting(asyncJobsWaiting)
                    .throughputPerSecond(0.0)
                    .build();

        } catch (Exception e) {
            log.error("分析流程引擎性能失败: {}", e.getMessage());
            return EngineMetrics.builder()
                    .activeProcessInstances(0L)
                    .pendingTasks(0L)
                    .asyncJobsWaiting(0L)
                    .build();
        }
    }

    /**
     * 检测慢查询
     */
    private List<SlowQueryInfo> detectSlowQueries() {
        // 简化实现，实际应该从数据库慢查询日志获取
        return new ArrayList<>();
    }

    /**
     * 生成优化建议
     */
    private List<OptimizationSuggestion> generateOptimizationSuggestions(
            DatabaseMetrics dbMetrics, CacheMetrics cacheMetrics,
            EngineMetrics engineMetrics, List<SlowQueryInfo> slowQueries) {

        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        // 数据库连接池优化建议
        if (dbMetrics.getConnectionUtilization() > 0.8) {
            suggestions.add(OptimizationSuggestion.builder()
                    .category("DATABASE")
                    .priority("HIGH")
                    .description("数据库连接池使用率过高")
                    .action("考虑增加最大连接数或优化查询性能")
                    .expectedImprovement("减少连接等待时间")
                    .build());
        }

        // 缓存命中率优化建议
        if (cacheMetrics.getHitRate() < 0.8) {
            suggestions.add(OptimizationSuggestion.builder()
                    .category("CACHE")
                    .priority("MEDIUM")
                    .description("缓存命中率低于80%")
                    .action("检查缓存策略，增加热点数据缓存时间")
                    .expectedImprovement("提高查询响应速度")
                    .build());
        }

        // 待处理任务过多建议
        if (engineMetrics.getPendingTasks() > 1000) {
            suggestions.add(OptimizationSuggestion.builder()
                    .category("ENGINE")
                    .priority("HIGH")
                    .description("待处理任务数量过多")
                    .action("检查任务分配策略，增加处理人员或自动化处理")
                    .expectedImprovement("减少任务积压")
                    .build());
        }

        // 异步作业积压建议
        if (engineMetrics.getAsyncJobsWaiting() > 100) {
            suggestions.add(OptimizationSuggestion.builder()
                    .category("ENGINE")
                    .priority("MEDIUM")
                    .description("异步作业队列积压")
                    .action("增加异步执行器线程数或检查作业执行效率")
                    .expectedImprovement("加快异步作业处理速度")
                    .build());
        }

        return suggestions;
    }

    /**
     * 计算健康状态
     */
    private HealthStatus calculateHealthStatus(
            DatabaseMetrics dbMetrics, CacheMetrics cacheMetrics, EngineMetrics engineMetrics) {

        // 严重问题检查
        if (dbMetrics.getConnectionUtilization() > 0.95 ||
            engineMetrics.getAsyncJobsWaiting() > 500) {
            return HealthStatus.CRITICAL;
        }

        // 警告问题检查
        if (dbMetrics.getConnectionUtilization() > 0.8 ||
            cacheMetrics.getHitRate() < 0.6 ||
            engineMetrics.getPendingTasks() > 1000) {
            return HealthStatus.WARNING;
        }

        return HealthStatus.HEALTHY;
    }

    /**
     * 计算性能评分
     */
    private int calculatePerformanceScore(
            DatabaseMetrics dbMetrics, CacheMetrics cacheMetrics, EngineMetrics engineMetrics) {

        int score = 100;

        // 数据库连接池评分（最多扣20分）
        if (dbMetrics.getConnectionUtilization() > 0.8) {
            score -= (int) ((dbMetrics.getConnectionUtilization() - 0.8) * 100);
        }

        // 缓存命中率评分（最多扣20分）
        if (cacheMetrics.getHitRate() < 0.8) {
            score -= (int) ((0.8 - cacheMetrics.getHitRate()) * 25);
        }

        // 待处理任务评分（最多扣20分）
        if (engineMetrics.getPendingTasks() > 500) {
            score -= Math.min(20, (int) (engineMetrics.getPendingTasks() / 100));
        }

        // 异步作业评分（最多扣20分）
        if (engineMetrics.getAsyncJobsWaiting() > 50) {
            score -= Math.min(20, (int) (engineMetrics.getAsyncJobsWaiting() / 10));
        }

        return Math.max(0, Math.min(100, score));
    }
}
