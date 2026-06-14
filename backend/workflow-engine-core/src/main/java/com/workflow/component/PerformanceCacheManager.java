package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.response.CacheStatisticsResult;
import com.workflow.exception.WorkflowBusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 性能管理组件 - 缓存操作与缓存统计协作类
 *
 * <p>承载 Redis 缓存读写、批量删除、缓存命中率统计等职责，
 * 逻辑由 {@link PerformanceManagerComponent} 拆分而来，行为零变化。
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceCacheManager {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    // 缓存统计
    private final AtomicLong cacheHitCount = new AtomicLong(0);
    private final AtomicLong cacheMissCount = new AtomicLong(0);

    // 缓存键前缀
    static final String CACHE_PREFIX = "workflow:";
    static final String PROCESS_DEF_CACHE = CACHE_PREFIX + "process_def:";
    static final String PROCESS_INST_CACHE = CACHE_PREFIX + "process_inst:";
    static final String TASK_CACHE = CACHE_PREFIX + "task:";
    static final String VARIABLE_CACHE = CACHE_PREFIX + "variable:";
    static final String STATISTICS_CACHE = CACHE_PREFIX + "statistics:";

    // ==================== 缓存操作方法 ====================

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

    public void setCache(String key, Object value, long ttlSeconds) {
        String cacheKey = buildCacheKey(key);

        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(cacheKey, jsonValue, Duration.ofSeconds(ttlSeconds));
            log.debug("缓存设置成功: key={}, ttl={}s", cacheKey, ttlSeconds);
        } catch (JsonProcessingException e) {
            log.error("缓存序列化失败: key={}, error={}", cacheKey, e.getMessage());
            throw new WorkflowBusinessException("CACHE_SERIALIZATION_FAILED", "Cache serialization failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("缓存设置失败: key={}, error={}", cacheKey, e.getMessage());
            throw new WorkflowBusinessException("CACHE_SET_FAILED", "Cache set failed: " + e.getMessage());
        }
    }

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

    public void evictProcessDefinitionCache(String processDefinitionKey) {
        deleteCacheByPattern(PROCESS_DEF_CACHE + processDefinitionKey + "*");
    }

    public void evictProcessInstanceCache(String processInstanceId) {
        deleteCache(PROCESS_INST_CACHE + processInstanceId);
    }

    public void evictTaskCache(String taskId) {
        deleteCache(TASK_CACHE + taskId);
    }

    // ==================== 缓存统计方法 ====================

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
            throw new WorkflowBusinessException("CACHE_STATISTICS_FAILED", "Failed to get cache statistics: " + e.getMessage());
        }
    }

    public void resetCacheStatistics() {
        cacheHitCount.set(0);
        cacheMissCount.set(0);
        log.info("缓存统计已重置");
    }

    public double getCacheHitRate() {
        long hits = cacheHitCount.get();
        long total = hits + cacheMissCount.get();
        return total > 0 ? (double) hits / total : 0.0;
    }

    // ==================== 统计计数访问（供性能分析协作类复用） ====================

    long getCacheHitCount() {
        return cacheHitCount.get();
    }

    long getCacheMissCount() {
        return cacheMissCount.get();
    }

    // ==================== 私有辅助方法 ====================

    private String buildCacheKey(String key) {
        if (key.startsWith(CACHE_PREFIX)) {
            return key;
        }
        return CACHE_PREFIX + key;
    }

    long countKeysByPattern(String pattern) {
        try {
            Set<String> keys = stringRedisTemplate.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("统计缓存键数量失败: pattern={}, error={}", pattern, e.getMessage());
            return 0;
        }
    }
}
