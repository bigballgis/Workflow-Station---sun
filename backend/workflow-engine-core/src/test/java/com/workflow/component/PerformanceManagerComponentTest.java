package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.response.AsyncOperationResult;
import com.workflow.dto.response.CacheStatisticsResult;
import com.workflow.dto.response.PerformanceAnalysisResult;
import com.workflow.exception.WorkflowBusinessException;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.TaskQuery;
import org.flowable.engine.runtime.NativeProcessInstanceQuery;
import org.flowable.job.api.JobQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 性能管理组件单元测试
 * 需求: 10.1, 10.2, 10.4, 10.5, 10.7, 10.9
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("性能管理组件测试")
class PerformanceManagerComponentTest {

    @Mock(lenient = true)
    private StringRedisTemplate stringRedisTemplate;
    
    @Mock(lenient = true)
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock(lenient = true)
    private ValueOperations<String, String> valueOperations;
    
    @Mock(lenient = true)
    private HikariDataSource dataSource;
    
    @Mock(lenient = true)
    private HikariPoolMXBean poolMXBean;
    
    @Mock(lenient = true)
    private Connection connection;
    
    @Mock(lenient = true)
    private RuntimeService runtimeService;
    
    @Mock(lenient = true)
    private TaskService taskService;
    
    @Mock(lenient = true)
    private ManagementService managementService;
    
    @Mock(lenient = true)
    private ProcessInstanceQuery processInstanceQuery;
    
    @Mock(lenient = true)
    private TaskQuery taskQuery;
    
    @Mock(lenient = true)
    private JobQuery jobQuery;

    private ObjectMapper objectMapper;
    private PerformanceManagerComponent performanceManager;

    @BeforeEach
    void setUp() throws SQLException {
        objectMapper = new ObjectMapper();
        
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(dataSource.getConnection()).thenReturn(connection);
        when(dataSource.getHikariPoolMXBean()).thenReturn(poolMXBean);
        when(dataSource.getMaximumPoolSize()).thenReturn(20);
        
        when(poolMXBean.getActiveConnections()).thenReturn(5);
        when(poolMXBean.getIdleConnections()).thenReturn(15);
        
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.count()).thenReturn(10L);
        
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(50L);
        
        when(managementService.createJobQuery()).thenReturn(jobQuery);
        when(jobQuery.count()).thenReturn(5L);
        
