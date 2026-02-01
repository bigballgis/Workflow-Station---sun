package com.developer.config;

import com.developer.resilience.CircuitBreakerConfig;
import com.developer.resilience.CircuitBreakerRegistry;
import com.developer.resilience.GracefulDegradationManager;
import com.developer.resilience.ResilienceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration for resilience components including circuit breakers and graceful degradation.
 * 
 * Requirements: 3.5
 */
@Configuration
@EnableScheduling
@Slf4j
public class ResilienceConfig {
    
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return new CircuitBreakerRegistry();
    }
    
    @Bean
    public GracefulDegradationManager gracefulDegradationManager(CircuitBreakerRegistry circuitBreakerRegistry) {
        return new GracefulDegradationManager(circuitBreakerRegistry);
    }
    
    @Bean
    public ResilienceService resilienceService(CircuitBreakerRegistry circuitBreakerRegistry,
                                             GracefulDegradationManager gracefulDegradationManager) {
        return new ResilienceService(circuitBreakerRegistry, gracefulDegradationManager);
    }
    
    @Bean
    @ConfigurationProperties(prefix = "app.resilience")
    public ResilienceProperties resilienceProperties() {
        return new ResilienceProperties();
    }
    
    /**
     * Scheduled health check for resilience components
     */
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void performHealthCheck() {
        try {
            ResilienceService resilienceService = resilienceService(circuitBreakerRegistry(), 
                    gracefulDegradationManager(circuitBreakerRegistry()));
            resilienceService.performHealthCheck();
        } catch (Exception e) {
            log.error("Error during resilience health check", e);
        }
    }
    
    /**
     * Configuration properties for resilience settings
     */
    public static class ResilienceProperties {
        
        private Map<String, CircuitBreakerSettings> circuitBreakers = new ConcurrentHashMap<>();
        private boolean healthCheckEnabled = true;
        private Duration healthCheckInterval = Duration.ofSeconds(30);
        private boolean emergencyModeEnabled = false;
        
        public Map<String, CircuitBreakerSettings> getCircuitBreakers() {
            return circuitBreakers;
        }
        
        public void setCircuitBreakers(Map<String, CircuitBreakerSettings> circuitBreakers) {
            this.circuitBreakers = circuitBreakers;
        }
        
        public boolean isHealthCheckEnabled() {
            return healthCheckEnabled;
        }
        
        public void setHealthCheckEnabled(boolean healthCheckEnabled) {
            this.healthCheckEnabled = healthCheckEnabled;
        }
        
        public Duration getHealthCheckInterval() {
            return healthCheckInterval;
        }
        
        public void setHealthCheckInterval(Duration healthCheckInterval) {
            this.healthCheckInterval = healthCheckInterval;
        }
        
        public boolean isEmergencyModeEnabled() {
            return emergencyModeEnabled;
        }
        
        public void setEmergencyModeEnabled(boolean emergencyModeEnabled) {
            this.emergencyModeEnabled = emergencyModeEnabled;
        }
    }
    
    /**
     * Settings for individual circuit breakers
     */
    public static class CircuitBreakerSettings {
        
        private int failureThreshold = 5;
        private Duration failureWindow = Duration.ofMinutes(1);
        private Duration recoveryTimeout = Duration.ofSeconds(30);
        private int successThreshold = 3;
        private Duration requestTimeout = Duration.ofSeconds(10);
        private boolean autoRecovery = true;
        
        public CircuitBreakerConfig toConfig() {
            return CircuitBreakerConfig.builder()
                    .failureThreshold(failureThreshold)
                    .failureWindow(failureWindow)
                    .recoveryTimeout(recoveryTimeout)
                    .successThreshold(successThreshold)
                    .requestTimeout(requestTimeout)
                    .autoRecovery(autoRecovery)
                    .build();
        }
        
        // Getters and setters
        public int getFailureThreshold() {
            return failureThreshold;
        }
        
        public void setFailureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
        }
        
        public Duration getFailureWindow() {
            return failureWindow;
        }
        
        public void setFailureWindow(Duration failureWindow) {
            this.failureWindow = failureWindow;
        }
        
        public Duration getRecoveryTimeout() {
            return recoveryTimeout;
        }
        
        public void setRecoveryTimeout(Duration recoveryTimeout) {
            this.recoveryTimeout = recoveryTimeout;
        }
        
        public int getSuccessThreshold() {
            return successThreshold;
        }
        
        public void setSuccessThreshold(int successThreshold) {
            this.successThreshold = successThreshold;
        }
        
        public Duration getRequestTimeout() {
            return requestTimeout;
        }
        
        public void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
        }
        
        public boolean isAutoRecovery() {
            return autoRecovery;
        }
        
        public void setAutoRecovery(boolean autoRecovery) {
            this.autoRecovery = autoRecovery;
        }
    }
}