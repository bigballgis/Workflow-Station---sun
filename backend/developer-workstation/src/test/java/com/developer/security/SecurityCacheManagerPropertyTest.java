package com.developer.security;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.junit.jupiter.api.DisplayName;
import org.assertj.core.api.Assertions;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Property-based tests for SecurityCacheManager.
 * Validates universal properties of caching behavior.
 * 
 * Feature: security-permission-system, Property 5: Session-Based Caching
 * Feature: security-permission-system, Property 10: Cache Invalidation
 * Feature: security-permission-system, Property 11: Cache Size Management
 * Validates: Requirements 1.5, 2.5, 6.1, 6.2, 6.5
 */
@Label("Feature: security-permission-system, Property 5,10,11: Caching Behavior")
public class SecurityCacheManagerPropertyTest {
    
    private SecurityCacheManager cacheManager;
    
    @BeforeProperty
    void setUp() {
        // Use short timeout for testing (1 second)
        cacheManager = new SecurityCacheManager(1, 10); // 1 minute timeout, max 10 entries
    }
    
    /**
     * Property 5: Session-Based Caching
     * For any user session, permission and role results should be cached and 
     * subsequent identical checks should return cached results without database queries.
     */
    @Property(tries = 100)
    @DisplayName("Permission results should be cached for subsequent identical checks")
    void permissionCachingConsistency(
            @ForAll("validUserIds") String userId,
            @ForAll("validPermissions") String permission,
            @ForAll boolean hasPermission) {
        
        // First call should return empty (no cache)
        Optional<Boolean> initialResult = cacheManager.getCachedPermission(userId, permission);
        Assertions.assertThat(initialResult).isEmpty();
        
        // Cache the result
        cacheManager.cachePermission(userId, permission, hasPermission);
        
        // Second call should return cached result
        Optional<Boolean> cachedResult = cacheManager.getCachedPermission(userId, permission);
        Assertions.assertThat(cachedResult).isPresent();
        Assertions.assertThat(cachedResult.get()).isEqualTo(hasPermission);
        
        // Multiple subsequent calls should return the same cached result
        for (int i = 0; i < 5; i++) {
            Optional<Boolean> subsequentResult = cacheManager.getCachedPermission(userId, permission);
            Assertions.assertThat(subsequentResult).isPresent();
            Assertions.assertThat(subsequentResult.get()).isEqualTo(hasPermission);
        }
    }
    
    /**
     * Property 5b: Role Caching Consistency
     * For any user session, role results should be cached consistently.
     */
    @Property(tries = 100)
    @DisplayName("Role results should be cached for subsequent identical checks")
    void roleCachingConsistency(
            @ForAll("validUserIds") String userId,
            @ForAll("validRoles") String role,
            @ForAll boolean hasRole) {
        
        // First call should return empty (no cache)
        Optional<Boolean> initialResult = cacheManager.getCachedRole(userId, role);
        Assertions.assertThat(initialResult).isEmpty();
        
        // Cache the result
        cacheManager.cacheRole(userId, role, hasRole);
        
        // Second call should return cached result
        Optional<Boolean> cachedResult = cacheManager.getCachedRole(userId, role);
        Assertions.assertThat(cachedResult).isPresent();
        Assertions.assertThat(cachedResult.get()).isEqualTo(hasRole);
    }
    
    /**
     * Property 10: Cache Invalidation
     * For any user whose permissions change, the system should invalidate 
     * the relevant cache entries.
     */
    @Property(tries = 100)
    @DisplayName("Cache invalidation should remove all cached data for a user")
    void cacheInvalidationCompleteness(
            @ForAll("validUserIds") String userId,
            @ForAll("validPermissions") String permission,
            @ForAll("validRoles") String role,
            @ForAll boolean hasPermission,
            @ForAll boolean hasRole) {
        
        // Cache some data for the user
        cacheManager.cachePermission(userId, permission, hasPermission);
        cacheManager.cacheRole(userId, role, hasRole);
        
        // Verify data is cached
        Assertions.assertThat(cacheManager.getCachedPermission(userId, permission)).isPresent();
        Assertions.assertThat(cacheManager.getCachedRole(userId, role)).isPresent();
        
        // Invalidate user cache
        cacheManager.invalidateUserCache(userId);
        
        // Verify all data is removed
        Assertions.assertThat(cacheManager.getCachedPermission(userId, permission)).isEmpty();
        Assertions.assertThat(cacheManager.getCachedRole(userId, role)).isEmpty();
    }
    
    /**
     * Property 10b: Selective Cache Invalidation
     * Cache invalidation should only affect the specified user, not others.
     */
    @Property(tries = 100)
    @DisplayName("Cache invalidation should only affect the specified user")
    void selectiveCacheInvalidation(
            @ForAll("validUserIds") String userId1,
            @ForAll("validUserIds") String userId2,
            @ForAll("validPermissions") String permission,
            @ForAll boolean hasPermission1,
            @ForAll boolean hasPermission2) {
        
        Assume.that(!userId1.equals(userId2)); // Ensure different users
        
        // Cache data for both users
        cacheManager.cachePermission(userId1, permission, hasPermission1);
        cacheManager.cachePermission(userId2, permission, hasPermission2);
        
        // Verify both are cached
        Assertions.assertThat(cacheManager.getCachedPermission(userId1, permission)).isPresent();
        Assertions.assertThat(cacheManager.getCachedPermission(userId2, permission)).isPresent();
        
        // Invalidate only user1's cache
        cacheManager.invalidateUserCache(userId1);
        
        // Verify only user1's cache is cleared
        Assertions.assertThat(cacheManager.getCachedPermission(userId1, permission)).isEmpty();
        Assertions.assertThat(cacheManager.getCachedPermission(userId2, permission)).isPresent();
        Assertions.assertThat(cacheManager.getCachedPermission(userId2, permission).get()).isEqualTo(hasPermission2);
    }
    