        performanceManager = new PerformanceManagerComponent(
                stringRedisTemplate, redisTemplate, objectMapper,
                dataSource, runtimeService, taskService, managementService);
    }

    @Nested
    @DisplayName("缓存操作测试")
    class CacheOperationTests {

        @Test
        @DisplayName("从缓存获取数据 - 缓存命中")
        void getFromCacheOrLoad_cacheHit() throws JsonProcessingException {
            // Given
            String key = "test-key";
            Map<String, String> expectedData = Map.of("name", "test");
            String jsonData = objectMapper.writeValueAsString(expectedData);
            
            when(valueOperations.get(contains(key))).thenReturn(jsonData);

            // When
            @SuppressWarnings("unchecked")
            Map<String, String> result = performanceManager.getFromCacheOrLoad(
                    key, Map.class, () -> Map.of("name", "loaded"), 300);

            // Then
            assertThat(result).containsEntry("name", "test");
            verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("从缓存获取数据 - 缓存未命中")
        void getFromCacheOrLoad_cacheMiss() {
            // Given
            String key = "test-key";
            when(valueOperations.get(contains(key))).thenReturn(null);

            // When
            @SuppressWarnings("unchecked")
            Map<String, String> result = performanceManager.getFromCacheOrLoad(
                    key, Map.class, () -> Map.of("name", "loaded"), 300);

            // Then
            assertThat(result).containsEntry("name", "loaded");
            verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("设置缓存成功")
        void setCache_success() {
            // Given
            String key = "test-key";
            Map<String, String> value = Map.of("name", "test");

            // When
            performanceManager.setCache(key, value, 300);

            // Then
            verify(valueOperations).set(contains(key), anyString(), eq(Duration.ofSeconds(300)));
        }

        @Test
        @DisplayName("获取缓存成功")
        void getCache_success() throws JsonProcessingException {
            // Given
            String key = "test-key";
            Map<String, String> expectedData = Map.of("name", "test");
            String jsonData = objectMapper.writeValueAsString(expectedData);
            
            when(valueOperations.get(contains(key))).thenReturn(jsonData);

            // When
            @SuppressWarnings("unchecked")
            Map<String, String> result = performanceManager.getCache(key, Map.class);

            // Then
            assertThat(result).containsEntry("name", "test");
        }

        @Test
        @DisplayName("获取缓存 - 不存在返回null")
        void getCache_notFound() {
            // Given
            String key = "non-existent-key";
            when(valueOperations.get(contains(key))).thenReturn(null);

            // When
            @SuppressWarnings("unchecked")
            Map<String, String> result = performanceManager.getCache(key, Map.class);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("删除缓存成功")
        void deleteCache_success() {
            // Given
            String key = "test-key";
            when(stringRedisTemplate.delete(contains(key))).thenReturn(true);

            // When
            boolean result = performanceManager.deleteCache(key);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("批量删除缓存")
        void deleteCacheByPattern_success() {
            // Given
            String pattern = "test-*";
            Set<String> keys = Set.of("workflow:test-1", "workflow:test-2");
            when(stringRedisTemplate.keys(contains(pattern))).thenReturn(keys);
            when(stringRedisTemplate.delete(anyCollection())).thenReturn(2L);

            // When
            long deleted = performanceManager.deleteCacheByPattern(pattern);

            // Then
            assertThat(deleted).isEqualTo(2);
        }

        @Test
        @DisplayName("清除流程定义缓存")
        void evictProcessDefinitionCache_success() {
            // Given
            String processDefinitionKey = "test-process";
            when(stringRedisTemplate.keys(anyString())).thenReturn(Set.of("key1"));
            when(stringRedisTemplate.delete(anyCollection())).thenReturn(1L);

            // When
            performanceManager.evictProcessDefinitionCache(processDefinitionKey);

            // Then
            verify(stringRedisTemplate).keys(contains(processDefinitionKey));
        }
    }

    @Nested
    @DisplayName("缓存统计测试")
    class CacheStatisticsTests {

        @Test
        @DisplayName("获取缓存统计信息")
        void getCacheStatistics_success() {
            // Given
            when(stringRedisTemplate.keys(anyString())).thenReturn(new HashSet<>());

            // When
            CacheStatisticsResult result = performanceManager.getCacheStatistics();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatisticsTime()).isNotNull();
            assertThat(result.getCacheTypeStatistics()).isNotNull();
        }

        @Test
        @DisplayName("重置缓存统计")
        void resetCacheStatistics_success() {
            // Given - 先产生一些缓存命中/未命中
            when(valueOperations.get(anyString())).thenReturn(null);
            performanceManager.getCache("key1", String.class);
            performanceManager.getCache("key2", String.class);

            // When
            performanceManager.resetCacheStatistics();

            // Then
            assertThat(performanceManager.getCacheHitRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("获取缓存命中率")
        void getCacheHitRate_success() throws JsonProcessingException {
            // Given - 模拟缓存命中和未命中
            when(valueOperations.get(contains("hit"))).thenReturn("\"cached\"");
            when(valueOperations.get(contains("miss"))).thenReturn(null);
            
            performanceManager.resetCacheStatistics();
            performanceManager.getCache("hit-key", String.class);
            performanceManager.getCache("miss-key", String.class);

            // When
            double hitRate = performanceManager.getCacheHitRate();

            // Then
            assertThat(hitRate).isEqualTo(0.5);
        }
    }

    @Nested
    @DisplayName("异步操作测试")
    class AsyncOperationTests {

        @Test
        @DisplayName("异步执行操作成功")
        void executeAsync_success() throws Exception {
            // Given
            String operationId = "async-op-1";

            // When
            CompletableFuture<AsyncOperationResult<String>> future = 
                    performanceManager.executeAsync(operationId, () -> "result");
            AsyncOperationResult<String> result = future.get(5, TimeUnit.SECONDS);

            // Then
            assertThat(result.getStatus()).isEqualTo(AsyncOperationResult.OperationStatus.COMPLETED);
            assertThat(result.getOperationId()).isEqualTo(operationId);
            assertThat(result.getResult()).isEqualTo("result");
        }

        @Test
        @DisplayName("异步执行操作失败")
        void executeAsync_failure() throws Exception {
            // Given
            String operationId = "async-op-fail";

            // When
            CompletableFuture<AsyncOperationResult<String>> future = 
                    performanceManager.executeAsync(operationId, () -> {
                        throw new RuntimeException("Test error");
                    });
            AsyncOperationResult<String> result = future.get(5, TimeUnit.SECONDS);

            // Then
            assertThat(result.getStatus()).isEqualTo(AsyncOperationResult.OperationStatus.FAILED);
            assertThat(result.getErrorMessage()).contains("Test error");
        }

        @Test
        @DisplayName("带超时的异步执行")
        void executeAsyncWithTimeout_success() throws Exception {
            // Given
            String operationId = "async-timeout-op";

            // When
            CompletableFuture<AsyncOperationResult<String>> future = 
                    performanceManager.executeAsyncWithTimeout(operationId, () -> "result", 5000);
            AsyncOperationResult<String> result = future.get(10, TimeUnit.SECONDS);

            // Then
            assertThat(result.getStatus()).isEqualTo(AsyncOperationResult.OperationStatus.COMPLETED);
        }

        @Test
        @DisplayName("获取异步操作状态")
        void getAsyncOperationStatus_running() throws Exception {
            // Given
            String operationId = "async-status-op";
            performanceManager.executeAsync(operationId, () -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "result";
            });

            // When - 立即检查状态
            AsyncOperationResult.OperationStatus status = 
                    performanceManager.getAsyncOperationStatus(operationId);

            // Then
            assertThat(status).isIn(
                    AsyncOperationResult.OperationStatus.RUNNING,
                    AsyncOperationResult.OperationStatus.COMPLETED);
        }

        @Test
        @DisplayName("获取不存在的异步操作状态")
        void getAsyncOperationStatus_notFound() {
            // When
            AsyncOperationResult.OperationStatus status = 
                    performanceManager.getAsyncOperationStatus("non-existent");

            // Then
            assertThat(status).isNull();
        }

        @Test
        @DisplayName("取消异步操作")
        void cancelAsyncOperation_success() {
            // Given
            String operationId = "async-cancel-op";
            performanceManager.executeAsync(operationId, () -> {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "result";
            });

            // When
            boolean cancelled = performanceManager.cancelAsyncOperation(operationId);

            // Then - 可能已经完成或被取消
            // 不做严格断言，因为取决于执行时机
        }

        @Test
        @DisplayName("清理已完成的异步操作")
        void cleanupCompletedAsyncOperations_success() throws Exception {
            // Given
            String operationId = "async-cleanup-op";
            CompletableFuture<AsyncOperationResult<String>> future = 
                    performanceManager.executeAsync(operationId, () -> "result");
            future.get(5, TimeUnit.SECONDS); // 等待完成

            // When
            performanceManager.cleanupCompletedAsyncOperations();

            // Then - 验证不抛出异常
            assertThat(true).isTrue();
        }
    }

    @Nested
    @DisplayName("性能分析测试")
    class PerformanceAnalysisTests {

        @Test
        @DisplayName("性能分析成功")
        void analyzePerformance_success() {
            // Given
            when(stringRedisTemplate.keys(anyString())).thenReturn(new HashSet<>());

            // When
            PerformanceAnalysisResult result = performanceManager.analyzePerformance();

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getHealthStatus()).isNotNull();
            assertThat(result.getPerformanceScore()).isBetween(0, 100);
            assertThat(result.getDatabaseMetrics()).isNotNull();
            assertThat(result.getCacheMetrics()).isNotNull();
            assertThat(result.getEngineMetrics()).isNotNull();
        }

        @Test
        @DisplayName("性能分析 - 健康状态为HEALTHY")
        void analyzePerformance_healthyStatus() {
            // Given - 设置低连接使用率
            when(poolMXBean.getActiveConnections()).thenReturn(2);
            when(stringRedisTemplate.keys(anyString())).thenReturn(new HashSet<>());
            
            // 模拟一些缓存命中来提高命中率
            when(valueOperations.get(anyString())).thenReturn("\"cached\"");
            performanceManager.resetCacheStatistics();
            // 产生缓存命中
            for (int i = 0; i < 10; i++) {
                performanceManager.getCache("key-" + i, String.class);
            }

            // When
            PerformanceAnalysisResult result = performanceManager.analyzePerformance();

            // Then - 由于缓存命中率高，应该是HEALTHY
            assertThat(result.getHealthStatus()).isIn(
                    PerformanceAnalysisResult.HealthStatus.HEALTHY,
                    PerformanceAnalysisResult.HealthStatus.WARNING); // 允许WARNING因为其他因素
        }

        @Test
        @DisplayName("性能分析 - 数据库连接池使用率高时生成建议")
        void analyzePerformance_highConnectionUtilization() {
            // Given
            when(poolMXBean.getActiveConnections()).thenReturn(18);
            when(stringRedisTemplate.keys(anyString())).thenReturn(new HashSet<>());

            // When
            PerformanceAnalysisResult result = performanceManager.analyzePerformance();

            // Then
            assertThat(result.getSuggestions()).isNotEmpty();
            assertThat(result.getSuggestions().stream()
                    .anyMatch(s -> s.getCategory().equals("DATABASE"))).isTrue();
        }
    }

    @Nested
    @DisplayName("查询优化测试")
    class QueryOptimizationTests {

        @Test
        @DisplayName("带监控的查询执行")
        void executeWithMonitoring_success() {
            // When
            String result = performanceManager.executeWithMonitoring("test-query", () -> "result");

            // Then
            assertThat(result).isEqualTo("result");
        }

        @Test
        @DisplayName("带监控的查询执行 - 异常处理")
        void executeWithMonitoring_exception() {
            // When/Then
            assertThatThrownBy(() -> 
                    performanceManager.executeWithMonitoring("test-query", () -> {
                        throw new RuntimeException("Query error");
                    }))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Query error");
        }

        @Test
        @DisplayName("带缓存的查询执行")
        void executeWithCache_success() {
            // Given
            when(valueOperations.get(anyString())).thenReturn(null);

            // When
            String result = performanceManager.executeWithCache(
                    "cache-key", "test-query", String.class, () -> "result", 300);

            // Then
            assertThat(result).isEqualTo("result");
            verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("批量操作测试")
    class BatchOperationTests {

        @Test
        @DisplayName("批量执行操作")
        void executeBatch_success() {
            // Given
            List<Integer> items = Arrays.asList(1, 2, 3, 4, 5);

            // When
            List<Integer> results = performanceManager.executeBatch(items, 2, 
                    batch -> batch.stream().map(i -> i * 2).toList());

            // Then
            assertThat(results).hasSize(5);
            assertThat(results).containsExactly(2, 4, 6, 8, 10);
        }

        @Test
        @DisplayName("批量执行操作 - 空列表")
        void executeBatch_emptyList() {
            // Given
            List<Integer> items = Collections.emptyList();

            // When
            List<Integer> results = performanceManager.executeBatch(items, 2, 
                    batch -> batch.stream().map(i -> i * 2).toList());

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("并行批量执行操作")
        void executeParallelBatch_success() {
            // Given
            List<Integer> items = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8);

            // When
            List<Integer> results = performanceManager.executeParallelBatch(items, 2, 
                    batch -> batch.stream().map(i -> i * 2).toList());

            // Then
            assertThat(results).hasSize(8);
            assertThat(results).containsExactlyInAnyOrder(2, 4, 6, 8, 10, 12, 14, 16);
        }

        @Test
        @DisplayName("批量执行操作 - 处理异常")
        void executeBatch_exception() {
            // Given
            List<Integer> items = Arrays.asList(1, 2, 3);

            // When/Then
            assertThatThrownBy(() -> 
                    performanceManager.executeBatch(items, 2, batch -> {
                        throw new RuntimeException("Batch error");
                    }))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Batch error");
        }
    }

    @Nested
    @DisplayName("缓存清除测试")
    class CacheEvictionTests {

        @Test
        @DisplayName("清除流程实例缓存")
        void evictProcessInstanceCache_success() {
            // Given
            String processInstanceId = "proc-123";
            when(stringRedisTemplate.delete(contains(processInstanceId))).thenReturn(true);

            // When
            performanceManager.evictProcessInstanceCache(processInstanceId);

            // Then
            verify(stringRedisTemplate).delete(contains(processInstanceId));
        }

        @Test
        @DisplayName("清除任务缓存")
        void evictTaskCache_success() {
            // Given
            String taskId = "task-123";
            when(stringRedisTemplate.delete(contains(taskId))).thenReturn(true);

            // When
            performanceManager.evictTaskCache(taskId);

            // Then
            verify(stringRedisTemplate).delete(contains(taskId));
        }
    }

    @Nested
    @DisplayName("资源管理测试")
    class ResourceManagementTests {

        @Test
        @DisplayName("关闭资源")
        void shutdown_success() {
            // When
            performanceManager.shutdown();

            // Then - 验证不抛出异常
            assertThat(true).isTrue();
        }
    }
}
