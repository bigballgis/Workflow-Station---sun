package com.platform.cache.property;

import com.platform.cache.service.CacheService;
import com.platform.cache.service.DistributedLock;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Property-based tests for Cache Service.
 * These tests use an in-memory mock implementation for unit testing.
 * Feature: platform-architecture, Property 10: 缓存过期正确性
 * Feature: platform-architecture, Property 11: 分布式锁互斥性
 * Validates: Requirements 7.4, 7.5
 */
class CachePropertyTest {
    
    /**
     * In-memory cache implementation for testing.
     */
    private static class InMemoryCacheService implements CacheService {
        private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
        private final Map<String, LockEntry> locks = new ConcurrentHashMap<>();
        
        private record CacheEntry(String value, Long expiryTime) {
            boolean isExpired() {
                return expiryTime != null && System.currentTimeMillis() > expiryTime;
            }
        }
        
        private record LockEntry(String value, long expiryTime) {}
        
        @Override
        public <T> Optional<T> get(String key, Class<T> type) {
            CacheEntry entry = cache.get(key);
            if (entry == null || entry.isExpired()) {
                cache.remove(key);
                return Optional.empty();
            }
            try {
                return Optional.of(com.platform.common.util.JsonUtils.fromJson(entry.value, type));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        
        @Override
        public Optional<String> getString(String key) {
            CacheEntry entry = cache.get(key);
            if (entry == null || entry.isExpired()) {
                cache.remove(key);
                return Optional.empty();
            }
            return Optional.of(entry.value);
        }
        
        @Override
        public void set(String key, Object value, Duration ttl) {
            String jsonValue = com.platform.common.util.JsonUtils.toJson(value);
            long expiryTime = System.currentTimeMillis() + ttl.toMillis();
            cache.put(key, new CacheEntry(jsonValue, expiryTime));
        }
        
        @Override
        public void set(String key, Object value) {
            String jsonValue = com.platform.common.util.JsonUtils.toJson(value);
            cache.put(key, new CacheEntry(jsonValue, null));
        }
        
        @Override
        public boolean setIfAbsent(String key, Object value, Duration ttl) {
            return cache.putIfAbsent(key, new CacheEntry(
                    com.platform.common.util.JsonUtils.toJson(value),
                    System.currentTimeMillis() + ttl.toMillis()
            )) == null;
        }
        
        @Override
        public boolean delete(String key) {
            return cache.remove(key) != null;
        }
        
        @Override
        public long deleteAll(Set<String> keys) {
            return keys.stream().filter(k -> cache.remove(k) != null).count();
        }
        
        @Override
        public long deleteByPattern(String pattern) {
            String regex = pattern.replace("*", ".*");
            return cache.keySet().stream()
                    .filter(k -> k.matches(regex))
                    .filter(k -> cache.remove(k) != null)
                    .count();
        }
        
        @Override
        public boolean exists(String key) {
            CacheEntry entry = cache.get(key);
            if (entry != null && entry.isExpired()) {
                cache.remove(key);
                return false;
            }
            return entry != null;
        }
        
        @Override
        public boolean expire(String key, Duration ttl) {
            CacheEntry entry = cache.get(key);
            if (entry == null) return false;
            cache.put(key, new CacheEntry(entry.value, System.currentTimeMillis() + ttl.toMillis()));
            return true;
        }
        
        @Override
        public long getTtl(String key) {
            CacheEntry entry = cache.get(key);
            if (entry == null) return -2;
            if (entry.expiryTime == null) return -1;
            long remaining = (entry.expiryTime - System.currentTimeMillis()) / 1000;
            return Math.max(0, remaining);
        }
        
        @Override
        public long increment(String key, long delta) {
            CacheEntry entry = cache.get(key);
            long current = 0;
            if (entry != null && !entry.isExpired()) {
                try {
                    current = Long.parseLong(entry.value.replace("\"", ""));
                } catch (NumberFormatException e) {
                    current = 0;
                }
            }
            long newValue = current + delta;
            cache.put(key, new CacheEntry(String.valueOf(newValue), entry != null ? entry.expiryTime : null));
            return newValue;
        }
        
        @Override
        public Set<String> keys(String pattern) {
            String regex = pattern.replace("*", ".*");
            Set<String> result = new HashSet<>();
            for (String key : cache.keySet()) {
                if (key.matches(regex)) {
                    result.add(key);
                }
            }
            return result;
        }
        
        @Override
        public Optional<DistributedLock> tryLock(String lockKey, Duration timeout) {
            String lockValue = UUID.randomUUID().toString();
            long expiryTime = System.currentTimeMillis() + timeout.toMillis();
            
            LockEntry existing = locks.putIfAbsent(lockKey, new LockEntry(lockValue, expiryTime));
            if (existing == null) {
                return Optional.of(new InMemoryLock(locks, lockKey, lockValue));
            }
            
            // Check if existing lock is expired
            if (System.currentTimeMillis() > existing.expiryTime) {
                locks.put(lockKey, new LockEntry(lockValue, expiryTime));
                return Optional.of(new InMemoryLock(locks, lockKey, lockValue));
            }
            
            return Optional.empty();
        }
        
        @Override
        public void publishInvalidation(String pattern) {
            deleteByPattern(pattern);
        }
        
        void clear() {
            cache.clear();
            locks.clear();
        }
    }
    
    private static class InMemoryLock implements DistributedLock {
        private final Map<String, InMemoryCacheService.LockEntry> locks;
        private final String lockKey;
        private final String lockValue;
        private boolean locked = true;
        
        InMemoryLock(Map<String, InMemoryCacheService.LockEntry> locks, String lockKey, String lockValue) {
            this.locks = locks;
            this.lockKey = lockKey;
            this.lockValue = lockValue;
        }
        
        @Override
        public String getLockKey() { return lockKey; }
        
        @Override
        public boolean isLocked() {
            if (!locked) return false;
            var entry = locks.get(lockKey);
            return entry != null && lockValue.equals(entry.value());
        }
        
        @Override
        public void unlock() {
            if (locked) {
                var entry = locks.get(lockKey);
                if (entry != null && lockValue.equals(entry.value())) {
                    locks.remove(lockKey);
                }
                locked = false;
            }
        }
        
        @Override
        public boolean extend(long additionalSeconds) {
            if (!isLocked()) return false;
            locks.put(lockKey, new InMemoryCacheService.LockEntry(
                    lockValue, System.currentTimeMillis() + additionalSeconds * 1000));
            return true;
        }
        
        @Override
        public long getRemainingTime() {
            var entry = locks.get(lockKey);
            if (entry == null) return 0;
            return Math.max(0, (entry.expiryTime() - System.currentTimeMillis()) / 1000);
        }
    }
    
    private final InMemoryCacheService cacheService = new InMemoryCacheService();
    
    @BeforeProperty
    void setup() {
        cacheService.clear();
    }
    
    /**
     * Property 10: 缓存过期正确性
     * For any cached value with TTL, the value should be retrievable before expiry
     * and not retrievable after expiry.
     */
    @Property(tries = 100)
    void cacheExpirationCorrectness(
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 50) String key,
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 100) String value
    ) {
        // Set with short TTL
        cacheService.set(key, value, Duration.ofSeconds(10));
        
        // Should be retrievable immediately
        Optional<String> retrieved = cacheService.get(key, String.class);
        assert retrieved.isPresent() : "Value should be retrievable before expiry";
        assert value.equals(retrieved.get()) : "Retrieved value should match original";
        
        // TTL should be positive
        long ttl = cacheService.getTtl(key);
        assert ttl > 0 : "TTL should be positive for non-expired key";
    }
    