    /**
     * Property 11: Cache Size Management
     * For any caching operation, the system should limit cache size to prevent memory exhaustion.
     */
    @Property(tries = 50)
    @DisplayName("Cache should enforce size limits and evict oldest entries")
    void cacheSizeManagement(@ForAll("userIdList") java.util.List<String> userIds) {
        
        // Create a cache manager with small size limit for testing
        SecurityCacheManager smallCacheManager = new SecurityCacheManager(60, 3); // 3 entries max
        
        // Cache data for more users than the limit allows
        for (int i = 0; i < userIds.size() && i < 5; i++) {
            String userId = userIds.get(i);
            smallCacheManager.cachePermission(userId, "test:permission", true);
        }
        
        // Verify cache stats respect the size limit
        Map<String, Object> stats = smallCacheManager.getCacheStats();
        int totalUsers = (Integer) stats.get("totalUsers");
        int maxCacheSize = (Integer) stats.get("maxCacheSize");
        
        // Property: Total users should not exceed max cache size
        Assertions.assertThat(totalUsers).isLessThanOrEqualTo(maxCacheSize);
    }
    
    /**
     * Property 11b: Cache Cleanup Effectiveness
     * Cleanup should remove expired entries and maintain cache health.
     */
    @Property(tries = 30)
    @DisplayName("Cache cleanup should remove expired entries")
    void cacheCleanupEffectiveness(@ForAll("validUserIds") String userId) throws InterruptedException {
        
        // Create cache manager with very short timeout for testing
        SecurityCacheManager shortTimeoutCache = new SecurityCacheManager(1, 100); // 1 minute timeout
        
        // Cache some data
        shortTimeoutCache.cachePermission(userId, "test:permission", true);
        
        // Verify data is cached
        Assertions.assertThat(shortTimeoutCache.getCachedPermission(userId, "test:permission")).isPresent();
        
        // Wait for expiration (simulate by creating new cache with 0 timeout)
        SecurityCacheManager expiredCache = new SecurityCacheManager(0, 100); // 0 timeout = immediate expiration
        expiredCache.cachePermission(userId, "test:permission", true);
        
        // Small delay to ensure expiration
        Thread.sleep(10);
        
        // Cleanup expired entries
        expiredCache.cleanupExpiredEntries();
        
        // Verify expired data is removed
        Assertions.assertThat(expiredCache.getCachedPermission(userId, "test:permission")).isEmpty();
    }
    
    /**
     * Property 5c: Cache Isolation
     * Different permissions/roles for the same user should be cached independently.
     */
    @Property(tries = 100)
    @DisplayName("Different permissions and roles should be cached independently")
    void cacheIsolation(
            @ForAll("validUserIds") String userId,
            @ForAll("validPermissions") String permission1,
            @ForAll("validPermissions") String permission2,
            @ForAll("validRoles") String role1,
            @ForAll boolean hasPermission1,
            @ForAll boolean hasPermission2,
            @ForAll boolean hasRole1) {
        
        Assume.that(!permission1.equals(permission2)); // Ensure different permissions
        
        // Cache different permissions and roles for the same user
        cacheManager.cachePermission(userId, permission1, hasPermission1);
        cacheManager.cachePermission(userId, permission2, hasPermission2);
        cacheManager.cacheRole(userId, role1, hasRole1);
        
        // Verify all are cached independently
        Optional<Boolean> cachedPerm1 = cacheManager.getCachedPermission(userId, permission1);
        Optional<Boolean> cachedPerm2 = cacheManager.getCachedPermission(userId, permission2);
        Optional<Boolean> cachedRole1 = cacheManager.getCachedRole(userId, role1);
        
        Assertions.assertThat(cachedPerm1).isPresent();
        Assertions.assertThat(cachedPerm2).isPresent();
        Assertions.assertThat(cachedRole1).isPresent();
        
        Assertions.assertThat(cachedPerm1.get()).isEqualTo(hasPermission1);
        Assertions.assertThat(cachedPerm2.get()).isEqualTo(hasPermission2);
        Assertions.assertThat(cachedRole1.get()).isEqualTo(hasRole1);
    }
    
    @Provide
    Arbitrary<String> validUserIds() {
        return Arbitraries.of("user1", "user2", "user3", "admin", "developer", "manager");
    }
    
    @Provide
    Arbitrary<String> validPermissions() {
        return Arbitraries.of(
                "ADMIN:USER:READ", "ADMIN:USER:WRITE", "ADMIN:ROLE:READ", "ADMIN:ROLE:WRITE",
                "DEVELOPER:FUNCTION:READ", "DEVELOPER:FUNCTION:WRITE", "USER:TASK:READ"
        );
    }
    
    @Provide
    Arbitrary<String> validRoles() {
        return Arbitraries.of("ADMIN", "USER", "DEVELOPER", "MANAGER", "GUEST");
    }
    
    @Provide
    Arbitrary<java.util.List<String>> userIdList() {
        return Arbitraries.of("user1", "user2", "user3", "user4", "user5", "user6", "user7")
                .list().ofMinSize(3).ofMaxSize(7);
    }
}