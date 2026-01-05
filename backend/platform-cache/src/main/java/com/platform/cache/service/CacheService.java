package com.platform.cache.service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Cache service interface for distributed caching operations.
 * Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5
 */
public interface CacheService {
    
    /**
     * Get a cached value by key.
     * 
     * @param key Cache key
     * @param type Value type class
     * @return Optional containing the value if present, empty otherwise
     */
    <T> Optional<T> get(String key, Class<T> type);
    
    /**
     * Get a cached value as string.
     * 
     * @param key Cache key
     * @return Optional containing the string value if present, empty otherwise
     */
    Optional<String> getString(String key);
    
    /**
     * Set a cached value with TTL.
     * 
     * @param key Cache key
     * @param value Value to cache
     * @param ttl Time to live
     */
    void set(String key, Object value, Duration ttl);
    
    /**
     * Set a cached value without TTL (permanent until deleted).
     * 
     * @param key Cache key
     * @param value Value to cache
     */
    void set(String key, Object value);
    
    /**
     * Set a cached value only if the key doesn't exist.
     * 
     * @param key Cache key
     * @param value Value to cache
     * @param ttl Time to live
     * @return true if set successfully, false if key already exists
     */
    boolean setIfAbsent(String key, Object value, Duration ttl);
    
    /**
     * Delete a cached value.
     * 
     * @param key Cache key
     * @return true if deleted, false if key didn't exist
     */
    boolean delete(String key);
    
    /**
     * Delete multiple cached values.
     * 
     * @param keys Cache keys to delete
     * @return Number of keys deleted
     */
    long deleteAll(Set<String> keys);
    
    /**
     * Delete all keys matching a pattern.
     * 
     * @param pattern Key pattern (e.g., "user:*")
     * @return Number of keys deleted
     */
    long deleteByPattern(String pattern);
    
    /**
     * Check if a key exists.
     * 
     * @param key Cache key
     * @return true if key exists, false otherwise
     */
    boolean exists(String key);
    
    /**
     * Set expiration time for an existing key.
     * 
     * @param key Cache key
     * @param ttl Time to live
     * @return true if expiration was set, false if key doesn't exist
     */
    boolean expire(String key, Duration ttl);
    
    /**
     * Get remaining TTL for a key.
     * 
     * @param key Cache key
     * @return Remaining TTL in seconds, -1 if no TTL, -2 if key doesn't exist
     */
    long getTtl(String key);
    
    /**
     * Increment a numeric value.
     * 
     * @param key Cache key
     * @param delta Amount to increment
     * @return New value after increment
     */
    long increment(String key, long delta);
    
    /**
     * Get all keys matching a pattern.
     * 
     * @param pattern Key pattern (e.g., "user:*")
     * @return Set of matching keys
     */
    Set<String> keys(String pattern);
    
    /**
     * Acquire a distributed lock.
     * 
     * @param lockKey Lock key
     * @param timeout Lock timeout
     * @return Optional containing the lock if acquired, empty otherwise
     */
    Optional<DistributedLock> tryLock(String lockKey, Duration timeout);
    
    /**
     * Publish a cache invalidation message.
     * 
     * @param pattern Key pattern to invalidate
     */
    void publishInvalidation(String pattern);
}
