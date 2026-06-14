package com.workflow.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.resource.ResourceManager;
import com.platform.common.resource.ResourceTimeoutException;
import com.platform.common.resource.ResourceLimitExceededException;
import com.workflow.dto.response.AsyncOperationResult;
import com.workflow.dto.response.CacheStatisticsResult;
import com.workflow.dto.response.PerformanceAnalysisResult;
import com.workflow.exception.WorkflowBusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.ManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 性能管理组件
 *
 * 负责Redis缓存集成、数据库查询优化、异步处理机制和性能监控调优
 * 支持缓存命中率统计、慢查询检测和资源使用分析
 *
 * <p>本类为门面（facade），全部 public 方法签名逐字不变，方法体委托给同包协作类：
 * <ul>
 *   <li>{@link PerformanceCacheManager} —— 缓存操作与缓存统计</li>
 *   <li>{@link PerformanceAsyncExecutor} —— 异步处理与批量操作</li>
 *   <li>{@link PerformanceAnalyzer} —— 性能分析</li>
 * </ul>
 * 协作类通过 {@code @Lazy @Autowired} 字段注入以破除潜在循环依赖并保持原构造签名不变；
 * 当字段为 null（如纯 Mockito 单元测试直接 new 本类）时，由 lazy accessor 用门面已注入的
 * 依赖兜底构造，保证行为零变化。
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
    private final ResourceManager resourceManager;

    // 协作类（@Lazy 破环；纯单元测试场景下为 null，由 lazy accessor 兜底）
    @Lazy
    @Autowired(required = false)
    private PerformanceCacheManager cacheManager;

    @Lazy
    @Autowired(required = false)
    private PerformanceAsyncExecutor asyncExecutor;

    @Lazy
    @Autowired(required = false)
    private PerformanceAnalyzer performanceAnalyzer;

    // lazy accessor 兜底实例（@Lazy null 时构造一次后复用，保证共享状态一致）
    private volatile PerformanceCacheManager fallbackCacheManager;
    private volatile PerformanceAsyncExecutor fallbackAsyncExecutor;
    private volatile PerformanceAnalyzer fallbackPerformanceAnalyzer;

    private PerformanceCacheManager cacheManager() {
        if (cacheManager != null) {
            return cacheManager;
        }
        if (fallbackCacheManager == null) {
            synchronized (this) {
                if (fallbackCacheManager == null) {
                    fallbackCacheManager = new PerformanceCacheManager(stringRedisTemplate, objectMapper);
                }
            }
        }
        return fallbackCacheManager;
    }

    private PerformanceAsyncExecutor asyncExecutor() {
        if (asyncExecutor != null) {
            return asyncExecutor;
        }
        if (fallbackAsyncExecutor == null) {
            synchronized (this) {
                if (fallbackAsyncExecutor == null) {
                    fallbackAsyncExecutor = new PerformanceAsyncExecutor();
                }
            }
        }
        return fallbackAsyncExecutor;
    }

    private PerformanceAnalyzer performanceAnalyzer() {
        if (performanceAnalyzer != null) {
            return performanceAnalyzer;
        }
        if (fallbackPerformanceAnalyzer == null) {
            synchronized (this) {
                if (fallbackPerformanceAnalyzer == null) {
                    fallbackPerformanceAnalyzer = new PerformanceAnalyzer(
                            dataSource, runtimeService, taskService, managementService, cacheManager());
                }
            }
        }
        return fallbackPerformanceAnalyzer;
    }

    // 慢查询阈值（毫秒）
    private static final long SLOW_QUERY_THRESHOLD_MS = 500;

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
        return cacheManager().getFromCacheOrLoad(key, type, loader, ttlSeconds);
    }

    /**
     * 设置缓存
     *
     * @param key 缓存键
     * @param value 缓存值
     * @param ttlSeconds 过期时间（秒）
     */
    public void setCache(String key, Object value, long ttlSeconds) {
        cacheManager().setCache(key, value, ttlSeconds);
    }

    /**
     * 获取缓存
     *
     * @param key 缓存键
     * @param type 数据类型
     * @return 缓存值，不存在返回null
     */
    public <T> T getCache(String key, Class<T> type) {
        return cacheManager().getCache(key, type);
    }

    /**
     * 删除缓存
     *
     * @param key 缓存键
     * @return 是否删除成功
     */
    public boolean deleteCache(String key) {
        return cacheManager().deleteCache(key);
    }

    /**
     * 批量删除缓存
     *
     * @param pattern 键模式
     * @return 删除的键数量
     */
    public long deleteCacheByPattern(String pattern) {
        return cacheManager().deleteCacheByPattern(pattern);
    }

    /**
     * 清除流程定义缓存
     *
     * @param processDefinitionKey 流程定义键
     */
    public void evictProcessDefinitionCache(String processDefinitionKey) {
        cacheManager().evictProcessDefinitionCache(processDefinitionKey);
    }

    /**
     * 清除流程实例缓存
     *
     * @param processInstanceId 流程实例ID
     */
    public void evictProcessInstanceCache(String processInstanceId) {
        cacheManager().evictProcessInstanceCache(processInstanceId);
    }

    /**
     * 清除任务缓存
     *
     * @param taskId 任务ID
     */
    public void evictTaskCache(String taskId) {
        cacheManager().evictTaskCache(taskId);
    }

    // ==================== 缓存统计方法 ====================

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计结果
     */
    public CacheStatisticsResult getCacheStatistics() {
        return cacheManager().getCacheStatistics();
    }

    /**
     * 重置缓存统计
     */
    public void resetCacheStatistics() {
        cacheManager().resetCacheStatistics();
    }

    /**
     * 获取当前缓存命中率
     *
     * @return 命中率（0-1）
     */
    public double getCacheHitRate() {
        return cacheManager().getCacheHitRate();
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
        return asyncExecutor().executeAsync(operationId, operation);
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
        return asyncExecutor().executeAsyncWithTimeout(operationId, operation, timeoutMs);
    }

    /**
     * 获取异步操作状态
     *
     * @param operationId 操作ID
     * @return 操作状态
     */
    public AsyncOperationResult.OperationStatus getAsyncOperationStatus(String operationId) {
        return asyncExecutor().getAsyncOperationStatus(operationId);
    }

    /**
     * 取消异步操作
     *
     * @param operationId 操作ID
     * @return 是否取消成功
     */
    public boolean cancelAsyncOperation(String operationId) {
        return asyncExecutor().cancelAsyncOperation(operationId);
    }

    /**
     * 清理已完成的异步操作
     */
    public void cleanupCompletedAsyncOperations() {
        asyncExecutor().cleanupCompletedAsyncOperations();
    }

    // ==================== Resource-Managed Operations ====================

    /**
     * Execute resource-intensive operation with timeout and resource management
     *
     * @param operationId Operation identifier
     * @param operation Operation to execute
     * @param timeoutMs Timeout in milliseconds
     * @return Operation result
     */
    public <T> T executeResourceManagedOperation(String operationId, Supplier<T> operation, long timeoutMs) {
        try {
            return resourceManager.executeWithTimeout(operationId, operation, timeoutMs);
        } catch (ResourceTimeoutException e) {
            log.warn("Resource-managed operation timed out: {}", e.getMessage());
            throw new WorkflowBusinessException("OPERATION_TIMEOUT", e.getMessage());
        } catch (ResourceLimitExceededException e) {
            log.warn("Resource limit exceeded: {}", e.getMessage());
            throw new WorkflowBusinessException("RESOURCE_LIMIT_EXCEEDED", e.getMessage());
        }
    }

    /**
     * Execute database operation with resource management
     *
     * @param operationId Operation identifier
     * @param operation Database operation
     * @param timeoutMs Timeout in milliseconds
     * @return Operation result
     */
    public <T> T executeResourceManagedDatabaseOperation(String operationId,
                                                        ResourceManager.DatabaseOperation<T> operation,
                                                        long timeoutMs) {
        try {
            return resourceManager.executeWithConnection(operationId, operation, timeoutMs);
        } catch (ResourceTimeoutException e) {
            log.warn("Resource-managed database operation timed out: {}", e.getMessage());
            throw new WorkflowBusinessException("DATABASE_OPERATION_TIMEOUT", e.getMessage());
        } catch (ResourceLimitExceededException e) {
            log.warn("Database resource limit exceeded: {}", e.getMessage());
            throw new WorkflowBusinessException("DATABASE_RESOURCE_LIMIT_EXCEEDED", e.getMessage());
        }
    }

    /**
     * Execute cached operation with resource management
     *
     * @param cacheKey Cache key
     * @param operationId Operation identifier
     * @param type Result type
     * @param operation Operation to execute
     * @param ttlSeconds Cache TTL
     * @param timeoutMs Operation timeout
     * @return Operation result
     */
    public <T> T executeResourceManagedCachedOperation(String cacheKey, String operationId, Class<T> type,
                                                      Supplier<T> operation, long ttlSeconds, long timeoutMs) {
        return getFromCacheOrLoad(cacheKey, type,
                () -> executeResourceManagedOperation(operationId, operation, timeoutMs),
                ttlSeconds);
    }

    // ==================== Enhanced Performance Analysis ====================

    /**
     * 获取性能分析结果
     *
     * @return 性能分析结果
     */
    public PerformanceAnalysisResult analyzePerformance() {
        return performanceAnalyzer().analyzePerformance();
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
        return asyncExecutor().executeBatch(items, batchSize, processor);
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
        return asyncExecutor().executeParallelBatch(items, batchSize, processor);
    }

    // ==================== 资源关闭 ====================

    /**
     * 关闭资源
     */
    public void shutdown() {
        asyncExecutor().shutdown();
    }
}