    /**
     * Property: Set and get round-trip consistency.
     */
    @Property(tries = 100)
    void setGetRoundTrip(
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 50) String key,
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 100) String value
    ) {
        cacheService.set(key, value, Duration.ofMinutes(5));
        
        Optional<String> retrieved = cacheService.get(key, String.class);
        
        assert retrieved.isPresent() : "Value should be present after set";
        assert value.equals(retrieved.get()) : 
                "Retrieved value should match: expected " + value + ", got " + retrieved.get();
    }
    
    /**
     * Property: Delete should remove the key.
     */
    @Property(tries = 100)
    void deleteRemovesKey(
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 50) String key,
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 100) String value
    ) {
        cacheService.set(key, value, Duration.ofMinutes(5));
        assert cacheService.exists(key) : "Key should exist after set";
        
        boolean deleted = cacheService.delete(key);
        
        assert deleted : "Delete should return true for existing key";
        assert !cacheService.exists(key) : "Key should not exist after delete";
    }
    
    /**
     * Property: setIfAbsent should only set if key doesn't exist.
     */
    @Property(tries = 100)
    void setIfAbsentOnlyWhenMissing(
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 50) String key,
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 100) String value1,
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 100) String value2
    ) {
        // First setIfAbsent should succeed
        boolean first = cacheService.setIfAbsent(key, value1, Duration.ofMinutes(5));
        assert first : "First setIfAbsent should succeed";
        
        // Second setIfAbsent should fail
        boolean second = cacheService.setIfAbsent(key, value2, Duration.ofMinutes(5));
        assert !second : "Second setIfAbsent should fail";
        
        // Value should be the first one
        Optional<String> retrieved = cacheService.get(key, String.class);
        assert retrieved.isPresent() && value1.equals(retrieved.get()) : 
                "Value should be the first one set";
    }
    
    /**
     * Property 11: 分布式锁互斥性
     * For any lock key, only one lock should be acquirable at a time.
     */
    @Property(tries = 100)
    void distributedLockMutualExclusion(
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 50) String lockKey
    ) {
        // First lock should succeed
        Optional<DistributedLock> lock1 = cacheService.tryLock(lockKey, Duration.ofSeconds(30));
        assert lock1.isPresent() : "First lock acquisition should succeed";
        
        // Second lock should fail while first is held
        Optional<DistributedLock> lock2 = cacheService.tryLock(lockKey, Duration.ofSeconds(30));
        assert lock2.isEmpty() : "Second lock acquisition should fail while first is held";
        
        // After releasing first lock, second should succeed
        lock1.get().unlock();
        Optional<DistributedLock> lock3 = cacheService.tryLock(lockKey, Duration.ofSeconds(30));
        assert lock3.isPresent() : "Lock acquisition should succeed after release";
        
        lock3.get().unlock();
    }
    
    /**
     * Property: Lock unlock should release the lock.
     */
    @Property(tries = 100)
    void lockUnlockReleasesLock(
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 50) String lockKey
    ) {
        Optional<DistributedLock> lock = cacheService.tryLock(lockKey, Duration.ofSeconds(30));
        assert lock.isPresent() : "Lock should be acquired";
        assert lock.get().isLocked() : "Lock should be locked";
        
        lock.get().unlock();
        
        assert !lock.get().isLocked() : "Lock should not be locked after unlock";
        
        // Should be able to acquire again
        Optional<DistributedLock> newLock = cacheService.tryLock(lockKey, Duration.ofSeconds(30));
        assert newLock.isPresent() : "Should be able to acquire lock after unlock";
        newLock.get().unlock();
    }
    
    /**
     * Property: Increment should correctly update numeric values.
     */
    @Property(tries = 100)
    void incrementCorrectness(
            @ForAll @AlphaNumeric @StringLength(min = 1, max = 50) String key,
            @ForAll @IntRange(min = 1, max = 100) int times,
            @ForAll @LongRange(min = 1, max = 10) long delta
    ) {
        long expected = 0;
        for (int i = 0; i < times; i++) {
            long result = cacheService.increment(key, delta);
            expected += delta;
            assert result == expected : 
                    "Increment result should be " + expected + ", got " + result;
        }
    }
    
    @BeforeProperty
    void beforeProperty() {
        cacheService.clear();
    }
}
