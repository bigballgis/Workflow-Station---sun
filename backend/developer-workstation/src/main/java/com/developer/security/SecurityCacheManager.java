package com.developer.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-based caching for security permission and role checks.
 * Provides performance optimization while maintaining security.
 * 
 * Requirements: 1.5, 2.5, 6.1, 6.2, 6.5
 */
@Component
@Slf4j
public class SecurityCacheManager {
    
    private final Map<String, UserSecurityCache> sessionCaches = new ConcurrentHashMap<>();
    private final long sessionTimeoutMs;
    private final int maxCacheSize;
    
    public SecurityCacheManager(
            @Value("${security.cache.session-timeout-minutes:30}") int sessionTimeoutMinutes,
            @Value("${security.cache.max-size:1000}") int maxCacheSize) {
        this.sessionTimeoutMs = sessionTimeoutMinutes * 60 * 1000L;
        this.maxCacheSize = maxCacheSize;
        log.info("SecurityCacheManager initialized with session timeout: {} minutes, max size: {}", 
                sessionTimeoutMinutes, maxCacheSize);
    }
    
    /**
     * Get cached permission result for a user.
     * 
     * @param userId the user ID
     * @param permission the permission to check
     * @return cached result if available and not expired, empty otherwise
     */
    public Optional<Boolean> getCachedPermission(String userId, String permission) {
        UserSecurityCache cache = sessionCaches.get(userId);
        if (cache == null || cache.isExpired()) {
            if (cache != null) {
                sessionCaches.remove(userId);
                log.debug("Removed expired cache for user: {}", userId);
            }
            return Optional.empty();
        }
        
        Boolean result = cache.getPermission(permission);
        if (result != null) {
            log.debug("Cache hit for permission check: user={}, permission={}, result={}", 
                    userId, permission, result);
        }
        return Optional.ofNullable(result);
    }
    
    /**
     * Cache permission result for a user.
     * 
     * @param userId the user ID
     * @param permission the permission to cache
     * @param hasPermission the permission result
     */
    public void cachePermission(String userId, String permission, boolean hasPermission) {
        // Check cache size limit
        if (sessionCaches.size() >= maxCacheSize) {
            evictOldestCache();
        }
        
        UserSecurityCache cache = sessionCaches.computeIfAbsent(userId, 
                k -> new UserSecurityCache(sessionTimeoutMs));
        cache.setPermission(permission, hasPermission);
        
        log.debug("Cached permission result: user={}, permission={}, result={}", 
                userId, permission, hasPermission);
    }
    
    /**
     * Get cached role result for a user.
     * 
     * @param userId the user ID
     * @param role the role to check
     * @return cached result if available and not expired, empty otherwise
     */
    public Optional<Boolean> getCachedRole(String userId, String role) {
        UserSecurityCache cache = sessionCaches.get(userId);
        if (cache == null || cache.isExpired()) {
            if (cache != null) {
                sessionCaches.remove(userId);
                log.debug("Removed expired cache for user: {}", userId);
            }
            return Optional.empty();
        }
        
        Boolean result = cache.getRole(role);
        if (result != null) {
            log.debug("Cache hit for role check: user={}, role={}, result={}", 
                    userId, role, result);
        }
        return Optional.ofNullable(result);
    }
    
    /**
     * Cache role result for a user.
     * 
     * @param userId the user ID
     * @param role the role to cache
     * @param hasRole the role result
     */
    public void cacheRole(String userId, String role, boolean hasRole) {
        // Check cache size limit
        if (sessionCaches.size() >= maxCacheSize) {
            evictOldestCache();
        }
        
        UserSecurityCache cache = sessionCaches.computeIfAbsent(userId, 
                k -> new UserSecurityCache(sessionTimeoutMs));
        cache.setRole(role, hasRole);
        
        log.debug("Cached role result: user={}, role={}, result={}", 
                userId, role, hasRole);
    }
    
    /**
     * Invalidate all cached data for a user.
     * Should be called when user permissions change.
     * 
     * @param userId the user ID to invalidate cache for
     */
    public void invalidateUserCache(String userId) {
        UserSecurityCache removed = sessionCaches.remove(userId);
        if (removed != null) {
            log.info("Invalidated security cache for user: {}", userId);
        }
    }
    
    /**
     * Get current cache statistics.
     * 
     * @return map containing cache statistics
     */
    public Map<String, Object> getCacheStats() {
        int totalUsers = sessionCaches.size();
        int expiredUsers = (int) sessionCaches.values().stream()
                .mapToLong(cache -> cache.isExpired() ? 1 : 0)
                .sum();
        
        return Map.of(
                "totalUsers", totalUsers,
                "activeUsers", totalUsers - expiredUsers,
                "expiredUsers", expiredUsers,
                "maxCacheSize", maxCacheSize,
                "sessionTimeoutMs", sessionTimeoutMs
        );
    }
    
    /**
     * Clean up expired cache entries.
     * Should be called periodically to prevent memory leaks.
     */
    public void cleanupExpiredEntries() {
        int removedCount = 0;
        var iterator = sessionCaches.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            log.debug("Cleaned up {} expired cache entries", removedCount);
        }
    }
    
    /**
     * Evict the oldest cache entry when cache size limit is reached.
     */
    private void evictOldestCache() {
        String oldestUserId = null;
        long oldestTime = Long.MAX_VALUE;
        
        for (Map.Entry<String, UserSecurityCache> entry : sessionCaches.entrySet()) {
            long creationTime = entry.getValue().getCreationTime();
            if (creationTime < oldestTime) {
                oldestTime = creationTime;
                oldestUserId = entry.getKey();
            }
        }
        
        if (oldestUserId != null) {
            sessionCaches.remove(oldestUserId);
            log.debug("Evicted oldest cache entry for user: {}", oldestUserId);
        }
    }
    
    /**
     * User security cache data structure.
     */
    public static class UserSecurityCache {
        private final Map<String, Boolean> permissions = new ConcurrentHashMap<>();
        private final Map<String, Boolean> roles = new ConcurrentHashMap<>();
        private final long creationTime = System.currentTimeMillis();
        private final long sessionTimeout;
        
        public UserSecurityCache(long sessionTimeout) {
            this.sessionTimeout = sessionTimeout;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - creationTime > sessionTimeout;
        }
        
        public long getCreationTime() {
            return creationTime;
        }
        
        public Boolean getPermission(String permission) {
            return permissions.get(permission);
        }
        
        public void setPermission(String permission, boolean hasPermission) {
            permissions.put(permission, hasPermission);
        }
        
        public Boolean getRole(String role) {
            return roles.get(role);
        }
        
        public void setRole(String role, boolean hasRole) {
            roles.put(role, hasRole);
        }
    }
}