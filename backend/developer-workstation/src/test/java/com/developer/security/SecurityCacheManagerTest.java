package com.developer.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SecurityCacheManager.
 * Tests basic caching functionality.
 */
@DisplayName("SecurityCacheManager Unit Tests")
public class SecurityCacheManagerTest {
    
    private SecurityCacheManager cacheManager;
    
    @BeforeEach
    void setUp() {
        cacheManager = new SecurityCacheManager(60, 100); // 60 minutes, 100 entries
    }
    
    @Test
    @DisplayName("Should cache and retrieve permission results")
    void shouldCacheAndRetrievePermissionResults() {
        String userId = "testUser";
        String permission = "TEST:PERMISSION";
        
        // Initially no cache
        Optional<Boolean> initialResult = cacheManager.getCachedPermission(userId, permission);
        assertThat(initialResult).isEmpty();
        
        // Cache a result
        cacheManager.cachePermission(userId, permission, true);
        
        // Should retrieve cached result
        Optional<Boolean> cachedResult = cacheManager.getCachedPermission(userId, permission);
        assertThat(cachedResult).isPresent();
        assertThat(cachedResult.get()).isTrue();
    }
    
    @Test
    @DisplayName("Should cache and retrieve role results")
    void shouldCacheAndRetrieveRoleResults() {
        String userId = "testUser";
        String role = "TEST_ROLE";
        
        // Initially no cache
        Optional<Boolean> initialResult = cacheManager.getCachedRole(userId, role);
        assertThat(initialResult).isEmpty();
        
        // Cache a result
        cacheManager.cacheRole(userId, role, false);
        
        // Should retrieve cached result
        Optional<Boolean> cachedResult = cacheManager.getCachedRole(userId, role);
        assertThat(cachedResult).isPresent();
        assertThat(cachedResult.get()).isFalse();
    }
    
    @Test
    @DisplayName("Should invalidate user cache")
    void shouldInvalidateUserCache() {
        String userId = "testUser";
        String permission = "TEST:PERMISSION";
        String role = "TEST_ROLE";
        
        // Cache some data
        cacheManager.cachePermission(userId, permission, true);
        cacheManager.cacheRole(userId, role, true);
        
        // Verify cached
        assertThat(cacheManager.getCachedPermission(userId, permission)).isPresent();
        assertThat(cacheManager.getCachedRole(userId, role)).isPresent();
        
        // Invalidate cache
        cacheManager.invalidateUserCache(userId);
        
        // Should be empty
        assertThat(cacheManager.getCachedPermission(userId, permission)).isEmpty();
        assertThat(cacheManager.getCachedRole(userId, role)).isEmpty();
    }
    
    @Test
    @DisplayName("Should provide cache statistics")
    void shouldProvideCacheStatistics() {
        var stats = cacheManager.getCacheStats();
        
        assertThat(stats).containsKeys("totalUsers", "activeUsers", "expiredUsers", "maxCacheSize", "sessionTimeoutMs");
        assertThat(stats.get("maxCacheSize")).isEqualTo(100);
    }
}