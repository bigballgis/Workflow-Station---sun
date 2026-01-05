package com.platform.gateway.config;

import com.platform.gateway.filter.AuthenticationFilter;
import com.platform.gateway.filter.RateLimitFilter;
import com.platform.gateway.filter.RequestLoggingFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway routing configuration.
 * Validates: Requirements 5.1, 5.2
 */
@Configuration
@RequiredArgsConstructor
public class GatewayConfig {
    
    private final AuthenticationFilter authenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RequestLoggingFilter requestLoggingFilter;
    
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Developer Workstation Service
                .route("developer-workstation", r -> r
                        .path("/api/developer/**")
                        .filters(f -> f
                                .stripPrefix(2)
                                .filter(requestLoggingFilter)
                                .filter(authenticationFilter)
                                .filter(rateLimitFilter))
                        .uri("lb://developer-workstation-service"))
                
                // Admin Center Service
                .route("admin-center", r -> r
                        .path("/api/admin/**")
                        .filters(f -> f
                                .stripPrefix(2)
                                .filter(requestLoggingFilter)
                                .filter(authenticationFilter)
                                .filter(rateLimitFilter))
                        .uri("lb://admin-center-service"))
                
                // User Portal Service
                .route("user-portal", r -> r
                        .path("/api/portal/**")
                        .filters(f -> f
                                .stripPrefix(2)
                                .filter(requestLoggingFilter)
                                .filter(authenticationFilter)
                                .filter(rateLimitFilter))
                        .uri("lb://user-portal-service"))
                
                // Workflow Engine Service
                .route("workflow-engine", r -> r
                        .path("/api/workflow/**")
                        .filters(f -> f
                                .stripPrefix(2)
                                .filter(requestLoggingFilter)
                                .filter(authenticationFilter)
                                .filter(rateLimitFilter))
                        .uri("lb://workflow-engine-service"))
                
                // Auth Service (no authentication required)
                .route("auth-service", r -> r
                        .path("/api/auth/**")
                        .filters(f -> f
                                .stripPrefix(2)
                                .filter(requestLoggingFilter)
                                .filter(rateLimitFilter))
                        .uri("lb://auth-service"))
                
                .build();
    }
}
