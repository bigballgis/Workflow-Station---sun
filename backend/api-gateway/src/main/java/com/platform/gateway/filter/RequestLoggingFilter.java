package com.platform.gateway.filter;

import com.platform.gateway.config.PlatformGatewayProperties;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Request logging filter with distributed tracing support.
 * Validates: Requirements 5.7, 8.3
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestLoggingFilter implements GatewayFilter, Ordered {
    
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String REQUEST_START_TIME = "requestStartTime";
    
    private final PlatformGatewayProperties gatewayProperties;
    private final Tracer tracer;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        Instant startTime = Instant.now();
        
        // Get or generate trace ID
        String traceId = getTraceId();
        
        // Add trace ID to request headers for downstream services
        ServerHttpRequest modifiedRequest = request.mutate()
                .header(TRACE_ID_HEADER, traceId)
                .build();
        
        // Store start time for duration calculation
        exchange.getAttributes().put(REQUEST_START_TIME, startTime);
        
        // Log request
        logRequest(modifiedRequest, traceId);
        
        return chain.filter(exchange.mutate().request(modifiedRequest).build())
                .then(Mono.fromRunnable(() -> logResponse(exchange, traceId, startTime)));
    }
    
    @Override
    public int getOrder() {
        return -200; // Highest priority
    }
    
    private String getTraceId() {
        if (tracer != null && tracer.currentSpan() != null) {
            return tracer.currentSpan().context().traceId();
        }
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
    
    private void logRequest(ServerHttpRequest request, String traceId) {
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getPath().value();
        String clientIp = request.getRemoteAddress() != null 
                ? request.getRemoteAddress().getAddress().getHostAddress() 
                : "unknown";
        
        log.info("[{}] {} {} from {}", traceId, method, path, clientIp);
        
        if (gatewayProperties.getLogging().isLogHeaders()) {
            request.getHeaders().forEach((name, values) -> {
                if (!gatewayProperties.getLogging().getExcludeHeaders().contains(name.toLowerCase())) {
                    log.debug("[{}] Header: {} = {}", traceId, name, values);
                }
            });
        }
    }
    
    private void logResponse(ServerWebExchange exchange, String traceId, Instant startTime) {
        ServerHttpResponse response = exchange.getResponse();
        Duration duration = Duration.between(startTime, Instant.now());
        
        int statusCode = response.getStatusCode() != null 
                ? response.getStatusCode().value() 
                : 0;
        
        String path = exchange.getRequest().getPath().value();
        
        log.info("[{}] {} {} completed with status {} in {}ms", 
                traceId, 
                exchange.getRequest().getMethod(),
                path,
                statusCode, 
                duration.toMillis());
        
        // Add trace ID to response headers
        response.getHeaders().add(TRACE_ID_HEADER, traceId);
    }
}
