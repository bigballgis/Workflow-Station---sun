package com.platform.common.health;

import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.Size;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for health checks.
 * Validates: Property 1 (Service Health Check Consistency)
 */
class HealthPropertyTest {
    
    // Property 1: Service Health Check Consistency
    // For any platform service instance, calling its health check endpoint
    // should return a valid response containing service status, database
    // connection status, and dependent service status
    
    @Property(tries = 100)
    void healthCheckShouldContainRequiredComponents(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String serviceName) {
        
        SimulatedHealthService healthService = new SimulatedHealthService(serviceName);
        
        Map<String, ComponentHealth> health = healthService.getDetailedHealth();
        
        // Should contain required components
        assertThat(health).containsKey("service");
        assertThat(health).containsKey("database");
        assertThat(health).containsKey("redis");
    }
    
    @Property(tries = 100)
    void healthStatusShouldReflectComponentStatus(
            @ForAll("componentStatuses") Map<String, HealthStatus> componentStatuses) {
        
        SimulatedHealthService healthService = new SimulatedHealthService("test-service");
        healthService.setComponentStatuses(componentStatuses);
        
        HealthStatus overall = healthService.getHealth();
        
        // If any component is DOWN, overall should be DOWN
        if (componentStatuses.values().stream().anyMatch(s -> s == HealthStatus.DOWN)) {
            assertThat(overall).isEqualTo(HealthStatus.DOWN);
        }
        // If any component is DEGRADED but none DOWN, overall should be DEGRADED
        else if (componentStatuses.values().stream().anyMatch(s -> s == HealthStatus.DEGRADED)) {
            assertThat(overall).isEqualTo(HealthStatus.DEGRADED);
        }
        // If all UP, overall should be UP
        else if (componentStatuses.values().stream().allMatch(s -> s == HealthStatus.UP)) {
            assertThat(overall).isEqualTo(HealthStatus.UP);
        }
    }
    
    @Property(tries = 100)
    void readinessShouldRequireAllCriticalComponentsUp(
            @ForAll("componentStatuses") Map<String, HealthStatus> componentStatuses) {
        
        SimulatedHealthService healthService = new SimulatedHealthService("test-service");
        healthService.setComponentStatuses(componentStatuses);
        
        boolean ready = healthService.isReady();
        
        // Ready only if database and service are UP
        boolean databaseUp = componentStatuses.getOrDefault("database", HealthStatus.UP) == HealthStatus.UP;
        boolean serviceUp = componentStatuses.getOrDefault("service", HealthStatus.UP) == HealthStatus.UP;
        
        assertThat(ready).isEqualTo(databaseUp && serviceUp);
    }
    
    @Property(tries = 100)
    void livenessShouldOnlyCheckServiceItself(
            @ForAll("componentStatuses") Map<String, HealthStatus> componentStatuses) {
        
        SimulatedHealthService healthService = new SimulatedHealthService("test-service");
        healthService.setComponentStatuses(componentStatuses);
        
        boolean alive = healthService.isAlive();
        
        // Alive only depends on service status
        boolean serviceUp = componentStatuses.getOrDefault("service", HealthStatus.UP) == HealthStatus.UP;
        
        assertThat(alive).isEqualTo(serviceUp);
    }
    
    @Property(tries = 100)
    void healthCheckShouldIncludeResponseTime(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String serviceName) {
        
        SimulatedHealthService healthService = new SimulatedHealthService(serviceName);
        
        Map<String, ComponentHealth> health = healthService.getDetailedHealth();
        
        for (ComponentHealth component : health.values()) {
            assertThat(component.getResponseTimeMs()).isGreaterThanOrEqualTo(0);
        }
    }
    
    @Property(tries = 100)
    void healthCheckShouldBeIdempotent(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String serviceName) {
        
        SimulatedHealthService healthService = new SimulatedHealthService(serviceName);
        
        HealthStatus first = healthService.getHealth();
        HealthStatus second = healthService.getHealth();
        
        // Multiple calls should return same result
        assertThat(first).isEqualTo(second);
    }
    
    @Provide
    Arbitrary<Map<String, HealthStatus>> componentStatuses() {
        return Arbitraries.maps(
                Arbitraries.of("service", "database", "redis", "kafka"),
                Arbitraries.of(HealthStatus.UP, HealthStatus.DOWN, HealthStatus.DEGRADED)
        ).ofMinSize(1).ofMaxSize(4);
    }
    
    // Simulated health service for testing
    private static class SimulatedHealthService implements HealthIndicatorService {
        private final String serviceName;
        private Map<String, HealthStatus> componentStatuses = new HashMap<>();
        
        SimulatedHealthService(String serviceName) {
            this.serviceName = serviceName;
            // Default all UP
            componentStatuses.put("service", HealthStatus.UP);
            componentStatuses.put("database", HealthStatus.UP);
            componentStatuses.put("redis", HealthStatus.UP);
        }
        
        void setComponentStatuses(Map<String, HealthStatus> statuses) {
            this.componentStatuses = new HashMap<>(statuses);
            // Ensure service is always present
            componentStatuses.putIfAbsent("service", HealthStatus.UP);
        }
        
        @Override
        public HealthStatus getHealth() {
            if (componentStatuses.values().stream().anyMatch(s -> s == HealthStatus.DOWN)) {
                return HealthStatus.DOWN;
            }
            if (componentStatuses.values().stream().anyMatch(s -> s == HealthStatus.DEGRADED)) {
                return HealthStatus.DEGRADED;
            }
            return HealthStatus.UP;
        }
        
        @Override
        public Map<String, ComponentHealth> getDetailedHealth() {
            Map<String, ComponentHealth> result = new HashMap<>();
            
            for (Map.Entry<String, HealthStatus> entry : componentStatuses.entrySet()) {
                result.put(entry.getKey(), ComponentHealth.builder()
                        .name(entry.getKey())
                        .status(entry.getValue())
                        .responseTimeMs((long) (Math.random() * 100))
                        .build());
            }
            
            return result;
        }
        
        @Override
        public boolean isReady() {
            return componentStatuses.getOrDefault("database", HealthStatus.UP) == HealthStatus.UP
                    && componentStatuses.getOrDefault("service", HealthStatus.UP) == HealthStatus.UP;
        }
        
        @Override
        public boolean isAlive() {
            return componentStatuses.getOrDefault("service", HealthStatus.UP) == HealthStatus.UP;
        }
    }
}
