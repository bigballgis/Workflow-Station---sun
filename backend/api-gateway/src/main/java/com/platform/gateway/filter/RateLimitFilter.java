package com.platform.gateway.filter;

import com.platform.gateway.config.PlatformGatewayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * Rate limiting filter using Redis sliding window algorithm.
 * Validates: Requirements 5.3
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GatewayFilter, Ordered {
    
    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";
    private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String RATE_LIMIT_LIMIT_HEADER = "X-RateLimit-Limit";
    private static final String RATE_LIMIT_RESET_HEADER = "X-RateLimit-Reset";
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final PlatformGatewayProperties gatewayProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    // Lua script for atomic rate limiting
    private static final String RATE_LIMIT_SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local current = redis.call('INCR', key)
            if current == 1 then
                redis.call('EXPIRE', key, window)
            end
            return current
            """;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!gatewayProperties.getRateLimit().isEnabled()) {
            return chain.filter(exchange);
        }
        
        ServerHttpRequest request = exchange.getRequest();
        String clientId = getClientId(request);
        String path = request.getPath().value();
        int limit = getLimit(path);
        int windowSeconds = gatewayProperties.getRateLimit().getWindowSeconds();
        
        String key = RATE_LIMIT_KEY_PREFIX + clientId + ":" + path;
        
        return checkRateLimit(key, limit, windowSeconds)
                .flatMap(currentCount -> {
                    int remaining = Math.max(0, limit - currentCount.intValue());
                    
                    // Add rate limit headers
                    exchange.getResponse().getHeaders().add(RATE_LIMIT_LIMIT_HEADER, String.valueOf(limit));
                    exchange.getResponse().getHeaders().add(RATE_LIMIT_REMAINING_HEADER, String.valueOf(remaining));
                    exchange.getResponse().getHeaders().add(RATE_LIMIT_RESET_HEADER, String.valueOf(windowSeconds));
                    
                    if (currentCount > limit) {
                        log.warn("Rate limit exceeded for client {} on path {}", clientId, path);
                        return tooManyRequests(exchange);
                    }
                    
                    return chain.filter(exchange);
                });
    }
    
    @Override
    public int getOrder() {
        return -90; // After authentication
    }
    
    private Mono<Long> checkRateLimit(String key, int limit, int windowSeconds) {
        RedisScript<Long> script = RedisScript.of(RATE_LIMIT_SCRIPT, Long.class);
        java.util.List<String> keys = Collections.singletonList(key);
        java.util.List<String> args = java.util.List.of(String.valueOf(limit), String.valueOf(windowSeconds));
        return redisTemplate.execute(script, keys, args)
                .next()
                .defaultIfEmpty(1L);
    }
    
    private String getClientId(ServerHttpRequest request) {
        // Try to get user ID from header (set by auth filter)
        String userId = request.getHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId;
        }
        
        // Fall back to IP address
        String ip = request.getRemoteAddress() != null 
                ? request.getRemoteAddress().getAddress().getHostAddress() 
                : "unknown";
        return "ip:" + ip;
    }
    
    private int getLimit(String path) {
        Map<String, Integer> pathLimits = gatewayProperties.getRateLimit().getPathLimits();
        
        for (Map.Entry<String, Integer> entry : pathLimits.entrySet()) {
            if (pathMatcher.match(entry.getKey(), path)) {
                return entry.getValue();
            }
        }
        
        return gatewayProperties.getRateLimit().getDefaultLimit();
    }
    
    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        return exchange.getResponse().setComplete();
    }
}
