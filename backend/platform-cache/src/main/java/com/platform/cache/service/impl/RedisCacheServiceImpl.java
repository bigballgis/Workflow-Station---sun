package com.platform.cache.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.cache.service.CacheService;
import com.platform.cache.service.DistributedLock;
import com.platform.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis implementation of CacheService.
 * Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheServiceImpl implements CacheService {
    
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = JsonUtils.getObjectMapper();
    
    private static final String LOCK_PREFIX = "lock:";
    private static final String INVALIDATION_CHANNEL = "cache:invalidation";
    
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, type));
        } catch (Exception e) {
            log.error("Failed to get cache value for key: {}", key, e);
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<String> getString(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }
    
    @Override
    public void set(String key, Object value, Duration ttl) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, jsonValue, ttl);
        } catch (Exception e) {
            log.error("Failed to set cache value for key: {}", key, e);
            throw new RuntimeException("Cache set failed", e);
        }
    }
    
    @Override
    public void set(String key, Object value) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, jsonValue);
        } catch (Exception e) {
            log.error("Failed to set cache value for key: {}", key, e);
            throw new RuntimeException("Cache set failed", e);
        }
    }
    
    @Override
    public boolean setIfAbsent(String key, Object value, Duration ttl) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            Boolean result = redisTemplate.opsForValue().setIfAbsent(key, jsonValue, ttl);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Failed to setIfAbsent for key: {}", key, e);
            return false;
        }
    }
    
    @Override
    public boolean delete(String key) {
        Boolean result = redisTemplate.delete(key);
        return Boolean.TRUE.equals(result);
    }
    
    @Override
    public long deleteAll(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        Long result = redisTemplate.delete(keys);
        return result != null ? result : 0;
    }
    
    @Override
    public long deleteByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        Long result = redisTemplate.delete(keys);
        return result != null ? result : 0;
    }
    
    @Override
    public boolean exists(String key) {
        Boolean result = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(result);
    }
    
    @Override
    public boolean expire(String key, Duration ttl) {
        Boolean result = redisTemplate.expire(key, ttl);
        return Boolean.TRUE.equals(result);
    }
    
    @Override
    public long getTtl(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null ? ttl : -2;
    }
    
    @Override
    public long increment(String key, long delta) {
        Long result = redisTemplate.opsForValue().increment(key, delta);
        return result != null ? result : 0;
    }
    
    @Override
    public Set<String> keys(String pattern) {
        Set<String> result = redisTemplate.keys(pattern);
        return result != null ? result : Set.of();
    }
    
    @Override
    public Optional<DistributedLock> tryLock(String lockKey, Duration timeout) {
        String fullLockKey = LOCK_PREFIX + lockKey;
        String lockValue = UUID.randomUUID().toString();
        
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(fullLockKey, lockValue, timeout);
        
        if (Boolean.TRUE.equals(acquired)) {
            return Optional.of(new RedisDistributedLock(
                    redisTemplate, fullLockKey, lockValue, timeout.getSeconds()
            ));
        }
        
        return Optional.empty();
    }
    
    @Override
    public void publishInvalidation(String pattern) {
        redisTemplate.convertAndSend(INVALIDATION_CHANNEL, pattern);
    }
    
    /**
     * Redis implementation of DistributedLock.
     */
    private static class RedisDistributedLock implements DistributedLock {
        
        private final StringRedisTemplate redisTemplate;
        private final String lockKey;
        private final String lockValue;
        private long remainingSeconds;
        private boolean locked;
        
        RedisDistributedLock(StringRedisTemplate redisTemplate, String lockKey, 
                            String lockValue, long timeoutSeconds) {
            this.redisTemplate = redisTemplate;
            this.lockKey = lockKey;
            this.lockValue = lockValue;
            this.remainingSeconds = timeoutSeconds;
            this.locked = true;
        }
        
        @Override
        public String getLockKey() {
            return lockKey;
        }
        
        @Override
        public boolean isLocked() {
            if (!locked) return false;
            
            // Verify lock is still held by us
            String currentValue = redisTemplate.opsForValue().get(lockKey);
            return lockValue.equals(currentValue);
        }
        
        @Override
        public void unlock() {
            if (!locked) return;
            
            // Only delete if we still own the lock
            String currentValue = redisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(currentValue)) {
                redisTemplate.delete(lockKey);
            }
            locked = false;
        }
        
        @Override
        public boolean extend(long additionalSeconds) {
            if (!isLocked()) return false;
            
            Boolean result = redisTemplate.expire(lockKey, Duration.ofSeconds(additionalSeconds));
            if (Boolean.TRUE.equals(result)) {
                remainingSeconds = additionalSeconds;
                return true;
            }
            return false;
        }
        
        @Override
        public long getRemainingTime() {
            if (!locked) return 0;
            Long ttl = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
            return ttl != null && ttl > 0 ? ttl : 0;
        }
    }
}
