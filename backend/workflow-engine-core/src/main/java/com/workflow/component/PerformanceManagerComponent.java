package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.request.CacheOperationRequest;
import com.workflow.dto.response.AsyncOperationResult;
import com.workflow.dto.response.CacheStatisticsResult;
import com.workflow.dto.response.PerformanceAnalysisResult;
import com.workflow.dto.response.PerformanceAnalysisResult.*;
import com.workflow.exception.WorkflowBusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.ManagementService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 性能管理组件
 * 
 * 负责Redis缓存集成、数据库查询优化、异步处理机制和性能监控调优
 * 支持缓存命中率统计、慢查询检测和资源使用分析
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceManagerComponent {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final DataSource dataSource;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final ManagementService managementService;
    
    // 缓存统计
    private final AtomicLong cacheHitCount = new AtomicLong(0);
    private final AtomicLong cacheMissCount = new AtomicLong(0);
    
    // 异步操作执行器
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2,
            r -> {
                Thread t = new Thread(r, "async-workflow-");
                t.setDaemon(true);
                return t;
            }
    );
    
    // 异步操作结果存储
    private final ConcurrentHashMap<String, CompletableFuture<?>> asyncOperations = new ConcurrentHashMap<>();
    
    // 慢查询阈值（毫秒）
    private static final long SLOW_QUERY_THRESHOLD_MS = 500;
    
    // 缓存键前缀
    private static final String CACHE_PREFIX = "workflow:";
    private static final String PROCESS_DEF_CACHE = CACHE_PREFIX + "process_def:";
    private static final String PROCESS_INST_CACHE = CACHE_PREFIX + "process_inst:";
    private static final String TASK_CACHE = CACHE_PREFIX + "task:";
    private static final String VARIABLE_CACHE = CACHE_PREFIX + "variable:";
    private static final String STATISTICS_CACHE = CACHE_PREFIX + "statistics:";

    // ==================== 缓存操作方法 ====================

    /**
     * 从缓存获取数据，如果不存在则从数据源加载
     * 
     * @param key 缓存键
     * @param type 数据类型
     * @param loader 数据加载器
     * @param ttlSeconds 过期时间（秒）
     * @return 缓存数据
     */
    public <T> T getFromCacheOrLoad(String key, Class<T> type, Supplier<T> loader, long ttlSeconds) {
        String cacheKey = buildCacheKey(key);
        
        try {
            // 尝试从缓存获取
            String cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
            
            if (cachedValue != null) {
                cacheHitCount.incrementAndGet();
                log.debug("缓存命中: key={}", cacheKey);
                return objectMapper.readValue(cachedValue, type);
            }
            
            cacheMissCount.incrementAndGet();
            log.debug("缓存未命中: key={}", cacheKey);
            
            // 从数据源加载
            T value = loader.get();
            
            if (value != null) {
                // 存入缓存
                String jsonValue = objectMapper.writeValueAsString(value);
                stringRedisTemplate.opsForValue().set(cacheKey, jsonValue, Duration.ofSeconds(ttlSeconds));
                log.debug("数据已缓存: key={}, ttl={}s", cacheKey, ttlSeconds);
            }
            
            return value;
            
        } catch (JsonProcessingException e) {
            log.error("缓存序列化/反序列化失败: key={}, error={}", cacheKey, e.getMessage());
            // 降级：直接从数据源加载
            return loader.get();
        } catch (Exception e) {
            log.error("缓存操作失败: key={}, error={}", cacheKey, e.getMessage());
            // 降级：直接从数据源加载
            return loader.get();
        }
    }

    /**
     * 设置缓存
     * 
     * @param key 缓存键
     * @param value 缓存值
     * @param ttlSeconds 过期时间（秒）
     */
    public void setCache(String key, Object value, long ttlSeconds) {
        String cacheKey = buildCacheKey(key);
        
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(cacheKey, jsonValue, Duration.ofSeconds(ttlSeconds));
            log.debug("缓存设置成功: key={}, ttl={}s", cacheKey, ttlSeconds);
        } catch (JsonProcessingException e) {
            log.error("缓存序列化失败: key={}, error={}", cacheKey, e.getMessage());
            throw new WorkflowBusinessException("CACHE_SERIALIZATION_FAILED", "缓存序列化失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("缓存设置失败: key={}, error={}", cacheKey, e.getMessage());
            throw new WorkflowBusinessException("CACHE_SET_FAILED", "缓存设置失败: " + e.getMessage());
        }
    }

    /**
     * 获取缓存
     * 
     * @param key 缓存键
     * @param type 数据类型
     * @return 缓存值，不存在返回null
     */
    public <T> T getCache(String key, Class<T> type) {
        String cacheKey = buildCacheKey(key);
        
        try {
            String cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
            
            if (cachedValue != null) {
                cacheHitCount.incrementAndGet();
                return objectMapper.readValue(cachedValue, type);
            }
            
            cacheMissCount.incrementAndGet();
            return null;
            
        } catch (JsonProcessingException e) {
            log.error("缓存反序列化失败: key={}, error={}", cacheKey, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("缓存获取失败: key={}, error={}", cacheKey, e.getMessage());
            return null;
        }
    }

    /**
     * 删除缓存
     * 
     * @param key 缓存键
     * @return 是否删除成功
     */
    public boolean deleteCache(String key) {
        String cacheKey = buildCacheKey(key);
        
        try {
            Boolean deleted = stringRedisTemplate.delete(cacheKey);
            log.debug("缓存删除: key={}, result={}", cacheKey, deleted);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            log.error("缓存删除失败: key={}, error={}", cacheKey, e.getMessage());
            return false;
        }
    }

    /**
     * 批量删除缓存
     * 
     * @param pattern 键模式
     * @return 删除的键数量
     */
    public long deleteCacheByPattern(String pattern) {
        String cachePattern = buildCacheKey(pattern);
        
        try {
            Set<String> keys = stringRedisTemplate.keys(cachePattern);
            if (keys != null && !keys.isEmpty()) {
                Long deleted = stringRedisTemplate.delete(keys);
                log.info("批量删除缓存: pattern={}, count={}", cachePattern, deleted);
                return deleted != null ? deleted : 0;
            }
            return 0;
        } catch (Exception e) {
            log.error("批量删除缓存失败: pattern={}, error={}", cachePattern, e.getMessage());
            return 0;
        }
    }

    /**
     * 清除流程定义缓存
     * 
     * @param processDefinitionKey 流程定义键
     */
    public void evictProcessDefinitionCache(String processDefinitionKey) {
        deleteCacheByPattern(PROCESS_DEF_CACHE + processDefinitionKey + "*");
    }

    /**
     * 清除流程实例缓存
     * 
     * @param processInstanceId 流程实例ID
     */
    public void evictProcessInstanceCache(String processInstanceId) {
        deleteCache(PROCESS_INST_CACHE + processInstanceId);
    }

    /**
     * 清除任务缓存
     * 
     * @param taskId 任务ID
     */
    public void evictTaskCache(String taskId) {
        deleteCache(TASK_CACHE + taskId);
    }

    // ==================== 缓存统计方法 ====================

    /**
     * 获取缓存统计信息
     * 
     * @return 缓存统计结果
     */
    public CacheStatisticsResult getCacheStatistics() {
        log.info("获取缓存统计信息");
        
        try {
            long hits = cacheHitCount.get();
            long misses = cacheMissCount.get();
            long total = hits + misses;
            double hitRate = total > 0 ? (double) hits / total : 0.0;
            
            // 获取各类型缓存键数量
            Map<String, Long> cacheTypeStatistics = new HashMap<>();
            cacheTypeStatistics.put("PROCESS_DEFINITION", countKeysByPattern(PROCESS_DEF_CACHE + "*"));
            cacheTypeStatistics.put("PROCESS_INSTANCE", countKeysByPattern(PROCESS_INST_CACHE + "*"));
            cacheTypeStatistics.put("TASK", countKeysByPattern(TASK_CACHE + "*"));
            cacheTypeStatistics.put("VARIABLE", countKeysByPattern(VARIABLE_CACHE + "*"));
            cacheTypeStatistics.put("STATISTICS", countKeysByPattern(STATISTICS_CACHE + "*"));
            
            long totalKeys = cacheTypeStatistics.values().stream().mapToLong(Long::longValue).sum();
            
            return CacheStatisticsResult.builder()
                    .hitCount(hits)
                    .missCount(misses)
                    .hitRate(hitRate)
                    .totalKeys(totalKeys)
                    .cacheTypeStatistics(cacheTypeStatistics)
                    .statisticsTime(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("获取缓存统计信息失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("CACHE_STATISTICS_FAILED", "获取缓存统计信息失败: " + e.getMessage());
        }
    }

    /**
     * 重置缓存统计
     */
    public void resetCacheStatistics() {
        cacheHitCount.set(0);
        cacheMissCount.set(0);
        log.info("缓存统计已重置");
    }

    /**
     * 获取当前缓存命中率
     * 
     * @return 命中率（0-1）
     */
    public double getCacheHitRate() {
        long hits = cacheHitCount.get();
        long total = hits + cacheMissCount.get();
        return total > 0 ? (double) hits / total : 0.0;
    }

    // ==================== 异步处理方法 ====================

    /**
     * 异步执行操作
     * 
     * @param operationId 操作ID
     * @param operation 操作逻辑
     * @return 异步操作结果
     */
    public <T> CompletableFuture<AsyncOperationResult<T>> executeAsync(String operationId, Supplier<T> operation) {
        log.info("开始异步操作: operationId={}", operationId);
        
        CompletableFuture<AsyncOperationResult<T>> future = CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                T result = operation.get();
                long executionTime = System.currentTimeMillis() - startTime;
                
                log.info("异步操作完成: operationId={}, executionTime={}ms", operationId, executionTime);
                return AsyncOperationResult.success(operationId, result, executionTime);
                
            } catch (Exception e) {
                log.error("异步操作失败: operationId={}, error={}", operationId, e.getMessage(), e);
                return AsyncOperationResult.failure(operationId, e.getMessage());
            }
        }, asyncExecutor);
        
        asyncOperations.put(operationId, future);
        return future;
    }

    /**
     * 异步执行带超时的操作
     * 
     * @param operationId 操作ID
     * @param operation 操作逻辑
     * @param timeoutMs 超时时间（毫秒）
     * @return 异步操作结果
     */
    public <T> CompletableFuture<AsyncOperationResult<T>> executeAsyncWithTimeout(
            String operationId, Supplier<T> operation, long timeoutMs) {
        
        log.info("开始带超时的异步操作: operationId={}, timeout={}ms", operationId, timeoutMs);
        
        CompletableFuture<AsyncOperationResult<T>> future = executeAsync(operationId, operation)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    if (ex instanceof TimeoutException) {
                        log.warn("异步操作超时: operationId={}", operationId);
                        return AsyncOperationResult.failure(operationId, "操作超时");
                    }
                    return AsyncOperationResult.failure(operationId, ex.getMessage());
                });
        
        return future;
    }

    /**
     * 获取异步操作状态
     * 
     * @param operationId 操作ID
     * @return 操作状态
     */
    public AsyncOperationResult.OperationStatus getAsyncOperationStatus(String operationId) {
        CompletableFuture<?> future = asyncOperations.get(operationId);
        
        if (future == null) {
            return null;
        }
        
        if (future.isDone()) {
            if (future.isCompletedExceptionally()) {
                return AsyncOperationResult.OperationStatus.FAILED;
            }
            if (future.isCancelled()) {
                return AsyncOperationResult.OperationStatus.CANCELLED;
            }
            return AsyncOperationResult.OperationStatus.COMPLETED;
        }
        
        return AsyncOperationResult.OperationStatus.RUNNING;
    }

    /**
     * 取消异步操作
     * 
     * @param operationId 操作ID
     * @return 是否取消成功
     */
    public boolean cancelAsyncOperation(String operationId) {
        CompletableFuture<?> future = asyncOperations.get(operationId);
        
        if (future != null && !future.isDone()) {
            boolean cancelled = future.cancel(true);
            log.info("取消异步操作: operationId={}, result={}", operationId, cancelled);
            return cancelled;
        }
        
        return false;
    }

    /**
     * 清理已完成的异步操作
     */
    public void cleanupCompletedAsyncOperations() {
        asyncOperations.entrySet().removeIf(entry -> entry.getValue().isDone());
        log.debug("清理已完成的异步操作");
    }

    // ==================== 性能监控方法 ====================

    /**
     * 获取性能分析结果
     * 
     * @return 性能分析结果
     */
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
        long hits = cacheHitCount.get();
        long misses = cacheMissCount.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total : 0.0;
        
        long totalKeys = countKeysByPattern(CACHE_PREFIX + "*");
        
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

    // ==================== 查询优化方法 ====================

    /**
     * 执行带性能监控的查询
     * 
     * @param queryName 查询名称
     * @param query 查询逻辑
     * @return 查询结果
     */
    public <T> T executeWithMonitoring(String queryName, Supplier<T> query) {
        long startTime = System.currentTimeMillis();
        
        try {
            T result = query.get();
            long executionTime = System.currentTimeMillis() - startTime;
            
            if (executionTime > SLOW_QUERY_THRESHOLD_MS) {
                log.warn("慢查询检测: queryName={}, executionTime={}ms", queryName, executionTime);
            } else {
                log.debug("查询执行: queryName={}, executionTime={}ms", queryName, executionTime);
            }
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("查询执行失败: queryName={}, executionTime={}ms, error={}", 
                    queryName, executionTime, e.getMessage());
            throw e;
        }
    }

    /**
     * 执行带缓存的查询
     * 
     * @param cacheKey 缓存键
     * @param queryName 查询名称
     * @param type 结果类型
     * @param query 查询逻辑
     * @param ttlSeconds 缓存过期时间
     * @return 查询结果
     */
    public <T> T executeWithCache(String cacheKey, String queryName, Class<T> type, 
                                   Supplier<T> query, long ttlSeconds) {
        return getFromCacheOrLoad(cacheKey, type, () -> executeWithMonitoring(queryName, query), ttlSeconds);
    }

    // ==================== 批量操作优化方法 ====================

    /**
     * 批量执行操作
     * 
     * @param items 待处理项目
     * @param batchSize 批次大小
     * @param processor 处理器
     * @return 处理结果列表
     */
    public <T, R> List<R> executeBatch(List<T> items, int batchSize, java.util.function.Function<List<T>, List<R>> processor) {
        log.info("开始批量操作: totalItems={}, batchSize={}", items.size(), batchSize);
        
        List<R> results = new ArrayList<>();
        
        for (int i = 0; i < items.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, items.size());
            List<T> batch = items.subList(i, endIndex);
            
            try {
                List<R> batchResults = processor.apply(batch);
                results.addAll(batchResults);
                log.debug("批次处理完成: batch={}/{}, processed={}", 
                        (i / batchSize) + 1, (items.size() + batchSize - 1) / batchSize, batch.size());
            } catch (Exception e) {
                log.error("批次处理失败: batch={}, error={}", (i / batchSize) + 1, e.getMessage());
                throw e;
            }
        }
        
        log.info("批量操作完成: totalProcessed={}", results.size());
        return results;
    }

    /**
     * 并行批量执行操作
     * 
     * @param items 待处理项目
     * @param batchSize 批次大小
     * @param processor 处理器
     * @return 处理结果列表
     */
    public <T, R> List<R> executeParallelBatch(List<T> items, int batchSize, 
                                                java.util.function.Function<List<T>, List<R>> processor) {
        log.info("开始并行批量操作: totalItems={}, batchSize={}", items.size(), batchSize);
        
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < items.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, items.size());
            batches.add(items.subList(i, endIndex));
        }
        
        List<CompletableFuture<List<R>>> futures = batches.stream()
                .map(batch -> CompletableFuture.supplyAsync(() -> processor.apply(batch), asyncExecutor))
                .toList();
        
        List<R> results = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();
        
        log.info("并行批量操作完成: totalProcessed={}", results.size());
        return results;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建缓存键
     */
    private String buildCacheKey(String key) {
        if (key.startsWith(CACHE_PREFIX)) {
            return key;
        }
        return CACHE_PREFIX + key;
    }

    /**
     * 统计匹配模式的键数量
     */
    private long countKeysByPattern(String pattern) {
        try {
            Set<String> keys = stringRedisTemplate.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("统计缓存键数量失败: pattern={}, error={}", pattern, e.getMessage());
            return 0;
        }
    }

    /**
     * 关闭资源
     */
    public void shutdown() {
        log.info("关闭性能管理组件");
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
