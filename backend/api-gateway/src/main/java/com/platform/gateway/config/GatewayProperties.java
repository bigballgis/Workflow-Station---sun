package com.platform.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gateway configuration properties.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {
    
    /**
     * Rate limiting configuration.
     */
    private RateLimitConfig rateLimit = new RateLimitConfig();
    
    /**
     * Paths that don't require authentication.
     */
    private List<String> publicPaths = new ArrayList<>();
    
    /**
     * Request logging configuration.
     */
    private LoggingConfig logging = new LoggingConfig();
    
    @Data
    public static class RateLimitConfig {
        /**
         * Default requests per second limit.
         */
        private int defaultLimit = 100;
        
        /**
         * Time window in seconds.
         */
        private int windowSeconds = 1;
        
        /**
         * Custom limits per path pattern.
         */
        private Map<String, Integer> pathLimits = new HashMap<>();
        
        /**
         * Whether rate limiting is enabled.
         */
        private boolean enabled = true;
    }
    
    @Data
    public static class LoggingConfig {
        /**
         * Whether to log request headers.
         */
        private boolean logHeaders = false;
        
        /**
         * Whether to log request body.
         */
        private boolean logBody = false;
        
        /**
         * Headers to exclude from logging.
         */
        private List<String> excludeHeaders = new ArrayList<>();
    }
}
