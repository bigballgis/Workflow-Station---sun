package com.platform.gateway.filter;

import com.platform.gateway.config.GatewayProperties;
import com.platform.security.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Authentication filter for validating JWT tokens.
 * Validates: Requirements 3.1, 3.2, 5.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GatewayFilter, Ordered {
    
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLES_HEADER = "X-User-Roles";
    
    private final JwtTokenService jwtTokenService;
    private final GatewayProperties gatewayProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        
        // Check if path is public
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }
        
        // Extract token from Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return unauthorized(exchange);
        }
        
        String token = authHeader.substring(BEARER_PREFIX.length());
        
        // Validate token
        if (!jwtTokenService.validateToken(token)) {
            log.warn("Invalid JWT token for path: {}", path);
            return unauthorized(exchange);
        }
        
        // Check if token is expired
        if (jwtTokenService.isTokenExpired(token)) {
            log.warn("Expired JWT token for path: {}", path);
            return unauthorized(exchange);
        }
        
        // Extract user info and add to headers for downstream services
        String userId = jwtTokenService.extractUserId(token);
        var userPrincipal = jwtTokenService.extractUserPrincipal(token);
        
        ServerHttpRequest modifiedRequest = request.mutate()
                .header(USER_ID_HEADER, userId)
                .header(USER_ROLES_HEADER, String.join(",", userPrincipal.getRoles()))
                .build();
        
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }
    
    @Override
    public int getOrder() {
        return -100; // High priority
    }
    
    private boolean isPublicPath(String path) {
        return gatewayProperties.getPublicPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
    
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
