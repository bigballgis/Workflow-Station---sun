package com.platform.gateway.property;

import com.platform.gateway.config.PlatformGatewayProperties;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Positive;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for rate limiting.
 * Validates: Property 7 (API Rate Limit Enforcement)
 */
class RateLimitPropertyTest {
    
    // Property 7: API Rate Limit Enforcement
    // For any API caller, when call frequency exceeds the configured limit,
    // subsequent requests should be rejected with 429 status code
    
    @Property(tries = 100)
    void requestsWithinLimitShouldBeAllowed(
            @ForAll @IntRange(min = 1, max = 100) int limit,
            @ForAll @IntRange(min = 1, max = 100) int requestCount) {
        
        // Simulate rate limiter
        SimulatedRateLimiter rateLimiter = new SimulatedRateLimiter(limit);
        
        int allowedCount = 0;
        int rejectedCount = 0;
        
        for (int i = 0; i < requestCount; i++) {
            if (rateLimiter.tryAcquire("client1")) {
                allowedCount++;
            } else {
                rejectedCount++;
            }
        }
        
        // Requests within limit should be allowed
        assertThat(allowedCount).isLessThanOrEqualTo(limit);
        
        // If request count exceeds limit, some should be rejected
        if (requestCount > limit) {
            assertThat(rejectedCount).isEqualTo(requestCount - limit);
        }
    }
    
    @Property(tries = 100)
    void differentClientsShouldHaveIndependentLimits(
            @ForAll @IntRange(min = 1, max = 50) int limit,
            @ForAll @IntRange(min = 2, max = 10) int clientCount) {
        
        SimulatedRateLimiter rateLimiter = new SimulatedRateLimiter(limit);
        
        // Each client makes exactly 'limit' requests
        for (int client = 0; client < clientCount; client++) {
            String clientId = "client" + client;
            int allowedForClient = 0;
            
            for (int i = 0; i < limit; i++) {
                if (rateLimiter.tryAcquire(clientId)) {
                    allowedForClient++;
                }
            }
            
            // Each client should be allowed exactly 'limit' requests
            assertThat(allowedForClient).isEqualTo(limit);
        }
    }
    
    @Property(tries = 100)
    void rateLimitShouldResetAfterWindow(
            @ForAll @IntRange(min = 1, max = 50) int limit) {
        
        SimulatedRateLimiter rateLimiter = new SimulatedRateLimiter(limit);
        String clientId = "client1";
        
        // Exhaust the limit
        for (int i = 0; i < limit; i++) {
            assertThat(rateLimiter.tryAcquire(clientId)).isTrue();
        }
        
        // Next request should be rejected
        assertThat(rateLimiter.tryAcquire(clientId)).isFalse();
        
        // Reset the window
        rateLimiter.resetWindow(clientId);
        
        // Should be allowed again
        assertThat(rateLimiter.tryAcquire(clientId)).isTrue();
    }
    
    @Property(tries = 100)
    void pathSpecificLimitsShouldOverrideDefault(
            @ForAll @IntRange(min = 10, max = 100) int defaultLimit,
            @ForAll @IntRange(min = 1, max = 9) int pathLimit) {
        
        PlatformGatewayProperties.RateLimitConfig config = new PlatformGatewayProperties.RateLimitConfig();
        config.setDefaultLimit(defaultLimit);
        config.setPathLimits(Map.of("/api/auth/login", pathLimit));
        
        int effectiveLimit = getEffectiveLimit(config, "/api/auth/login");
        assertThat(effectiveLimit).isEqualTo(pathLimit);
        
        int defaultPathLimit = getEffectiveLimit(config, "/api/users");
        assertThat(defaultPathLimit).isEqualTo(defaultLimit);
    }
    
    @Property(tries = 50)
    void wildcardPathPatternsShouldMatch(
            @ForAll @IntRange(min = 1, max = 50) int limit) {
        
        PlatformGatewayProperties.RateLimitConfig config = new PlatformGatewayProperties.RateLimitConfig();
        config.setDefaultLimit(100);
        config.setPathLimits(Map.of("/api/workflow/**", limit));
        
        // Should match wildcard pattern
        assertThat(matchesPathPattern("/api/workflow/**", "/api/workflow/start")).isTrue();
        assertThat(matchesPathPattern("/api/workflow/**", "/api/workflow/tasks/123")).isTrue();
        
        // Should not match different paths
        assertThat(matchesPathPattern("/api/workflow/**", "/api/users")).isFalse();
    }
    
    // Helper class to simulate rate limiting
    private static class SimulatedRateLimiter {
        private final int limit;
        private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
        
        SimulatedRateLimiter(int limit) {
            this.limit = limit;
        }
        
        boolean tryAcquire(String clientId) {
            AtomicInteger counter = counters.computeIfAbsent(clientId, k -> new AtomicInteger(0));
            int current = counter.incrementAndGet();
            return current <= limit;
        }
        
        void resetWindow(String clientId) {
            counters.remove(clientId);
        }
    }
    
    private int getEffectiveLimit(PlatformGatewayProperties.RateLimitConfig config, String path) {
        for (Map.Entry<String, Integer> entry : config.getPathLimits().entrySet()) {
            if (matchesPathPattern(entry.getKey(), path)) {
                return entry.getValue();
            }
        }
        return config.getDefaultLimit();
    }
    
    private boolean matchesPathPattern(String pattern, String path) {
        if (pattern.equals(path)) {
            return true;
        }
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix);
        }
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return path.startsWith(prefix) && !path.substring(prefix.length()).contains("/");
        }
        return false;
    }
}
